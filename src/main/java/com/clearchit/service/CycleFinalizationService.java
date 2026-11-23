package com.clearchit.service;

import com.clearchit.model.Bid;
import com.clearchit.model.BidStatus;
import com.clearchit.model.ChitGroupMember;
import com.clearchit.model.CycleStatus;
import com.clearchit.model.Member;
import com.clearchit.model.MonthlyCycle;
import com.clearchit.repository.BidRepository;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.MonthlyCycleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CycleFinalizationService {

    private final MonthlyCycleRepository monthlyCycleRepository;
    private final BidRepository bidRepository;
    private final ChitGroupMemberRepository chitGroupMemberRepository;

    public CycleFinalizationService(
            MonthlyCycleRepository monthlyCycleRepository,
            BidRepository bidRepository,
            ChitGroupMemberRepository chitGroupMemberRepository) {

        this.monthlyCycleRepository = monthlyCycleRepository;
        this.bidRepository = bidRepository;
        this.chitGroupMemberRepository = chitGroupMemberRepository;
    }

    @Transactional
    public void finalizeCycle(MonthlyCycle cycle) {

        /*
         * Lock the cycle row before doing anything.
         */
        MonthlyCycle lockedCycle =
                monthlyCycleRepository
                        .findByIdForUpdate(cycle.getId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Cycle not found"));

        /*
         * If another transaction already finalized
         * this cycle, stop.
         */
        if (lockedCycle.getStatus() != CycleStatus.BIDDING_OPEN) {
            return;
        }

        List<Bid> bids =
                bidRepository.findByCycleId(
                        lockedCycle.getId());

        Member winner;
        BigDecimal winningBidAmount;

        /*
         * CASE 1:
         * At least one bid exists.
         */
        if (!bids.isEmpty()) {

            /*
             * Winner rules:
             *
             * 1. Lowest bid amount
             * 2. If tied -> earliest bid
             * 3. If timestamp also tied -> lower ID
             */
            Bid winningBid =
                    bids.stream()
                            .min(
                                    Comparator
                                            .comparing(
                                                    Bid::getBidAmount)
                                            .thenComparing(
                                                    Bid::getCreatedAt)
                                            .thenComparing(
                                                    Bid::getId)
                            )
                            .orElseThrow();

            winner = winningBid.getMember();
            winningBidAmount =
                    winningBid.getBidAmount();

            /*
             * Mark winner and reject the rest.
             */
            for (Bid bid : bids) {

                if (bid.getId()
                        .equals(winningBid.getId())) {

                    bid.setStatus(
                            BidStatus.WINNING);

                } else {

                    bid.setStatus(
                            BidStatus.REJECTED);
                }
            }

            bidRepository.saveAll(bids);

        }

        /*
         * CASE 2:
         * Nobody placed a bid.
         *
         * Randomly select someone who hasn't
         * won before.
         */
        else {

            List<ChitGroupMember> memberships =
                    chitGroupMemberRepository
                            .findByChitGroupId(
                                    lockedCycle
                                            .getChitGroup()
                                            .getId());

            List<Member> eligibleMembers =
                    memberships.stream()
                            .map(ChitGroupMember::getMember)
                            .filter(member ->
                                    !hasAlreadyWon(
                                            lockedCycle
                                                    .getChitGroup()
                                                    .getId(),
                                            member.getId()))
                            .toList();

            if (eligibleMembers.isEmpty()) {
                throw new RuntimeException(
                        "No eligible members available");
            }

            int randomIndex =
                    ThreadLocalRandom.current()
                            .nextInt(
                                    eligibleMembers.size());

            winner =
                    eligibleMembers.get(randomIndex);

            /*
             * No bid means winner receives the
             * full chit value.
             */
            winningBidAmount =
                    lockedCycle
                            .getChitGroup()
                            .getChitValue();
        }

        BigDecimal chitValue =
                lockedCycle
                        .getChitGroup()
                        .getChitValue();

        /*
         * Discount = Chit value - winning bid
         */
        BigDecimal discount =
                chitValue.subtract(
                        winningBidAmount);

        /*
         * Commission =
         * discount × commission percentage / 100
         */
        BigDecimal commission =
                discount
                        .multiply(
                                lockedCycle
                                        .getChitGroup()
                                        .getOrganiserCommissionRate())
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP);

        /*
         * Remaining discount becomes dividend pool.
         */
        BigDecimal dividendPool =
                discount.subtract(commission);

        /*
         * Number of members receiving dividend.
         * Winner does not receive dividend.
         */
        int dividendMembers =
                chitGroupMemberRepository
                        .findByChitGroupId(
                                lockedCycle
                                        .getChitGroup()
                                        .getId())
                        .size() - 1;

        /*
         * This field is kept for now as an approximate
         * per-member value.
         *
         * Actual individual dividend amounts will be
         * calculated by DividendService so that
         * rounding remainders are not lost.
         */
        BigDecimal dividendPerMember =
                BigDecimal.ZERO;

        if (dividendMembers > 0) {

            dividendPerMember =
                    dividendPool.divide(
                            BigDecimal.valueOf(
                                    dividendMembers),
                            2,
                            RoundingMode.DOWN);
        }

        /*
         * Winner receives the winning bid amount.
         */
        BigDecimal payout =
                winningBidAmount;

        /*
         * Store final cycle results.
         */
        lockedCycle.setWinner(winner);

        lockedCycle.setWinningBidAmount(
                winningBidAmount);

        lockedCycle.setDiscount(
                discount);

        lockedCycle.setCommission(
                commission);

        lockedCycle.setDividendPool(
                dividendPool);

        lockedCycle.setDividendPerMember(
                dividendPerMember);

        lockedCycle.setPayout(
                payout);

        lockedCycle.setStatus(
                CycleStatus.COMPLETED);

        monthlyCycleRepository.save(
                lockedCycle);
    }

    private boolean hasAlreadyWon(
            Long groupId,
            Long memberId) {

        // Winners of a no-bid (random) month have no WINNING bid row, so
        // also check the cycle's recorded winner.
        return bidRepository
                .existsByCycle_ChitGroup_IdAndMember_IdAndStatus(
                        groupId,
                        memberId,
                        BidStatus.WINNING)
                || monthlyCycleRepository
                .existsByChitGroupIdAndWinnerId(
                        groupId,
                        memberId);
    }
}
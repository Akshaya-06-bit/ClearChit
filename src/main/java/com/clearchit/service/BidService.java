package com.clearchit.service;

import com.clearchit.dto.BidRequest;
import com.clearchit.dto.BidResponse;
import com.clearchit.model.Bid;
import com.clearchit.model.BidStatus;
import com.clearchit.model.ChitGroupMember;
import com.clearchit.model.CycleStatus;
import com.clearchit.model.Member;
import com.clearchit.model.MonthlyCycle;
import com.clearchit.repository.BidRepository;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.MemberRepository;
import com.clearchit.repository.MonthlyCycleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BidService {

    private final BidRepository bidRepository;
    private final MonthlyCycleRepository monthlyCycleRepository;
    private final MemberRepository memberRepository;
    private final ChitGroupMemberRepository chitGroupMemberRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public BidService(
            BidRepository bidRepository,
            MonthlyCycleRepository monthlyCycleRepository,
            MemberRepository memberRepository,
            ChitGroupMemberRepository chitGroupMemberRepository,
            AuthorizationService authorizationService,
            AuditService auditService) {

        this.bidRepository = bidRepository;
        this.monthlyCycleRepository = monthlyCycleRepository;
        this.memberRepository = memberRepository;
        this.chitGroupMemberRepository = chitGroupMemberRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /*
     * Place a bid.
     *
     * The cycle row is locked before we perform
     * any bidding checks.
     *
     * This prevents a race between:
     *
     * 1. Someone submitting a bid
     * 2. Scheduler finalizing the cycle
     */
    @Transactional
    public Bid placeBid(
            Long cycleId,
            BidRequest request) {

        /*
         * LOCK THE CYCLE FIRST.
         *
         * This ensures bid submission and cycle
         * finalization cannot make decisions about
         * the same cycle simultaneously.
         */
        MonthlyCycle cycle =
                monthlyCycleRepository
                        .findByIdForUpdate(cycleId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Cycle not found"));

        /*
         * Check the status AFTER acquiring the lock.
         */
        // UPCOMING is accepted too: the scheduler flips the status within
        // seconds of the start time, and the time window below is the real
        // rule.
        if (cycle.getStatus() != CycleStatus.BIDDING_OPEN
                && cycle.getStatus() != CycleStatus.UPCOMING) {

            throw new RuntimeException(
                    "Bidding is closed for this cycle");
        }

        /*
         * Check the actual bidding time.
         *
         * The scheduler may not have changed the
         * status yet, so we must independently verify
         * the configured time window.
         */
        LocalDateTime now =
                LocalDateTime.now();

        if (now.isBefore(
                cycle.getBiddingStartTime())) {

            throw new RuntimeException(
                    "Bidding has not started yet. It opens at "
                            + cycle.getBiddingStartTime()
                            .toString().replace('T', ' '));
        }

        if (!now.isBefore(
                cycle.getBiddingEndTime())) {

            throw new RuntimeException(
                    "Bidding period has ended. Bids can no longer be "
                            + "placed or changed.");
        }

        /*
         * -------------------------------------------------
         * BID AMOUNT VALIDATION
         * -------------------------------------------------
         *
         * Bid must be:
         *
         *     0 < bidAmount <= chitValue
         *
         * The lower bound is already handled by
         * @DecimalMin in BidRequest.
         *
         * Here we enforce the upper bound using the
         * actual chit group's value.
         */
        BigDecimal bidAmount =
                request.getBidAmount();

        BigDecimal chitValue =
                cycle.getChitGroup()
                        .getChitValue();

        if (bidAmount.compareTo(chitValue) > 0) {

            throw new RuntimeException(
                    "Bid amount cannot exceed the chit value");
        }

        /*
         * The owner organises the group and never bids.
         */
        if (cycle.getChitGroup().getOwner() != null
                && cycle.getChitGroup().getOwner().getId()
                .equals(request.getMemberId())) {

            throw new RuntimeException(
                    "The group owner organises the group and cannot bid");
        }

        /*
         * Check whether the member exists.
         */
        authorizationService.requireGroupMember(
                cycle.getChitGroup().getId(),
                request.getMemberId());

        Member member =
                memberRepository
                        .findById(request.getMemberId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Member not found"));

        /*
         * Member must belong to this chit group.
         */
        boolean isMember =
                chitGroupMemberRepository
                        .existsByChitGroupIdAndMemberId(
                                cycle.getChitGroup().getId(),
                                member.getId());

        if (!isMember) {

            throw new RuntimeException(
                    "Member does not belong to this chit group");
        }

        /*
         * A member who already won a previous cycle
         * cannot bid again.
         */
        boolean alreadyWon =
                bidRepository
                        .existsByCycle_ChitGroup_IdAndMember_IdAndStatus(
                                cycle.getChitGroup().getId(),
                                member.getId(),
                                BidStatus.WINNING)
                        || monthlyCycleRepository
                        .existsByChitGroupIdAndWinnerId(
                                cycle.getChitGroup().getId(),
                                member.getId());

        if (alreadyWon) {

            throw new RuntimeException(
                    "Member has already won a previous cycle");
        }

        /*
         * Only one bid per member per cycle.
         *
         * Application-level check.
         *
         * The database UNIQUE(cycle_id, member_id)
         * constraint is the final safety net.
         */
        Optional<Bid> existingBid =
                bidRepository
                        .findByCycleIdAndMemberId(
                                cycleId,
                                member.getId());

        /*
         * The member already bid: while bidding is open they may change
         * their bid. createdAt is refreshed because it is the tie-breaker
         * (earliest time at that amount wins).
         */
        if (existingBid.isPresent()) {

            Bid bid = existingBid.get();

            bid.setBidAmount(bidAmount);
            bid.setCreatedAt(now);
            bid.setStatus(BidStatus.ACTIVE);

            Bid updated = bidRepository.save(bid);

            auditService.log(
                    com.clearchit.model.AuditAction.BID_PLACED,
                    "CYCLE",
                    cycleId,
                    "Bid updated by member " + member.getId());

            return updated;
        }

        /*
         * Create the bid.
         */
        Bid bid =
                new Bid();

        bid.setCycle(cycle);
        bid.setMember(member);
        bid.setBidAmount(bidAmount);
        bid.setCreatedAt(now);
        bid.setStatus(BidStatus.ACTIVE);

        /*
         * Save inside the same transaction
         * while the cycle remains locked.
         */
        Bid saved = bidRepository.save(bid);
        auditService.log(com.clearchit.model.AuditAction.BID_PLACED, "CYCLE", cycleId, "Bid placed by member " + member.getId());
        return saved;
    }

    public List<BidResponse> getBidsByCycle(
            Long cycleId) {

        return bidRepository
                .findByCycleId(cycleId)
                .stream()
                .map(bid -> {

                    BidResponse response =
                            new BidResponse();

                    response.setId(
                            bid.getId());

                    response.setMemberId(
                            bid.getMember().getId());

                    response.setMemberName(
                            bid.getMember().getName());

                    response.setMemberEmail(
                            bid.getMember().getEmail());

                    response.setBidAmount(
                            bid.getBidAmount());

                    response.setCreatedAt(
                            bid.getCreatedAt());

                    response.setStatus(
                            bid.getStatus());

                    return response;
                })
                .toList();
    }
}


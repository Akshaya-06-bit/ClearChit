package com.clearchit.service;

import com.clearchit.exception.BadRequestException;
import com.clearchit.model.*;
import com.clearchit.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CycleSettlementService {

    private final MonthlyCycleRepository monthlyCycleRepository;
    private final MonthlyPaymentRepository monthlyPaymentRepository;
    private final ChitGroupMemberRepository chitGroupMemberRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final DividendRepository dividendRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public CycleSettlementService(
            MonthlyCycleRepository monthlyCycleRepository,
            MonthlyPaymentRepository monthlyPaymentRepository,
            ChitGroupMemberRepository chitGroupMemberRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            DividendRepository dividendRepository,
            AuthorizationService authorizationService,
            AuditService auditService) {

        this.monthlyCycleRepository = monthlyCycleRepository;
        this.monthlyPaymentRepository = monthlyPaymentRepository;
        this.chitGroupMemberRepository = chitGroupMemberRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.dividendRepository = dividendRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /*
     * Called when the GROUP OWNER presses "Distribute payout".
     *
     * Pays the winner, credits the organiser commission and pays the
     * dividend to every other member, all in ONE transaction: if anything
     * fails, nothing is paid.
     */
    @Transactional
    public MonthlyCycle distributePayout(Long cycleId) {

        MonthlyCycle cycle =
                monthlyCycleRepository
                        .findByIdForUpdate(cycleId)
                        .orElseThrow(() ->
                                new BadRequestException("Cycle not found"));

        authorizationService.requireGroupOwner(cycle.getChitGroup());

        if (cycle.isPayoutReleased()) {
            throw new BadRequestException(
                    "The payout for this cycle has already been distributed.");
        }

        if (cycle.getStatus() != CycleStatus.COMPLETED
                || cycle.getWinner() == null
                || cycle.getPayout() == null) {
            throw new BadRequestException(
                    "The winner has not been decided yet. The payout can be "
                            + "distributed after bidding ends ("
                            + cycle.getBiddingEndTime()
                            .toString().replace('T', ' ') + ").");
        }

        Long groupId = cycle.getChitGroup().getId();

        long totalMembers =
                chitGroupMemberRepository.countByChitGroupId(groupId);

        long paidMembers =
                monthlyPaymentRepository.countByCycleIdAndStatus(
                        cycleId,
                        PaymentStatus.PAID);

        if (totalMembers == 0 || paidMembers != totalMembers) {
            throw new BadRequestException(
                    "Cannot distribute yet: " + paidMembers + " of "
                            + totalMembers + " members have paid their "
                            + "contribution for this cycle.");
        }

        releasePayout(cycle);
        releaseDividends(cycle);

        // Last cycle paid out -> the group is finished.
        ChitGroup group = cycle.getChitGroup();

        if (cycle.getCycleNumber()
                >= Math.min(group.getDurationMonths(),
                group.getMaxMembers())) {
            group.setStatus(ChitStatus.COMPLETED);
        }

        MonthlyCycle saved = monthlyCycleRepository.save(cycle);

        auditService.log(
                AuditAction.PAYOUT_RELEASED,
                "CYCLE",
                cycleId,
                "Payout of " + cycle.getPayout()
                        + " distributed to member "
                        + cycle.getWinner().getId());

        return saved;
    }

    private void releasePayout(MonthlyCycle cycle) {

        Long winnerId =
                cycle.getWinner().getId();

        Wallet wallet =
                walletRepository
                        .findByMemberIdForUpdate(winnerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Winner wallet not found"));

        BigDecimal payout =
                cycle.getPayout();

        BigDecimal newBalance =
                wallet.getBalance().add(payout);

        wallet.setBalance(newBalance);

        walletRepository.save(wallet);

        /*
         * Create financial transaction record.
         */
        WalletTransaction transaction =
                new WalletTransaction();

        transaction.setWallet(wallet);
        transaction.setMember(cycle.getWinner());
        transaction.setCycle(cycle);
        transaction.setType(WalletTransactionType.PAYOUT);
        transaction.setAmount(payout);
        transaction.setBalanceAfter(newBalance);
        transaction.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(transaction);

        /*
         * The organiser's commission is part of the money collected this
         * month. It was calculated but never credited, so that money simply
         * disappeared from the books.
         */
        creditCommission(cycle);

        /*
         * Mark payout as released.
         */
        cycle.setPayoutReleased(true);
    }

    private void creditCommission(MonthlyCycle cycle) {

        BigDecimal commission = cycle.getCommission();
        Member owner = cycle.getChitGroup().getOwner();

        if (commission == null
                || commission.compareTo(BigDecimal.ZERO) <= 0
                || owner == null) {
            return;
        }

        Wallet ownerWallet =
                walletRepository
                        .findByMemberIdForUpdate(owner.getId())
                        .orElseGet(() -> {
                            Wallet created = new Wallet();
                            created.setMember(owner);
                            created.setBalance(BigDecimal.ZERO);
                            return walletRepository.save(created);
                        });

        BigDecimal newBalance =
                ownerWallet.getBalance().add(commission);

        ownerWallet.setBalance(newBalance);
        walletRepository.save(ownerWallet);

        // Recorded as PAYOUT (owner's wallet, linked to the cycle). A new
        // enum value would clash with the DB check constraint that
        // ddl-auto=update created for existing databases.
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(ownerWallet);
        transaction.setMember(owner);
        transaction.setCycle(cycle);
        transaction.setType(WalletTransactionType.PAYOUT);
        transaction.setAmount(commission);
        transaction.setBalanceAfter(newBalance);
        transaction.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(transaction);
    }

    private void releaseDividends(MonthlyCycle cycle) {

        BigDecimal dividendPool =
                cycle.getDividendPool();

        /*
         * No dividend to distribute.
         *
         * Example:
         * no-bid month -> discount = 0
         * -> dividend pool = 0
         */
        if (dividendPool == null
                || dividendPool.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Long groupId =
                cycle.getChitGroup().getId();

        Long winnerId =
                cycle.getWinner().getId();

        /*
         * Get all members except the winner.
         */
        List<Member> eligibleMembers =
                chitGroupMemberRepository
                        .findByChitGroupId(groupId)
                        .stream()
                        .map(ChitGroupMember::getMember)
                        .filter(member ->
                                !member.getId().equals(winnerId))
                        .sorted((a, b) ->
                                a.getId().compareTo(b.getId()))
                        .toList();

        if (eligibleMembers.isEmpty()) {
            return;
        }

        int memberCount =
                eligibleMembers.size();

        /*
         * Divide the pool to two decimal places.
         *
         * Example:
         *
         * ₹100 / 3
         *
         * base = ₹33.33
         * remainder = ₹0.01
         */
        BigDecimal baseDividend =
                dividendPool.divide(
                        BigDecimal.valueOf(memberCount),
                        2,
                        RoundingMode.DOWN
                );

        BigDecimal distributed =
                baseDividend.multiply(
                        BigDecimal.valueOf(memberCount));

        BigDecimal remainder =
                dividendPool.subtract(distributed);

        int extraPaisa =
                remainder
                        .movePointRight(2)
                        .intValueExact();

        /*
         * Credit each member.
         */
        for (int i = 0;
             i < eligibleMembers.size();
             i++) {

            Member member =
                    eligibleMembers.get(i);

            /*
             * Idempotency protection.
             *
             * If this dividend already exists,
             * don't credit the wallet again.
             */
            if (dividendRepository
                    .existsByCycleIdAndMemberId(
                            cycle.getId(),
                            member.getId())) {

                continue;
            }

            BigDecimal amount =
                    baseDividend;

            /*
             * Distribute leftover paisa deterministically.
             *
             * Example:
             *
             * ₹100 / 3
             *
             * Member 1 -> 33.34
             * Member 2 -> 33.33
             * Member 3 -> 33.33
             */
            if (i < extraPaisa) {
                amount =
                        amount.add(
                                new BigDecimal("0.01"));
            }

            /*
             * Lock this member's wallet.
             */
            Wallet wallet =
                    walletRepository
                            .findByMemberIdForUpdate(
                                    member.getId())
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Wallet not found for member "
                                                    + member.getId()));

            BigDecimal newBalance =
                    wallet.getBalance()
                            .add(amount);

            wallet.setBalance(newBalance);

            walletRepository.save(wallet);

            /*
             * Financial ledger entry.
             */
            WalletTransaction transaction =
                    new WalletTransaction();

            transaction.setWallet(wallet);
            transaction.setMember(member);
            transaction.setCycle(cycle);
            transaction.setType(WalletTransactionType.DIVIDEND);
            transaction.setAmount(amount);
            transaction.setBalanceAfter(newBalance);
            transaction.setCreatedAt(LocalDateTime.now());

            walletTransactionRepository.save(
                    transaction);

            /*
             * Dividend record.
             */
            Dividend dividend =
                    new Dividend();

            dividend.setCycle(cycle);
            dividend.setMember(member);
            dividend.setAmount(amount);
            dividend.setCreditedAt(
                    LocalDateTime.now());

            dividendRepository.save(dividend);
        }
    }
}
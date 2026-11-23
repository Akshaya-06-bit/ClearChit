package com.clearchit.service;

import com.clearchit.model.*;
import com.clearchit.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PayoutService {

    private final MonthlyPaymentRepository monthlyPaymentRepository;
    private final ChitGroupMemberRepository chitGroupMemberRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final MonthlyCycleRepository monthlyCycleRepository;

    public PayoutService(
            MonthlyPaymentRepository monthlyPaymentRepository,
            ChitGroupMemberRepository chitGroupMemberRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            MonthlyCycleRepository monthlyCycleRepository) {

        this.monthlyPaymentRepository = monthlyPaymentRepository;
        this.chitGroupMemberRepository = chitGroupMemberRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.monthlyCycleRepository = monthlyCycleRepository;
    }

    @Transactional
    public void tryReleasePayout(MonthlyCycle cycle) {

        /*
         * Re-fetch and LOCK the cycle row.
         *
         * This prevents two concurrent transactions
         * from releasing the same payout.
         */
        MonthlyCycle lockedCycle =
                monthlyCycleRepository
                        .findByIdForUpdate(cycle.getId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Cycle not found"));

        // Already released
        if (lockedCycle.isPayoutReleased()) {
            return;
        }

        // Cycle must be completed
        if (lockedCycle.getStatus()
                != CycleStatus.COMPLETED) {
            return;
        }

        // Winner must exist
        if (lockedCycle.getWinner() == null) {
            return;
        }

        // Payout amount must exist
        if (lockedCycle.getPayout() == null) {
            return;
        }

        Long groupId =
                lockedCycle.getChitGroup().getId();

        // Total members
        long totalMembers =
                chitGroupMemberRepository
                        .countByChitGroupId(groupId);

        // Members who have paid
        long paidMembers =
                monthlyPaymentRepository
                        .countByCycleIdAndStatus(
                                lockedCycle.getId(),
                                PaymentStatus.PAID);

        /*
         * Do NOT release the payout until
         * everyone has contributed.
         */
        if (totalMembers == 0
                || paidMembers != totalMembers) {
            return;
        }

        // Winner's wallet
        Wallet wallet =
                walletRepository
                        .findByMemberId(
                                lockedCycle
                                        .getWinner()
                                        .getId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Winner wallet not found"));

        BigDecimal payout =
                lockedCycle.getPayout();

        // Update wallet
        BigDecimal newBalance =
                wallet.getBalance()
                        .add(payout);

        wallet.setBalance(newBalance);

        walletRepository.save(wallet);

        // Create financial ledger entry
        WalletTransaction transaction =
                new WalletTransaction();

        transaction.setWallet(wallet);
        transaction.setType(
                WalletTransactionType.PAYOUT);
        transaction.setAmount(payout);
        transaction.setBalanceAfter(newBalance);
        transaction.setCreatedAt(
                LocalDateTime.now());

        walletTransactionRepository.save(transaction);

        /*
         * Mark payout as released.
         *
         * Because this happens inside the SAME
         * transaction, either everything commits
         * or everything rolls back.
         */
        lockedCycle.setPayoutReleased(true);

        monthlyCycleRepository.save(lockedCycle);
    }
}
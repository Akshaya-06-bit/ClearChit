package com.clearchit.service;

import com.clearchit.model.ChitGroupMember;
import com.clearchit.model.CycleStatus;
import com.clearchit.model.Dividend;
import com.clearchit.model.Member;
import com.clearchit.model.MonthlyCycle;
import com.clearchit.model.PaymentStatus;
import com.clearchit.model.Wallet;
import com.clearchit.model.WalletTransaction;
import com.clearchit.model.WalletTransactionType;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.DividendRepository;
import com.clearchit.repository.MonthlyCycleRepository;
import com.clearchit.repository.MonthlyPaymentRepository;
import com.clearchit.repository.WalletRepository;
import com.clearchit.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.List;

@Service
public class DividendService {

    private final DividendRepository dividendRepository;
    private final ChitGroupMemberRepository chitGroupMemberRepository;
    private final MonthlyPaymentRepository monthlyPaymentRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final MonthlyCycleRepository monthlyCycleRepository;

    public DividendService(
            DividendRepository dividendRepository,
            ChitGroupMemberRepository chitGroupMemberRepository,
            MonthlyPaymentRepository monthlyPaymentRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            MonthlyCycleRepository monthlyCycleRepository) {

        this.dividendRepository =
                dividendRepository;

        this.chitGroupMemberRepository =
                chitGroupMemberRepository;

        this.monthlyPaymentRepository =
                monthlyPaymentRepository;

        this.walletRepository =
                walletRepository;

        this.walletTransactionRepository =
                walletTransactionRepository;

        this.monthlyCycleRepository =
                monthlyCycleRepository;
    }

    @Transactional
    public void releaseDividends(
            MonthlyCycle cycle) {

        /*
         * Lock cycle before processing.
         */
        MonthlyCycle lockedCycle =
                monthlyCycleRepository
                        .findByIdForUpdate(
                                cycle.getId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Cycle not found"));

        /*
         * Dividends can only be released
         * after cycle completion.
         */
        if (lockedCycle.getStatus()
                != CycleStatus.COMPLETED) {

            return;
        }

        if (lockedCycle.getWinner() == null) {
            return;
        }

        if (lockedCycle.getDividendPool() == null
                || lockedCycle
                .getDividendPool()
                .compareTo(BigDecimal.ZERO) <= 0) {

            return;
        }

        Long cycleId =
                lockedCycle.getId();

        Long groupId =
                lockedCycle
                        .getChitGroup()
                        .getId();

        Long winnerId =
                lockedCycle
                        .getWinner()
                        .getId();

        /*
         * Everyone must have paid before
         * dividends are released.
         */
        long totalMembers =
                chitGroupMemberRepository
                        .countByChitGroupId(groupId);

        long paidMembers =
                monthlyPaymentRepository
                        .countByCycleIdAndStatus(
                                cycleId,
                                PaymentStatus.PAID);

        if (totalMembers == 0
                || paidMembers != totalMembers) {

            return;
        }

        /*
         * Winner doesn't receive dividend.
         */
        List<Member> eligibleMembers =
                chitGroupMemberRepository
                        .findByChitGroupId(groupId)
                        .stream()
                        .map(ChitGroupMember::getMember)
                        .filter(member ->
                                !member.getId()
                                        .equals(winnerId))
                        .toList();

        if (eligibleMembers.isEmpty()) {
            return;
        }

        BigDecimal dividendPool =
                lockedCycle
                        .getDividendPool();

        int memberCount =
                eligibleMembers.size();

        /*
         * Base dividend.
         *
         * Example:
         *
         * 100 / 3 = 33.33
         */
        BigDecimal baseDividend =
                dividendPool.divide(
                        BigDecimal.valueOf(
                                memberCount),
                        2,
                        RoundingMode.DOWN);

        /*
         * Calculate how much we distributed
         * using the base amount.
         */
        BigDecimal distributed =
                baseDividend.multiply(
                        BigDecimal.valueOf(
                                memberCount));

        /*
         * Calculate leftover paisa.
         */
        BigDecimal remainder =
                dividendPool.subtract(
                        distributed);

        int extraPaisa =
                remainder
                        .movePointRight(2)
                        .intValueExact();

        /*
         * Credit each eligible member.
         */
        for (int i = 0;
             i < eligibleMembers.size();
             i++) {

            Member member =
                    eligibleMembers.get(i);

            /*
             * Extra paisa goes to the first
             * few members.
             */
            BigDecimal amount =
                    baseDividend;

            if (i < extraPaisa) {

                amount =
                        amount.add(
                                new BigDecimal("0.01"));
            }

            /*
             * Safety check against duplicate
             * dividend records.
             */
            if (dividendRepository
                    .existsByCycleIdAndMemberId(
                            cycleId,
                            member.getId())) {

                continue;
            }

            /*
             * Lock the member wallet.
             */
            Wallet wallet =
                    walletRepository
                            .findByMemberIdForUpdate(
                                    member.getId())
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Wallet not found for member "
                                                    + member.getId()));

            /*
             * Add dividend.
             */
            BigDecimal newBalance =
                    wallet.getBalance()
                            .add(amount);

            wallet.setBalance(
                    newBalance);

            walletRepository.save(wallet);

            /*
             * Financial ledger entry.
             */
            WalletTransaction transaction =
                    new WalletTransaction();

            transaction.setWallet(wallet);

            transaction.setType(
                    WalletTransactionType.DIVIDEND);

            transaction.setAmount(
                    amount);

            transaction.setBalanceAfter(
                    newBalance);

            transaction.setCreatedAt(
                    LocalDateTime.now());

            walletTransactionRepository.save(
                    transaction);

            /*
             * Business dividend record.
             */
            Dividend dividend =
                    new Dividend();

            dividend.setCycle(
                    lockedCycle);

            dividend.setMember(
                    member);

            dividend.setAmount(
                    amount);

            dividend.setCreditedAt(
                    LocalDateTime.now());

            dividendRepository.save(
                    dividend);
        }
    }
}
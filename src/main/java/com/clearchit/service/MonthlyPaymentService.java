package com.clearchit.service;

import com.clearchit.dto.PaymentSummaryResponse;
import com.clearchit.exception.BadRequestException;
import com.clearchit.model.MonthlyCycle;
import com.clearchit.model.MonthlyPayment;
import com.clearchit.model.PaymentStatus;
import com.clearchit.model.Wallet;
import com.clearchit.model.WalletTransaction;
import com.clearchit.model.WalletTransactionType;
import com.clearchit.model.Member;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.MonthlyCycleRepository;
import com.clearchit.repository.MonthlyPaymentRepository;
import com.clearchit.repository.WalletRepository;
import com.clearchit.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MonthlyPaymentService {

    private final MonthlyPaymentRepository monthlyPaymentRepository;
    private final MonthlyCycleRepository monthlyCycleRepository;
    private final ChitGroupMemberRepository chitGroupMemberRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public MonthlyPaymentService(
            MonthlyPaymentRepository monthlyPaymentRepository,
            MonthlyCycleRepository monthlyCycleRepository,
            ChitGroupMemberRepository chitGroupMemberRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            AuthorizationService authorizationService,
            AuditService auditService) {

        this.monthlyPaymentRepository =
                monthlyPaymentRepository;

        this.monthlyCycleRepository =
                monthlyCycleRepository;

        this.chitGroupMemberRepository =
                chitGroupMemberRepository;

        this.walletRepository =
                walletRepository;

        this.walletTransactionRepository =
                walletTransactionRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /*
     * Create payment records for every member
     * who belongs to this cycle's group.
     *
     * This method is intentionally idempotent.
     */
    @Transactional
    public void createPaymentsForCycle(
            MonthlyCycle cycle) {

        List<com.clearchit.model.ChitGroupMember> memberships =
                chitGroupMemberRepository
                        .findByChitGroupId(
                                cycle.getChitGroup().getId());

        for (com.clearchit.model.ChitGroupMember membership
                : memberships) {

            Member member =
                    membership.getMember();

            boolean exists =
                    monthlyPaymentRepository
                            .existsByCycleIdAndMemberId(
                                    cycle.getId(),
                                    member.getId());

            if (exists) {
                continue;
            }

            MonthlyPayment payment =
                    new MonthlyPayment();

            payment.setCycle(cycle);
            payment.setMember(member);

            payment.setAmount(
                    cycle.getChitGroup()
                            .getMonthlyContribution());

            payment.setStatus(
                    PaymentStatus.PENDING);

            monthlyPaymentRepository.save(payment);
        }
    }

    /*
     * Pay one member's contribution.
     */
    @Transactional
    public MonthlyPayment payContribution(
            Long cycleId,
            Long memberId) {

        MonthlyCycle cycle =
                monthlyCycleRepository
                        .findById(cycleId)
                        .orElseThrow(() ->
                                new BadRequestException("Cycle not found"));

        if (cycle.getChitGroup().getOwner() != null
                && cycle.getChitGroup().getOwner().getId().equals(memberId)) {
            throw new BadRequestException(
                    "The group owner organises the group and does not pay "
                            + "contributions");
        }

        authorizationService.requireGroupMember(
                cycle.getChitGroup().getId(), memberId);

        /*
         * Payment rows are normally created together with the cycle, but a
         * cycle created by an earlier version of the app (or a member who
         * joined later) may not have one. Create whatever is missing now
         * (idempotent) instead of failing with "Payment not found".
         */
        createPaymentsForCycle(cycle);

        MonthlyPayment payment =
                monthlyPaymentRepository
                        .findByCycleIdAndMemberIdForUpdate(
                                cycleId,
                                memberId)
                        .orElseThrow(() ->
                                new BadRequestException(
                                        "Payment not found"));

        if (payment.getStatus() == PaymentStatus.PAID) {

            throw new BadRequestException(
                    "Contribution already paid");
        }

        Wallet wallet =
                walletRepository
                        .findByMemberIdForUpdate(memberId)
                        .orElseThrow(() ->
                                new BadRequestException(
                                        "Your wallet has no funds yet. "
                                                + "Add funds on the wallet "
                                                + "page first."));

        BigDecimal amount =
                payment.getAmount();

        if (wallet.getBalance()
                .compareTo(amount) < 0) {

            throw new BadRequestException(
                    "Insufficient wallet balance");
        }

        BigDecimal newBalance =
                wallet.getBalance()
                        .subtract(amount);

        wallet.setBalance(newBalance);

        walletRepository.save(wallet);

        WalletTransaction transaction =
                new WalletTransaction();

        transaction.setWallet(wallet);
        transaction.setMember(payment.getMember());
        transaction.setCycle(payment.getCycle());
        transaction.setType(
                WalletTransactionType.CONTRIBUTION);
        transaction.setAmount(amount.negate());
        transaction.setBalanceAfter(newBalance);
        transaction.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(
                transaction);

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());

        monthlyPaymentRepository.save(payment);

        auditService.log(com.clearchit.model.AuditAction.CONTRIBUTION_PAID, "CYCLE", cycleId, "Contribution paid by member " + memberId);
        return payment;
    }

    public List<MonthlyPayment> getPaymentsForCycle(
            Long cycleId) {

        return monthlyPaymentRepository
                .findByCycleId(cycleId);
    }

    public MonthlyPayment getPayment(
            Long cycleId,
            Long memberId) {

        return monthlyPaymentRepository
                .findByCycleIdAndMemberId(
                        cycleId,
                        memberId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Payment not found"));
    }

    public long getPaidCount(Long cycleId) {

        return monthlyPaymentRepository
                .countByCycleIdAndStatus(
                        cycleId,
                        PaymentStatus.PAID);
    }

    public long getPendingCount(Long cycleId) {

        return monthlyPaymentRepository
                .countByCycleIdAndStatus(
                        cycleId,
                        PaymentStatus.PENDING);
    }

    public BigDecimal getCollectedAmount(
            Long cycleId) {

        return monthlyPaymentRepository
                .sumAmountByCycleIdAndStatus(
                        cycleId,
                        PaymentStatus.PAID);
    }

    public BigDecimal getRemainingAmount(
            Long cycleId) {

        return monthlyPaymentRepository
                .sumAmountByCycleIdAndStatus(
                        cycleId,
                        PaymentStatus.PENDING);
    }
    public PaymentSummaryResponse getPaymentSummary(Long cycleId) {

        MonthlyCycle cycle =
                monthlyCycleRepository
                        .findById(cycleId)
                        .orElseThrow(() ->
                                new RuntimeException("Cycle not found"));

        long totalMembers =
                chitGroupMemberRepository
                        .countByChitGroupId(
                                cycle.getChitGroup().getId());

        long paidMembers =
                getPaidCount(cycleId);

        // Derived from membership, so it is right even if some payment rows
        // have not been created yet.
        long pendingMembers =
                totalMembers - paidMembers;

        BigDecimal expectedCollection =
                cycle.getChitGroup()
                        .getMonthlyContribution()
                        .multiply(
                                BigDecimal.valueOf(totalMembers));

        BigDecimal collectedAmount =
                getCollectedAmount(cycleId);

        BigDecimal remainingAmount =
                expectedCollection.subtract(collectedAmount);

        PaymentSummaryResponse response =
                new PaymentSummaryResponse();

        response.setCycleId(cycleId);
        response.setTotalMembers(totalMembers);
        response.setPaidMembers(paidMembers);
        response.setPendingMembers(pendingMembers);
        response.setExpectedCollection(expectedCollection);
        response.setCollectedAmount(collectedAmount);
        response.setRemainingAmount(remainingAmount);
        response.setCollectionComplete(
                paidMembers == totalMembers
        );

        return response;
    }
}
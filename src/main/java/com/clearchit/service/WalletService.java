package com.clearchit.service;

import com.clearchit.dto.WalletTransactionResponse;
import com.clearchit.model.Member;
import com.clearchit.model.Wallet;
import com.clearchit.model.WalletTransaction;
import com.clearchit.model.WalletTransactionType;
import com.clearchit.repository.MemberRepository;
import com.clearchit.repository.WalletRepository;
import com.clearchit.repository.WalletTransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final MemberRepository memberRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public WalletService(
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            MemberRepository memberRepository,
            AuthorizationService authorizationService,
            AuditService auditService) {

        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.memberRepository = memberRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public Wallet recharge(Long memberId, BigDecimal amount) {

        authorizationService.requireMember(memberId);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Recharge amount must be greater than zero");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new RuntimeException("Member not found"));

        Wallet wallet = findOrCreateWallet(member);

        BigDecimal newBalance =
                wallet.getBalance().add(amount);

        wallet.setBalance(newBalance);

        walletRepository.save(wallet);

        createTransaction(
                wallet,
                WalletTransactionType.RECHARGE,
                amount,
                newBalance
        );

        auditService.log(com.clearchit.model.AuditAction.WALLET_RECHARGED, "MEMBER", memberId, "Wallet recharged: " + amount);
        return wallet;
    }

    @Transactional
    public Wallet withdraw(Long memberId, BigDecimal amount) {

        authorizationService.requireMember(memberId);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Withdrawal amount must be greater than zero");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new RuntimeException("Member not found"));

        Wallet wallet = findOrCreateWallet(member);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient wallet balance");
        }

        BigDecimal newBalance =
                wallet.getBalance().subtract(amount);

        wallet.setBalance(newBalance);

        walletRepository.save(wallet);

        createTransaction(
                wallet,
                WalletTransactionType.WITHDRAWAL,
                amount.negate(),
                newBalance
        );

        auditService.log(com.clearchit.model.AuditAction.WALLET_WITHDRAWN, "MEMBER", memberId, "Wallet withdrawal: " + amount);

        return wallet;
    }

    public Wallet getWallet(Long memberId) {

        authorizationService.requireMember(memberId);

        return findOrCreateWallet(authorizationService.currentMember());
    }

    public List<WalletTransactionResponse> getTransactions(Long memberId) {

        authorizationService.requireMember(memberId);

        Wallet wallet =
                findOrCreateWallet(authorizationService.currentMember());

        List<WalletTransaction> transactions =
                walletTransactionRepository
                        .findByWalletIdOrderByCreatedAtDesc(
                                wallet.getId());

        return transactions.stream()
                .map(transaction -> {

                    WalletTransactionResponse response =
                            new WalletTransactionResponse();

                    response.setTransactionId(
                            transaction.getId());

                    response.setType(
                            transaction.getType());

                    response.setAmount(
                            transaction.getAmount());

                    response.setBalanceAfter(
                            transaction.getBalanceAfter());

                    response.setMemberId(
                            transaction.getMember() != null
                                    ? transaction.getMember().getId()
                                    : null);

                    response.setCycleId(
                            transaction.getCycle() != null
                                    ? transaction.getCycle().getId()
                                    : null);

                    response.setCreatedAt(
                            transaction.getCreatedAt());

                    return response;
                })
                .toList();
    }

    /*
     * Accounts registered before wallets were created at sign-up have no
     * wallet row. Create it on first use instead of failing.
     */
    private Wallet findOrCreateWallet(Member member) {

        return walletRepository.findByMemberId(member.getId())
                .orElseGet(() -> {
                    Wallet created = new Wallet();
                    created.setMember(member);
                    created.setBalance(BigDecimal.ZERO);
                    return walletRepository.save(created);
                });
    }

    private void createTransaction(
            Wallet wallet,
            WalletTransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter) {

        WalletTransaction transaction =
                new WalletTransaction();

        transaction.setWallet(wallet);

        // The wallet belongs to this member
        transaction.setMember(wallet.getMember());

        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(transaction);
    }
}


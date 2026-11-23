package com.clearchit.service;

import com.clearchit.dto.LoginRequest;
import com.clearchit.dto.RegisterRequest;
import com.clearchit.model.Member;
import com.clearchit.model.Role;
import com.clearchit.model.UserAccount;
import com.clearchit.model.Wallet;
import com.clearchit.repository.MemberRepository;
import com.clearchit.repository.UserAccountRepository;
import com.clearchit.repository.WalletRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserAccountRepository userAccountRepository,
            MemberRepository memberRepository,
            WalletRepository walletRepository,
            PasswordEncoder passwordEncoder) {

        this.userAccountRepository = userAccountRepository;
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount register(RegisterRequest request) {

        if (userAccountRepository.existsByEmail(
                request.getEmail())) {

            throw new RuntimeException(
                    "Email already registered");
        }

        Member member = new Member();

        member.setName(request.getName());
        member.setEmail(request.getEmail());

        Member savedMember =
                memberRepository.save(member);

        // Every member needs a wallet. Without it, wallet pages, recharge
        // and contribution payments all fail with "Wallet not found".
        Wallet wallet = new Wallet();
        wallet.setMember(savedMember);
        wallet.setBalance(BigDecimal.ZERO);
        walletRepository.save(wallet);

        UserAccount userAccount =
                new UserAccount();

        userAccount.setEmail(request.getEmail());

        userAccount.setPasswordHash(
                passwordEncoder.encode(
                        request.getPassword()));

        userAccount.setRole(Role.MEMBER);

        userAccount.setMember(savedMember);

        return userAccountRepository.save(
                userAccount);
    }
    public UserAccount login(LoginRequest request) {

        UserAccount userAccount =
                userAccountRepository
                        .findByEmail(request.getEmail())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Invalid email or password"));

        boolean passwordMatches =
                passwordEncoder.matches(
                        request.getPassword(),
                        userAccount.getPasswordHash());

        if (!passwordMatches) {
            throw new RuntimeException(
                    "Invalid email or password");
        }

        return userAccount;
    }
}
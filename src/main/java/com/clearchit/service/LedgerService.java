package com.clearchit.service;

import com.clearchit.dto.WalletTransactionResponse;
import com.clearchit.model.WalletTransaction;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.ChitGroupRepository;
import com.clearchit.repository.WalletTransactionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LedgerService {
    private final WalletTransactionRepository repository;
    private final ChitGroupRepository groupRepository;
    private final ChitGroupMemberRepository membershipRepository;
    private final AuthorizationService authorizationService;

    public LedgerService(WalletTransactionRepository repository,
                         ChitGroupRepository groupRepository,
                         ChitGroupMemberRepository membershipRepository,
                         AuthorizationService authorizationService) {
        this.repository = repository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
    }

    public List<WalletTransactionResponse> getGroupLedger(Long groupId) {
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> new com.clearchit.exception.ChitGroupNotFoundException(
                        "Chit group not found with id: " + groupId));

        var currentMember = authorizationService.currentMember();

        boolean allowed = group.getOwner() != null
                && group.getOwner().getId().equals(currentMember.getId())
                || membershipRepository.existsByChitGroupIdAndMemberId(
                        groupId, currentMember.getId());

        if (!allowed) {
            throw new AccessDeniedException(
                    "You cannot access this group ledger");
        }

        return repository.findByChitGroupId(groupId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private WalletTransactionResponse toResponse(WalletTransaction transaction) {
        WalletTransactionResponse response = new WalletTransactionResponse();
        response.setTransactionId(transaction.getId());
        response.setType(transaction.getType());
        response.setAmount(transaction.getAmount());
        response.setBalanceAfter(transaction.getBalanceAfter());
        response.setMemberId(transaction.getMember() != null
                ? transaction.getMember().getId() : null);
        response.setCycleId(transaction.getCycle() != null
                ? transaction.getCycle().getId() : null);
        response.setCreatedAt(transaction.getCreatedAt());
        return response;
    }
}

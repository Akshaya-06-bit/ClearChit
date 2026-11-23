package com.clearchit.service;

import com.clearchit.model.AuditAction;
import com.clearchit.model.AuditLog;
import com.clearchit.repository.AuditLogRepository;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.ChitGroupRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    private final ChitGroupRepository groupRepository;
    private final ChitGroupMemberRepository membershipRepository;

    public AuditService(AuditLogRepository repository,
                        ChitGroupRepository groupRepository,
                        ChitGroupMemberRepository membershipRepository) {
        this.repository = repository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public AuditLog log(AuditAction action, String entityType,
                        Long entityId, String details) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String actor = authentication == null
                || authentication.getName() == null
                || authentication.getName().equals("anonymousUser")
                ? "SYSTEM"
                : authentication.getName();

        AuditLog log = new AuditLog();
        log.setActorEmail(actor);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setCreatedAt(LocalDateTime.now());
        return repository.save(log);
    }

    public List<AuditLog> getGroupAuditLogs(Long groupId) {
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> new com.clearchit.exception.ChitGroupNotFoundException(
                        "Chit group not found with id: " + groupId));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        String email = authentication.getName();
        boolean owner = group.getOwner() != null
                && group.getOwner().getEmail().equals(email);

        boolean member = membershipRepository.findByChitGroupId(groupId)
                .stream()
                .anyMatch(m -> m.getMember().getEmail().equals(email));

        if (!owner && !member) {
            throw new AccessDeniedException(
                    "You cannot access this group's audit log");
        }

        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "CHIT_GROUP", groupId);
    }
}

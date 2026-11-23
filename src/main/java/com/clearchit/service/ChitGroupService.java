package com.clearchit.service;

import com.clearchit.dto.ChitGroupRequest;
import com.clearchit.exception.BadRequestException;
import com.clearchit.exception.ChitGroupNotFoundException;
import com.clearchit.exception.ChitGroupUpdateNotAllowedException;
import com.clearchit.model.ChitGroup;
import com.clearchit.model.ChitGroupMember;
import com.clearchit.model.ChitStatus;
import com.clearchit.model.Member;
import com.clearchit.model.UserAccount;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.ChitGroupRepository;
import com.clearchit.repository.UserAccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ChitGroupService {

    private final ChitGroupRepository chitGroupRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public ChitGroupService(
            ChitGroupRepository chitGroupRepository,
            AuthorizationService authorizationService,
            AuditService auditService) {

        this.chitGroupRepository = chitGroupRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public ChitGroup createChitGroup(ChitGroupRequest request) {

        validateAmounts(request);

        Member owner = authorizationService.currentMember();

        // Create the chit group
        ChitGroup chitGroup = new ChitGroup();

        chitGroup.setName(request.getName());
        chitGroup.setChitValue(request.getChitValue());
        chitGroup.setMonthlyContribution(
                request.getMonthlyContribution()
        );
        chitGroup.setOrganiserCommissionRate(
                request.getOrganiserCommissionRate()
        );
        chitGroup.setMaxMembers(request.getMaxMembers());
        chitGroup.setDurationMonths(request.getDurationMonths());
        chitGroup.setStartDate(request.getStartDate());

        // Every new group starts as DRAFT
        chitGroup.setStatus(ChitStatus.DRAFT);

        // The person creating the group becomes its owner
        chitGroup.setOwner(owner);

        ChitGroup savedChitGroup =
                chitGroupRepository.save(chitGroup);

        auditService.log(com.clearchit.model.AuditAction.GROUP_CREATED, "CHIT_GROUP", savedChitGroup.getId(), "Group created");

        return savedChitGroup;
    }

    /*
     * Each month every member pays the contribution and the pot paid out
     * is the chit value, so chitValue must equal contribution x members.
     * Otherwise wallet money would be created or destroyed on settlement.
     */
    private void validateAmounts(ChitGroupRequest request) {

        BigDecimal expected =
                request.getMonthlyContribution()
                        .multiply(BigDecimal.valueOf(
                                request.getMaxMembers()));

        if (expected.compareTo(request.getChitValue()) != 0) {

            throw new BadRequestException(
                    "Chit value must equal monthly contribution x maximum "
                            + "members (expected "
                            + expected.stripTrailingZeros().toPlainString()
                            + ")");
        }
    }

    public List<ChitGroup> getAllChitGroups() {

        return chitGroupRepository.findAll();
    }

    public ChitGroup getChitGroupById(Long id) {

        return chitGroupRepository.findById(id)
                .orElseThrow(() ->
                        new ChitGroupNotFoundException(
                                "Chit group not found with id: " + id
                        )
                );
    }

    @Transactional
    public ChitGroup updateChitGroup(
            Long id,
            ChitGroupRequest updatedChitGroup) {

        ChitGroup existing =
                chitGroupRepository.findById(id)
                        .orElseThrow(() ->
                                new ChitGroupNotFoundException(
                                        "Chit group not found with id: " + id
                                )
                        );

        // Only the owner can update this group
        authorizationService.requireGroupOwner(existing);

        validateAmounts(updatedChitGroup);

        if (existing.getStatus() != ChitStatus.DRAFT) {

            throw new ChitGroupUpdateNotAllowedException(
                    "Only DRAFT chit groups can be updated"
            );
        }

        existing.setName(updatedChitGroup.getName());

        existing.setChitValue(
                updatedChitGroup.getChitValue()
        );

        existing.setMonthlyContribution(
                updatedChitGroup.getMonthlyContribution()
        );

        existing.setOrganiserCommissionRate(
                updatedChitGroup.getOrganiserCommissionRate()
        );

        existing.setMaxMembers(
                updatedChitGroup.getMaxMembers()
        );

        existing.setDurationMonths(
                updatedChitGroup.getDurationMonths()
        );

        existing.setStartDate(
                updatedChitGroup.getStartDate()
        );

        ChitGroup saved = chitGroupRepository.save(existing);
        auditService.log(com.clearchit.model.AuditAction.GROUP_UPDATED, "CHIT_GROUP", id, "Group updated");
        return saved;
    }

    @Transactional
    public void deleteChitGroup(Long id) {

        ChitGroup existing =
                chitGroupRepository.findById(id)
                        .orElseThrow(() ->
                                new ChitGroupNotFoundException(
                                        "Chit group not found with id: " + id
                                )
                        );

        // Only the owner can delete this group
        authorizationService.requireGroupOwner(existing);
        auditService.log(com.clearchit.model.AuditAction.GROUP_DELETED, "CHIT_GROUP", id, "Group deleted");
        chitGroupRepository.delete(existing);
    }

}

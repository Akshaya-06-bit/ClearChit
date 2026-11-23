package com.clearchit.service;

import com.clearchit.dto.MemberChitGroupResponse;
import com.clearchit.dto.MemberRequest;
import com.clearchit.model.ChitGroup;
import com.clearchit.model.ChitGroupMember;
import com.clearchit.model.ChitStatus;
import com.clearchit.model.Member;
import com.clearchit.model.UserAccount;
import com.clearchit.model.Wallet;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.ChitGroupRepository;
import com.clearchit.repository.MemberRepository;
import com.clearchit.repository.UserAccountRepository;
import com.clearchit.repository.WalletRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final ChitGroupRepository chitGroupRepository;
    private final ChitGroupMemberRepository chitGroupMemberRepository;
    private final WalletRepository walletRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public MemberService(
            MemberRepository memberRepository,
            ChitGroupRepository chitGroupRepository,
            ChitGroupMemberRepository chitGroupMemberRepository,
            WalletRepository walletRepository,
            AuthorizationService authorizationService,
            AuditService auditService) {

        this.memberRepository = memberRepository;
        this.chitGroupRepository = chitGroupRepository;
        this.chitGroupMemberRepository = chitGroupMemberRepository;
        this.walletRepository = walletRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    // ------------------------------------------------
    // Create Member + Wallet
    // ------------------------------------------------

    @Transactional
    public Member createMember(MemberRequest request) {

        Member member = new Member();

        member.setName(request.getName());
        member.setEmail(request.getEmail());

        Member savedMember =
                memberRepository.save(member);

        // Automatically create wallet
        Wallet wallet = new Wallet();

        wallet.setMember(savedMember);
        wallet.setBalance(BigDecimal.ZERO);

        walletRepository.save(wallet);

        return savedMember;
    }

    // ------------------------------------------------
    // Add Member to Chit Group
    // ------------------------------------------------

    @Transactional
    public ChitGroupMember addMemberToGroup(
            Long groupId,
            Long memberId) {

        ChitGroup chitGroup =
                chitGroupRepository.findById(groupId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Chit group not found"
                                )
                        );

        // Only the owner of THIS group can add members
        authorizationService.requireGroupOwner(chitGroup);

        Member member =
                memberRepository.findById(memberId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Member not found"
                                )
                        );

        // Membership is only open while the group is still filling up.
        // Once the owner schedules the first cycle (UPCOMING) it is locked.
        if (chitGroup.getStatus() != ChitStatus.DRAFT) {

            throw new RuntimeException(
                    "Members can only be added while the group is in DRAFT. "
                            + "This group is " + chitGroup.getStatus() + "."
            );
        }

        // The owner organises the group; they do not bid or pay.
        if (chitGroup.getOwner() != null
                && chitGroup.getOwner().getId().equals(memberId)) {

            throw new RuntimeException(
                    "The group owner organises the group and cannot also be "
                            + "a member. Add the other participants instead."
            );
        }

        // Check maximum members
        long currentMemberCount =
                chitGroupMemberRepository
                        .countByChitGroupId(groupId);

        if (currentMemberCount >= chitGroup.getMaxMembers()) {

            throw new RuntimeException(
                    "Maximum members reached"
            );
        }

        // Check duplicate membership
        boolean alreadyMember =
                chitGroupMemberRepository
                        .existsByChitGroupIdAndMemberId(
                                groupId,
                                memberId
                        );

        if (alreadyMember) {

            throw new RuntimeException(
                    "Member already belongs to this group"
            );
        }

        ChitGroupMember membership =
                new ChitGroupMember();

        membership.setChitGroup(chitGroup);
        membership.setMember(member);

        ChitGroupMember saved = chitGroupMemberRepository.save(membership);
        auditService.log(com.clearchit.model.AuditAction.MEMBER_ADDED, "CHIT_GROUP", groupId, "Member " + memberId + " added");
        return saved;
    }

    // ------------------------------------------------
    // Remove Member from Chit Group
    // ------------------------------------------------

    @Transactional
    public void removeMemberFromGroup(
            Long groupId,
            Long memberId) {

        ChitGroup chitGroup =
                chitGroupRepository.findById(groupId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Chit group not found"
                                )
                        );

        // Only the owner of THIS group can remove members
        authorizationService.requireGroupOwner(chitGroup);

        // Members can only be removed while the group is in DRAFT
        if (chitGroup.getStatus() != ChitStatus.DRAFT) {

            throw new RuntimeException(
                    "Members can only be removed while the group is in DRAFT. "
                            + "This group is " + chitGroup.getStatus() + "."
            );
        }

        // Find membership
        ChitGroupMember membership =
                chitGroupMemberRepository
                        .findByChitGroupIdAndMemberId(
                                groupId,
                                memberId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Member does not belong to this group"
                                )
                        );

        // Delete membership
        chitGroupMemberRepository.delete(membership);
        auditService.log(com.clearchit.model.AuditAction.MEMBER_REMOVED, "CHIT_GROUP", groupId, "Member " + memberId + " removed");
    }

    // ------------------------------------------------
    // Get Members of a Chit Group
    // ------------------------------------------------

    public List<ChitGroupMember> getMembersByGroup(
            Long groupId) {

        ChitGroup group = chitGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Chit group not found"));

        Member current = authorizationService.currentMember();

        boolean allowed = group.getOwner() != null
                && group.getOwner().getId().equals(current.getId())
                || chitGroupMemberRepository.existsByChitGroupIdAndMemberId(
                        groupId, current.getId());

        if (!allowed) {
            throw new AccessDeniedException(
                    "You are not allowed to view this group's members");
        }

        return chitGroupMemberRepository
                .findByChitGroupId(groupId);
    }

    // ------------------------------------------------
    // Get Chit Groups of a Member
    // ------------------------------------------------

    public List<MemberChitGroupResponse> getGroupsByMember(
            Long memberId) {

        authorizationService.requireMember(memberId);

        List<ChitGroupMember> memberships =
                chitGroupMemberRepository
                        .findByMemberId(memberId);

        return memberships.stream()
                .map(membership -> {

                    ChitGroup group =
                            membership.getChitGroup();

                    MemberChitGroupResponse response =
                            new MemberChitGroupResponse();

                    response.setId(group.getId());
                    response.setName(group.getName());
                    response.setChitValue(
                            group.getChitValue()
                    );
                    response.setMonthlyContribution(
                            group.getMonthlyContribution()
                    );
                    response.setMaxMembers(
                            group.getMaxMembers()
                    );
                    response.setDurationMonths(
                            group.getDurationMonths()
                    );
                    response.setStartDate(
                            group.getStartDate()
                    );
                    response.setStatus(
                            group.getStatus()
                    );

                    return response;
                })
                .toList();
    }

}

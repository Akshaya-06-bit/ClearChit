package com.clearchit.repository;

import com.clearchit.model.ChitGroupMember;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChitGroupMemberRepository
        extends JpaRepository<ChitGroupMember, Long> {
    boolean existsByChitGroupIdAndMemberId(
            Long chitGroupId,
            Long memberId
    );
    long countByChitGroupId(Long chitGroupId);

    List<ChitGroupMember> findByChitGroupId(Long chitGroupId);

    List<ChitGroupMember> findByMemberId(Long memberId);
    Optional<ChitGroupMember> findByChitGroupIdAndMemberId(
            Long chitGroupId,
            Long memberId
    );
}
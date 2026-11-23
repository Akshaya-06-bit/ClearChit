package com.clearchit.repository;

import com.clearchit.model.Bid;
import com.clearchit.model.BidStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidRepository
        extends JpaRepository<Bid, Long> {

    Optional<Bid> findByCycleIdAndMemberId(
            Long cycleId,
            Long memberId
    );

    List<Bid> findByCycleId(Long cycleId);

    boolean existsByCycleIdAndMemberId(
            Long cycleId,
            Long memberId
    );
    boolean existsByCycle_ChitGroup_IdAndMember_IdAndStatus(
            Long groupId,
            Long memberId,
            BidStatus status
    );
}
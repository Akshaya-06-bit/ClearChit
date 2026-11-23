package com.clearchit.repository;

import com.clearchit.model.CycleStatus;
import com.clearchit.model.MonthlyCycle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MonthlyCycleRepository
        extends JpaRepository<MonthlyCycle, Long> {

    List<MonthlyCycle> findByChitGroupId(Long chitGroupId);

    List<MonthlyCycle> findByStatusIn(Collection<CycleStatus> statuses);

    boolean existsByChitGroupIdAndWinnerId(
            Long chitGroupId,
            Long winnerId
    );

    boolean existsByChitGroupIdAndCycleNumber(
            Long chitGroupId,
            Integer cycleNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c
            FROM MonthlyCycle c
            WHERE c.id = :cycleId
            """)
    Optional<MonthlyCycle> findByIdForUpdate(
            @Param("cycleId") Long cycleId
    );
}
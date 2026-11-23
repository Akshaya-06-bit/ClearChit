package com.clearchit.repository;

import com.clearchit.model.MonthlyPayment;
import com.clearchit.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MonthlyPaymentRepository
        extends JpaRepository<MonthlyPayment, Long> {

    List<MonthlyPayment> findByCycleId(Long cycleId);

    Optional<MonthlyPayment> findByCycleIdAndMemberId(
            Long cycleId,
            Long memberId
    );

    boolean existsByCycleIdAndMemberId(
            Long cycleId,
            Long memberId
    );

    long countByCycleIdAndStatus(
            Long cycleId,
            PaymentStatus status
    );

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM MonthlyPayment p
            WHERE p.cycle.id = :cycleId
            AND p.status = :status
            """)
    BigDecimal sumAmountByCycleIdAndStatus(
            @Param("cycleId") Long cycleId,
            @Param("status") PaymentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM MonthlyPayment p
            WHERE p.cycle.id = :cycleId
            AND p.member.id = :memberId
            """)
    Optional<MonthlyPayment> findByCycleIdAndMemberIdForUpdate(
            @Param("cycleId") Long cycleId,
            @Param("memberId") Long memberId
    );
}
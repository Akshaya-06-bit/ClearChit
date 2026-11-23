package com.clearchit.repository;

import com.clearchit.model.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DividendRepository extends JpaRepository<Dividend, Long> {

    List<Dividend> findByCycleId(Long cycleId);

    boolean existsByCycleIdAndMemberId(Long cycleId, Long memberId);
}
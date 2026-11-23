package com.clearchit.repository;

import com.clearchit.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(
            Long walletId
    );

    @Query("""
            SELECT t FROM WalletTransaction t
            WHERE t.cycle.chitGroup.id = :groupId
            ORDER BY t.createdAt DESC
            """)
    List<WalletTransaction> findByChitGroupId(@Param("groupId") Long groupId);
}

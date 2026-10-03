package com.expensetracker.repository;

import com.expensetracker.model.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    List<BankTransaction> findByUserId(Long userId);
    Optional<BankTransaction> findByUserIdAndTxnId(Long userId, String txnId);
    boolean existsByUserIdAndTxnId(Long userId, String txnId);
    List<BankTransaction> findByUserIdAndProcessedFalse(Long userId);
}

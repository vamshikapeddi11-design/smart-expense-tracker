package com.expensetracker.repository;

import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserId(Long userId);

    List<Expense> findByUserIdAndDateBetween(Long userId, LocalDate start, LocalDate end);

    List<Expense> findByUserIdAndCategoryAndDateBetween(
            Long userId, Category category, LocalDate start, LocalDate end);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    /** Exact bank ref lookup - fastest dedup check for synced/imported txns. */
    Optional<Expense> findByUserIdAndBankTxnRef(Long userId, String bankTxnRef);

    /** Fuzzy-dedup window: same user, amount, and a small date range. */
    List<Expense> findByUserIdAndAmountAndDateBetween(
            Long userId, java.math.BigDecimal amount, LocalDate start, LocalDate end);

    void deleteByIdAndUserId(Long id, Long userId);
}

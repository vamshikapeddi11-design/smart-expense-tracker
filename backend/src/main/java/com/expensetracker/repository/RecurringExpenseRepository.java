package com.expensetracker.repository;

import com.expensetracker.model.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {
    List<RecurringExpense> findByUserId(Long userId);
    Optional<RecurringExpense> findByIdAndUserId(Long id, Long userId);

    /** Finds rules whose next due date has arrived (for the scheduled job). */
    List<RecurringExpense> findByActiveTrueAndNextDueDateLessThanEqual(LocalDate date);

    void deleteByIdAndUserId(Long id, Long userId);
}

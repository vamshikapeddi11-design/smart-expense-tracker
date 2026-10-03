package com.expensetracker.repository;

import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserId(Long userId);
    Optional<Budget> findByIdAndUserId(Long id, Long userId);
    Optional<Budget> findByUserIdAndCategory(Long userId, Category category);
    void deleteByIdAndUserId(Long id, Long userId);
}

package com.expensetracker.service;

import com.expensetracker.dto.BudgetAlert;
import com.expensetracker.dto.BudgetRequest;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Monthly budgets per category + alert computation.
 *
 * Alert statuses (evaluated against the CURRENT calendar month):
 *   EXCEEDED - spent > limit
 *   NEAR     - spent >= limit * alertThreshold (but not exceeded)
 *   OK       - below the threshold
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final CurrentUser currentUser;

    public List<Budget> list() {
        return budgetRepository.findByUserId(currentUser.userId());
    }

    /** Creates a budget, or replaces the existing one for that category. */
    @Transactional
    public Budget create(BudgetRequest request) {
        User user = currentUser.user();
        Category category = parseCategory(request.getCategory());
        double threshold = request.getAlertThreshold() != null
                ? request.getAlertThreshold() : 0.8;
        if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("alertThreshold must be between 0 and 1");
        }
        Budget budget = budgetRepository.findByUserIdAndCategory(user.getId(), category)
                .orElseGet(() -> Budget.builder().user(user).category(category).build());
        budget.setMonthlyLimit(request.getMonthlyLimit());
        budget.setAlertThreshold(threshold);
        return budgetRepository.save(budget);
    }

    @Transactional
    public Budget update(Long id, BudgetRequest request) {
        Budget budget = budgetRepository.findByIdAndUserId(id, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found: " + id));
        if (request.getMonthlyLimit() != null) budget.setMonthlyLimit(request.getMonthlyLimit());
        if (request.getAlertThreshold() != null) {
            if (request.getAlertThreshold() < 0 || request.getAlertThreshold() > 1) {
                throw new IllegalArgumentException("alertThreshold must be between 0 and 1");
            }
            budget.setAlertThreshold(request.getAlertThreshold());
        }
        return budgetRepository.save(budget);
    }

    @Transactional
    public void delete(Long id) {
        budgetRepository.findByIdAndUserId(id, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found: " + id));
        budgetRepository.deleteByIdAndUserId(id, currentUser.userId());
    }
    public List<BudgetAlert> alerts() {
    Long userId = currentUser.userId();

    LocalDate now = LocalDate.now();
    LocalDate start = now.withDayOfMonth(1);
    LocalDate end = now.withDayOfMonth(now.lengthOfMonth());

    return budgetRepository.findByUserId(userId).stream()
            .map(budget -> {

                BigDecimal spent = expenseRepository
                        .findByUserIdAndCategoryAndDateBetween(
                                userId,
                                budget.getCategory(),
                                start,
                                end
                        )
                        .stream()
                        .map(Expense::getAmount)
                        .filter(amount -> amount != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal limit = budget.getMonthlyLimit();

                if (limit == null) {
                    limit = BigDecimal.ZERO;
                }

                double percentUsed = 0.0;

                if (limit.compareTo(BigDecimal.ZERO) > 0) {
                    percentUsed = spent
                            .multiply(BigDecimal.valueOf(100))
                            .divide(limit, 2, RoundingMode.HALF_UP)
                            .doubleValue();
                }

                String status;

                if (limit.compareTo(BigDecimal.ZERO) <= 0) {
                    status = "OK";
                } else if (spent.compareTo(limit) > 0) {
                    status = "EXCEEDED";
                } else if (spent.compareTo(
                        limit.multiply(
                                BigDecimal.valueOf(budget.getAlertThreshold())
                        )
                ) >= 0) {
                    status = "NEAR";
                } else {
                    status = "OK";
                }

                return BudgetAlert.builder()
                        .category(budget.getCategory().name())
                        .limit(limit)
                        .spent(spent)
                        .percentUsed(percentUsed)
                        .status(status)
                        .build();
            })
            .toList();
}
            

    private Category parseCategory(String raw) {
        try {
            return Category.valueOf(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown category: " + raw);
        }
    }
}

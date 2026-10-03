package com.expensetracker.controller;

import com.expensetracker.dto.BudgetAlert;
import com.expensetracker.dto.BudgetRequest;
import com.expensetracker.model.Budget;
import com.expensetracker.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Monthly per-category budgets and spend alerts. All endpoints need a JWT. */
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    /** GET /api/budgets */
    @GetMapping
    public List<Budget> list() {
        return budgetService.list();
    }

    /**
     * GET /api/budgets/alerts -> [{category, limit, spent, percentUsed,
     * status OK|NEAR|EXCEEDED}] evaluated against the current month's spend.
     */
    @GetMapping("/alerts")
    public List<BudgetAlert> alerts() {
        return budgetService.alerts();
    }

    /** POST /api/budgets {category, monthlyLimit, alertThreshold} -> 201 */
    @PostMapping
    public ResponseEntity<Budget> create(@Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.create(request));
    }

    /** PUT /api/budgets/{id} */
    @PutMapping("/{id}")
    public Budget update(@PathVariable Long id,
                         @Valid @RequestBody BudgetRequest request) {
        return budgetService.update(id, request);
    }

    /** DELETE /api/budgets/{id} -> 204 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        budgetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

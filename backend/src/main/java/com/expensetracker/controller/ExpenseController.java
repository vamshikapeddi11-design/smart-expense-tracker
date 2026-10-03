package com.expensetracker.controller;

import com.expensetracker.dto.CorrectionRequest;
import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.SummaryResponse;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.SummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Expense CRUD + category correction + dashboard summary.
 * All endpoints require a JWT (except none here) and are scoped to the
 * logged-in user by the service layer.
 */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final SummaryService summaryService;

    /** GET /api/expenses?month=2026-09&category=Food (both optional) */
    @GetMapping
    public List<ExpenseResponse> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String category) {
        return expenseService.list(month, category);
    }

    /** GET /api/expenses/summary?period=month|week&year=2026&month=9[&day=15] */
    @GetMapping("/summary")
    public SummaryResponse summary(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Integer day) {
        return summaryService.summarize(period, year, month, day);
    }

    /** GET /api/expenses/{id} */
    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable Long id) {
        return expenseService.get(id);
    }

    /** POST /api/expenses -> 201 (auto-categorizes via ML when category blank) */
    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.create(request));
    }

    /** PUT /api/expenses/{id} */
    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    /**
     * POST /api/expenses/{id}/correct {category}:
     * user fixes the category; the correction is also remembered for this
     * merchant (merchant learning) so future txns categorize correctly.
     */
    @PostMapping("/{id}/correct")
    public ExpenseResponse correct(@PathVariable Long id,
                                   @Valid @RequestBody CorrectionRequest request) {
        return expenseService.correct(id, request);
    }

    /** DELETE /api/expenses/{id} -> 204 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

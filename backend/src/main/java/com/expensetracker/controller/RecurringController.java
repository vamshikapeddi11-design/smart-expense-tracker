package com.expensetracker.controller;

import com.expensetracker.dto.RecurringRequest;
import com.expensetracker.model.RecurringExpense;
import com.expensetracker.service.RecurringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Recurring expense rules (e.g. "Netflix, Rs 199, monthly"). JWT required. */
@RestController
@RequestMapping("/api/recurring")
@RequiredArgsConstructor
public class RecurringController {

    private final RecurringService recurringService;

    /** GET /api/recurring */
    @GetMapping
    public List<RecurringExpense> list() {
        return recurringService.list();
    }

    /** POST /api/recurring -> 201 */
    @PostMapping
    public ResponseEntity<RecurringExpense> create(@Valid @RequestBody RecurringRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringService.create(request));
    }

    /** PUT /api/recurring/{id} */
    @PutMapping("/{id}")
    public RecurringExpense update(@PathVariable Long id,
                                   @Valid @RequestBody RecurringRequest request) {
        return recurringService.update(id, request);
    }

    /** DELETE /api/recurring/{id} -> 204 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recurringService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

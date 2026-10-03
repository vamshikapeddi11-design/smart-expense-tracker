package com.expensetracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body of POST /api/expenses (and PUT /api/expenses/{id}).
 * Category may be blank/"Other": then the backend asks the ML service
 * (when merchant or notes are present) before saving.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    /** Optional. Blank or "Other" triggers ML auto-categorization. */
    private String category;

    private LocalDate date; // defaults to today when absent

    private String paymentMode; // UPI, Cash, Card, ...
    private String merchant;
    private String notes;
}

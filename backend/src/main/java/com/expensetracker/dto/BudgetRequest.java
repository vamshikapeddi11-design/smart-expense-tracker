package com.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

/** Body of POST /api/budgets. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BudgetRequest {

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Monthly limit is required")
    @Positive(message = "Monthly limit must be positive")
    private BigDecimal monthlyLimit;

    /** Optional fraction 0-1, e.g. 0.8 = warn at 80%. Defaults to 0.8. */
    private Double alertThreshold;
}

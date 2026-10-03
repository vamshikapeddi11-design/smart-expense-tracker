package com.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body of POST/PUT /api/recurring. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecurringRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Category is required")
    private String category;

    /** MONTHLY or WEEKLY. */
    @NotBlank(message = "Frequency is required")
    private String frequency;

    private LocalDate nextDueDate; // defaults to today when absent
    private String paymentMode;
    private String merchant;
}

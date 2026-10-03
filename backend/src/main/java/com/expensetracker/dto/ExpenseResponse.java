package com.expensetracker.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Expense as returned by the API (never exposes internal entity details). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseResponse {

    private Long id;
    private BigDecimal amount;
    private String category;
    private LocalDate date;
    private String paymentMode;
    private String merchant;
    private String notes;
    private String source;
    private String bankTxnRef;
}

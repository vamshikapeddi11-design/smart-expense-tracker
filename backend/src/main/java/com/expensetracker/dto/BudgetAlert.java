package com.expensetracker.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * One row of GET /api/budgets/alerts.
 * status: OK (under threshold), NEAR (spent >= limit*threshold),
 *         EXCEEDED (spent > limit).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BudgetAlert {

    private String category;
    private BigDecimal limit;
    private BigDecimal spent;
    private double percentUsed; // 0-100+
    private String status;
}

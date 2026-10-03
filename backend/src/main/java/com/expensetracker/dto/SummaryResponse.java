package com.expensetracker.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/** Body of GET /api/expenses/summary - powers the dashboard charts. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SummaryResponse {

    private BigDecimal totalSpent;
    private int transactionCount;
    private List<CategorySlice> byCategory; // pie chart data
    private List<DaySlice> byDay;           // bar/line chart data

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CategorySlice {
        private String category;
        private BigDecimal amount;
        private int count;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DaySlice {
        private String date; // ISO yyyy-MM-dd
        private BigDecimal amount;
    }
}

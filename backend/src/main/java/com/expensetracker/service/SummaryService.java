package com.expensetracker.service;

import com.expensetracker.dto.SummaryResponse;
import com.expensetracker.model.Expense;
import com.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Builds the dashboard summary: total spent, per-category and per-day
 * breakdowns for a month or a week.
 *
 * period=month -> year+month required (e.g. year=2026&month=9)
 * period=week  -> year+month+day required; week = Mon..Sun containing that day
 * (ISO week, WeekFields.ISO).
 */
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final ExpenseRepository expenseRepository;
    private final CurrentUser currentUser;

    public SummaryResponse summarize(String period, int year, int month, Integer day) {
        LocalDate start;
        LocalDate end;
        if ("week".equalsIgnoreCase(period)) {
            if (day == null) {
                throw new IllegalArgumentException("day is required when period=week");
            }
            LocalDate any = LocalDate.of(year, month, day);
            // ISO week starts on Monday.
            start = any.with(WeekFields.ISO.dayOfWeek(), 1);
            end = start.plusDays(6);
        } else { // "month" (default)
            start = LocalDate.of(year, month, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        }

        List<Expense> expenses =
                expenseRepository.findByUserIdAndDateBetween(currentUser.userId(), start, end);

        BigDecimal total = BigDecimal.ZERO;
        Map<String, SummaryResponse.CategorySlice> byCat = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();

        for (Expense e : expenses) {
            total = total.add(e.getAmount());
            byCat.computeIfAbsent(e.getCategory().name(),
                            c -> SummaryResponse.CategorySlice.builder()
                                    .category(c).amount(BigDecimal.ZERO).count(0).build());
            SummaryResponse.CategorySlice slice = byCat.get(e.getCategory().name());
            slice.setAmount(slice.getAmount().add(e.getAmount()));
            slice.setCount(slice.getCount() + 1);
            byDay.merge(e.getDate(), e.getAmount(), BigDecimal::add);
        }

        List<SummaryResponse.DaySlice> days = byDay.entrySet().stream()
                .map(en -> SummaryResponse.DaySlice.builder()
                        .date(en.getKey().toString())
                        .amount(en.getValue())
                        .build())
                .toList();

        return SummaryResponse.builder()
                .totalSpent(total)
                .transactionCount(expenses.size())
                .byCategory(new ArrayList<>(byCat.values()))
                .byDay(days)
                .build();
    }
}

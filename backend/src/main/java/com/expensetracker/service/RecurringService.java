package com.expensetracker.service;

import com.expensetracker.dto.RecurringRequest;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.RecurringExpense;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.RecurringExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * CRUD for recurring-expense rules, plus the engine that materializes them.
 *
 * materializeDue(): for every ACTIVE rule whose nextDueDate is today or
 * earlier, create one Expense (source=RECURRING) and advance nextDueDate by
 * one frequency step. Called by the scheduled job in SyncService (or manually).
 * The while-loop catches up rules that fell multiple periods behind.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringService {

    private final RecurringExpenseRepository recurringRepository;
    private final ExpenseRepository expenseRepository;
    private final CurrentUser currentUser;

    public List<RecurringExpense> list() {
        return recurringRepository.findByUserId(currentUser.userId());
    }

    @Transactional
    public RecurringExpense create(RecurringRequest request) {
        User user = currentUser.user();
        RecurringExpense rule = RecurringExpense.builder()
                .user(user)
                .name(request.getName().trim())
                .amount(request.getAmount())
                .category(parseCategory(request.getCategory()))
                .frequency(parseFrequency(request.getFrequency()))
                .nextDueDate(request.getNextDueDate() != null
                        ? request.getNextDueDate() : LocalDate.now())
                .paymentMode(request.getPaymentMode())
                .merchant(request.getMerchant())
                .active(true)
                .build();
        return recurringRepository.save(rule);
    }

    @Transactional
    public RecurringExpense update(Long id, RecurringRequest request) {
        RecurringExpense rule = recurringRepository.findByIdAndUserId(id, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring expense not found: " + id));
        if (request.getName() != null) rule.setName(request.getName().trim());
        if (request.getAmount() != null) rule.setAmount(request.getAmount());
        if (request.getCategory() != null) rule.setCategory(parseCategory(request.getCategory()));
        if (request.getFrequency() != null) rule.setFrequency(parseFrequency(request.getFrequency()));
        if (request.getNextDueDate() != null) rule.setNextDueDate(request.getNextDueDate());
        if (request.getPaymentMode() != null) rule.setPaymentMode(request.getPaymentMode());
        if (request.getMerchant() != null) rule.setMerchant(request.getMerchant());
        return recurringRepository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        recurringRepository.findByIdAndUserId(id, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring expense not found: " + id));
        recurringRepository.deleteByIdAndUserId(id, currentUser.userId());
    }

    /**
     * Turns due rules into expenses. Returns how many expenses were created.
     * Runs inside one transaction per call; safe to run often (idempotent per
     * due date because nextDueDate always advances past the created expense).
     */
    @Transactional
    public int materializeDue() {
        LocalDate today = LocalDate.now();
        List<RecurringExpense> due =
                recurringRepository.findByActiveTrueAndNextDueDateLessThanEqual(today);
        int created = 0;
        for (RecurringExpense rule : due) {
            while (!rule.getNextDueDate().isAfter(today)) {
                Expense expense = Expense.builder()
                        .user(rule.getUser())
                        .amount(rule.getAmount())
                        .category(rule.getCategory())
                        .date(rule.getNextDueDate())
                        .paymentMode(rule.getPaymentMode())
                        .merchant(rule.getMerchant() != null ? rule.getMerchant() : rule.getName())
                        .notes("Auto-generated from recurring rule: " + rule.getName())
                        .source(Expense.Source.RECURRING)
                        .build();
                expenseRepository.save(expense);
                rule.setNextDueDate(advance(rule.getNextDueDate(), rule.getFrequency()));
                created++;
            }
            recurringRepository.save(rule);
        }
        if (created > 0) log.info("Materialized {} recurring expenses", created);
        return created;
    }

    private LocalDate advance(LocalDate date, RecurringExpense.Frequency frequency) {
        return switch (frequency) {
            case MONTHLY -> date.plusMonths(1);
            case WEEKLY -> date.plusWeeks(1);
        };
    }

    private Category parseCategory(String raw) {
        try {
            return Category.valueOf(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown category: " + raw);
        }
    }

    private RecurringExpense.Frequency parseFrequency(String raw) {
        try {
            return RecurringExpense.Frequency.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Frequency must be MONTHLY or WEEKLY");
        }
    }
}

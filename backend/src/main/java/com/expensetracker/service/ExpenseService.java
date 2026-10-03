package com.expensetracker.service;

import com.expensetracker.dto.CorrectionRequest;
import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * CRUD for expenses, plus:
 *  - auto-categorization on create (merchant learning -> ML service -> Other)
 *  - category correction, which also teaches MerchantLearningService
 *
 * Controllers stay thin: they only map HTTP <-> DTOs and call these methods.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CurrentUser currentUser;
    private final MlClientService mlClientService;
    private final MerchantLearningService learningService;

    // ---------------------------------------------------------------- list

    /** GET /api/expenses with optional month (yyyy-MM) and category filters. */
    public List<ExpenseResponse> list(String month, String category) {
        Long userId = currentUser.userId();
        LocalDate start = null, end = null;
        if (month != null && !month.isBlank()) {
            // "2026-09" -> 2026-09-01 .. 2026-09-30
            LocalDate first = LocalDate.parse(month + "-01");
            start = first;
            end = first.withDayOfMonth(first.lengthOfMonth());
        }
        Category cat = parseCategoryOrNull(category);
        List<Expense> expenses;
        if (start != null && cat != null) {
            expenses = expenseRepository.findByUserIdAndCategoryAndDateBetween(userId, cat, start, end);
        } else if (start != null) {
            expenses = expenseRepository.findByUserIdAndDateBetween(userId, start, end);
        } else {
            expenses = expenseRepository.findByUserId(userId);
        }
        return expenses.stream().map(this::toResponse).toList();
    }

    public ExpenseResponse get(Long id) {
        return toResponse(owned(id));
    }

    // --------------------------------------------------------------- create

    /**
     * Creates an expense with source=MANUAL.
     * Category resolution order when blank or "Other":
     *   1. learned merchant mapping (from past corrections)
     *   2. ML microservice (only if merchant or notes present)
     *   3. Category.Other
     */
    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        User user = currentUser.user();
        Category category = resolveCategory(user, request.getCategory(),
                request.getMerchant(), request.getNotes());
        Expense expense = Expense.builder()
                .user(user)
                .amount(request.getAmount())
                .category(category)
                .date(request.getDate() != null ? request.getDate() : LocalDate.now())
                .paymentMode(request.getPaymentMode())
                .merchant(request.getMerchant())
                .notes(request.getNotes())
                .source(Expense.Source.MANUAL)
                .build();
        return toResponse(expenseRepository.save(expense));
    }

    // --------------------------------------------------------------- update

    @Transactional
    public ExpenseResponse update(Long id, ExpenseRequest request) {
        Expense expense = owned(id);
        if (request.getAmount() != null) expense.setAmount(request.getAmount());
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            expense.setCategory(parseCategory(request.getCategory()));
        }
        if (request.getDate() != null) expense.setDate(request.getDate());
        if (request.getPaymentMode() != null) expense.setPaymentMode(request.getPaymentMode());
        if (request.getMerchant() != null) expense.setMerchant(request.getMerchant());
        if (request.getNotes() != null) expense.setNotes(request.getNotes());
        return toResponse(expenseRepository.save(expense));
    }

    /**
     * POST /api/expenses/{id}/correct {category}:
     * fixes the category AND teaches the merchant mapping so the same
     * merchant is categorized correctly next time.
     */
    @Transactional
    public ExpenseResponse correct(Long id, CorrectionRequest request) {
        Expense expense = owned(id);
        Category category = parseCategory(request.getCategory());
        expense.setCategory(category);
        learningService.learn(expense.getUser(), expense.getMerchant(), category);
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public void delete(Long id) {
        owned(id); // throws 404 when not found / not owned
        expenseRepository.deleteByIdAndUserId(id, currentUser.userId());
    }

    // ---------------------------------------------------------------- util

    /** Loads the expense or throws 404 (also enforces ownership). */
    private Expense owned(Long id) {
        return expenseRepository.findByIdAndUserId(id, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));
    }

    private Category resolveCategory(User user, String requested,
                                     String merchant, String notes) {
        if (requested != null && !requested.isBlank()
                && !requested.equalsIgnoreCase("Other")) {
            return parseCategory(requested); // user knows best
        }
        // 1. learned mapping
        return learningService.lookup(user.getId(), merchant)
                // 2. ML service (only worth asking when we have text to classify)
                .or(() -> (merchant != null && !merchant.isBlank())
                        || (notes != null && !notes.isBlank())
                        ? mlClientService.categorize(merchant, notes)
                        : java.util.Optional.empty())
                // 3. fallback
                .orElse(Category.Other);
    }

    private Category parseCategory(String raw) {
        try {
            return Category.valueOf(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown category: " + raw
                    + ". Valid: " + java.util.Arrays.toString(Category.values()));
        }
    }

    private Category parseCategoryOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseCategory(raw);
    }

    public ExpenseResponse toResponse(Expense e) {
        return ExpenseResponse.builder()
                .id(e.getId())
                .amount(e.getAmount())
                .category(e.getCategory().name())
                .date(e.getDate())
                .paymentMode(e.getPaymentMode())
                .merchant(e.getMerchant())
                .notes(e.getNotes())
                .source(e.getSource().name())
                .bankTxnRef(e.getBankTxnRef())
                .build();
    }
}

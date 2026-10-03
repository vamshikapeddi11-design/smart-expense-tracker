package com.expensetracker.service;

import com.expensetracker.model.Expense;
import com.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Prevents the same real-world purchase from being stored twice - e.g. when a
 * bank sync runs twice, or a statement file overlaps with an earlier sync.
 *
 * Two records are considered the SAME transaction when ALL hold:
 *   1. amounts are exactly equal, AND
 *   2. |date difference| <= 2 days (banks sometimes post late), AND
 *   3. normalized merchant names overlap OR VPAs overlap
 *      (overlap = one contains the other, case-insensitive, after trimming).
 *
 * A matching bankTxnRef is an even stronger signal and is checked first by
 * callers via ExpenseRepository.findByUserIdAndBankTxnRef.
 */
@Service
@RequiredArgsConstructor
public class DedupService {

    private final ExpenseRepository expenseRepository;

    /** True if an equivalent expense already exists for this user. */
    public boolean isDuplicate(Long userId, BigDecimal amount, LocalDate date,
                               String merchant, String vpa) {
        List<Expense> candidates = expenseRepository.findByUserIdAndAmountAndDateBetween(
                userId, amount, date.minusDays(2), date.plusDays(2));
        String normMerchant = norm(merchant);
        String normVpa = norm(vpa);
        return candidates.stream().anyMatch(e ->
                overlaps(normMerchant, norm(e.getMerchant()))
                        || overlaps(normVpa, normVpaOf(e)));
    }

    /** VPA isn't stored on Expense; we check it inside notes for BANK imports. */
    private String normVpaOf(Expense e) {
        // Imported expenses embed "vpa:<id>" in notes; plain merchants won't match.
        return norm(e.getNotes());
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    /** Overlap = either side contains the other; empty strings never overlap. */
    private static boolean overlaps(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.contains(b) || b.contains(a);
    }
}

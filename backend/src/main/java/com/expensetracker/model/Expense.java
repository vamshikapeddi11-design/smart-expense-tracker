package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single expense record belonging to a user.
 *
 * {@code source} tells us where the expense came from:
 *   MANUAL    - typed in by the user
 *   QR        - captured by scanning a UPI QR code
 *   BANK      - imported/synced from a bank statement
 *   RECURRING - auto-generated from a RecurringExpense rule
 *
 * {@code bankTxnRef} links back to the original bank transaction id so the
 * dedup logic can recognise the same transaction if it is synced twice.
 */
@Entity
@Table(name = "expenses", indexes = {
        @Index(name = "idx_expense_user_date", columnList = "user_id, date")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Expense {

    public enum Source { MANUAL, QR, BANK, RECURRING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // always positive; expenses are money spent

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private LocalDate date; // the day the money was spent

    private String paymentMode; // e.g. UPI, Cash, Card, NetBanking

    private String merchant; // shop / payee name, e.g. "Swiggy"

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    /** Original bank transaction id, used to avoid importing the same txn twice. */
    private String bankTxnRef;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

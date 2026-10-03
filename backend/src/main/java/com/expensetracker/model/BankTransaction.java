package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A raw transaction fetched from the bank provider (before it becomes an
 * Expense). Keeping the raw feed lets us re-run categorization and keeps an
 * audit trail of what the bank actually sent.
 */
@Entity
@Table(name = "bank_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_banktxn_user_txn", columnNames = {"user_id", "txnId"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Transaction id assigned by the bank/provider (used for dedup). */
    @Column(nullable = false)
    private String txnId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String type; // DEBIT or CREDIT (only debits become expenses)

    private String merchant;
    private String vpa; // UPI id, e.g. "shop@okhdfcbank"
    private String note;

    private BigDecimal balance; // account balance after this txn (informational)

    /** True once this raw txn has been converted into an Expense. */
    @Column(nullable = false)
    private boolean processed = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

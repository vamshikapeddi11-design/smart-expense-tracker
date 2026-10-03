package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A rule like "Netflix, Rs 199, monthly" - the app turns each due occurrence
 * into a real Expense (source = RECURRING) and moves nextDueDate forward.
 */
@Entity
@Table(name = "recurring_expenses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecurringExpense {

    public enum Frequency { MONTHLY, WEEKLY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name; // e.g. "Netflix", "House rent"

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(nullable = false)
    private LocalDate nextDueDate; // when the next expense should be created

    private String paymentMode;
    private String merchant;

    @Column(nullable = false)
    private boolean active = true; // user can pause a rule without deleting it

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

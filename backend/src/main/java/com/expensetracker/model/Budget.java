package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Monthly spending limit for one category, set by the user.
 *
 * {@code alertThreshold} is a fraction like 0.8 meaning "warn me when I've
 * spent 80% of the limit". The alerts endpoint compares actual spend against
 * limit * threshold to report OK / NEAR / EXCEEDED.
 */
@Entity
@Table(name = "budgets", uniqueConstraints = {
        @UniqueConstraint(name = "uq_budget_user_category", columnNames = {"user_id", "category"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyLimit;

    /** 0.0 - 1.0, e.g. 0.8 = alert at 80% of the limit. */
    @Column(nullable = false)
    private Double alertThreshold;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (alertThreshold == null) alertThreshold = 0.8;
    }
}

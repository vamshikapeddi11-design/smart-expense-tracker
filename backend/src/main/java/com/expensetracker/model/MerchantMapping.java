package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Learned mapping: normalized merchant name -> category.
 *
 * When the user corrects an expense's category
 * (POST /api/expenses/{id}/correct), we remember "merchant X means category Y"
 * so future transactions from the same merchant are categorized automatically.
 * This is the app's tiny on-device "learning" layer, complementing the ML
 * service.
 */
@Entity
@Table(name = "merchant_mappings", uniqueConstraints = {
        @UniqueConstraint(name = "uq_merchant_user_key", columnNames = {"user_id", "merchantKey"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MerchantMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Lower-cased, trimmed merchant name used as the lookup key. */
    @Column(nullable = false)
    private String merchantKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    /** How many times this mapping has been confirmed - a simple confidence score. */
    @Column(nullable = false)
    private int hitCount = 1;

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

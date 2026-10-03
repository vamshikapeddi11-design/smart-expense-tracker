package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A bank account the user linked via consent.
 *
 * Privacy: we store ONLY the masked number (e.g. "XXXX1234"), never the full
 * account number. The real number stays with the bank.
 */
@Entity
@Table(name = "bank_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_id")
    private BankConsent consent;

    @Column(nullable = false)
    private String bankName;

    /** Masked account number, e.g. "XXXX-XXXX-1234". Full number is never stored. */
    @Column(nullable = false)
    private String maskedNumber;

    /** Provider-side account id (opaque reference, not the real number). */
    private String externalAccountId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

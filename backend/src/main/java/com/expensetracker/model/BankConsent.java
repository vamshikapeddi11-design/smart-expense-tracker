package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Records a user's (mock) consent to share bank data - like the Account
 * Aggregator flow in real UPI/banking apps. In this project the "bank" is a
 * mock provider, so consent goes PENDING -> APPROVED immediately.
 */
@Entity
@Table(name = "bank_consents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankConsent {

    public enum Status { PENDING, APPROVED, REVOKED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String bankName;

    /** External reference id returned by the (mock) bank. */
    @Column(nullable = false, unique = true)
    private String consentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

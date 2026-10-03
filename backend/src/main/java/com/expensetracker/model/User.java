package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * App user. One row per registered account.
 * Passwords are NEVER stored in plain text - only the BCrypt hash.
 */
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false, unique = true)
    private String email; // also used as the login username

    @Column(nullable = false)
    private String passwordHash; // BCrypt hash of the password

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

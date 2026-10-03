package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One row per bank-sync run (manual or scheduled).
 * Useful for the UI ("last synced 2h ago") and for debugging.
 */
@Entity
@Table(name = "sync_runs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SyncRun {

    public enum Status { SUCCESS, PARTIAL, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int newTransactions;

    @Column(nullable = false)
    private int duplicatesSkipped;

    private String message; // error detail when status is FAILED/PARTIAL

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @PrePersist
    void onCreate() {
        startedAt = Instant.now();
    }
}

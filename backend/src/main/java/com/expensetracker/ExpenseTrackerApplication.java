package com.expensetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Smart Expense Tracker backend.
 *
 * @EnableScheduling turns on Spring's scheduler, which is used by
 * {@code SyncService} to periodically pull bank transactions in the
 * background (can be disabled via {@code bank.sync.enabled=false}).
 */
@SpringBootApplication
@EnableScheduling
public class ExpenseTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackerApplication.class, args);
    }
}

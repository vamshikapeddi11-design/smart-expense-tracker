package com.expensetracker.bank;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Normalized transaction as returned by any BankProvider.
 * Providers translate their own wire format into this DTO so the rest of the
 * app (SyncService, import) only ever deals with one shape.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankTransactionDto {

    private String txnId;
    private LocalDate date;
    private BigDecimal amount;
    private String type; // DEBIT or CREDIT
    private String merchant;
    private String vpa;
    private String note;
    private BigDecimal balance;
}

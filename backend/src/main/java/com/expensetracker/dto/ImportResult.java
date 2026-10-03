package com.expensetracker.dto;

import lombok.*;

/** Result of a bank sync or a statement file import. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportResult {

    private int imported;        // brand-new transactions stored
    private int skipped;         // duplicates ignored
    private int newTransactions; // alias of imported (used by /bank/sync contract)
    private int duplicatesSkipped; // alias of skipped (used by /bank/sync contract)
}

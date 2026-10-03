package com.expensetracker.bank;

import java.util.List;

/**
 * Pluggable bank integration.
 *
 * Today we only have MockBankProvider (reads a JSON fixture from the
 * classpath). To add a real bank later, implement this interface and mark it
 * @Primary instead - SyncService and BankController only depend on the
 * interface, never on the mock.
 */
public interface BankProvider {

    /** Human-readable name, e.g. "MockBank". Shown in the UI. */
    String getProviderName();

    /**
     * "Links" an account for the consent: returns masked account info.
     * Only masked numbers may leave this method - never full account numbers.
     */
    BankAccountInfo linkAccount(String bankName, String consentId);

    /** Fetches recent transactions for the consent (newest last is fine). */
    List<BankTransactionDto> fetchTransactions(String consentId);

    /** Simple value object for a linked account. */
    record BankAccountInfo(String bankName, String maskedNumber, String externalAccountId) {}
}

package com.expensetracker.controller;

import com.expensetracker.bank.BankProvider;
import com.expensetracker.dto.ConsentRequest;
import com.expensetracker.dto.ImportResult;
import com.expensetracker.model.BankAccount;
import com.expensetracker.model.BankConsent;
import com.expensetracker.model.User;
import com.expensetracker.repository.BankAccountRepository;
import com.expensetracker.repository.BankConsentRepository;
import com.expensetracker.service.CurrentUser;
import com.expensetracker.service.StatementImportService;
import com.expensetracker.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mock Account-Aggregator style bank flow + statement import.
 * All endpoints need a JWT. Only MASKED account numbers are ever returned.
 */
@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class BankController {

    private final BankConsentRepository consentRepository;
    private final BankAccountRepository accountRepository;
    private final BankProvider bankProvider;
    private final SyncService syncService;
    private final StatementImportService importService;
    private final CurrentUser currentUser;

    /**
     * POST /api/bank/consent {bankName} -> 201
     * {consentId, status, redirectUrl (mock)}.
     * The mock provider approves immediately (PENDING -> APPROVED) and links
     * one account, like a real AA redirect flow would after user approval.
     */
    @PostMapping("/consent")
    public ResponseEntity<Map<String, Object>> consent(
            @Valid @RequestBody ConsentRequest request) {
        User user = currentUser.user();
        String consentId = "consent-" + UUID.randomUUID();
        BankConsent consent = BankConsent.builder()
                .user(user)
                .bankName(request.getBankName())
                .consentId(consentId)
                .status(BankConsent.Status.PENDING)
                .build();
        consent = consentRepository.save(consent);

        // Mock approval: instantly approve and link the account via the provider.
        BankProvider.BankAccountInfo info =
                bankProvider.linkAccount(request.getBankName(), consentId);
        accountRepository.save(BankAccount.builder()
                .user(user)
                .consent(consent)
                .bankName(info.bankName())
                .maskedNumber(info.maskedNumber()) // masked only - never full numbers
                .externalAccountId(info.externalAccountId())
                .build());
        consent.setStatus(BankConsent.Status.APPROVED);
        consentRepository.save(consent);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "consentId", consentId,
                "status", consent.getStatus().name(),
                // In a real flow this would be the bank/AA approval page.
                "redirectUrl", "http://localhost:8080/mock-bank/approve?consentId=" + consentId));
    }

    /** GET /api/bank/accounts -> linked accounts (masked numbers only). */
    @GetMapping("/accounts")
    public List<Map<String, String>> accounts() {
        return accountRepository.findByUserId(currentUser.userId()).stream()
                .map(a -> Map.of(
                        "bankName", a.getBankName(),
                        "maskedNumber", a.getMaskedNumber()))
                .toList();
    }

    /**
     * POST /api/bank/sync -> {newTransactions, duplicatesSkipped}.
     * Fetches via BankProvider, dedups, categorizes new ones into expenses.
     */
    @PostMapping("/sync")
    public ImportResult sync() {
        return syncService.syncForCurrentUser();
    }

    /**
     * POST /api/bank/import (multipart "file", .csv/.xlsx/.pdf)
     * -> {imported, skipped}.
     */
    @PostMapping("/import")
    public ImportResult importStatement(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        return importService.importFile(file);
    }
}

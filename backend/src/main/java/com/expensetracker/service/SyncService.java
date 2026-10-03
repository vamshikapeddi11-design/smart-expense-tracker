package com.expensetracker.service;

import com.expensetracker.bank.BankProvider;
import com.expensetracker.bank.BankTransactionDto;
import com.expensetracker.dto.ImportResult;
import com.expensetracker.model.BankConsent;
import com.expensetracker.model.SyncRun;
import com.expensetracker.model.User;
import com.expensetracker.repository.BankConsentRepository;
import com.expensetracker.repository.SyncRunRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bank sync orchestration.
 *
 * syncForUser(): for each APPROVED consent of the user, fetch transactions
 * from the BankProvider and store them via StatementImportService (which
 * handles dedup + categorization). Every run is recorded as a SyncRun row.
 *
 * scheduledSync(): runs every 6h (fixedDelay) for all users, but only when
 * bank.sync.enabled=true (default false). Manual sync via POST /api/bank/sync
 * always works regardless of the flag.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final BankProvider bankProvider; // MockBankProvider (@Primary)
    private final BankConsentRepository consentRepository;
    private final SyncRunRepository syncRunRepository;
    private final UserRepository userRepository;
    private final StatementImportService importService;
    private final RecurringService recurringService;
    private final CurrentUser currentUser;

    @Value("${bank.sync.enabled:false}")
    private boolean syncEnabled;

    /** Manual sync for the logged-in user (POST /api/bank/sync). */
    public ImportResult syncForCurrentUser() {
        return syncForUser(currentUser.user());
    }

    public ImportResult syncForUser(User user) {
        List<BankConsent> consents = consentRepository.findByUserId(user.getId()).stream()
                .filter(c -> c.getStatus() == BankConsent.Status.APPROVED)
                .toList();
        SyncRun run = SyncRun.builder()
                .user(user)
                .status(SyncRun.Status.SUCCESS)
                .newTransactions(0)
                .duplicatesSkipped(0)
                .build();
        syncRunRepository.save(run);

        int totalNew = 0, totalDup = 0;
        try {
            for (BankConsent consent : consents) {
                List<BankTransactionDto> txns =
                        bankProvider.fetchTransactions(consent.getConsentId());
                ImportResult r = importService.storeAll(user, txns);
                totalNew += r.getNewTransactions();
                totalDup += r.getDuplicatesSkipped();
            }
            // Also materialize any recurring expenses that came due.
            recurringService.materializeDue();
        } catch (Exception e) {
            log.error("Bank sync failed for user {}", user.getId(), e);
            run.setStatus(SyncRun.Status.FAILED);
            run.setMessage(e.getMessage());
        }
        run.setNewTransactions(totalNew);
        run.setDuplicatesSkipped(totalDup);
        run.setFinishedAt(java.time.Instant.now());
        syncRunRepository.save(run);

        log.info("Bank sync for user {}: {} new, {} duplicates",
                user.getId(), totalNew, totalDup);
        return ImportResult.builder()
                .imported(totalNew).skipped(totalDup)
                .newTransactions(totalNew).duplicatesSkipped(totalDup)
                .build();
    }

    /**
     * Background job: every 6 hours after the previous run finished.
     * Disabled by default via bank.sync.enabled=false.
     */
    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000)
    public void scheduledSync() {
        if (!syncEnabled) {
            return;
        }
        log.info("Starting scheduled bank sync for all users");
        for (User user : userRepository.findAll()) {
            try {
                syncForUser(user);
            } catch (Exception e) {
                log.error("Scheduled sync failed for user {}", user.getId(), e);
            }
        }
    }
}

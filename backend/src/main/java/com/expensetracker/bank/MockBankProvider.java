package com.expensetracker.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mock bank for demos and tests.
 *
 * Reads {@code src/main/resources/mock-statement.json} from the classpath
 * (placed there by another module). Expected shape:
 * <pre>
 * {
 *   "account": {"accountId": "...", "maskedNumber": "XXXX1234", "bankName": "..."},
 *   "transactions": [
 *     {"txnId": "...", "date": "2026-09-01", "amount": 250.00,
 *      "type": "DEBIT", "merchant": "...", "vpa": "...",
 *      "note": "...", "balance": 1234.00}
 *   ]
 * }
 * </pre>
 * Marked @Primary so it is the BankProvider injected everywhere.
 */
@Component
@Primary
@Slf4j
public class MockBankProvider implements BankProvider {

    private static final String MOCK_FILE = "mock-statement.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getProviderName() {
        return "MockBank";
    }

    @Override
    public BankAccountInfo linkAccount(String bankName, String consentId) {
        JsonNode root = loadMockFile();
        JsonNode account = root.path("account");
        // Prefer the fixture's values; fall back to the requested bank name.
        String name = account.path("bankName").asText(bankName);
        String masked = account.path("maskedNumber").asText("XXXX0000");
        String accountId = account.path("accountId").asText(UUID.randomUUID().toString());
        log.info("MockBank: linked account {} for consent {}", masked, consentId);
        return new BankAccountInfo(name, masked, accountId);
    }

    @Override
    public List<BankTransactionDto> fetchTransactions(String consentId) {
        JsonNode root = loadMockFile();
        List<BankTransactionDto> result = new ArrayList<>();
        for (JsonNode t : root.path("transactions")) {
            result.add(BankTransactionDto.builder()
                    .txnId(t.path("txnId").asText())
                    .date(LocalDate.parse(t.path("date").asText()))
                    .amount(new BigDecimal(t.path("amount").asText("0")))
                    .type(t.path("type").asText("DEBIT"))
                    .merchant(nullIfBlank(t.path("merchant").asText(null)))
                    .vpa(nullIfBlank(t.path("vpa").asText(null)))
                    .note(nullIfBlank(t.path("note").asText(null)))
                    .balance(t.hasNonNull("balance")
                            ? new BigDecimal(t.path("balance").asText("0")) : null)
                    .build());
        }
        log.info("MockBank: returning {} transactions for consent {}", result.size(), consentId);
        return result;
    }

    /** Loads the JSON fixture from the classpath (works inside the jar too). */
    private JsonNode loadMockFile() {
        try (InputStream in = new ClassPathResource(MOCK_FILE).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not read " + MOCK_FILE + " from classpath. "
                            + "It must be placed at src/main/resources/" + MOCK_FILE, e);
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}

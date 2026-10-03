package com.expensetracker.service;

import com.expensetracker.bank.BankTransactionDto;
import com.expensetracker.dto.ImportResult;
import com.expensetracker.model.BankTransaction;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.BankTransactionRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports bank statements uploaded as .csv / .xlsx / .pdf.
 *
 * Pipeline: parse file -> normalize rows to BankTransactionDto -> dedup
 * (exact txnId, then fuzzy DedupService) -> categorize (learned mapping, then
 * ML, then Other) -> store BankTransaction raw row + Expense.
 *
 * Only DEBIT rows become expenses (credits are income, out of scope).
 * CSV/Excel are expected to have a header row with columns like:
 *   date, description/merchant, debit/amount, type, txnId ...
 * PDF parsing is line-regex based (best effort for simple statements).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatementImportService {

    private final BankTransactionRepository bankTransactionRepository;
    private final ExpenseRepository expenseRepository;
    private final DedupService dedupService;
    private final MlClientService mlClientService;
    private final MerchantLearningService learningService;
    private final CurrentUser currentUser;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,                 // 2026-09-01
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
    };

    // Matches lines like: 01/09/2026  SWIGGY  250.00  Dr
    private static final Pattern PDF_LINE =
            Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\s+(.+?)\\s+([0-9,]+\\.\\d{2})\\s*(Dr|Cr|DEBIT|CREDIT)?",
                    Pattern.CASE_INSENSITIVE);

    // ------------------------------------------------------------------ api

    /** Imports one uploaded statement file for the current user. */
    @Transactional
    public ImportResult importFile(MultipartFile file) {
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        List<BankTransactionDto> rows;
        try {
            if (name.endsWith(".csv")) {
                rows = parseCsv(file);
            } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                rows = parseExcel(file);
            } else if (name.endsWith(".pdf")) {
                rows = parsePdf(file);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported file type. Upload .csv, .xlsx or .pdf");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse statement file: " + e.getMessage());
        }
        return storeAll(currentUser.user(), rows);
    }

    // ----------------------------------------------------------------- parse

    private List<BankTransactionDto> parseCsv(MultipartFile file) throws Exception {
        List<BankTransactionDto> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) return rows;
            int date = col(header, "date"), desc = col(header, "description", "merchant", "narration"),
                amt = col(header, "amount", "debit"), type = col(header, "type", "dr/cr"),
                txn = col(header, "txnid", "transaction id", "reference");
            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length == 0 || isBlankRow(line)) continue;
                rows.add(BankTransactionDto.builder()
                        .txnId(cell(line, txn))
                        .date(parseDate(cell(line, date)))
                        .amount(parseAmount(cell(line, amt)))
                        .type(normalizeType(cell(line, type)))
                        .merchant(cell(line, desc))
                        .note(cell(line, desc))
                        .build());
            }
        }
        return rows;
    }

    private List<BankTransactionDto> parseExcel(MultipartFile file) throws Exception {
        List<BankTransactionDto> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return rows;
            String[] header = rowToStrings(headerRow);
            int date = col(header, "date"), desc = col(header, "description", "merchant", "narration"),
                amt = col(header, "amount", "debit"), type = col(header, "type", "dr/cr"),
                txn = col(header, "txnid", "transaction id", "reference");
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String[] line = rowToStrings(row);
                if (isBlankRow(line)) continue;
                rows.add(BankTransactionDto.builder()
                        .txnId(cell(line, txn))
                        .date(parseDate(cell(line, date)))
                        .amount(parseAmount(cell(line, amt)))
                        .type(normalizeType(cell(line, type)))
                        .merchant(cell(line, desc))
                        .note(cell(line, desc))
                        .build());
            }
        }
        return rows;
    }

    /** Best-effort: extracts "date ... description ... amount ... Dr/Cr" lines. */
    private List<BankTransactionDto> parsePdf(MultipartFile file) throws Exception {
        List<BankTransactionDto> rows = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            String text = new PDFTextStripper().getText(doc);
            int seq = 1;
            for (String line : text.lines().toArray(String[]::new)) {
                Matcher m = PDF_LINE.matcher(line.trim());
                if (!m.find()) continue;
                String drCr = m.group(4);
                rows.add(BankTransactionDto.builder()
                        .txnId("pdf-" + (seq++))
                        .date(parseDate(m.group(1)))
                        .amount(parseAmount(m.group(3)))
                        .type(drCr != null && drCr.equalsIgnoreCase("Cr") ? "CREDIT" : "DEBIT")
                        .merchant(m.group(2).trim())
                        .note(line.trim())
                        .build());
            }
        }
        return rows;
    }

    // ----------------------------------------------------------------- store

    /**
     * Dedups + categorizes + stores every row.
     * Two-level dedup: exact bank txnId first, then fuzzy (amount/date/merchant).
     */
    @Transactional
    public ImportResult storeAll(User user, List<BankTransactionDto> rows) {
        int imported = 0, skipped = 0;
        for (BankTransactionDto dto : rows) {
            if (dto.getDate() == null || dto.getAmount() == null) {
                skipped++; // unparseable row
                continue;
            }
            // 1. exact txnId dedup (raw table + already-imported expenses)
            if (dto.getTxnId() != null
                    && (bankTransactionRepository.existsByUserIdAndTxnId(user.getId(), dto.getTxnId())
                    || expenseRepository.findByUserIdAndBankTxnRef(user.getId(), dto.getTxnId()).isPresent())) {
                skipped++;
                continue;
            }
            // 2. fuzzy dedup against existing expenses
            if (dedupService.isDuplicate(user.getId(), dto.getAmount(), dto.getDate(),
                    dto.getMerchant(), dto.getVpa())) {
                skipped++;
                continue;
            }
            // Keep the raw bank row for audit/re-categorization.
            bankTransactionRepository.save(BankTransaction.builder()
                    .user(user)
                    .txnId(dto.getTxnId() != null ? dto.getTxnId()
                            : "gen-" + user.getId() + "-" + System.nanoTime())
                    .date(dto.getDate())
                    .amount(dto.getAmount())
                    .type(dto.getType() != null ? dto.getType() : "DEBIT")
                    .merchant(dto.getMerchant())
                    .vpa(dto.getVpa())
                    .note(dto.getNote())
                    .balance(dto.getBalance())
                    .processed(true)
                    .build());

            // Only debits are expenses; credits are income (out of scope).
            if ("CREDIT".equalsIgnoreCase(dto.getType())) {
                skipped++;
                continue;
            }
            Category category = learningService.lookup(user.getId(), dto.getMerchant())
                    .or(() -> mlClientService.categorize(dto.getMerchant(), dto.getNote()))
                    .orElse(Category.Other);
            Expense expense = Expense.builder()
                    .user(user)
                    .amount(dto.getAmount())
                    .category(category)
                    .date(dto.getDate())
                    .paymentMode("Bank")
                    .merchant(dto.getMerchant())
                    .notes(dto.getNote() != null ? dto.getNote()
                            : (dto.getVpa() != null ? "vpa:" + dto.getVpa() : null))
                    .source(Expense.Source.BANK)
                    .bankTxnRef(dto.getTxnId())
                    .build();
            expenseRepository.save(expense);
            imported++;
        }
        log.info("Statement import for user {}: {} imported, {} skipped",
                user.getId(), imported, skipped);
        return ImportResult.builder()
                .imported(imported).skipped(skipped)
                .newTransactions(imported).duplicatesSkipped(skipped)
                .build();
    }

    // ------------------------------------------------------------ cell utils

    /** Finds the first header column whose name contains one of the keys. */
    private int col(String[] header, String... keys) {
        for (int i = 0; i < header.length; i++) {
            String h = header[i] == null ? "" : header[i].trim().toLowerCase();
            for (String k : keys) {
                if (h.contains(k)) return i;
            }
        }
        return -1;
    }

    private String cell(String[] line, int idx) {
        if (idx < 0 || idx >= line.length) return null;
        String v = line[idx];
        return v == null || v.isBlank() ? null : v.trim();
    }

    private boolean isBlankRow(String[] line) {
        for (String c : line) if (c != null && !c.isBlank()) return false;
        return true;
    }

    private String[] rowToStrings(Row row) {
        DataFormatter fmt = new DataFormatter(); // formats numbers/dates as displayed
        String[] out = new String[row.getLastCellNum()];
        for (int i = 0; i < out.length; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            out[i] = fmt.formatCellValue(cell);
        }
        return out;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) { /* try next */ }
        }
        return null;
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // strips currency symbols, spaces, thousand separators
            return new BigDecimal(raw.replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "Dr"/"debit"/"-" -> DEBIT, "Cr"/"credit" -> CREDIT, default DEBIT. */
    private String normalizeType(String raw) {
        if (raw == null) return "DEBIT";
        String t = raw.trim().toLowerCase();
        if (t.startsWith("cr") || t.equals("credit")) return "CREDIT";
        return "DEBIT";
    }
}

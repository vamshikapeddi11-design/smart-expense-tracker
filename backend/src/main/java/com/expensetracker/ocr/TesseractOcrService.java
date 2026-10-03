package com.expensetracker.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OCR via the Tesseract native binary.
 *
 * STUB / BEST-EFFORT IMPLEMENTATION (clearly marked as required):
 * Tesseract is a C++ program, not a Java library, so this service shells out
 * to the `tesseract` command if it is installed on the host. Steps:
 *   1. Write the uploaded image bytes to a temp file.
 *   2. Run `tesseract <img> stdout` and capture its text output.
 *   3. If the binary is missing (or fails), return OcrResult.unavailable(...)
 *      with the structured reason "OCR_UNAVAILABLE" instead of throwing, so
 *      the QR scan endpoint can degrade gracefully to "QR not found".
 *
 * To enable real OCR on the demo machine: `sudo apt install tesseract-ocr`.
 */
@Service
@Slf4j
public class TesseractOcrService implements OcrService {

    private static final String REASON_UNAVAILABLE = "OCR_UNAVAILABLE";

    @Override
    public OcrResult extractText(byte[] imageBytes, String contentType) {
        if (!isTesseractPresent()) {
            log.warn("tesseract binary not found - OCR unavailable");
            return OcrResult.unavailable(REASON_UNAVAILABLE + ": tesseract binary not installed");
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("qr-scan-", ".img");
            Files.write(tmp, imageBytes);
            Process process = new ProcessBuilder("tesseract", tmp.toString(), "stdout", "--psm", "6")
                    .redirectErrorStream(true)
                    .start();
            boolean done = process.waitFor(30, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return OcrResult.unavailable(REASON_UNAVAILABLE + ": tesseract timed out");
            }
            String output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0 || output.isBlank()) {
                return OcrResult.unavailable(REASON_UNAVAILABLE + ": tesseract produced no text");
            }
            List<String> lines = output.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty())
                    .toList();
            return OcrResult.of(lines);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("tesseract invocation failed", e);
            return OcrResult.unavailable(REASON_UNAVAILABLE + ": " + e.getMessage());
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { /* best effort */ }
            }
        }
    }

    /** Checks PATH for the tesseract binary without running a full OCR job. */
    private boolean isTesseractPresent() {
        try {
            Process p = new ProcessBuilder("tesseract", "--version")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}

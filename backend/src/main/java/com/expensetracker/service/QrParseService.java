package com.expensetracker.service;

import com.expensetracker.dto.UpiParseResponse;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.expensetracker.ocr.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UPI QR handling.
 *
 * 1. parseUpiPayload: turns "upi://pay?pa=...&pn=...&am=...&tn=...&cu=..."
 *    into a UpiParseResponse. Throws IllegalArgumentException on garbage
 *    input (controller maps it to HTTP 400).
 * 2. scanImage: decodes a QR code from an uploaded image with ZXing; if the
 *    QR content is a UPI payload it is parsed; otherwise, as a fallback, OCR
 *    text is scanned with regexes for amount/date/merchant hints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrParseService {

    private final OcrService ocrService; // TesseractOcrService (stub when binary missing)

    // Regexes used on OCR fallback text (Indian receipts/statements).
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\b(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})\\b");
    private static final Pattern UPI_ID_PATTERN =
            Pattern.compile("\\b([a-zA-Z0-9._-]{2,}@[a-zA-Z]{2,})\\b");

    // ---------------------------------------------------------------- parse

    /**
     * Parses a UPI intent URI. Only "upi://pay" payloads are accepted;
     * anything else (plain text, URLs, other schemes) is rejected with 400.
     */
    public UpiParseResponse parseUpiPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload must not be empty");
        }
        String trimmed = payload.trim();
        if (!trimmed.toLowerCase().startsWith("upi://pay?")) {
            throw new IllegalArgumentException(
                    "Invalid UPI payload: must start with 'upi://pay?'");
        }
        Map<String, String> params = parseQuery(trimmed.substring("upi://pay?".length()));

        String pa = params.get("pa");
        if (pa == null || pa.isBlank()) {
            throw new IllegalArgumentException("Invalid UPI payload: missing 'pa' (payee VPA)");
        }
        String am = params.get("am");
        BigDecimal amount = null;
        if (am != null && !am.isBlank()) {
            try {
                amount = new BigDecimal(am);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid UPI payload: bad 'am' amount");
            }
        }
        return UpiParseResponse.builder()
                .payeeVpa(pa)
                .payeeName(params.get("pn"))
                .amount(amount)
                .note(params.get("tn"))
                .currency(params.getOrDefault("cu", "INR"))
                .build();
    }

    /** Splits "a=1&b=2" into a map, URL-decoding keys and values. */
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            params.put(urlDecode(key), urlDecode(value));
        }
        return params;
    }

    private String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ scan image

    /**
     * Result of scanning an uploaded image: either a parsed UPI payload, or
     * OCR-derived hints, or a structured "nothing found" outcome.
     */
    public record ScanResult(boolean qrFound, UpiParseResponse upi,
                             OcrHints ocrHints, String detail) {}

    /** Best-effort fields pulled from OCR text when no QR is present. */
    public record OcrHints(BigDecimal amount, String date, String merchant) {}

    public ScanResult scanImage(byte[] imageBytes, String contentType) {
        // Step 1: try QR decode with ZXing.
        String qrText = decodeQr(imageBytes);
        if (qrText != null) {
            log.info("QR decoded from image");
            if (qrText.trim().toLowerCase().startsWith("upi://pay?")) {
                return new ScanResult(true, parseUpiPayload(qrText), null, "qr-upi");
            }
            return new ScanResult(true, null, null,
                    "qr-not-upi: QR found but it is not a UPI payment payload");
        }

        // Step 2: OCR fallback (gracefully unavailable when tesseract missing).
        OcrService.OcrResult ocr = ocrService.extractText(imageBytes, contentType);
        if (!ocr.available()) {
            return new ScanResult(false, null, null,
                    "no-qr: no QR code found; " + ocr.reason());
        }
        String text = String.join("\n", ocr.lines());
        return new ScanResult(false, null,
                new OcrHints(extractAmount(text), extractDate(text), extractMerchant(text)),
                "no-qr: OCR hints extracted");
    }

    /** Returns the QR text, or null when the image contains no QR code. */
    private String decodeQr(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) return null; // not a readable image
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            return new MultiFormatReader().decode(bitmap).getText();
        } catch (NotFoundException e) {
            return null; // ZXing's way of saying "no QR in this image"
        } catch (Exception e) {
            log.warn("QR decode failed: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------- OCR regex hints

    private BigDecimal extractAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        BigDecimal best = null;
        while (m.find()) { // take the largest money figure as the likely total
            BigDecimal v = new BigDecimal(m.group(1).replace(",", ""));
            if (best == null || v.compareTo(best) > 0) best = v;
        }
        return best;
    }

    private String extractDate(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String extractMerchant(String text) {
        // Heuristic: a UPI id often identifies the merchant; else first line.
        Matcher m = UPI_ID_PATTERN.matcher(text);
        if (m.find()) return m.group(1);
        String[] lines = text.lines().map(String::trim)
                .filter(l -> !l.isEmpty()).toArray(String[]::new);
        return lines.length > 0 ? lines[0] : null;
    }
}

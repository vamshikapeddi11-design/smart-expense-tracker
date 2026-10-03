package com.expensetracker.controller;

import com.expensetracker.dto.UpiParseRequest;
import com.expensetracker.dto.UpiParseResponse;
import com.expensetracker.service.QrParseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * UPI QR utilities.
 *  - POST /api/qr/parse is PUBLIC (no JWT): parsing a QR string needs no user.
 *  - POST /api/qr/scan-image needs a JWT (it touches OCR/file upload).
 */
@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
public class QrController {

    private final QrParseService qrParseService;

    /**
     * POST /api/qr/parse {payload} -> {payeeVpa, payeeName, amount, note, currency}.
     * Returns 400 when the payload is not a valid UPI pay URI.
     */
    @PostMapping("/parse")
    public UpiParseResponse parse(@Valid @RequestBody UpiParseRequest request) {
        return qrParseService.parseUpiPayload(request.getPayload());
    }

    /**
     * POST /api/qr/scan-image (multipart "file").
     * Tries ZXing QR decode; if the QR holds a UPI payload it is parsed.
     * Otherwise falls back to OCR hints (amount/date/merchant via regex),
     * or a structured "unavailable" detail when tesseract isn't installed.
     */
    @PostMapping("/scan-image")
    public ResponseEntity<Map<String, Object>> scanImage(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        try {
            QrParseService.ScanResult result =
                    qrParseService.scanImage(file.getBytes(), file.getContentType());
            if (result.qrFound() && result.upi() != null) {
                return ResponseEntity.ok(Map.of(
                        "type", "upi",
                        "upi", result.upi()));
            }
            if (result.qrFound()) {
                return ResponseEntity.ok(Map.of(
                        "type", "qr-not-upi",
                        "detail", result.detail()));
            }
            QrParseService.OcrHints hints = result.ocrHints();
            return ResponseEntity.ok(Map.of(
                    "type", "ocr",
                    "detail", result.detail(),
                    "amount", hints != null && hints.amount() != null
                            ? hints.amount() : "",
                    "date", hints != null && hints.date() != null
                            ? hints.date() : "",
                    "merchant", hints != null && hints.merchant() != null
                            ? hints.merchant() : ""));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not process image: " + e.getMessage());
        }
    }
}

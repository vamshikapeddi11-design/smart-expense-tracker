package com.expensetracker.ocr;

/**
 * OCR (Optical Character Recognition): turns an image of a bill/receipt into text.
 *
 * This is an interface so the real implementation is swappable. The only
 * implementation right now is TesseractOcrService, which shells out to the
 * `tesseract` native binary when it exists.
 */
public interface OcrService {

    /** Extracts text lines from an image. Never returns null. */
    OcrResult extractText(byte[] imageBytes, String contentType);

    /** Result wrapper: either text lines, or a structured "unavailable" reason. */
    record OcrResult(boolean available, java.util.List<String> lines, String reason) {

        /** Convenience factory for the "no OCR engine here" case. */
        public static OcrResult unavailable(String reason) {
            return new OcrResult(false, java.util.List.of(), reason);
        }

        public static OcrResult of(java.util.List<String> lines) {
            return new OcrResult(true, lines, null);
        }
    }
}

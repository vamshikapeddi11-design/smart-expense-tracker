package com.expensetracker.dto;

import lombok.*;

import java.math.BigDecimal;

/** Parsed UPI QR payload returned by POST /api/qr/parse and /scan-image. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpiParseResponse {

    private String payeeVpa;  // pa  - the UPI id to pay, e.g. "shop@okhdfcbank"
    private String payeeName; // pn  - display name of the payee
    private BigDecimal amount; // am - may be absent in the QR (then null)
    private String note;      // tn  - transaction note
    private String currency;  // cu  - usually "INR"
}

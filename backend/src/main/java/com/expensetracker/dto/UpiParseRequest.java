package com.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Body of POST /api/qr/parse: the raw text inside a scanned QR code. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpiParseRequest {

    @NotBlank(message = "Payload is required")
    private String payload; // e.g. "upi://pay?pa=shop@okhdfc&pn=Shop&am=250&cu=INR"
}

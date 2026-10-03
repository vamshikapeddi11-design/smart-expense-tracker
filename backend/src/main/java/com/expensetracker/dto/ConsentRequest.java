package com.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Body of POST /api/bank/consent. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ConsentRequest {

    @NotBlank(message = "Bank name is required")
    private String bankName;
}

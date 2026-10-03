package com.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Body of POST /api/expenses/{id}/correct - the user fixes the category. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CorrectionRequest {

    @NotBlank(message = "Category is required")
    private String category;
}

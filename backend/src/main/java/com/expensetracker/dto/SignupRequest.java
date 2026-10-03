package com.expensetracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body of POST /api/auth/signup.
 * Validation annotations are checked automatically before the controller runs;
 * failures produce a 400 with field messages (see GlobalExceptionHandler).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SignupRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}

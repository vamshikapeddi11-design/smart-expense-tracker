package com.expensetracker.dto;

import lombok.*;

/** Returned by signup/login: the JWT plus basic user info for the UI. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {

    private String token; // "Authorization: Bearer <token>" on later calls
    private UserInfo user;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UserInfo {
        private Long id;
        private String name;
        private String email;
    }
}

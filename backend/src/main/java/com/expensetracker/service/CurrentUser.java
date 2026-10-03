package com.expensetracker.service;

import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.model.User;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Small helper: resolves the currently authenticated user from Spring's
 * SecurityContext (populated by JwtAuthFilter). Every service uses this so
 * users can only ever see their own data.
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    /** Email stored as the "username" inside the JWT. */
    public String email() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResourceNotFoundException("Not authenticated");
        }
        return auth.getName();
    }

    public User user() {
        return userRepository.findByEmail(email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public Long userId() {
        return user().getId();
    }
}

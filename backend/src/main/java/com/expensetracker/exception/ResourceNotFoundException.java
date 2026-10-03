package com.expensetracker.exception;

/**
 * Thrown by services when an id is unknown or belongs to another user.
 * Handled by GlobalExceptionHandler -> HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

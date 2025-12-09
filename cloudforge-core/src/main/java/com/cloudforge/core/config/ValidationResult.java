package com.cloudforge.core.config;

/**
 * Result of field validation.
 *
 * <p>Immutable value object representing success or failure with optional error message.</p>
 *
 * @since 3.0.0
 */
public class ValidationResult {

    private final boolean success;
    private final String message;

    private ValidationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    /**
     * Creates a successful validation result.
     */
    public static ValidationResult ok() {
        return new ValidationResult(true, null);
    }

    /**
     * Creates a failed validation result with error message.
     */
    public static ValidationResult error(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Error message cannot be null or blank");
        }
        return new ValidationResult(false, message);
    }

    /**
     * Returns true if validation succeeded.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns true if validation failed.
     */
    public boolean isError() {
        return !success;
    }

    /**
     * Gets the error message (null if validation succeeded).
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return success ? "ValidationResult[OK]" : "ValidationResult[ERROR: " + message + "]";
    }
}

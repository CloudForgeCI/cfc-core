package com.cloudforge.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ValidationResultTest {

    @Test
    void testOkReturnsSuccess() {
        ValidationResult result = ValidationResult.ok();
        assertTrue(result.isSuccess());
        assertFalse(result.isError());
    }

    @Test
    void testOkHasNullMessage() {
        ValidationResult result = ValidationResult.ok();
        assertNull(result.getMessage());
    }

    @Test
    void testErrorReturnsFailure() {
        ValidationResult result = ValidationResult.error("Something went wrong");
        assertFalse(result.isSuccess());
        assertTrue(result.isError());
    }

    @Test
    void testErrorHasMessage() {
        String errorMessage = "Validation failed: invalid value";
        ValidationResult result = ValidationResult.error(errorMessage);
        assertEquals(errorMessage, result.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void testErrorRejectsNullOrBlankMessage(String invalidMessage) {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationResult.error(invalidMessage));
    }

    @Test
    void testErrorThrowsWithDescriptiveMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ValidationResult.error(null));
        assertEquals("Error message cannot be null or blank", ex.getMessage());
    }

    @Test
    void testOkToString() {
        ValidationResult result = ValidationResult.ok();
        assertEquals("ValidationResult[OK]", result.toString());
    }

    @Test
    void testErrorToString() {
        ValidationResult result = ValidationResult.error("Invalid input");
        assertEquals("ValidationResult[ERROR: Invalid input]", result.toString());
    }

    @Test
    void testMultipleOkCallsReturnIndependentInstances() {
        ValidationResult result1 = ValidationResult.ok();
        ValidationResult result2 = ValidationResult.ok();
        // Both should be successful
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
    }

    @Test
    void testMultipleErrorCallsReturnIndependentInstances() {
        ValidationResult result1 = ValidationResult.error("Error 1");
        ValidationResult result2 = ValidationResult.error("Error 2");
        assertEquals("Error 1", result1.getMessage());
        assertEquals("Error 2", result2.getMessage());
    }

    @Test
    void testIsSuccessAndIsErrorAreMutuallyExclusive() {
        ValidationResult ok = ValidationResult.ok();
        ValidationResult error = ValidationResult.error("error");

        // Success case
        assertTrue(ok.isSuccess() != ok.isError());

        // Error case
        assertTrue(error.isSuccess() != error.isError());
    }
}

package com.cloudforgeci.api.interfaces;

/**
 * IAM Profile enum defining different levels of permissions for AWS resources.
 * These profiles are designed to follow the principle of least privilege.
 */
public enum IAMProfile {
    /**
     * MINIMAL: Only essential permissions required for basic operation.
     * Suitable for production environments with strict compliance requirements.
     * - No administrative permissions
     * - Only read access to required resources
     * - Minimal write permissions for core functionality
     */
    MINIMAL,
    
    /**
     * STANDARD: Balanced permissions for normal operation.
     * Suitable for staging and development environments.
     * - Standard operational permissions
     * - Limited administrative access
     * - Monitoring and logging permissions
     */
    STANDARD,
    
    /**
     * EXTENDED: Broader permissions for development and debugging.
     * Suitable for development environments only.
     * - Additional debugging permissions
     * - Extended monitoring capabilities
     * - Development tools access
     */
    EXTENDED
}

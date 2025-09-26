package com.cloudforgeci.api.interfaces;

/**
 * IAM Configuration interface that extends the base BaseConfiguration interface.
 * Provides IAM-specific configuration methods for different permission profiles.
 */
public interface IAMConfiguration extends BaseConfiguration {
    /**
     * Returns the IAM profile type for this configuration.
     * @return the IAM profile
     */
    IAMProfile kind();
}

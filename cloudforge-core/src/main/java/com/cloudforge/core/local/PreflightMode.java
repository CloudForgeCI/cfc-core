package com.cloudforge.core.local;

import java.util.Locale;

/**
 * How local emulator deploy preflight treats violations.
 */
public enum PreflightMode {
    ENFORCE,
    WARN,
    OFF;

    public static PreflightMode fromEnv(String envKey, PreflightMode defaultMode) {
        String raw = System.getenv(envKey);
        if (raw == null || raw.isBlank()) {
            return defaultMode;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}

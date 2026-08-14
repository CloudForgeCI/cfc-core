package com.cloudforge.core.manager.auth;

import java.time.Instant;

/**
 * A CloudForge Manager user-directory account, as seen through {@link AuthBackend} — a typed
 * stand-in for what used to be an ad-hoc {@code Map<String,Object>} built by hand in two different
 * shapes (one for local rows, one for Cognito pool users). Several fields are only meaningful for
 * one backend and {@code null} for the other, rather than every implementation needing to invent a
 * value for a concept it doesn't have:
 *
 * <ul>
 *   <li>{@code enabled}/{@code status} — Cognito-only (account enable state / Cognito's
 *       {@code UserStatus}, e.g. {@code "CONFIRMED"}); always {@code null} for local accounts.</li>
 *   <li>{@code firstSeenAt}/{@code lastSeenAt}/{@code lastLoginAt}/{@code activeSessionCount} —
 *       local-only (Manager tracks these itself; Cognito sessions aren't Manager's to track);
 *       always {@code null} for Cognito accounts.</li>
 *   <li>{@code linkedAccountId} — the counterpart account's {@link #id()} in the *other* backend,
 *       when this account was created by (or has since been linked during) a migration; {@code
 *       null} otherwise. See {@code AuthBackendMigrationService} for how this avoids creating
 *       duplicate accounts on a later re-migration.</li>
 *   <li>{@code temporaryPassword} — only ever populated on the {@link AuthAccount} an {@code
 *       AuthBackend#createAccount} call returns, never on one from {@code listAccounts()}; a
 *       generated credential is shown exactly once.</li>
 * </ul>
 */
public record AuthAccount(
    String id,
    String username,
    String email,
    String displayName,
    String role,
    String authSource,
    Boolean enabled,
    String status,
    String linkedAccountId,
    String temporaryPassword,
    Instant firstSeenAt,
    Instant lastSeenAt,
    Instant lastLoginAt,
    Integer activeSessionCount
) {
}

package com.cloudforge.core.manager.auth;

/**
 * Request to create a new account via {@link AuthBackend#createAccount}.
 *
 * @param username required by both backends
 * @param email optional
 * @param role one of {@code admin}/{@code manager}/{@code viewer}
 * @param initialPassword required by {@code LocalH2AuthBackend} (local accounts have no other way
 *     to get a password); ignored by {@code CognitoAuthBackend}, which always generates and
 *     returns its own temporary password on {@link AuthAccount#temporaryPassword()} regardless of
 *     what's passed here
 * @param suppressInvite Cognito-only — {@code true} for a migration's bulk-created accounts (the
 *     temporary password is relayed to the admin instead), {@code false} for a single ad-hoc "Add
 *     User" from the panel (let Cognito email its own invite). Ignored by {@code LocalH2AuthBackend}.
 * @param linkedAccountId optional cross-backend link to record at creation time — see {@link
 *     AuthAccount#linkedAccountId()}
 */
public record AuthAccountRequest(
    String username,
    String email,
    String role,
    String initialPassword,
    boolean suppressInvite,
    String linkedAccountId
) {
}

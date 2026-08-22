package com.cloudforge.core.manager.auth;

/**
 * Partial update for {@link AuthBackend#updateAccount} — every field is {@code null}/unset unless
 * the caller means to change it (no "clear this field" sentinel is needed; every field here is
 * meaningful when present and skipped when absent, matching the semantics of the {@code
 * Map<String,Object> body}-based updates this replaced).
 *
 * @param email {@code null} to leave unchanged
 * @param displayName local-only concept, ignored by {@code CognitoAuthBackend}; {@code null} to
 *     leave unchanged
 * @param role {@code null} to leave unchanged
 * @param enabled Cognito-only (account enable/disable); ignored by {@code LocalH2AuthBackend};
 *     {@code null} to leave unchanged
 * @param newPassword local-only password reset; ignored by {@code CognitoAuthBackend}; {@code
 *     null} to leave unchanged
 * @param linkedAccountId {@code null} to leave unchanged; non-null sets/replaces the cross-backend
 *     link — see {@link AuthAccount#linkedAccountId()}
 */
public record AuthAccountUpdate(
    String email,
    String displayName,
    String role,
    Boolean enabled,
    String newPassword,
    String linkedAccountId
) {
}

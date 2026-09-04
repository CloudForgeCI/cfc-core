package com.cloudforge.core.manager.agent;

/**
 * Response to a successful {@code POST /api/v1/settings/devices} — carries the plaintext {@code
 * deviceToken} exactly once. Every subsequent call this device makes presents that token (same
 * bearer-credential shape as a personal access token); the client is responsible for storing it in
 * whatever OS-level secret store is appropriate (macOS Keychain, Windows Credential Manager, the
 * Linux Secret Service API) — it is never returned again by any other endpoint.
 */
public record DeviceRegistrationResponse(
    String id,
    String deviceToken,
    String displayName,
    AgentPlatform platform,
    String createdAt
) {
}

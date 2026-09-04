package com.cloudforge.core.manager.agent;

/**
 * Body of {@code POST /api/v1/settings/devices} — the one request every CloudForge Agent client
 * (cloudforge-studio/desktop/terminal) sends to register itself. Requires an already-authenticated
 * browser-equivalent session (whatever login the paired Manager instance runs — local password or
 * Cognito), same as issuing a personal access token; this is what turns that one-time login into a
 * durable, independently-revocable device credential. See this module's manager-devices OpenAPI
 * spec for the wire contract every non-JVM client builds against — this record is
 * cloudforge-manager's own implementation of that same contract, not a second source of truth.
 *
 * @param displayName client-supplied, human-chosen ("Phillip's MacBook") — shown as-is in the
 *     admin Devices view, never validated against the actual device.
 * @param platform which client is registering.
 */
public record DeviceRegistrationRequest(String displayName, AgentPlatform platform) {
}

package com.cloudforge.core.manager.agent;

/**
 * One row of {@code GET /api/v1/settings/devices} — never carries the device token itself (see
 * {@link DeviceRegistrationResponse}'s javadoc: that value exists only once, at registration).
 * {@code revokedAt} is {@code null} for an active device; an admin revokes one by id, and the
 * device's next authenticated call is rejected regardless of whether the device is reachable to be
 * told so.
 */
public record RegisteredDeviceView(
    String id,
    String displayName,
    AgentPlatform platform,
    String createdAt,
    String lastSeenAt,
    String revokedAt
) {
}

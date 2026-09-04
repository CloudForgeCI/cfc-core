package com.cloudforge.core.manager.agent;

/**
 * Which CloudForge Agent client a registered device is — cloudforge-studio (macOS),
 * cloudforge-desktop (Windows), or cloudforge-terminal (Linux). Purely descriptive (shown in the
 * admin Devices view so "revoke this one" is unambiguous); nothing in cloudforge-manager branches
 * on this value today.
 */
public enum AgentPlatform {
    MACOS,
    WINDOWS,
    LINUX
}

package com.cloudforge.core.local;

/**
 * A host port already claimed on the local machine by an emulator ECS task or stack output.
 */
public record LocalHostPortOccupant(
        String stackName,
        int hostPort,
        String detail) {

    public LocalHostPortOccupant {
        if (hostPort <= 0 || hostPort > 65535) {
            throw new IllegalArgumentException("invalid hostPort: " + hostPort);
        }
    }
}

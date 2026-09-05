package com.cloudforge.core.local;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Blocks local emulator deploys when the required {@code localhost} port is already in use.
 */
public final class LocalHostPortConflictChecker {

    private LocalHostPortConflictChecker() {
    }

    public static void validate(
            DeploymentTarget target,
            URI endpoint,
            String deployingLocalStackName,
            ApplicationSpec spec,
            JsonNode template,
            List<PreflightViolation> violations) throws IOException {
        validateAgainstOccupants(
            target,
            deployingLocalStackName,
            spec,
            template,
            LocalEmulatorHostPortProbe.probe(endpoint, target),
            violations);
    }

    public static void validateAgainstOccupants(
            DeploymentTarget target,
            String deployingLocalStackName,
            ApplicationSpec spec,
            JsonNode template,
            List<LocalHostPortOccupant> occupants,
            List<PreflightViolation> violations) {
        int requiredPort = LocalHostPortPolicy.resolveRequiredHostPort(spec, template);
        if (requiredPort <= 0) {
            return;
        }

        // LocalStack ECS publishes each task on an allocated host port, even when
        // multiple task definitions expose the same container port. Unlike
        // MiniStack, a container port is therefore not an exclusive host-port
        // reservation and must not block an otherwise valid deployment.
        if (target == DeploymentTarget.LOCALSTACK) {
            return;
        }

        if (LocalHostPortPolicy.isPrivilegedHostPort(requiredPort)) {
            violations.add(new PreflightViolation(
                PreflightSeverity.WARN,
                "PRIVILEGED_HOST_PORT",
                "Application binds host port " + requiredPort
                    + " (<1024). Binding may require elevated privileges outside Docker.",
                "Prefer apps on ports >=1024 on MiniStack/LocalStack, or ensure Docker can bind the port."));
        }

        for (LocalHostPortOccupant occupant : occupants) {
            if (occupant.hostPort() != requiredPort) {
                continue;
            }
            if (sameStack(deployingLocalStackName, occupant.stackName())) {
                continue;
            }
            violations.add(new PreflightViolation(
                PreflightSeverity.BLOCK,
                "HOST_PORT_CONFLICT",
                "Host port " + requiredPort + " is already used by "
                    + occupant.stackName() + " (" + occupant.detail() + "). "
                    + "MiniStack maps one app per localhost port.",
                LocalHostPortPolicy.portConflictSuggestion(target)));
            return;
        }
    }

    private static boolean sameStack(String deployingLocalStackName, String occupantStackName) {
        if (deployingLocalStackName == null || deployingLocalStackName.isBlank()
                || occupantStackName == null || occupantStackName.isBlank()) {
            return false;
        }
        return deployingLocalStackName.equals(occupantStackName)
            || deployingLocalStackName.equalsIgnoreCase(occupantStackName);
    }
}

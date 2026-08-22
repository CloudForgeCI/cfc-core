package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DockerEmulatorSupportTest {

    @Test
    void isHttpHealthyReturnsFalseForUnreachableEndpoint() {
        assertFalse(DockerEmulatorSupport.isHttpHealthy(
            URI.create("http://127.0.0.1:1/unreachable-health")));
    }

    @Test
    void containerNamesWithPrefixOnlySelectsManagedEcsTasks() {
        assertEquals(List.of("ls-ecs-manager", "ls-ecs-worker"),
            DockerEmulatorSupport.containerNamesWithPrefix(List.of(
                "ls-ecs-manager", "cfc-localstack", "ls-ecs-worker", "cfc-emulator-edge"),
                "ls-ecs-"));
    }
}

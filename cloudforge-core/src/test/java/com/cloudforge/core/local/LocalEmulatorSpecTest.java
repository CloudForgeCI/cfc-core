package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalEmulatorSpecTest {

    @Test
    void ministackSpecUsesCanonicalDefaults() {
        LocalEmulatorSpec spec = LocalEmulatorSpec.ministack();

        assertEquals(DeploymentTarget.MINISTACK, spec.target());
        assertEquals(LocalEmulatorDefaults.MINISTACK_CONTAINER, spec.containerName());
        assertEquals(LocalEmulatorDefaults.LOCALSTACK_CONTAINER, spec.conflictingContainerName());
        assertEquals(
            "http://localhost:4566/_ministack/health",
            spec.healthEndpoint().toString());
    }

    @Test
    void localstackSpecUsesCanonicalDefaults() {
        LocalEmulatorSpec spec = LocalEmulatorSpec.localstack();

        assertEquals(DeploymentTarget.LOCALSTACK, spec.target());
        assertEquals(LocalEmulatorDefaults.LOCALSTACK_CONTAINER, spec.containerName());
        assertEquals(
            "http://localhost:4566/_localstack/health",
            spec.healthEndpoint().toString());
    }

    @Test
    void awsTargetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LocalEmulatorSpec.forTarget(DeploymentTarget.AWS));
    }
}

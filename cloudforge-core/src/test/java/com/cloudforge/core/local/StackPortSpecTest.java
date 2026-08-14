package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StackPortSpecTest {

    @Test
    void ministackSpecUsesCanonicalDefaults() {
        StackPortSpec spec = StackPortSpec.ministack();

        assertEquals(DeploymentTarget.MINISTACK, spec.target());
        assertEquals(LocalEmulatorDefaults.MINISTACK_STACKPORT_CONTAINER, spec.containerName());
        assertEquals(
            LocalEmulatorDefaults.LOCALSTACK_STACKPORT_CONTAINER,
            spec.conflictingContainerName());
        assertEquals(
            "http://" + LocalEmulatorDefaults.MINISTACK_CONTAINER + ":4566",
            spec.emulatorEndpointUrl());
        assertEquals("ministack", spec.endpointConfigName());
        assertEquals(
            "ministack=http://" + LocalEmulatorDefaults.MINISTACK_CONTAINER + ":4566",
            spec.stackPortEndpointsEnvValue());
        assertEquals("http://localhost:8888", spec.browserUrl().toString());
    }

    @Test
    void localstackSpecUsesCanonicalDefaults() {
        StackPortSpec spec = StackPortSpec.localstack();

        assertEquals(DeploymentTarget.LOCALSTACK, spec.target());
        assertEquals(LocalEmulatorDefaults.LOCALSTACK_STACKPORT_CONTAINER, spec.containerName());
        assertEquals(
            LocalEmulatorDefaults.MINISTACK_STACKPORT_CONTAINER,
            spec.conflictingContainerName());
        assertEquals(
            "http://" + LocalEmulatorDefaults.LOCALSTACK_CONTAINER + ":4566",
            spec.emulatorEndpointUrl());
        assertEquals("localstack", spec.endpointConfigName());
        assertEquals(
            "localstack=http://" + LocalEmulatorDefaults.LOCALSTACK_CONTAINER + ":4566",
            spec.stackPortEndpointsEnvValue());
    }

    @Test
    void awsTargetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> StackPortSpec.forTarget(DeploymentTarget.AWS));
    }
}

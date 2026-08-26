package com.cloudforge.core.manager;

import com.cloudforge.core.local.DeploymentTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ManagerEndpointSupportTest {

    @AfterEach
    void clear() {
        System.clearProperty(ManagerEnvKeys.LOCALSTACK_ENDPOINT);
        System.clearProperty(ManagerEnvKeys.AWS_ENDPOINT_URL);
        System.clearProperty(ManagerEnvKeys.MINISTACK_ENDPOINT);
        System.clearProperty(ManagerEnvKeys.AWS_DEFAULT_REGION);
    }

    @Test
    void defaultsToLocalhostGateway() {
        assertEquals("http://localhost:4566", ManagerEndpointSupport.resolveLocalStackEndpoint());
        assertEquals("http://localhost:4566", ManagerEndpointSupport.resolveMiniStackEndpoint());
    }

    @Test
    void prefersSystemPropertiesWhenEnvUnset() {
        System.setProperty(ManagerEnvKeys.LOCALSTACK_ENDPOINT, "http://127.0.0.1:4566");
        System.setProperty(ManagerEnvKeys.MINISTACK_ENDPOINT, "http://127.0.0.1:4567");
        assertEquals("http://127.0.0.1:4566", ManagerEndpointSupport.resolveLocalStackEndpoint());
        assertEquals("http://127.0.0.1:4567", ManagerEndpointSupport.resolveMiniStackEndpoint());
    }

    /** The actual regression this method exists to close: a real {@code target=aws} run must
     *  ignore {@code AWS_ENDPOINT_URL} entirely, even when it's set to something that looks
     *  exactly like a real local emulator. */
    @Test
    void awsTargetIgnoresLocalEmulatorEnvVarsEntirely() {
        System.setProperty(ManagerEnvKeys.LOCALSTACK_ENDPOINT, "http://127.0.0.1:4566");
        System.setProperty(ManagerEnvKeys.AWS_ENDPOINT_URL, "http://attacker-controlled:4566");
        assertNull(ManagerEndpointSupport.resolveLocalEmulatorEndpoint(DeploymentTarget.AWS));
    }

    /** An unresolved target fails closed the same way AWS does — see the method's own javadoc for
     *  why that's the safer default rather than falling through to the env-var chain. */
    @Test
    void nullTargetFailsClosedTheSameWayAwsDoes() {
        System.setProperty(ManagerEnvKeys.AWS_ENDPOINT_URL, "http://127.0.0.1:4566");
        assertNull(ManagerEndpointSupport.resolveLocalEmulatorEndpoint(null));
    }

    @Test
    void localstackTargetUsesTheSameFallbackChain() {
        System.setProperty(ManagerEnvKeys.AWS_ENDPOINT_URL, "http://127.0.0.1:4566");
        assertEquals("http://127.0.0.1:4566",
            ManagerEndpointSupport.resolveLocalEmulatorEndpoint(DeploymentTarget.LOCALSTACK));

        System.setProperty(ManagerEnvKeys.LOCALSTACK_ENDPOINT, "http://127.0.0.1:4567");
        assertEquals("http://127.0.0.1:4567",
            ManagerEndpointSupport.resolveLocalEmulatorEndpoint(DeploymentTarget.LOCALSTACK));
    }

    @Test
    void localstackTargetReturnsNullWhenNeitherEnvVarIsSet() {
        assertNull(ManagerEndpointSupport.resolveLocalEmulatorEndpoint(DeploymentTarget.LOCALSTACK));
        assertNull(ManagerEndpointSupport.resolveLocalEmulatorEndpoint(DeploymentTarget.MINISTACK));
    }

    /** With both emulators configured side by side, each target must resolve to its own dedicated
     *  endpoint — a {@code MINISTACK} call should never fall through to {@code LOCALSTACK_ENDPOINT}
     *  just because both happen to be set, and vice versa. */
    @Test
    void ministackAndLocalstackResolveToTheirOwnDistinctEndpointsWhenBothAreSet() {
        System.setProperty(ManagerEnvKeys.LOCALSTACK_ENDPOINT, "http://127.0.0.1:4566");
        System.setProperty(ManagerEnvKeys.MINISTACK_ENDPOINT, "http://127.0.0.1:4567");

        assertEquals("http://127.0.0.1:4566",
            ManagerEndpointSupport.resolveLocalEmulatorEndpoint(DeploymentTarget.LOCALSTACK));
        assertEquals("http://127.0.0.1:4567",
            ManagerEndpointSupport.resolveLocalEmulatorEndpoint(DeploymentTarget.MINISTACK));
    }

    /** {@code MINISTACK} must check {@code MINISTACK_ENDPOINT} first, but still fall back to the
     *  shared {@code AWS_ENDPOINT_URL} when no dedicated endpoint is configured — same fallback
     *  shape {@code LOCALSTACK} already has. */
    @Test
    void ministackFallsBackToSharedAwsEndpointUrlWhenNoDedicatedEndpointIsSet() {
        System.setProperty(ManagerEnvKeys.AWS_ENDPOINT_URL, "http://127.0.0.1:4566");
        assertEquals("http://127.0.0.1:4566",
            ManagerEndpointSupport.resolveLocalEmulatorEndpoint(DeploymentTarget.MINISTACK));

        System.setProperty(ManagerEnvKeys.MINISTACK_ENDPOINT, "http://127.0.0.1:4567");
        assertEquals("http://127.0.0.1:4567",
            ManagerEndpointSupport.resolveLocalEmulatorEndpoint(DeploymentTarget.MINISTACK));
    }
}

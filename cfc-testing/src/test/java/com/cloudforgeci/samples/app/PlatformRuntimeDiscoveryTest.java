package com.cloudforgeci.samples.app;

import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.local.PlatformRuntimeProviders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformRuntimeDiscoveryTest {

    @Test
    void discoversTargetOwnedPlatformCapabilitiesWithoutTestingImports() {
        var providers = PlatformRuntimeProviders.all();
        assertTrue(providers.containsKey(DeploymentTarget.MINISTACK));
        assertTrue(providers.containsKey(DeploymentTarget.LOCALSTACK));
    }
}

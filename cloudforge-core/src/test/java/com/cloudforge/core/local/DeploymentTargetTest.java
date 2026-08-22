package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeploymentTargetTest {

    @Test
    void blankOrNullDefaultsToAws() {
        assertEquals(DeploymentTarget.AWS, DeploymentTarget.fromConfigKey(null));
        assertEquals(DeploymentTarget.AWS, DeploymentTarget.fromConfigKey(""));
        assertEquals(DeploymentTarget.AWS, DeploymentTarget.fromConfigKey("   "));
    }

    @Test
    void parsesEachKnownTargetCaseInsensitively() {
        assertEquals(DeploymentTarget.AWS, DeploymentTarget.fromConfigKey("aws"));
        assertEquals(DeploymentTarget.AWS, DeploymentTarget.fromConfigKey("AWS"));
        assertEquals(DeploymentTarget.MINISTACK, DeploymentTarget.fromConfigKey("ministack"));
        assertEquals(DeploymentTarget.MINISTACK, DeploymentTarget.fromConfigKey("MiniStack"));
        assertEquals(DeploymentTarget.LOCALSTACK, DeploymentTarget.fromConfigKey("localstack"));
        assertEquals(DeploymentTarget.LOCALSTACK, DeploymentTarget.fromConfigKey("LocalStack"));
    }

    @Test
    void configKeyRoundTripsThroughFromConfigKeyForEveryValue() {
        for (DeploymentTarget target : DeploymentTarget.values()) {
            assertEquals(target, DeploymentTarget.fromConfigKey(target.configKey()));
        }
    }

    @Test
    void unknownValueThrowsRatherThanSilentlyCoercing() {
        assertThrows(IllegalArgumentException.class, () -> DeploymentTarget.fromConfigKey("gcp"));
    }
}

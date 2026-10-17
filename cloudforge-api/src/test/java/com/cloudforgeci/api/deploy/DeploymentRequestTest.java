package com.cloudforgeci.api.deploy;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.local.DeploymentTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentRequestTest {

    @Test
    void rejectsAwsDeployWithoutApplicationId() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "App";
        assertThrows(IllegalArgumentException.class, () -> new DeploymentRequest(
            config,
            DeploymentTarget.AWS,
            DeployMode.DEPLOY,
            Path.of("cdk.out/App.template.json"),
            Path.of("cdk.out"),
            DeployOptions.defaults()));
    }

    @Test
    void acceptsAwsDeployWithApplicationId() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "App";
        config.applicationId = "jenkins";
        assertDoesNotThrow(() -> new DeploymentRequest(
            config,
            DeploymentTarget.AWS,
            DeployMode.DEPLOY,
            Path.of("cdk.out/App.template.json"),
            Path.of("cdk.out"),
            DeployOptions.defaults()));
    }

    @Test
    void acceptsAwsVerifyWithoutApplicationId() {
        // VERIFY doesn't deploy/tag anything, so it doesn't need applicationId.
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "App";
        assertDoesNotThrow(() -> new DeploymentRequest(
            config, DeploymentTarget.AWS, DeployMode.VERIFY, null, Path.of("cdk.out"), DeployOptions.defaults()));
    }

    @Test
    void deployRequiresCanonicalTemplate() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "App";
        assertThrows(IllegalArgumentException.class, () -> new DeploymentRequest(
            config,
            DeploymentTarget.MINISTACK,
            DeployMode.DEPLOY,
            null,
            Path.of("cdk.out"),
            DeployOptions.defaults()));
    }

    private static DeploymentRequest miniStackDeploy(String subdomain) {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "App";
        config.applicationId = "jenkins";
        config.subdomain = subdomain;
        return new DeploymentRequest(
            config, DeploymentTarget.MINISTACK, DeployMode.DEPLOY,
            Path.of("cdk.out/App.template.json"), Path.of("cdk.out"), DeployOptions.defaults());
    }

    @Test
    void aMiniStackDeployWithoutASubdomainReplacesTheSameApplication() {
        assertTrue(CloudForgeDeployment.replacesSameApplication(miniStackDeploy(null)));
        assertTrue(CloudForgeDeployment.replacesSameApplication(miniStackDeploy(" ")));
    }

    @Test
    void aMiniStackDeployWithASubdomainAddsAnInstanceInsteadOfReplacing() {
        assertFalse(CloudForgeDeployment.replacesSameApplication(miniStackDeploy("jenkins2")));
    }
}

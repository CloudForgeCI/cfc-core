package com.cloudforgeci.localstack;

import com.cloudforge.core.local.LocalSameApplicationStackReplacer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStackDeployerLifecycleTest {

    @Test
    void replaceSameApplicationStacksSkipsBlankApplicationId() {
        try (LocalStackDeployer deployer = new LocalStackDeployer("http://127.0.0.1:1", "us-east-1")) {
            LocalSameApplicationStackReplacer.Result result =
                deployer.replaceSameApplicationStacks(" ", "keep-localstack", Path.of("deployment-contexts"));
            assertTrue(result.deletedStacks().isEmpty());
            assertTrue(result.warning().isEmpty());
        }
    }

    @Test
    void verifyDeploymentFailsWhenStackMissing() {
        try (LocalStackDeployer deployer = new LocalStackDeployer("http://127.0.0.1:1", "us-east-1")) {
            org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> deployer.verifyDeployment("missing-localstack"));
        }
    }
}

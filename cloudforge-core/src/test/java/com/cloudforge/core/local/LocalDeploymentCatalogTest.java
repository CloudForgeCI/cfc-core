package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDeploymentCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void stripsLocalSuffixForLookup() throws Exception {
        Path catalog = tempDir.resolve("deployment-contexts");
        Files.createDirectories(catalog);
        Files.writeString(
            catalog.resolve("Jenkins-Dev.json"),
            "{\"applicationId\":\"jenkins\"}");

        Optional<String> id = LocalDeploymentCatalog.applicationIdForCfnStack(
            catalog, "Jenkins-Dev-ministack", DeploymentTarget.MINISTACK);

        assertEquals(Optional.of("jenkins"), id);
    }

    @Test
    void logicalStackNameStripsSuffix() {
        assertEquals(
            "Jenkins-Dev",
            LocalDeploymentCatalog.logicalStackName("Jenkins-Dev-localstack", DeploymentTarget.LOCALSTACK));
    }

    @Test
    void returnsEmptyWhenCatalogMissing() {
        assertTrue(LocalDeploymentCatalog.applicationIdForCfnStack(
            tempDir.resolve("missing"), "X-ministack", DeploymentTarget.MINISTACK).isEmpty());
    }
}

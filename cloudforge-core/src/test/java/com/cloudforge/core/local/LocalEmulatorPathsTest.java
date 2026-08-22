package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalEmulatorPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void localStackVolumeDirUsesExistingRepoDirectory() throws Exception {
        Path volumes = tempDir.resolve(LocalEmulatorDefaults.LOCALSTACK_VOLUME_DIR_NAME);
        Files.createDirectories(volumes);

        assertEquals(volumes, LocalEmulatorPaths.localStackVolumeDir(tempDir));
    }

    @Test
    void localStackVolumeDirUsesPomMarkerWhenVolumesMissing() throws Exception {
        Path project = tempDir.resolve("sample-project");
        Files.createDirectories(project);
        Files.createFile(project.resolve("pom.xml"));

        assertEquals(
            project.resolve(LocalEmulatorDefaults.LOCALSTACK_VOLUME_DIR_NAME),
            LocalEmulatorPaths.localStackVolumeDir(project));
    }
}

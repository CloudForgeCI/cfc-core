package com.cloudforge.core.local;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves on-disk paths used by local emulator runtimes.
 */
public final class LocalEmulatorPaths {

    private LocalEmulatorPaths() {
    }

    /** Resolve persisted LocalStack data directory (matches former compose bind mount). */
    public static Path localStackVolumeDir() {
        return localStackVolumeDir(Path.of("").toAbsolutePath().normalize());
    }

    public static Path localStackVolumeDir(Path workingDirectory) {
        String configured = System.getenv(LocalEmulatorDefaults.LOCALSTACK_VOLUME_DIR_ENV);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        Path repoDefault = workingDirectory.resolve(LocalEmulatorDefaults.LOCALSTACK_VOLUME_DIR_NAME);
        if (Files.exists(repoDefault) || Files.exists(workingDirectory.resolve("pom.xml"))) {
            return repoDefault;
        }
        return Path.of(
            System.getProperty("user.home"),
            ".cloudforge",
            LocalEmulatorDefaults.LOCALSTACK_VOLUME_DIR_NAME);
    }

    public static String dockerSocketMount() {
        return dockerSocketHostPath() + ":/var/run/docker.sock";
    }

    /**
     * Host-side path of the Docker socket, honoring {@link LocalEmulatorDefaults#DOCKER_SOCKET_ENV}
     * for non-default setups (Colima, Rancher Desktop, etc). Just the source half of
     * {@link #dockerSocketMount()} — for callers building an ECS task's {@code Host.SourcePath}
     * directly (e.g. giving CloudForge Manager's own container the same Docker access this
     * process already grants LocalStack's own container) rather than a docker-CLI {@code -v} arg.
     */
    public static String dockerSocketHostPath() {
        return System.getenv().getOrDefault(
            LocalEmulatorDefaults.DOCKER_SOCKET_ENV,
            LocalEmulatorDefaults.DEFAULT_DOCKER_SOCKET);
    }

    /** Persisted StackPort endpoint config (endpoints.json) per emulator target. */
    public static Path stackPortVolumeDir(DeploymentTarget target) {
        return stackPortVolumeDir(Path.of("").toAbsolutePath().normalize(), target);
    }

    public static Path stackPortVolumeDir(Path workingDirectory, DeploymentTarget target) {
        String subdir = target == DeploymentTarget.MINISTACK ? "ministack" : "localstack";
        Path repoDefault = workingDirectory
            .resolve(LocalEmulatorDefaults.STACKPORT_VOLUME_DIR_NAME)
            .resolve(subdir);
        if (Files.exists(repoDefault.getParent()) || Files.exists(workingDirectory.resolve("pom.xml"))) {
            return repoDefault;
        }
        return Path.of(
            System.getProperty("user.home"),
            ".cloudforge",
            LocalEmulatorDefaults.STACKPORT_VOLUME_DIR_NAME,
            subdir);
    }

    /** Runtime nginx conf dir ({@code conf.d}, generated vhosts). */
    public static Path emulatorEdgeDir() {
        return emulatorEdgeDir(Path.of("").toAbsolutePath().normalize());
    }

    public static Path emulatorEdgeDir(Path workingDirectory) {
        Path repoDefault = workingDirectory.resolve(LocalEmulatorDefaults.EMULATOR_EDGE_VOLUME_DIR_NAME);
        if (Files.exists(repoDefault)
            || Files.exists(workingDirectory.resolve("pom.xml"))
            || Files.exists(workingDirectory.resolve("docker/emulator-edge"))) {
            return repoDefault;
        }
        Path parent = workingDirectory.getParent();
        if (parent != null) {
            Path parentDefault = parent.resolve(LocalEmulatorDefaults.EMULATOR_EDGE_VOLUME_DIR_NAME);
            if (Files.exists(parent.resolve("docker/emulator-edge"))
                || Files.exists(parent.resolve("pom.xml"))) {
                return parentDefault;
            }
        }
        return Path.of(
            System.getProperty("user.home"),
            ".cloudforge",
            LocalEmulatorDefaults.EMULATOR_EDGE_VOLUME_DIR_NAME);
    }

    /** Prefer repo {@code docker/emulator-edge/nginx.conf}, else classpath extract location. */
    public static Path emulatorEdgeNginxConf(Path workingDirectory) {
        Path[] candidates = {
            workingDirectory.resolve("docker/emulator-edge/nginx.conf"),
            workingDirectory.resolve("../docker/emulator-edge/nginx.conf").normalize(),
            emulatorEdgeDir(workingDirectory).resolve("nginx.conf")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return emulatorEdgeDir(workingDirectory).resolve("nginx.conf");
    }
}

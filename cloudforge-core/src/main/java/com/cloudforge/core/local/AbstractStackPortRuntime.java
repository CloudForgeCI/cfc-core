package com.cloudforge.core.local;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared Docker lifecycle for target-owned StackPort resource browsers.
 */
public abstract class AbstractStackPortRuntime implements StackPortRuntime {

    private final StackPortSpec spec;

    protected AbstractStackPortRuntime(StackPortSpec spec) {
        this.spec = spec;
    }

    protected StackPortSpec spec() {
        return spec;
    }

    @Override
    public DeploymentTarget target() {
        return spec.target();
    }

    @Override
    public String containerName() {
        return spec.containerName();
    }

    @Override
    public URI browserUrl() {
        return spec.browserUrl();
    }

    @Override
    public void start() throws IOException {
        stopConflicting();
        String network = resolveStackPortNetwork();
        Path dataDir = prepareDataDir(false);

        if (DockerEmulatorSupport.isContainerRunning(spec.containerName())) {
            ensureEmulatorNetworkConnected();
            if (isHealthy()) {
                System.out.println(spec.displayName() + " already running: " + browserUrl());
                return;
            }
            DockerEmulatorSupport.stopContainer(spec.containerName());
        }

        if (DockerEmulatorSupport.containerExists(spec.containerName())) {
            DockerEmulatorSupport.startExistingContainer(spec.containerName());
            ensureEmulatorNetworkConnected();
        } else {
            DockerEmulatorSupport.runDetached(dockerCreateArgs(network, dataDir));
            ensureEmulatorNetworkConnected();
        }

        DockerEmulatorSupport.waitForHealthy(browserUrl());
        printStarted(dataDir, network);
    }

    @Override
    public void stop() throws IOException {
        DockerEmulatorSupport.stopContainer(spec.containerName());
        System.out.println(spec.displayName() + " stopped");
    }

    @Override
    public void rebuild() throws IOException {
        stopConflicting();
        stop();
        DockerEmulatorSupport.removeContainer(spec.containerName());
        String network = resolveStackPortNetwork();
        Path dataDir = prepareDataDir(true);
        DockerEmulatorSupport.pullImage(spec.image());
        DockerEmulatorSupport.runDetached(dockerCreateArgs(network, dataDir));
        ensureEmulatorNetworkConnected();
        DockerEmulatorSupport.waitForHealthy(browserUrl());
        printStarted(dataDir, network);
        System.out.println(spec.displayName() + " rebuilt (endpoint config reset)");
    }

    @Override
    public boolean isRunning() throws IOException {
        return DockerEmulatorSupport.isContainerRunning(spec.containerName());
    }

    @Override
    public boolean isHealthy() {
        return DockerEmulatorSupport.isHttpHealthy(browserUrl());
    }

    protected List<String> dockerCreateArgs(String network, Path dataDir) throws IOException {
        String emulatorUrl = resolveAwsEndpointUrl();
        String endpointsEnv = resolveStackPortEndpointsEnv();
        List<String> args = new ArrayList<>();
        args.addAll(List.of(
            "run", "-d",
            "--name", spec.containerName(),
            "--network", network,
            "-p", spec.hostPort() + ":" + spec.containerPort(),
            "-e", "AWS_ENDPOINT_URL=" + emulatorUrl,
            "-e", LocalEmulatorDefaults.STACKPORT_ENDPOINTS_ENV + "=" + endpointsEnv,
            "-e", "AWS_ACCESS_KEY_ID=test",
            "-e", "AWS_SECRET_ACCESS_KEY=test",
            "-e", "AWS_REGION=" + System.getenv().getOrDefault("AWS_DEFAULT_REGION", "us-east-1"),
            "-e", "STACKPORT_ALLOW_WRITES=true",
            "-e", LocalEmulatorDefaults.STACKPORT_DATA_DIR_ENV + "=/stackport-data",
            "-v", dataDir.toAbsolutePath() + ":/stackport-data",
            spec.image()));
        return args;
    }

    protected String resolveAwsEndpointUrl() {
        String override = System.getenv(LocalEmulatorDefaults.STACKPORT_ENDPOINT_ENV);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return spec.emulatorEndpointUrl();
    }

    protected String resolveStackPortEndpointsEnv() {
        String override = System.getenv(LocalEmulatorDefaults.STACKPORT_ENDPOINTS_ENV);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return spec.stackPortEndpointsEnvValue();
    }

    private String resolveStackPortNetwork() throws IOException {
        String emulatorNetwork = DockerEmulatorSupport.primaryContainerNetwork(spec.emulatorContainerName());
        if (emulatorNetwork != null && !emulatorNetwork.isBlank()) {
            return emulatorNetwork;
        }
        DockerEmulatorSupport.ensureNetwork(LocalEmulatorDefaults.DOCKER_NETWORK);
        return LocalEmulatorDefaults.DOCKER_NETWORK;
    }

    private void ensureEmulatorNetworkConnected() throws IOException {
        String emulatorNetwork = DockerEmulatorSupport.primaryContainerNetwork(spec.emulatorContainerName());
        if (emulatorNetwork != null && !emulatorNetwork.isBlank()) {
            DockerEmulatorSupport.connectNetwork(spec.containerName(), emulatorNetwork);
        }
    }

    private Path prepareDataDir(boolean reset) throws IOException {
        Path dataDir = LocalEmulatorPaths.stackPortVolumeDir(spec.target());
        if (reset && Files.exists(dataDir)) {
            try (Stream<Path> walk = Files.walk(dataDir)) {
                walk.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(dataDir))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to reset StackPort data: " + path, e);
                        }
                    });
            }
        }
        Files.createDirectories(dataDir);
        return dataDir;
    }

    private void printStarted(Path dataDir, String network) {
        System.out.println(spec.displayName() + " started: " + browserUrl());
        System.out.println("  Docker network: " + network);
        System.out.println("  Host emulator gateway: http://localhost:"
            + LocalEmulatorDefaults.GATEWAY_PORT);
        System.out.println("  StackPort endpoint name: " + spec.endpointConfigName());
        System.out.println("  StackPort AWS endpoint: " + resolveAwsEndpointUrl());
        System.out.println("  StackPort config volume: " + dataDir.toAbsolutePath());
        System.out.println("  Note: In StackPort Settings, use the '" + spec.endpointConfigName()
            + "' endpoint — not http://localhost:" + LocalEmulatorDefaults.GATEWAY_PORT
            + " (localhost is for your Mac, not inside the StackPort container)");
    }

    private void stopConflicting() throws IOException {
        if (spec.conflictingContainerName() != null && !spec.conflictingContainerName().isBlank()) {
            DockerEmulatorSupport.stopContainer(spec.conflictingContainerName());
        }
    }
}

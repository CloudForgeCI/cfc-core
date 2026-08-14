package com.cloudforge.core.local;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shared Docker CLI helpers for local emulator containers.
 */
public final class DockerEmulatorSupport {

    /** @deprecated use {@link LocalEmulatorDefaults#DOCKER_NETWORK} */
    @Deprecated
    public static final String DEFAULT_NETWORK = LocalEmulatorDefaults.DOCKER_NETWORK;
    /** @deprecated use {@link LocalEmulatorDefaults#GATEWAY_PORT} */
    @Deprecated
    public static final int DEFAULT_GATEWAY_PORT = LocalEmulatorDefaults.GATEWAY_PORT;

    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration DEFAULT_HEALTH_TIMEOUT = Duration.ofMinutes(2);

    private DockerEmulatorSupport() {
    }

    public static void ensureNetwork(String networkName) throws IOException {
        try {
            run(List.of("docker", "network", "inspect", networkName), DEFAULT_COMMAND_TIMEOUT);
        } catch (IOException ignored) {
            run(List.of("docker", "network", "create", networkName), DEFAULT_COMMAND_TIMEOUT);
        }
    }

    /**
     * Returns the primary Docker network for a running container, preferring {@code cfc-network}
     * or any network whose name contains {@code cfc} (e.g. compose project networks).
     */
    public static String primaryContainerNetwork(String containerName) throws IOException {
        if (!containerExists(containerName)) {
            return null;
        }
        List<String> networks = capture(List.of(
            "docker", "inspect", "-f",
            "{{range $k,$v := .NetworkSettings.Networks}}{{$k}}\n{{end}}",
            containerName));
        if (networks.isEmpty()) {
            return null;
        }
        for (String network : networks) {
            if (LocalEmulatorDefaults.DOCKER_NETWORK.equals(network)) {
                return network;
            }
        }
        for (String network : networks) {
            if (network.contains("cfc")) {
                return network;
            }
        }
        return networks.get(0);
    }

    public static void connectNetwork(String containerName, String networkName) throws IOException {
        if (networkName == null || networkName.isBlank()) {
            return;
        }
        try {
            run(List.of("docker", "network", "connect", networkName, containerName), DEFAULT_COMMAND_TIMEOUT);
        } catch (IOException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("already exists") || message.contains("already connected")) {
                return;
            }
            throw e;
        }
    }

    public static boolean containerExists(String containerName) throws IOException {
        List<String> output = capture(List.of(
            "docker", "ps", "-a", "--filter", "name=^" + containerName + "$",
            "--format", "{{.Names}}"));
        return output.stream().anyMatch(line -> containerName.equals(line.trim()));
    }

    public static boolean isContainerRunning(String containerName) throws IOException {
        if (!containerExists(containerName)) {
            return false;
        }
        List<String> output = capture(List.of(
            "docker", "inspect", "-f", "{{.State.Running}}", containerName));
        return !output.isEmpty() && "true".equalsIgnoreCase(output.get(0).trim());
    }

    public static void stopContainer(String containerName) throws IOException {
        if (!isContainerRunning(containerName)) {
            return;
        }
        run(List.of("docker", "stop", containerName), DEFAULT_COMMAND_TIMEOUT);
    }

    public static void startExistingContainer(String containerName) throws IOException {
        run(List.of("docker", "start", containerName), DEFAULT_COMMAND_TIMEOUT);
    }

    public static void removeContainer(String containerName) throws IOException {
        if (!containerExists(containerName)) {
            return;
        }
        stopContainer(containerName);
        run(List.of("docker", "rm", containerName), DEFAULT_COMMAND_TIMEOUT);
    }

    /**
     * Removes emulator-owned workload containers left behind when their LocalStack
     * control plane has been stopped. Names are filtered in-process rather than
     * passed as a Docker wildcard so unrelated containers are never targeted.
     *
     * @return the removed container names
     */
    public static List<String> removeContainersWithPrefix(String prefix) throws IOException {
        List<String> names = containerNamesWithPrefix(capture(List.of(
            "docker", "ps", "-a", "--format", "{{.Names}}")), prefix);
        for (String name : names) {
            removeContainer(name);
        }
        return names;
    }

    static List<String> containerNamesWithPrefix(List<String> names, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return names.stream()
            .map(String::trim)
            .filter(name -> name.startsWith(prefix))
            .toList();
    }

    public static void pullImage(String image) throws IOException {
        run(List.of("docker", "pull", image), Duration.ofMinutes(10));
    }

    public static void runDetached(List<String> dockerRunCommand) throws IOException {
        if (dockerRunCommand.isEmpty() || !"run".equals(dockerRunCommand.get(0))) {
            throw new IllegalArgumentException("Expected docker run command arguments");
        }
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(dockerRunCommand);
        run(command, DEFAULT_COMMAND_TIMEOUT);
    }

    public static void waitForHealthy(URI healthEndpoint) throws IOException {
        waitForHealthy(healthEndpoint, DEFAULT_HEALTH_TIMEOUT);
    }

    public static void waitForHealthy(URI healthEndpoint, Duration timeout) throws IOException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (isHttpHealthy(healthEndpoint)) {
                return;
            }
            sleep(Duration.ofMillis(500));
        }
        throw new IOException("Timed out waiting for healthy emulator at " + healthEndpoint);
    }

    public static boolean isHttpHealthy(URI uri) {
        try {
            HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void run(List<String> command, Duration timeout) throws IOException {
        Process process = startOrExplain(command);
        String output;
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Command timed out: " + String.join(" ", command));
            }
            output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0) {
                throw new IOException(
                    "Command failed (" + process.exitValue() + "): "
                        + String.join(" ", command)
                        + (output.isBlank() ? "" : "\n" + output.trim()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while running: " + String.join(" ", command), e);
        }
    }

    public static List<String> capture(List<String> command) throws IOException {
        Process process = startOrExplain(command);
        try {
            if (!process.waitFor(DEFAULT_COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Command timed out: " + String.join(" ", command));
            }
            String output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0) {
                return List.of();
            }
            return output.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while running: " + String.join(" ", command), e);
        }
    }

    /**
     * {@link ProcessBuilder#start()} throws a raw {@code "Cannot run program \"docker\": ... No
     * such file or directory"} {@link IOException} when the {@code docker} binary itself isn't on
     * {@code PATH} — exactly what happens when this runs inside CloudForge Manager's own runtime
     * container (built from a bare {@code eclipse-temurin} JRE image with no Docker CLI installed
     * and no access to the host's Docker socket), as opposed to the CLI (cfc-testing), which runs
     * directly on the developer's host where Docker is present. Re-thrown as-is that error reads
     * like an internal bug; wrapping it here gives every caller (companion Postgres/edge/emulator
     * containers, host-port probing, etc.) the same actionable message instead of duplicating this
     * check at each call site.
     */
    private static Process startOrExplain(List<String> command) throws IOException {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("No such file or directory") || message.contains("Cannot run program")) {
                throw new IOException(
                    "Docker CLI is not available in this runtime (needed to run: "
                        + String.join(" ", command) + "). This operation manages a local Docker "
                        + "container for the LocalStack/MiniStack emulator and only works where "
                        + "the Docker CLI is on PATH with access to the Docker daemon — e.g. the "
                        + "cfc-testing CLI running directly on your host. It cannot run from inside "
                        + "CloudForge Manager's own container, which has neither.", e);
            }
            throw e;
        }
    }

    private static void sleep(Duration duration) throws IOException {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting", e);
        }
    }
}

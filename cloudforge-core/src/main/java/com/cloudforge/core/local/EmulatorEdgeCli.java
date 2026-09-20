package com.cloudforge.core.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Command-line entry point for {@link EmulatorEdgeLifecycle} — backs {@code scripts/emulator-edge-*.sh}.
 *
 * <p>Invoked via {@code mvn -q -pl cloudforge-core org.codehaus.mojo:exec-maven-plugin:3.5.0:java
 * -Dexec.mainClass=com.cloudforge.core.local.EmulatorEdgeCli -Dexec.args=&lt;goal&gt;}. Fully
 * qualified plugin coordinates are used because no {@code cloudforge:} Maven plugin prefix is
 * registered.
 */
public final class EmulatorEdgeCli {

    private EmulatorEdgeCli() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: EmulatorEdgeCli <" + allowedActions() + ">");
            System.exit(2);
            return;
        }
        EmulatorEdgeLifecycleAction action;
        try {
            action = EmulatorEdgeLifecycleAction.valueOf(args[0].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown action '" + args[0] + "' — expected one of: " + allowedActions());
            System.exit(2);
            return;
        }
        try {
            EmulatorEdgeLifecycle.execute(new DefaultEmulatorEdgeRuntime(resolveWorkingDirectory()), action);
        } catch (Exception e) {
            System.err.println("emulator-edge " + args[0] + " failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String allowedActions() {
        return Arrays.stream(EmulatorEdgeLifecycleAction.values())
            .map(a -> a.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.joining("|"));
    }

    /**
     * {@link LocalEmulatorPaths#emulatorEdgeDir} resolves relative to the JVM's working
     * directory, assuming callers run from {@code cfc-testing/} (as InteractiveDeployer and the
     * deploy pipelines do). {@code scripts/emulator-edge-*.sh} runs from the repo root instead,
     * which also contains a {@code pom.xml}, so that heuristic would resolve to a root-level
     * {@code .emulator-edge} directory rather than {@code cfc-testing/.emulator-edge}, the path
     * bind-mounted into the nginx container; reconcile would then write a file nginx never reads.
     * This prefers a {@code cfc-testing} child directory when one exists (with the same
     * {@code docker/emulator-edge} marker {@link LocalEmulatorPaths} checks for).
     */
    private static Path resolveWorkingDirectory() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path cfcTesting = cwd.resolve("cfc-testing");
        if (Files.exists(cfcTesting.resolve("docker/emulator-edge"))) {
            return cfcTesting;
        }
        return cwd;
    }
}

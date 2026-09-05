package com.cloudforge.core.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Command-line entry point for {@link EmulatorEdgeLifecycle} — backs {@code scripts/emulator-edge-*.sh}.
 *
 * <p>{@code EmulatorEdgeLifecycle}'s own javadoc calls it "the preferred entry for Maven goals and
 * programmatic callers", but no Maven plugin ever actually registered a {@code cloudforge:} prefix
 * for it — {@code mvn -f cfc-testing cloudforge:emulator-edge-reconcile} (referenced in the nginx
 * edge's own banner text and every {@code scripts/emulator-edge-*.sh} wrapper) has always 404'd
 * with "No plugin found for prefix 'cloudforge'". This class is the actual, working entry point:
 * invoked via {@code mvn -q -pl cloudforge-core org.codehaus.mojo:exec-maven-plugin:3.5.0:java
 * -Dexec.mainClass=com.cloudforge.core.local.EmulatorEdgeCli -Dexec.args=&lt;goal&gt;} (fully-qualified
 * plugin coordinates — sidesteps the same prefix-resolution problem entirely rather than trying to
 * register one).
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
     * {@link LocalEmulatorPaths#emulatorEdgeDir} resolves relative to the JVM's ambient working
     * directory, on the assumption every caller runs from {@code cfc-testing/} (true of every
     * existing caller — InteractiveDeployer, the deploy pipelines — since that's their own natural
     * cwd). {@code scripts/emulator-edge-*.sh} instead runs `mvn -pl cloudforge-core exec:java`
     * from the repo root — which also happens to contain its own {@code pom.xml}, so that shared
     * heuristic would silently resolve to a `.emulator-edge` directory AT the repo root instead
     * of `cfc-testing/.emulator-edge` — nowhere near the path actually bind-mounted into the
     * running nginx container, so reconcile would compute and print the correct answer but write
     * it to a file nginx never reads, with nothing observable ever changing. Explicitly prefer a
     * `cfc-testing` child directory when one exists (with the same {@code docker/emulator-edge}
     * marker {@link LocalEmulatorPaths} itself checks for) rather than trusting ambient cwd.
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

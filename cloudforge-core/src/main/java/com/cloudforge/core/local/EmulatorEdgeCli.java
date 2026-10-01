package com.cloudforge.core.local;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Command-line entry point for {@link EmulatorEdgeLifecycle} — backs the
 * {@code scripts/emulator-edge-*.sh} wrappers in cloudforge-localstack and cloudforge-ministack
 * (this class itself lives here, in cloudforge-core, which both repos depend on as a resolved
 * artifact; the wrapper scripts do not live in cfc-core).
 *
 * <p>Invoked via {@code mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java
 * -Dexec.mainClass=com.cloudforge.core.local.EmulatorEdgeCli -Dexec.args=&lt;goal&gt;} from each
 * repo's own root — {@code cloudforge-core} resolves from the normal parent/dependency classpath
 * there, no {@code -pl} needed. Fully qualified plugin coordinates are used because no
 * {@code cloudforge:} Maven plugin prefix is registered.
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
     * directory — {@code scripts/emulator-edge-*.sh} runs from the repo root, which is also where
     * {@code docker/emulator-edge} (the path bind-mounted into the nginx container) lives, so no
     * redirection is needed here; {@link LocalEmulatorPaths} itself falls back to the parent
     * directory if the marker isn't found in the working directory.
     */
    private static Path resolveWorkingDirectory() {
        return Path.of("").toAbsolutePath().normalize();
    }
}

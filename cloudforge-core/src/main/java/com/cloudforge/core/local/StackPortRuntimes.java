package com.cloudforge.core.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers {@link StackPortRuntime} implementations registered by target modules.
 */
public final class StackPortRuntimes {

    private static final Map<DeploymentTarget, StackPortRuntime> BY_TARGET = load();

    private StackPortRuntimes() {
    }

    public static StackPortRuntime forTarget(DeploymentTarget target) {
        StackPortRuntime runtime = BY_TARGET.get(target);
        if (runtime == null) {
            throw new IllegalStateException(
                "No StackPortRuntime registered for " + target
                    + ". Add cloudforge-ministack or cloudforge-localstack to the classpath.");
        }
        return runtime;
    }

    public static StackPortRuntime forConfigKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            throw new IllegalArgumentException("configKey is required");
        }
        return forTarget(DeploymentTarget.valueOf(configKey.trim().toUpperCase(Locale.ROOT)));
    }

    public static List<StackPortRuntime> all() {
        return List.copyOf(BY_TARGET.values());
    }

    private static Map<DeploymentTarget, StackPortRuntime> load() {
        Map<DeploymentTarget, StackPortRuntime> runtimes = new EnumMap<>(DeploymentTarget.class);
        List<String> duplicates = new ArrayList<>();
        ServiceLoader.load(StackPortRuntimeProvider.class).forEach(provider -> {
            StackPortRuntime runtime = provider.runtime();
            DeploymentTarget target = runtime.target();
            if (target == DeploymentTarget.AWS) {
                throw new IllegalStateException(
                    "StackPortRuntimeProvider returned AWS target: "
                        + provider.getClass().getName());
            }
            if (runtimes.containsKey(target)) {
                duplicates.add(target.name());
                return;
            }
            runtimes.put(target, runtime);
        });
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException(
                "Duplicate StackPortRuntime registrations for: " + duplicates);
        }
        return Collections.unmodifiableMap(runtimes);
    }
}

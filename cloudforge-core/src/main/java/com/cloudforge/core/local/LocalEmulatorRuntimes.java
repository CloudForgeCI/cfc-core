package com.cloudforge.core.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers {@link LocalEmulatorRuntime} implementations registered by target modules.
 */
public final class LocalEmulatorRuntimes {

    private static final Map<DeploymentTarget, LocalEmulatorRuntime> BY_TARGET = load();

    private LocalEmulatorRuntimes() {
    }

    public static LocalEmulatorRuntime forTarget(DeploymentTarget target) {
        LocalEmulatorRuntime runtime = BY_TARGET.get(target);
        if (runtime == null) {
            throw new IllegalStateException(
                "No LocalEmulatorRuntime registered for " + target
                    + ". Add cloudforge-ministack or cloudforge-localstack to the classpath.");
        }
        return runtime;
    }

    public static LocalEmulatorRuntime forConfigKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            throw new IllegalArgumentException("configKey is required");
        }
        return forTarget(DeploymentTarget.valueOf(configKey.trim().toUpperCase(Locale.ROOT)));
    }

    public static List<LocalEmulatorRuntime> all() {
        return List.copyOf(BY_TARGET.values());
    }

    private static Map<DeploymentTarget, LocalEmulatorRuntime> load() {
        Map<DeploymentTarget, LocalEmulatorRuntime> runtimes = new EnumMap<>(DeploymentTarget.class);
        List<String> duplicates = new ArrayList<>();
        ServiceLoader.load(LocalEmulatorRuntimeProvider.class).forEach(provider -> {
            LocalEmulatorRuntime runtime = provider.runtime();
            DeploymentTarget target = runtime.target();
            if (target == DeploymentTarget.AWS) {
                throw new IllegalStateException(
                    "LocalEmulatorRuntimeProvider returned AWS target: "
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
                "Duplicate LocalEmulatorRuntime registrations for: " + duplicates);
        }
        return Collections.unmodifiableMap(runtimes);
    }
}

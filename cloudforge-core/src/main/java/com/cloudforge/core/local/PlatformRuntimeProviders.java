package com.cloudforge.core.local;

import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

/** Classpath registry for target-owned {@link PlatformRuntimeProvider} implementations. */
public final class PlatformRuntimeProviders {

    private PlatformRuntimeProviders() {
    }

    public static Map<DeploymentTarget, PlatformRuntimeProvider> all() {
        Map<DeploymentTarget, PlatformRuntimeProvider> providers = new EnumMap<>(DeploymentTarget.class);
        ServiceLoader.load(PlatformRuntimeProvider.class).forEach(provider -> {
            PlatformRuntimeProvider existing = providers.putIfAbsent(provider.target(), provider);
            if (existing != null) {
                throw new IllegalStateException("Duplicate PlatformRuntimeProvider for " + provider.target());
            }
        });
        return Map.copyOf(providers);
    }

    public static PlatformRuntimeProvider forTarget(DeploymentTarget target) {
        PlatformRuntimeProvider provider = all().get(target);
        if (provider == null) {
            throw new IllegalStateException("No PlatformRuntimeProvider registered for " + target);
        }
        return provider;
    }
}

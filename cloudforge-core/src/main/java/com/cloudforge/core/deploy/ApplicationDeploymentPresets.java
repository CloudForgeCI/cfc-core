package com.cloudforge.core.deploy;

import java.util.Optional;
import java.util.ServiceLoader;

/** Classpath discovery for optional {@link ApplicationDeploymentPreset} providers. */
public final class ApplicationDeploymentPresets {

    private ApplicationDeploymentPresets() {
    }

    public static Optional<ApplicationDeploymentPreset> find(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            return Optional.empty();
        }
        return ServiceLoader.load(ApplicationDeploymentPreset.class).stream()
            .map(ServiceLoader.Provider::get)
            .filter(preset -> applicationId.equals(preset.applicationId()))
            .findFirst();
    }
}

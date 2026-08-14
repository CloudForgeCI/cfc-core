package com.cloudforge.core.deploy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/** Classpath-wide registry for optional application deployment extensions. */
public final class ApplicationDeploymentExtensions {

    private ApplicationDeploymentExtensions() {
    }

    public static Optional<ApplicationDeploymentExtension> find(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(all().get(applicationId));
    }

    public static Map<String, ApplicationDeploymentExtension> all() {
        Map<String, ApplicationDeploymentExtension> extensions = new LinkedHashMap<>();
        ServiceLoader.load(ApplicationDeploymentExtension.class).forEach(extension ->
            extensions.putIfAbsent(extension.applicationId(), extension));
        return Map.copyOf(extensions);
    }
}

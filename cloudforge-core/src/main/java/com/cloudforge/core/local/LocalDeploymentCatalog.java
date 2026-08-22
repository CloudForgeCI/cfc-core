package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves {@code applicationId} from per-stack catalog files under {@code deployment-contexts/}.
 */
public final class LocalDeploymentCatalog {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LocalDeploymentCatalog() {
    }

    /**
     * Logical stack name from a CFN stack name (strips {@code -ministack} / {@code -localstack}).
     */
    public static String logicalStackName(String cfnStackName, DeploymentTarget target) {
        if (cfnStackName == null || cfnStackName.isBlank()) {
            return cfnStackName;
        }
        String suffix = switch (target) {
            case MINISTACK -> "-ministack";
            case LOCALSTACK -> "-localstack";
            case AWS -> "";
        };
        if (!suffix.isEmpty() && cfnStackName.endsWith(suffix)) {
            return cfnStackName.substring(0, cfnStackName.length() - suffix.length());
        }
        return cfnStackName;
    }

    /**
     * Read {@code applicationId} from {@code catalogDirectory/{logicalStack}.json} when present.
     */
    public static Optional<String> applicationIdForCfnStack(
            Path catalogDirectory,
            String cfnStackName,
            DeploymentTarget target) {
        if (catalogDirectory == null || cfnStackName == null || cfnStackName.isBlank()) {
            return Optional.empty();
        }
        if (!Files.isDirectory(catalogDirectory)) {
            return Optional.empty();
        }
        String logical = logicalStackName(cfnStackName, target);
        Path file = catalogDirectory.resolve(logical + ".json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.readValue(file.toFile(), Map.class);
            Object id = map.get("applicationId");
            return id == null || id.toString().isBlank()
                ? Optional.empty()
                : Optional.of(id.toString().trim());
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}

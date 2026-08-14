package com.cloudforgeci.api.core;

import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.rules.FrameworkLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


class Util {
    private static final Logger LOG = Logger.getLogger(Util.class.getName());

    private static ObjectMapper getMapper() {
        return new ObjectMapper();
    }

    /**
     * Creates a DeploymentContext from the 'cfc' context object.
     *
     * <p>This is the entry point for context creation and framework config merging.
     * (Renamed from {@code extractDeploymentContext} for clarity.)</p>
     *
     * @param cfc the raw context object (Map, JSON string, or POJO)
     * @return a fully constructed DeploymentContext with merged framework config
     */
    public static DeploymentContext createDeploymentContext(Object cfc) {
        Map<String, Object> map = convertToContext(cfc);

        // Merge framework-required configuration before constructing DeploymentContext
        map = mergeFrameworkConfiguration(map);

        return new DeploymentContext(map);
    }

    /**
     * @deprecated Use {@link #createDeploymentContext(Object)} instead.
     */
    @Deprecated
    public static DeploymentContext extractDeploymentContext(Object cfc) {
        return createDeploymentContext(cfc);
    }

    /**
     * Merge compliance framework configuration requirements into the deployment context.
     *
     * <p><b>Precedence order:</b></p>
     * <ol>
     *   <li>User-provided explicit configuration (cdk.json)</li>
     *   <li>Framework-required configuration (from getRequiredConfiguration())</li>
     *   <li>Security profile defaults (applied later in SecurityProfileConfiguration)</li>
     * </ol>
     *
     * @param userConfig user-provided configuration map
     * @return merged configuration map with framework requirements
     */
    private static Map<String, Object> mergeFrameworkConfiguration(Map<String, Object> userConfig) {
        // Get enabled frameworks from user config
        String frameworksStr = (String) userConfig.get("complianceFrameworks");
        if (frameworksStr == null || frameworksStr.trim().isEmpty()) {
            return userConfig; // No frameworks enabled, return as-is
        }

        // Parse enabled framework IDs
        String[] frameworkIds = frameworksStr.split(",");

        // Discover all available frameworks
        List<FrameworkRules<SystemContext>> allFrameworks = FrameworkLoader.discover();

        // Collect configuration from enabled frameworks
        Map<String, Object> frameworkConfig = new HashMap<>();
        for (FrameworkRules<SystemContext> framework : allFrameworks) {
            String frameworkId = framework.frameworkId();

            // Check if this framework is enabled (case-insensitive match)
            boolean isEnabled = false;
            for (String enabledId : frameworkIds) {
                if (enabledId.trim().equalsIgnoreCase(frameworkId)) {
                    isEnabled = true;
                    break;
                }
            }

            if (isEnabled) {
                Map<String, Object> required = framework.getRequiredConfiguration();
                if (required != null && !required.isEmpty()) {
                    LOG.info("Applying configuration from " + framework.displayName() +
                            " (" + frameworkId + "): " + required);

                    // Merge framework config (later frameworks override earlier ones if conflicts)
                    frameworkConfig.putAll(required);
                }
            }
        }

        // Merge: user config takes precedence over framework config
        Map<String, Object> merged = new HashMap<>(frameworkConfig);
        merged.putAll(userConfig); // User values override framework values

        // Log what was applied
        if (!frameworkConfig.isEmpty()) {
            frameworkConfig.forEach((key, frameworkValue) -> {
                Object userValue = userConfig.get(key);
                if (userValue != null && !userValue.equals(frameworkValue)) {
                    LOG.info("  " + key + ": Framework default (" + frameworkValue +
                            ") overridden by user config (" + userValue + ")");
                } else if (userValue == null) {
                    LOG.info("  " + key + ": Applying framework default (" + frameworkValue + ")");
                }
            });
        }

        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertToContext(Object obj) {
        if (obj == null) return java.util.Collections.emptyMap();

        if (obj instanceof Map<?, ?> m) {
            Map<String, Object> out = new java.util.HashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }

        if (obj instanceof String s) {
            String json = s.trim();
            if (json.isEmpty()) return java.util.Collections.emptyMap();
            try {
                return getMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse context JSON: " + json, e);
            }
        }

        return getMapper().convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }
}

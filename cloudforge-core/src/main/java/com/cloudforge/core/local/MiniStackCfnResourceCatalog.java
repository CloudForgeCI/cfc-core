package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps CloudFormation resource types to MiniStack support policy.
 */
public final class MiniStackCfnResourceCatalog {

    private MiniStackCfnResourceCatalog() {
    }

    public static MiniStackResourcePolicy policyFor(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return MiniStackResourcePolicy.UNSUPPORTED;
        }
        if (resourceType.startsWith("AWS::RDS::")) {
            return MiniStackResourcePolicy.UNSUPPORTED;
        }
        if (resourceType.startsWith("AWS::WAFv2::")
                || resourceType.startsWith("AWS::WAF::")) {
            return MiniStackResourcePolicy.UNSUPPORTED;
        }
        if (resourceType.startsWith("AWS::Config::")) {
            return MiniStackResourcePolicy.UNSUPPORTED;
        }
        if (resourceType.startsWith("AWS::CloudTrail::")) {
            return MiniStackResourcePolicy.UNSUPPORTED;
        }
        if (resourceType.startsWith("AWS::Backup::")) {
            return MiniStackResourcePolicy.UNSUPPORTED;
        }
        if (resourceType.startsWith("AWS::GuardDuty::")) {
            return MiniStackResourcePolicy.UNSUPPORTED;
        }
        if (resourceType.startsWith("AWS::EFS::")) {
            return MiniStackResourcePolicy.ADAPTED;
        }
        if (resourceType.startsWith("AWS::ApplicationAutoScaling::")) {
            return MiniStackResourcePolicy.ADAPTED;
        }
        if ("AWS::EC2::SecurityGroupIngress".equals(resourceType)) {
            return MiniStackResourcePolicy.ADAPTED;
        }
        return MiniStackResourcePolicy.SUPPORTED;
    }

    public static List<TemplateResourceRef> unsupportedResources(JsonNode template) {
        List<TemplateResourceRef> unsupported = new ArrayList<>();
        JsonNode resources = template == null ? null : template.path("Resources");
        if (resources == null || !resources.isObject()) {
            return unsupported;
        }
        resources.properties().forEach(entry -> {
            String type = entry.getValue().path("Type").asText(null);
            if (policyFor(type) == MiniStackResourcePolicy.UNSUPPORTED) {
                unsupported.add(new TemplateResourceRef(entry.getKey(), type));
            }
        });
        return unsupported;
    }

    public static Set<String> distinctTypes(JsonNode template) {
        Set<String> types = new LinkedHashSet<>();
        JsonNode resources = template == null ? null : template.path("Resources");
        if (resources == null || !resources.isObject()) {
            return types;
        }
        resources.forEach(node -> {
            String type = node.path("Type").asText(null);
            if (type != null && !type.isBlank()) {
                types.add(type);
            }
        });
        return types;
    }

    public static boolean templateRequiresRds(JsonNode template) {
        return distinctTypes(template).stream()
            .anyMatch(type -> type.startsWith("AWS::RDS::"));
    }

    public record TemplateResourceRef(String logicalId, String type) {
        @Override
        public String toString() {
            return logicalId + " (" + type + ")";
        }
    }
}

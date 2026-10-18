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

    /** Compliance resource types the MiniStack adapter removes because MiniStack cannot create them. */
    public static final Set<String> REMOVED_COMPLIANCE_TYPES = Set.of(
        "AWS::CloudTrail::Trail",
        "AWS::Backup::BackupSelection",
        "AWS::GuardDuty::Detector",
        "AWS::Config::ConfigRule",
        "AWS::Config::RemediationConfiguration",
        "AWS::Config::ConformancePack",
        "AWS::WAFv2::WebACLAssociation",
        "AWS::WAFv2::LoggingConfiguration",
        "AWS::EC2::FlowLog");

    private MiniStackCfnResourceCatalog() {
    }

    public static MiniStackResourcePolicy policyFor(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return MiniStackResourcePolicy.UNSUPPORTED;
        }
        // A database instance is created as a real container through MiniStack's RDS API and the
        // template is rewritten to use it; its subnet and parameter groups are plain CloudFormation.
        if ("AWS::RDS::DBInstance".equals(resourceType)) {
            return MiniStackResourcePolicy.ADAPTED;
        }
        if ("AWS::RDS::DBSubnetGroup".equals(resourceType) || "AWS::RDS::DBParameterGroup".equals(resourceType)) {
            return MiniStackResourcePolicy.SUPPORTED;
        }
        if (resourceType.startsWith("AWS::RDS::")) {
            return MiniStackResourcePolicy.UNSUPPORTED;
        }
        // The Private CA chain is replaced by plain HTTP, Cognito users and branding are created or
        // dropped by the deployer, and the CDK log-retention custom resource is removed.
        if (resourceType.startsWith("AWS::ACMPCA::")
                || "AWS::Cognito::UserPoolUser".equals(resourceType)
                || "AWS::Cognito::UserPoolUserToGroupAttachment".equals(resourceType)
                || "AWS::Cognito::ManagedLoginBranding".equals(resourceType)
                || "Custom::LogRetention".equals(resourceType)) {
            return MiniStackResourcePolicy.ADAPTED;
        }
        // Compliance resources MiniStack cannot create. The adapter removes them, so the application deploys
        // without that control instead of being refused.
        if (REMOVED_COMPLIANCE_TYPES.contains(resourceType)) {
            return MiniStackResourcePolicy.ADAPTED;
        }
        // MiniStack creates a web ACL, a backup vault and a backup plan.
        if ("AWS::WAFv2::WebACL".equals(resourceType)
                || "AWS::Backup::BackupVault".equals(resourceType)
                || "AWS::Backup::BackupPlan".equals(resourceType)) {
            return MiniStackResourcePolicy.SUPPORTED;
        }
        if (resourceType.startsWith("AWS::WAFv2::")
                || resourceType.startsWith("AWS::WAF::")
                || resourceType.startsWith("AWS::Config::")
                || resourceType.startsWith("AWS::CloudTrail::")
                || resourceType.startsWith("AWS::Backup::")
                || resourceType.startsWith("AWS::GuardDuty::")) {
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

package com.cloudforgeci.api.integration.deployment;

import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.*;

/**
 * Resource Validation Matrix for systematic CloudFormation template validation.
 *
 * This class provides utilities to:
 * - Validate expected AWS resources are present
 * - Check resource counts match expectations
 * - Verify resource properties systematically
 * - Detect missing or unexpected resources
 *
 * Used by truth table validation tests to ensure synthesis matches expectations.
 */
public class ResourceValidationMatrix {

    private final Template template;
    private final List<String> validationErrors;

    public ResourceValidationMatrix(Template template) {
        this.template = template;
        this.validationErrors = new ArrayList<>();
    }

    /**
     * Validate that all expected resources are present in the template.
     *
     * @param expectedResources List of AWS resource types that should exist
     * @return List of missing resource types
     */
    public List<String> validateExpectedResources(List<String> expectedResources) {
        List<String> missingResources = new ArrayList<>();

        for (String resourceType : expectedResources) {
            try {
                // Try to find at least one resource of this type
                template.hasResourceProperties(resourceType, Match.objectLike(Collections.emptyMap()));
            } catch (AssertionError e) {
                // Resource type not found
                missingResources.add(resourceType);
            }
        }

        return missingResources;
    }

    /**
     * Validate specific resource counts.
     *
     * @param resourceType The AWS resource type (e.g., "AWS::EC2::VPC")
     * @param expectedCount Expected number of resources
     * @return true if count matches, false otherwise
     */
    public boolean validateResourceCount(String resourceType, int expectedCount) {
        try {
            template.resourceCountIs(resourceType, expectedCount);
            return true;
        } catch (AssertionError e) {
            validationErrors.add(
                "Resource count mismatch for " + resourceType +
                ": expected " + expectedCount + " (actual count unknown)"
            );
            return false;
        }
    }

    /**
     * Validate that specific resource properties exist.
     *
     * @param resourceType The AWS resource type
     * @param properties Map of property name to expected value
     * @return true if all properties match, false otherwise
     */
    public boolean validateResourceProperties(String resourceType, Map<String, Object> properties) {
        try {
            template.hasResourceProperties(resourceType, properties);
            return true;
        } catch (AssertionError e) {
            validationErrors.add(
                "Resource properties mismatch for " + resourceType +
                ": " + e.getMessage()
            );
            return false;
        }
    }

    /**
     * Validate VPC configuration based on network mode.
     *
     * @param networkMode "public-no-nat" or "private-with-nat"
     */
    public void validateVpcConfiguration(String networkMode) {
        // VPC should always exist
        validateResourceCount("AWS::EC2::VPC", 1);

        if ("private-with-nat".equals(networkMode)) {
            // Should have NAT gateways
            try {
                template.hasResourceProperties("AWS::EC2::NatGateway", Match.objectLike(Collections.emptyMap()));
            } catch (AssertionError e) {
                validationErrors.add("private-with-nat mode requires NAT gateway but none found");
            }
        }
    }

    /**
     * Validate SSL/TLS configuration.
     *
     * @param sslEnabled Whether SSL should be configured
     * @param hasDomain Whether a domain is configured
     */
    public void validateSslConfiguration(boolean sslEnabled, boolean hasDomain) {
        if (sslEnabled && hasDomain) {
            // Should have ACM certificate
            validateResourceCount("AWS::CertificateManager::Certificate", 1);

            // Should have HTTPS listener
            validateResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Map.of(
                "Protocol", "HTTPS",
                "Port", 443
            ));
        }
    }

    /**
     * Validate authentication configuration.
     *
     * @param authMode Authentication mode ("none" or "alb-oidc")
     */
    public void validateAuthConfiguration(String authMode) {
        if ("alb-oidc".equals(authMode)) {
            // Should have Cognito resources
            validateResourceCount("AWS::Cognito::UserPool", 1);
            validateResourceCount("AWS::Cognito::UserPoolClient", 1);
            validateResourceCount("AWS::Cognito::UserPoolDomain", 1);
        }
    }

    /**
     * Validate Application Load Balancer is present (APPLICATION_SERVICE topology).
     */
    public void validateAlbExists() {
        validateResourceCount("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        validateResourceCount("AWS::ElasticLoadBalancingV2::TargetGroup", 1);
    }

    /**
     * Validate runtime-specific resources.
     *
     * @param runtime "EC2" or "FARGATE"
     */
    public void validateRuntimeResources(String runtime) {
        if ("FARGATE".equals(runtime)) {
            // Should have ECS cluster and service
            validateResourceCount("AWS::ECS::Cluster", 1);
            validateResourceCount("AWS::ECS::Service", 1);

            // Should have task definition
            try {
                template.hasResourceProperties("AWS::ECS::TaskDefinition", Match.objectLike(Collections.emptyMap()));
            } catch (AssertionError e) {
                validationErrors.add("FARGATE runtime requires ECS TaskDefinition");
            }
        } else if ("EC2".equals(runtime)) {
            // Should have Auto Scaling Group for APPLICATION_SERVICE
            try {
                template.hasResourceProperties("AWS::AutoScaling::AutoScalingGroup", Match.objectLike(Collections.emptyMap()));
            } catch (AssertionError e) {
                validationErrors.add("EC2 runtime with APPLICATION_SERVICE requires Auto Scaling Group");
            }
        }
    }

    /**
     * Validate security group resources exist.
     */
    public void validateSecurityGroups() {
        try {
            template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Collections.emptyMap()));
        } catch (AssertionError e) {
            validationErrors.add("No security groups found in template");
        }
    }

    /**
     * Validate IAM roles and policies exist.
     */
    public void validateIamResources() {
        try {
            template.hasResourceProperties("AWS::IAM::Role", Match.objectLike(Collections.emptyMap()));
        } catch (AssertionError e) {
            validationErrors.add("No IAM roles found in template");
        }
    }

    /**
     * Get all validation errors accumulated during checks.
     *
     * @return List of validation error messages
     */
    public List<String> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }

    /**
     * Check if validation passed (no errors).
     *
     * @return true if no errors, false otherwise
     */
    public boolean isValid() {
        return validationErrors.isEmpty();
    }

    /**
     * Get formatted error report.
     *
     * @return Multi-line string with all validation errors
     */
    public String getErrorReport() {
        if (validationErrors.isEmpty()) {
            return "✅ All validations passed";
        }

        StringBuilder report = new StringBuilder();
        report.append("❌ Validation failed with ").append(validationErrors.size()).append(" error(s):\n");
        for (int i = 0; i < validationErrors.size(); i++) {
            report.append("  ").append(i + 1).append(". ").append(validationErrors.get(i)).append("\n");
        }
        return report.toString();
    }

    /**
     * Comprehensive validation for a deployment configuration.
     *
     * @param runtime Runtime type
     * @param securityProfile Security profile
     * @param domainConfig Domain configuration
     * @param sslConfig SSL configuration
     * @param authMode Authentication mode
     * @param networkMode Network mode
     * @return Validation matrix with results
     */
    public static ResourceValidationMatrix validateConfiguration(
            Template template,
            String runtime,
            String securityProfile,
            String domainConfig,
            String sslConfig,
            String authMode,
            String networkMode) {

        ResourceValidationMatrix matrix = new ResourceValidationMatrix(template);

        // Core infrastructure
        matrix.validateVpcConfiguration(networkMode);
        matrix.validateAlbExists();
        matrix.validateSecurityGroups();
        matrix.validateIamResources();

        // Runtime-specific
        matrix.validateRuntimeResources(runtime);

        // SSL/TLS
        boolean hasDomain = "with-domain".equals(domainConfig);
        boolean sslEnabled = "ssl-enabled".equals(sslConfig);
        matrix.validateSslConfiguration(sslEnabled, hasDomain);

        // Authentication
        matrix.validateAuthConfiguration(authMode);

        return matrix;
    }
}

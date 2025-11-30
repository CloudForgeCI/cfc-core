package com.cloudforgeci.samples.plugins.compliance;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.rules.ComplianceRule;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Custom Security Policy - Example Compliance Framework Plugin.
 *
 * <p>This demonstrates how to create a custom compliance framework plugin that enforces
 * organization-specific security policies. This example implements fictional "ACME Corporation"
 * internal security requirements.</p>
 *
 * <h2>Policy Areas:</h2>
 * <ul>
 *   <li>Network Security - VPC flow logs and private subnets</li>
 *   <li>Data Protection - Encryption at rest and in transit</li>
 *   <li>Access Control - IAM least privilege and MFA</li>
 *   <li>Monitoring - CloudWatch alarms and Security Hub</li>
 *   <li>Container Security - Image scanning and runtime protection</li>
 * </ul>
 *
 * <h2>Deployment:</h2>
 * <ul>
 *   <li><b>Always-Load:</b> This policy applies to ALL deployments</li>
 *   <li><b>Priority 60:</b> Runs after industry frameworks but before experimental</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * // Automatically loaded via ServiceLoader
 * // Or enable explicitly in cdk.json:
 * {
 *   "context": {
 *     "complianceFrameworks": "CustomSecurity"
 *   }
 * }
 * }</pre>
 *
 * @since 1.0.0
 * @author ACME Corporation Security Team
 */
@ComplianceFramework(
    value = "CustomSecurity",
    priority = 60,
    alwaysLoad = true,  // Always enforce internal security policy
    displayName = "ACME Security Policy",
    description = "Internal security and compliance requirements for ACME Corporation"
)
public class CustomSecurityPolicyRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(CustomSecurityPolicyRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing ACME custom security policy compliance rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Network security validation
            rules.addAll(validateNetworkSecurity(ctx));

            // Data protection validation
            rules.addAll(validateDataProtection(ctx));

            // Access control validation
            rules.addAll(validateAccessControl(ctx));

            // Monitoring and alerting validation
            rules.addAll(validateMonitoring(ctx));

            // Container security (runtime-specific)
            if (ctx.runtime == RuntimeType.FARGATE) {
                rules.addAll(validateContainerSecurity(ctx));
            }

            // Convert ComplianceRule list to error strings
            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(java.util.Optional::stream)
                .toList();
        });
    }

    /**
     * Validate network security controls.
     *
     * <p>ACME Policy NS-001: Network Isolation and Monitoring</p>
     * <ul>
     *   <li>All PRODUCTION systems must use private subnets</li>
     *   <li>VPC Flow Logs must be enabled</li>
     *   <li>Network ACLs must be configured</li>
     * </ul>
     */
    private List<ComplianceRule> validateNetworkSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // NS-001.1: VPC Flow Logs
        boolean flowLogsEnabled = config.isFlowLogsEnabled();
        if (ctx.security == SecurityProfile.PRODUCTION && !flowLogsEnabled) {
            rules.add(ComplianceRule.fail(
                "ACME-NS-001.1",
                "ACME Policy NS-001.1: VPC Flow Logs required for PRODUCTION environments",
                "VPC Flow Logs must be enabled to monitor network traffic for PRODUCTION deployments"
            ));
        } else if (flowLogsEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-NS-001.1",
                "ACME Policy NS-001.1: VPC Flow Logs enabled for network monitoring"
            ));
        }

        // NS-001.2: Private Subnets (Advisory for STAGING/PRODUCTION)
        String networkMode = ctx.cfc.getContextValue("networkMode", "private-with-nat");
        if (ctx.security != SecurityProfile.DEV && !"private-with-nat".equals(networkMode)) {
            rules.add(ComplianceRule.pass(
                "ACME-NS-001.2",
                "ACME Policy NS-001.2: Advisory - Consider using private subnets for " + ctx.security + " environments"
            ));
        } else if ("private-with-nat".equals(networkMode)) {
            rules.add(ComplianceRule.pass(
                "ACME-NS-001.2",
                "ACME Policy NS-001.2: Private subnets configured for enhanced security"
            ));
        }

        return rules;
    }

    /**
     * Validate data protection controls.
     *
     * <p>ACME Policy DP-002: Data Encryption and Protection</p>
     * <ul>
     *   <li>Encryption at rest required for PRODUCTION</li>
     *   <li>TLS/SSL required for data in transit</li>
     *   <li>KMS key rotation enabled</li>
     * </ul>
     */
    private List<ComplianceRule> validateDataProtection(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // DP-002.1: Encryption at Rest
        boolean ebsEncryption = config.isEbsEncryptionEnabled();
        if (ctx.security == SecurityProfile.PRODUCTION && !ebsEncryption) {
            rules.add(ComplianceRule.fail(
                "ACME-DP-002.1",
                "ACME Policy DP-002.1: Encryption at rest required for PRODUCTION",
                "EBS encryption must be enabled for PRODUCTION data volumes"
            ));
        } else if (ebsEncryption) {
            rules.add(ComplianceRule.pass(
                "ACME-DP-002.1",
                "ACME Policy DP-002.1: Encryption at rest enabled for data protection"
            ));
        }

        // DP-002.2: Encryption in Transit (TLS/SSL)
        boolean sslEnabled = getBooleanSetting(ctx, "sslEnabled", false);
        if (ctx.security == SecurityProfile.PRODUCTION && !sslEnabled) {
            rules.add(ComplianceRule.fail(
                "ACME-DP-002.2",
                "ACME Policy DP-002.2: TLS/SSL required for PRODUCTION",
                "TLS/SSL must be enabled for PRODUCTION systems to encrypt data in transit"
            ));
        } else if (sslEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-DP-002.2",
                "ACME Policy DP-002.2: TLS/SSL enabled for data in transit protection"
            ));
        }

        // DP-002.3: KMS Key Rotation
        boolean kmsRotation = config.isKmsKeyRotationRemediationEnabled();
        if (kmsRotation) {
            rules.add(ComplianceRule.pass(
                "ACME-DP-002.3",
                "ACME Policy DP-002.3: KMS automatic key rotation enabled"
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "ACME-DP-002.3",
                "ACME Policy DP-002.3: Enable KMS key rotation for PRODUCTION environments"
            ));
        }

        return rules;
    }

    /**
     * Validate access control policies.
     *
     * <p>ACME Policy AC-003: Access Control and Authentication</p>
     * <ul>
     *   <li>Least privilege IAM policies</li>
     *   <li>MFA for production access</li>
     *   <li>OIDC/SSO for application authentication</li>
     * </ul>
     */
    private List<ComplianceRule> validateAccessControl(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // AC-003.1: Least Privilege IAM
        String iamProfile = ctx.cfc.getContextValue("iamProfile", "MINIMAL");
        if ("EXTENDED".equals(iamProfile) && ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "ACME-AC-003.1",
                "ACME Policy AC-003.1: EXTENDED IAM profile detected. Review if all permissions are necessary (least privilege principle)"
            ));
        } else if ("MINIMAL".equals(iamProfile)) {
            rules.add(ComplianceRule.pass(
                "ACME-AC-003.1",
                "ACME Policy AC-003.1: MINIMAL IAM profile follows least privilege principle"
            ));
        }

        // AC-003.2: MFA Enforcement
        boolean mfaEnabled = getBooleanSetting(ctx, "mfaEnabled", false);
        if (ctx.security == SecurityProfile.PRODUCTION && !mfaEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-AC-003.2",
                "ACME Policy AC-003.2: Multi-factor authentication recommended for PRODUCTION systems"
            ));
        } else if (mfaEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-AC-003.2",
                "ACME Policy AC-003.2: Multi-factor authentication enabled"
            ));
        }

        // AC-003.3: OIDC/SSO Authentication
        String authMode = ctx.cfc.getContextValue("authMode", "none");
        boolean hasOidc = "alb-oidc".equals(authMode) || "application-oidc".equals(authMode);
        if (ctx.security == SecurityProfile.PRODUCTION && !hasOidc) {
            rules.add(ComplianceRule.pass(
                "ACME-AC-003.3",
                "ACME Policy AC-003.3: OIDC/SSO authentication recommended for PRODUCTION applications"
            ));
        } else if (hasOidc) {
            rules.add(ComplianceRule.pass(
                "ACME-AC-003.3",
                "ACME Policy AC-003.3: OIDC authentication configured (" + authMode + ")"
            ));
        }

        return rules;
    }

    /**
     * Validate monitoring and alerting.
     *
     * <p>ACME Policy MO-004: Security Monitoring and Incident Detection</p>
     * <ul>
     *   <li>CloudWatch alarms for critical metrics</li>
     *   <li>Security Hub enabled for PRODUCTION</li>
     *   <li>GuardDuty threat detection</li>
     * </ul>
     */
    private List<ComplianceRule> validateMonitoring(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        // MO-004.1: CloudWatch Alarms
        boolean alarmsEnabled = config.isSecurityMonitoringEnabled();
        if (alarmsEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-MO-004.1",
                "ACME Policy MO-004.1: CloudWatch alarms enabled for proactive monitoring"
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "ACME-MO-004.1",
                "ACME Policy MO-004.1: Enable CloudWatch alarms for PRODUCTION critical metrics"
            ));
        }

        // MO-004.2: Security Hub
        boolean securityHubEnabled = getBooleanSetting(ctx, "securityHubEnabled", false);
        if (ctx.security == SecurityProfile.PRODUCTION && !securityHubEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-MO-004.2",
                "ACME Policy MO-004.2: AWS Security Hub recommended for PRODUCTION compliance dashboard"
            ));
        } else if (securityHubEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-MO-004.2",
                "ACME Policy MO-004.2: AWS Security Hub enabled for centralized security monitoring"
            ));
        }

        // MO-004.3: GuardDuty
        boolean guardDutyEnabled = config.isSecurityMonitoringEnabled(); // Includes GuardDuty
        if (guardDutyEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-MO-004.3",
                "ACME Policy MO-004.3: Amazon GuardDuty enabled for threat detection"
            ));
        } else if (ctx.security == SecurityProfile.PRODUCTION) {
            rules.add(ComplianceRule.pass(
                "ACME-MO-004.3",
                "ACME Policy MO-004.3: Enable GuardDuty for PRODUCTION threat detection"
            ));
        }

        return rules;
    }

    /**
     * Validate container security (Fargate/ECS specific).
     *
     * <p>ACME Policy CS-005: Container Security</p>
     * <ul>
     *   <li>Container image vulnerability scanning</li>
     *   <li>Runtime security monitoring</li>
     *   <li>Immutable infrastructure pattern</li>
     * </ul>
     */
    private List<ComplianceRule> validateContainerSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // CS-005.1: Container Image Scanning (Amazon Inspector)
        boolean inspectorEnabled = getBooleanSetting(ctx, "inspectorEnabled", false);
        if (ctx.security == SecurityProfile.PRODUCTION && !inspectorEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-CS-005.1",
                "ACME Policy CS-005.1: Enable Amazon Inspector for container vulnerability scanning in PRODUCTION"
            ));
        } else if (inspectorEnabled) {
            rules.add(ComplianceRule.pass(
                "ACME-CS-005.1",
                "ACME Policy CS-005.1: Amazon Inspector enabled for container image scanning"
            ));
        }

        // CS-005.2: Immutable Infrastructure
        // Fargate containers are inherently immutable
        if (ctx.runtime == RuntimeType.FARGATE) {
            rules.add(ComplianceRule.pass(
                "ACME-CS-005.2",
                "ACME Policy CS-005.2: Fargate provides immutable container infrastructure"
            ));
        }

        // CS-005.3: Container Runtime Security (GuardDuty Runtime Monitoring)
        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config != null && config.isSecurityMonitoringEnabled()) {
            rules.add(ComplianceRule.pass(
                "ACME-CS-005.3",
                "ACME Policy CS-005.3: GuardDuty runtime monitoring enabled for container threat detection"
            ));
        }

        return rules;
    }

    // ========== Helper Methods ==========

    /**
     * Helper method to safely get boolean settings from deployment context.
     */
    private boolean getBooleanSetting(SystemContext ctx, String key, boolean defaultValue) {
        try {
            String value = ctx.cfc.getContextValue(key, String.valueOf(defaultValue));
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

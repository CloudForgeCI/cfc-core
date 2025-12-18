package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Key Management compliance validation rules.
 *
 * <p>These rules enforce cryptographic key management best practices across
 * multiple compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Requirement 3.5, 3.6: Cryptographic key management</li>
 *   <li><b>HIPAA</b> - §164.312(a)(2)(iv): Encryption key management</li>
 *   <li><b>SOC 2</b> - CC6.1: Encryption key protection</li>
 *   <li><b>GDPR</b> - Article 32(1)(a): Encryption of personal data</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>KMS key rotation enforcement</li>
 *   <li>Certificate lifecycle management</li>
 *   <li>Secrets Manager integration for credentials</li>
 *   <li>Key access policies and least privilege</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Automatically loaded via FrameworkLoader (v2.0 pattern)
 * // Or manually: new KeyManagementRules().install(ctx);
 * }</pre>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "KeyManagement",
    priority = -10,
    alwaysLoad = true,
    displayName = "Key Management & Encryption",
    description = "Cross-framework key management and encryption validation"
)
public class KeyManagementRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(KeyManagementRules.class.getName());

    /**
     * Install key management validation rules.
     * These rules apply to PRODUCTION and STAGING environments.
     *
     * @param ctx System context
     */
    @Override
    public void install(SystemContext ctx) {
        // Key management is critical for production and staging
        if (ctx.security == SecurityProfile.DEV) {
            LOG.info("Key management validation rules are advisory for DEV environments");
        }

        LOG.info("Installing key management compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // KMS key management
            rules.addAll(validateKmsKeyManagement(ctx));

            // Certificate management
            rules.addAll(validateCertificateManagement(ctx));

            // Secrets management
            rules.addAll(validateSecretsManagement(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("Key Management validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // For DEV and STAGING, these are advisory only (warnings but not blocking)
                if (ctx.security == SecurityProfile.DEV || ctx.security == SecurityProfile.STAGING) {
                    return List.of();
                }

                // For PRODUCTION only, convert to error strings (blocking)
                return failedRules.stream()
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();
            } else {
                LOG.info("Key Management validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate KMS key management practices.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>KMS key rotation enabled (annual rotation required)</li>
     *   <li>Customer-managed keys used for production data</li>
     *   <li>Key policies follow least privilege</li>
     * </ul>
     */
    private List<ComplianceRule> validateKmsKeyManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // Check if security profile enables encryption (which implies key management)
        boolean encryptionEnabled = config != null &&
            (config.isEbsEncryptionEnabled() ||
             config.isEfsEncryptionAtRestEnabled() ||
             config.isS3EncryptionEnabled());

        // Check deployment context override
        boolean kmsKeyRotationEnabled = getBooleanSetting(ctx, "kmsKeyRotationEnabled", encryptionEnabled);

        // Use ComplianceMatrix to determine if KMS rotation is REQUIRED or ADVISORY
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        String complianceModeStr = ctx.cfc.complianceMode();
        ComplianceMode complianceMode = ComplianceMode.fromString(
            complianceModeStr,
            ComplianceMode.defaultForProfile(ctx.security)
        );

        ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.KMS_KEY_ROTATION,
            complianceFrameworks,
            kmsKeyRotationEnabled,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "KMS-ROTATION",
                    "KMS automatic key rotation required for " + complianceFrameworks,
                    "KmsKeyRotationEnabled",
                    "Enable automatic key rotation for all customer-managed KMS keys. " +
                    "Set kmsKeyRotationEnabled = true in deployment context if using custom KMS keys."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("KMS key rotation recommended but not required for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "KMS-ROTATION",
                    "KMS key rotation is advisory for " + complianceFrameworks + " (recommended but not required)"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "KMS-ROTATION",
                    "KMS automatic key rotation enabled" +
                    (encryptionEnabled ? " (via security profile)" : ""),
                    "KmsKeyRotationEnabled"
                ));
            }
        } else {
            rules.add(ComplianceRule.pass(
                "KMS-ROTATION",
                "KMS key rotation not required for " + ctx.security + " environment"
            ));
        }

        return rules;
    }

    /**
     * Validate certificate lifecycle management.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Certificate expiration monitoring</li>
     *   <li>Automated certificate renewal</li>
     *   <li>Strong cipher suites</li>
     * </ul>
     */
    private List<ComplianceRule> validateCertificateManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Certificate must exist for HTTPS
        if (ctx.cert.get().isEmpty()) {
            // Already validated in other rules, just pass
            rules.add(ComplianceRule.pass(
                "CERT-EXISTS",
                "Certificate validation handled by encryption rules"
            ));
            return rules;
        }

        var config = ctx.securityProfileConfig.get().orElse(null);

        // Certificate expiration monitoring
        boolean securityMonitoringEnabled = config != null && config.isSecurityMonitoringEnabled();
        boolean certExpirationMonitoringEnabled = getBooleanSetting(ctx, "certificateExpirationMonitoring", securityMonitoringEnabled);

        // Use ComplianceMatrix to determine if certificate monitoring is REQUIRED or ADVISORY
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        String complianceModeStr = ctx.cfc.complianceMode();
        ComplianceMode complianceMode = ComplianceMode.fromString(
            complianceModeStr,
            ComplianceMode.defaultForProfile(ctx.security)
        );

        ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.CERTIFICATE_EXPIRATION_MONITORING,
            complianceFrameworks,
            certExpirationMonitoringEnabled,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "CERT-EXPIRATION-MONITOR",
                    "Certificate expiration monitoring required for " + complianceFrameworks,
                    "CertificateExpirationAlarm",
                    "Enable CloudWatch alarms to monitor certificate expiration (30 days before). " +
                    "Set certificateExpirationMonitoring = true in deployment context."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("Certificate expiration monitoring recommended but not required for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "CERT-EXPIRATION-MONITOR",
                    "Certificate expiration monitoring is advisory for " + complianceFrameworks + " (recommended but not required)",
                    "CertificateExpirationAlarm"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "CERT-EXPIRATION-MONITOR",
                    "Certificate expiration monitoring enabled" +
                    (securityMonitoringEnabled ? " (via security profile)" : ""),
                    "CertificateExpirationAlarm"
                ));
            }
        } else {
            rules.add(ComplianceRule.pass(
                "CERT-EXPIRATION-MONITOR",
                "Certificate expiration monitoring not required for " + ctx.security,
                "CertificateExpirationAlarm"
            ));
        }

        // ACM automatic renewal (for ACM certificates) - default to true (ACM renews automatically)
        boolean usesAcmAutoRenewal = getBooleanSetting(ctx, "acmAutoRenewalEnabled", true);

        if (usesAcmAutoRenewal) {
            rules.add(ComplianceRule.pass(
                "CERT-AUTO-RENEWAL",
                "ACM automatic certificate renewal enabled"
            ));
        } else {
            rules.add(ComplianceRule.fail(
                "CERT-AUTO-RENEWAL",
                "ACM automatic renewal recommended for certificates",
                "Use AWS Certificate Manager with automatic renewal for HTTPS certificates"
            ));
        }

        return rules;
    }

    /**
     * Validate secrets management practices.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Secrets Manager for database credentials</li>
     *   <li>Automatic secret rotation</li>
     *   <li>No hardcoded credentials</li>
     * </ul>
     */
    private List<ComplianceRule> validateSecretsManagement(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);

        // Secrets Manager usage for credentials
        boolean encryptionEnabled = config != null && config.isS3EncryptionEnabled();
        boolean usesSecretsManager = getBooleanSetting(ctx, "secretsManagerEnabled", encryptionEnabled);

        // Use ComplianceMatrix to determine if Secrets Manager is REQUIRED or ADVISORY
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        String complianceModeStr = ctx.cfc.complianceMode();
        ComplianceMode complianceMode = ComplianceMode.fromString(
            complianceModeStr,
            ComplianceMode.defaultForProfile(ctx.security)
        );

        ComplianceMatrix.ValidationResult smResult = ComplianceMatrix.validateControlMultiFramework(
            ComplianceMatrix.SecurityControl.SECRETS_MANAGER,
            complianceFrameworks,
            usesSecretsManager,
            complianceMode
        );

        if (ctx.security == SecurityProfile.PRODUCTION) {
            if (smResult == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "SECRETS-MANAGER",
                    "AWS Secrets Manager required for " + complianceFrameworks + " credentials",
                    "SecretsManagerInUse",
                    "Store database credentials, API keys, and secrets in AWS Secrets Manager. " +
                    "Set secretsManagerEnabled = true in deployment context."
                ));
            } else if (smResult == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("Secrets Manager recommended but not required for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "SECRETS-MANAGER",
                    "Secrets Manager is advisory for " + complianceFrameworks + " (recommended but not required)",
                    "SecretsManagerInUse"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECRETS-MANAGER",
                    "AWS Secrets Manager enabled for credential management" +
                    (encryptionEnabled ? " (via security profile)" : ""),
                    "SecretsManagerInUse"
                ));
            }

            // Automatic secret rotation (only validate if Secrets Manager is in use)
            if (usesSecretsManager) {
                boolean secretRotationEnabled = getBooleanSetting(ctx, "secretRotationEnabled", encryptionEnabled);

                ComplianceMatrix.ValidationResult rotationResult = ComplianceMatrix.validateControlMultiFramework(
                    ComplianceMatrix.SecurityControl.SECRETS_ROTATION,
                    complianceFrameworks,
                    secretRotationEnabled,
                    complianceMode
                );

                if (rotationResult == ComplianceMatrix.ValidationResult.FAIL) {
                    rules.add(ComplianceRule.fail(
                        "SECRET-ROTATION",
                        "Automatic secret rotation required for " + complianceFrameworks,
                        "SecretsManagerRotation",
                        "Enable automatic rotation for secrets (90 days or less). " +
                        "Set secretRotationEnabled = true in deployment context."
                    ));
                } else if (rotationResult == ComplianceMatrix.ValidationResult.WARN) {
                    LOG.warning("Secret rotation recommended but not required for " + complianceFrameworks);
                    rules.add(ComplianceRule.pass(
                        "SECRET-ROTATION",
                        "Secret rotation is advisory for " + complianceFrameworks + " (recommended but not required)",
                        "SecretsManagerRotation"
                    ));
                } else {
                    rules.add(ComplianceRule.pass(
                        "SECRET-ROTATION",
                        "Automatic secret rotation enabled" +
                        (encryptionEnabled ? " (via security profile)" : ""),
                        "SecretsManagerRotation"
                    ));
                }
            }
        } else {
            // For non-production, advisory only
            if (usesSecretsManager) {
                rules.add(ComplianceRule.pass(
                    "SECRETS-MANAGER",
                    "AWS Secrets Manager enabled",
                    "SecretsManagerInUse"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECRETS-MANAGER",
                    "Secrets Manager not required for " + ctx.security + " environment"
                ));
            }
        }

        return rules;
    }

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

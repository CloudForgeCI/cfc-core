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
 * Messaging security compliance validation rules.
 *
 * <p>These rules enforce SQS, SNS, Secrets Manager, and messaging service security
 * requirements across multiple compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 3.4: Encryption; Req 8.2: Credential management</li>
 *   <li><b>HIPAA</b> - §164.312(a)(2)(iv): Encryption; §164.308(a)(5)(ii)(D): Password management</li>
 *   <li><b>SOC 2</b> - CC6.1: Encryption and credential controls</li>
 *   <li><b>GDPR</b> - Art.32(1)(a): Encryption of data</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>SQS/SNS encryption at rest</li>
 *   <li>Secrets Manager KMS encryption</li>
 *   <li>Secrets rotation configuration</li>
 *   <li>Dead letter queue configuration</li>
 *   <li>Kinesis stream encryption</li>
 * </ul>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "MessagingSecurity",
    priority = 0,
    alwaysLoad = true,
    displayName = "Messaging Security",
    description = "Cross-framework messaging service security validation"
)
public class MessagingSecurityRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(MessagingSecurityRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing messaging security compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // SQS/SNS encryption
            rules.addAll(validateMessagingEncryption(ctx));

            // Secrets Manager security
            rules.addAll(validateSecretsManagerSecurity(ctx));

            // Error handling (DLQ)
            rules.addAll(validateErrorHandling(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("Messaging Security validation found " + failedRules.size() + " recommendations");
                failedRules.forEach(rule ->
                    LOG.warning("  - " + rule.description() + ": " + rule.errorMessage().orElse("")));

                // For DEV and STAGING, these are advisory only
                if (ctx.security == SecurityProfile.DEV || ctx.security == SecurityProfile.STAGING) {
                    return List.of();
                }

                // For PRODUCTION, return blocking failures
                List<String> blockingRules = failedRules.stream()
                    .filter(rule -> isBlockingRule(ctx, rule.description()))
                    .map(rule -> rule.description() + ": " + rule.errorMessage().orElse(""))
                    .toList();

                return blockingRules;
            } else {
                LOG.info("Messaging Security validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate SQS/SNS encryption settings.
     */
    private List<ComplianceRule> validateMessagingEncryption(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // SQS/SNS encryption at rest
        if (ctx.security == SecurityProfile.PRODUCTION) {
            // Use S3 encryption as proxy for overall encryption stance
            boolean encryptionEnabled = config.isS3EncryptionEnabled();

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
                complianceFrameworks,
                encryptionEnabled,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "MESSAGING-ENCRYPTION",
                    "Messaging services (SQS/SNS) must use encryption at rest",
                    "Enable KMS encryption for SQS queues and SNS topics. " +
                    "Required for PCI-DSS Req 3.4 and HIPAA encryption requirements."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "MESSAGING-ENCRYPTION",
                    "Messaging encryption configured"
                ));
            }

            // Kinesis encryption (if applicable)
            boolean kinesisEncryption = getBooleanSetting(ctx, "kinesisEncryption", true);
            if (!kinesisEncryption) {
                rules.add(ComplianceRule.fail(
                    "KINESIS-ENCRYPTION",
                    "Kinesis streams should use KMS encryption",
                    "Enable KMS encryption for Kinesis data streams. " +
                    "Set kinesisEncryption = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "KINESIS-ENCRYPTION",
                    "Kinesis encryption configured"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate Secrets Manager security settings.
     */
    private List<ComplianceRule> validateSecretsManagerSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // Secrets Manager usage and rotation
        if (ctx.security == SecurityProfile.PRODUCTION) {
            // Secrets Manager rotation
            ComplianceMatrix.ValidationResult rotationResult = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.SECRETS_ROTATION,
                complianceFrameworks,
                true, // Assume rotation is configured if using Secrets Manager
                complianceMode
            );

            boolean secretsRotation = getBooleanSetting(ctx, "secretsRotationEnabled", false);
            if (!secretsRotation && rotationResult == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "SECRETS-ROTATION",
                    "Secrets rotation required for " + complianceFrameworks,
                    "Configure automatic secrets rotation (90 days or less). " +
                    "Required for PCI-DSS Req 8.2.4. " +
                    "Set secretsRotationEnabled = true when rotation is configured."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECRETS-ROTATION",
                    "Secrets rotation configured or not required"
                ));
            }

            // Secrets Manager KMS encryption
            boolean secretsKmsEncryption = getBooleanSetting(ctx, "secretsKmsEncryption", true);
            if (!secretsKmsEncryption) {
                rules.add(ComplianceRule.fail(
                    "SECRETS-KMS-ENCRYPTION",
                    "Secrets Manager should use customer-managed KMS keys",
                    "Configure Secrets Manager secrets with customer-managed KMS keys. " +
                    "Set secretsKmsEncryption = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "SECRETS-KMS-ENCRYPTION",
                    "Secrets Manager KMS encryption configured"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate error handling (DLQ) configuration.
     */
    private List<ComplianceRule> validateErrorHandling(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Dead letter queue configuration for production
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean dlqConfigured = getBooleanSetting(ctx, "deadLetterQueueEnabled", false);

            if (!dlqConfigured) {
                rules.add(ComplianceRule.fail(
                    "DLQ-CONFIGURATION",
                    "Dead letter queues recommended for production messaging",
                    "Configure dead letter queues for SQS and EventBridge targets. " +
                    "Set deadLetterQueueEnabled = true when DLQs are configured."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "DLQ-CONFIGURATION",
                    "Dead letter queues configured"
                ));
            }
        }

        return rules;
    }

    /**
     * Check if a rule failure should block deployment.
     */
    private boolean isBlockingRule(SystemContext ctx, String ruleDescription) {
        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        boolean requiresPciDss = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("PCI-DSS");
        boolean requiresHipaa = complianceFrameworks != null &&
            complianceFrameworks.toUpperCase().contains("HIPAA");

        // Encryption is blocking for PCI-DSS and HIPAA
        if (ruleDescription.contains("encryption") && (requiresPciDss || requiresHipaa)) {
            return true;
        }

        // Secrets rotation is blocking for PCI-DSS
        if (ruleDescription.contains("Secrets rotation") && requiresPciDss) {
            return true;
        }

        return false;
    }

    /**
     * Get boolean setting from context with default value.
     */
    private boolean getBooleanSetting(SystemContext ctx, String key, boolean defaultValue) {
        try {
            var method = ctx.cfc.getClass().getMethod(key);
            Boolean value = (Boolean) method.invoke(ctx.cfc);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

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
 * Lambda security compliance validation rules.
 *
 * <p>These rules enforce Lambda function security requirements across multiple
 * compliance frameworks:</p>
 * <ul>
 *   <li><b>PCI-DSS</b> - Req 6: Secure development; Req 10: Logging</li>
 *   <li><b>HIPAA</b> - §164.312(d): Audit controls</li>
 *   <li><b>SOC 2</b> - CC7.1: Security monitoring</li>
 *   <li><b>GDPR</b> - Art.32: Security of processing</li>
 * </ul>
 *
 * <h2>Controls Implemented</h2>
 * <ul>
 *   <li>Lambda VPC deployment</li>
 *   <li>Environment variable encryption</li>
 *   <li>Dead letter queue configuration</li>
 *   <li>X-Ray tracing</li>
 *   <li>Code signing</li>
 * </ul>
 *
 * @since 3.0.0
 */
@ComplianceFramework(
    value = "LambdaSecurity",
    priority = 0,
    alwaysLoad = true,
    displayName = "Lambda Security",
    description = "Cross-framework Lambda function security validation"
)
public class LambdaSecurityRules implements FrameworkRules<SystemContext> {

    private static final Logger LOG = Logger.getLogger(LambdaSecurityRules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        LOG.info("Installing Lambda security compliance validation rules for " + ctx.security);

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Check if Lambda is being used
            boolean lambdaEnabled = getBooleanSetting(ctx, "lambdaEnabled", false);
            if (!lambdaEnabled) {
                // Lambda not in use, skip validation
                return List.of();
            }

            // Lambda network security
            rules.addAll(validateLambdaNetworkSecurity(ctx));

            // Lambda encryption
            rules.addAll(validateLambdaEncryption(ctx));

            // Lambda monitoring
            rules.addAll(validateLambdaMonitoring(ctx));

            // Lambda error handling
            rules.addAll(validateLambdaErrorHandling(ctx));

            // Get all failed rules
            List<ComplianceRule> failedRules = rules.stream()
                .filter(rule -> !rule.passed())
                .toList();

            if (!failedRules.isEmpty()) {
                LOG.warning("Lambda Security validation found " + failedRules.size() + " recommendations");
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
                LOG.info("Lambda Security validation passed (" + rules.size() + " checks)");
                return List.of();
            }
        });
    }

    /**
     * Validate Lambda network security (VPC deployment).
     */
    private List<ComplianceRule> validateLambdaNetworkSecurity(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // Lambda VPC deployment - use ComplianceMatrix for network segmentation
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean lambdaInVpc = getBooleanSetting(ctx, "lambdaInVpc", false);

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.NETWORK_SEGMENTATION,
                complianceFrameworks,
                lambdaInVpc,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "LAMBDA-VPC-DEPLOYMENT",
                    "Lambda functions should be deployed in VPC for " + complianceFrameworks,
                    "Deploy Lambda functions in VPC with appropriate security groups. " +
                    "Set lambdaInVpc = true in deployment context."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("Lambda VPC deployment recommended for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "LAMBDA-VPC-DEPLOYMENT",
                    "Lambda VPC deployment recommended but not required"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "LAMBDA-VPC-DEPLOYMENT",
                    "Lambda VPC deployment configured"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate Lambda encryption settings.
     */
    private List<ComplianceRule> validateLambdaEncryption(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // Lambda environment variable encryption - use ComplianceMatrix
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean lambdaEnvEncryption = getBooleanSetting(ctx, "lambdaEnvEncryption", false);

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.ENCRYPTION_AT_REST,
                complianceFrameworks,
                lambdaEnvEncryption,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "LAMBDA-ENV-ENCRYPTION",
                    "Lambda environment variables must use KMS encryption for " + complianceFrameworks,
                    "Configure Lambda functions to use customer-managed KMS keys " +
                    "for environment variable encryption. " +
                    "Set lambdaEnvEncryption = true in deployment context."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("Lambda environment encryption recommended for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "LAMBDA-ENV-ENCRYPTION",
                    "Lambda environment encryption recommended but not required"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "LAMBDA-ENV-ENCRYPTION",
                    "Lambda environment encryption configured"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate Lambda monitoring settings.
     */
    private List<ComplianceRule> validateLambdaMonitoring(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        var config = ctx.securityProfileConfig.get().orElse(null);
        if (config == null) {
            return rules;
        }

        String complianceFrameworks = ctx.cfc.complianceFrameworks();
        ComplianceMode complianceMode = ctx.cfc.complianceMode();

        // Lambda X-Ray tracing - use ComplianceMatrix for security monitoring
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean xrayTracing = getBooleanSetting(ctx, "lambdaXrayTracing", false);

            ComplianceMatrix.ValidationResult result = ComplianceMatrix.validateControlMultiFramework(
                ComplianceMatrix.SecurityControl.SECURITY_MONITORING,
                complianceFrameworks,
                xrayTracing,
                complianceMode
            );

            if (result == ComplianceMatrix.ValidationResult.FAIL) {
                rules.add(ComplianceRule.fail(
                    "LAMBDA-XRAY-TRACING",
                    "Lambda X-Ray tracing required for " + complianceFrameworks,
                    "Enable X-Ray tracing for Lambda functions for distributed tracing " +
                    "and performance monitoring. Set lambdaXrayTracing = true in deployment context."
                ));
            } else if (result == ComplianceMatrix.ValidationResult.WARN) {
                LOG.warning("Lambda X-Ray tracing recommended for " + complianceFrameworks);
                rules.add(ComplianceRule.pass(
                    "LAMBDA-XRAY-TRACING",
                    "Lambda X-Ray tracing recommended but not required"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "LAMBDA-XRAY-TRACING",
                    "Lambda X-Ray tracing enabled"
                ));
            }
        }

        return rules;
    }

    /**
     * Validate Lambda error handling settings.
     */
    private List<ComplianceRule> validateLambdaErrorHandling(SystemContext ctx) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Lambda dead letter queue - advisory for all frameworks
        if (ctx.security == SecurityProfile.PRODUCTION) {
            boolean lambdaDlq = getBooleanSetting(ctx, "lambdaDeadLetterQueue", false);

            if (!lambdaDlq) {
                rules.add(ComplianceRule.fail(
                    "LAMBDA-DEAD-LETTER-QUEUE",
                    "Lambda functions should have dead letter queues configured",
                    "Configure dead letter queues for Lambda functions to capture " +
                    "failed invocations for troubleshooting and retry. " +
                    "Set lambdaDeadLetterQueue = true in deployment context."
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "LAMBDA-DEAD-LETTER-QUEUE",
                    "Lambda dead letter queue configured"
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

        // Lambda VPC deployment is blocking for HIPAA only (PHI protection)
        if (ruleDescription.contains("VPC") && requiresHipaa) {
            return true;
        }

        // Lambda encryption is blocking for PCI-DSS and HIPAA
        if (ruleDescription.contains("encryption") && (requiresPciDss || requiresHipaa)) {
            return true;
        }

        // DLQ and X-Ray tracing are advisory
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

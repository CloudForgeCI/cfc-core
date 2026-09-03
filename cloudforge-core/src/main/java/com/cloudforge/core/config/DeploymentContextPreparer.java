package com.cloudforge.core.config;

import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fills missing {@link DeploymentConfig} fields from ApplicationSpec, property files,
 * and auth/domain coherence rules — mirroring the interactive deployer when loading
 * a partial {@code deployment-context.json}.
 *
 * @since 3.3.0
 */
public final class DeploymentContextPreparer {

    private DeploymentContextPreparer() {
    }

    /**
     * Messages describing defaults applied or auth coercions performed.
     */
    public record PrepareResult(List<String> messages) {
        public PrepareResult() {
            this(new ArrayList<>());
        }

        void info(String message) {
            messages.add(message);
        }

        void warn(String message) {
            messages.add("⚠️  " + message);
        }
    }

    /**
     * Prepare configuration for synthesis or deployment.
     *
     * @param config deployment context (mutated in place)
     * @param spec application spec (falls back to {@code config.applicationSpec})
     * @param deploymentTarget {@code aws}, {@code ministack}, {@code localstack}, or {@code null}
     */
    public static PrepareResult prepare(
            DeploymentConfig config,
            ApplicationSpec spec,
            String deploymentTarget) {
        PrepareResult result = new PrepareResult();
        if (config == null) {
            return result;
        }

        ApplicationPropertyLoader.applyPropertyDefaults(config);

        ApplicationSpec appSpec = spec != null ? spec : config.applicationSpec;
        if (appSpec != null) {
            config.applicationSpec = appSpec;
            applyApplicationSpecFieldDefaults(config, appSpec, result);
            applyResourceDefaults(config, appSpec, result);
            applyDatabaseRequirements(config, appSpec, result);
        }

        applyStructuralDefaults(config, appSpec, result);
        applyOidcCoherence(config, appSpec, result);

        if (deploymentTarget != null && !deploymentTarget.isBlank() && appSpec != null) {
            coerceAuthModeForTarget(config, appSpec, deploymentTarget, result);
        }

        return result;
    }

    private static void applyApplicationSpecFieldDefaults(
            DeploymentConfig config,
            ApplicationSpec appSpec,
            PrepareResult result) {
        for (Field field : DeploymentConfig.class.getDeclaredFields()) {
            if (!field.isAnnotationPresent(com.cloudforge.core.annotation.ConfigField.class)) {
                continue;
            }
            ConfigFieldInfo info = ConfigFieldInfo.from(field);
            if (info.defaultFrom() == null || info.defaultFrom().isBlank()) {
                continue;
            }
            if (hasValue(info.getValue(config))) {
                continue;
            }
            Object resolved = DefaultValueResolver.resolve(info, appSpec, null);
            if (resolved != null) {
                info.setValue(config, resolved);
                result.info("Set " + info.fieldName() + " from ApplicationSpec (" + info.defaultFrom() + ")");
            }
        }
    }

    private static void applyResourceDefaults(
            DeploymentConfig config,
            ApplicationSpec appSpec,
            PrepareResult result) {
        RuntimeType runtime = config.runtime != null ? config.runtime : RuntimeType.FARGATE;
        if (runtime == RuntimeType.EC2) {
            if (config.instanceType == null || config.instanceType.isBlank()) {
                config.instanceType = appSpec.defaultInstanceType();
                result.info("Set instanceType from ApplicationSpec");
            }
            return;
        }
        if (config.cpu <= 0) {
            config.cpu = appSpec.defaultCpu();
            result.info("Set cpu from ApplicationSpec");
        }
        if (config.memory <= 0) {
            config.memory = appSpec.defaultMemory();
            result.info("Set memory from ApplicationSpec");
        }
    }

    private static void applyDatabaseRequirements(
            DeploymentConfig config,
            ApplicationSpec appSpec,
            PrepareResult result) {
        if (!(appSpec instanceof DatabaseSpec dbSpec)) {
            return;
        }
        DatabaseSpec.DatabaseRequirement requirement = dbSpec.databaseRequirement();
        if (requirement == null) {
            return;
        }
        if (requirement.type() == DatabaseSpec.DatabaseRequirement.RequirementType.REQUIRED
                && (config.provisionDatabase == null || !config.provisionDatabase)) {
            config.provisionDatabase = true;
            result.info("Enabled provisionDatabase (required by ApplicationSpec)");
        }
    }

    private static void applyStructuralDefaults(
            DeploymentConfig config, ApplicationSpec appSpec, PrepareResult result) {
        if (config.topology == null) {
            // Defaults to CMS_SERVICE for a CmsSpec implementer (WordPress, Drupal, etc.), not the
            // generic APPLICATION_SERVICE every other app falls back to — TopologyType.CMS_SERVICE
            // exists specifically for these ("auto-wires S3 media, Redis, CDN, RDS from CmsSpec" —
            // see its javadoc), so a flat default would leave a CMS app's actual deployed topology
            // mismatched with what the wizard shows/submits. Still just a default: topology stays
            // a genuinely selectable field (see DeploymentConfig.topology's allowedValues) so a
            // future CmsSpec-implementing integration can still override it either direction.
            config.topology = appSpec instanceof com.cloudforge.core.interfaces.CmsSpec
                ? TopologyType.CMS_SERVICE
                : TopologyType.APPLICATION_SERVICE;
            result.info("Set topology=" + config.topology);
        }
        if (config.networkMode == null) {
            config.networkMode = NetworkMode.PUBLIC;
            result.info("Set networkMode=public-no-nat");
        }
        if (config.securityProfile == null) {
            config.securityProfile = SecurityProfile.DEV;
            result.info("Set securityProfile=DEV");
        }
        if (config.runtime == null) {
            config.runtime = RuntimeType.FARGATE;
            result.info("Set runtime=FARGATE");
        }
        if (config.minInstanceCapacity <= 0) {
            config.minInstanceCapacity = 1;
        }
        if (config.maxInstanceCapacity <= 0) {
            config.maxInstanceCapacity = Math.max(1, config.minInstanceCapacity);
        }
        if (config.enableAutoScaling == null) {
            config.enableAutoScaling = config.maxInstanceCapacity > config.minInstanceCapacity;
        }
        if (config.wafEnabled == null) {
            config.wafEnabled = false;
        }
        if (config.enableMonitoring == null) {
            config.enableMonitoring = true;
        }
        if (config.enableEncryption == null) {
            config.enableEncryption = true;
        }
        if (config.awsConfigEnabled == null) {
            config.awsConfigEnabled = false;
        }
        if (config.guardDutyEnabled == null) {
            config.guardDutyEnabled = false;
        }
        if (config.auditManagerEnabled == null) {
            config.auditManagerEnabled = false;
        }
    }

    private static void applyOidcCoherence(
            DeploymentConfig config,
            ApplicationSpec appSpec,
            PrepareResult result) {
        AuthMode auth = config.authMode != null ? config.authMode : AuthMode.NONE;
        if (auth != AuthMode.ALB_OIDC && auth != AuthMode.APPLICATION_OIDC) {
            return;
        }
        if (appSpec != null && !appSpec.supportsOidcIntegration()) {
            result.warn("authMode " + auth + " requested but ApplicationSpec does not support OIDC");
            return;
        }

        if (config.enableSsl == null || !config.enableSsl) {
            config.enableSsl = true;
            result.info("Set enableSsl=true (OIDC requires HTTPS)");
        }

        if (config.oidcProvider == null || config.oidcProvider.isBlank() || "none".equals(config.oidcProvider)) {
            config.oidcProvider = "cognito";
            result.info("Set oidcProvider=cognito");
        }

        if ("cognito".equals(config.oidcProvider) || "cognito-saml".equals(config.oidcProvider)) {
            applyCognitoDefaults(config, result);
        }
    }

    private static void applyCognitoDefaults(DeploymentConfig config, PrepareResult result) {
        boolean usingExistingPool = config.cognitoUserPoolId != null && !config.cognitoUserPoolId.isBlank();
        if (!usingExistingPool && (config.cognitoAutoProvision == null || !config.cognitoAutoProvision)) {
            config.cognitoAutoProvision = true;
            result.info("Set cognitoAutoProvision=true");
        }
        if (config.cognitoAutoProvision == null || !config.cognitoAutoProvision) {
            return;
        }
        if (config.cognitoDomainPrefix == null || config.cognitoDomainPrefix.isBlank()) {
            String prefix = slug(config.stackName != null ? config.stackName : config.applicationId) + "-auth";
            config.cognitoDomainPrefix = prefix;
            result.info("Set cognitoDomainPrefix=" + prefix);
        }
        if (config.cognitoUserPoolName == null || config.cognitoUserPoolName.isBlank()) {
            config.cognitoUserPoolName = config.stackName + "-users";
        }
        if (config.cognitoCreateGroups == null) {
            config.cognitoCreateGroups = true;
            result.info("Set cognitoCreateGroups=true");
        }
        if (Boolean.TRUE.equals(config.cognitoCreateGroups)) {
            String app = config.applicationId != null ? config.applicationId : "app";
            if (config.cognitoAdminGroupName == null || config.cognitoAdminGroupName.isBlank()) {
                config.cognitoAdminGroupName = app + "-Admins";
            }
            if (config.cognitoUserGroupName == null || config.cognitoUserGroupName.isBlank()) {
                config.cognitoUserGroupName = app + "-Users";
            }
        }
        if (config.cognitoInitialAdminEmail == null || config.cognitoInitialAdminEmail.isBlank()) {
            String domain = config.domain != null && !config.domain.isBlank() ? config.domain : "local.test";
            config.cognitoInitialAdminEmail = "admin@" + domain;
            result.info("Set cognitoInitialAdminEmail=" + config.cognitoInitialAdminEmail);
        }
        if (config.cognitoMfaEnabled == null) {
            config.cognitoMfaEnabled = config.securityProfile == SecurityProfile.PRODUCTION;
        }
    }

    private static void coerceAuthModeForTarget(
            DeploymentConfig config,
            ApplicationSpec appSpec,
            String deploymentTarget,
            PrepareResult result) {
        List<String> allowed = appSpec.getSupportedAuthModes(deploymentTarget);
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        String current = config.authMode == null ? "none" : config.authMode.getValue();
        if (allowed.stream().anyMatch(mode -> mode.equalsIgnoreCase(current))) {
            return;
        }
        String coerced = appSpec.getRecommendedAuthMode(deploymentTarget);
        config.authMode = AuthMode.fromString(coerced);
        result.warn("authMode '" + current + "' is not supported for "
            + config.applicationId + " on " + deploymentTarget + "; using '" + coerced + "'");
    }

    private static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        return true;
    }

    private static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "cloudforge";
        }
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }
}

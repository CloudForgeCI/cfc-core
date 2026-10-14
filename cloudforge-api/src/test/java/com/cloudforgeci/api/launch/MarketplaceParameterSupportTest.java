package com.cloudforgeci.api.launch;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.manager.ManagerEnvKeys;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.iam.ManagerOperatorIamSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Marketplace buyer launches this template directly from the AWS Console and never runs the
 * CDK CLI, so cognitoInitialAdminEmail/managerLicenseKey must become real CloudFormation
 * Parameters (see {@link MarketplaceParameterSupport}'s own javadoc for why this is value-only,
 * not structural, and why Marketplace mode is a plain method parameter rather than a {@code
 * DeploymentConfig} field). {@link ApplicationLoader} (cloudforge-manager-deployment) isn't on
 * this module's test classpath -- it depends on cloudforge-api, not the reverse -- so a minimal
 * {@link ApplicationSpec} stub stands in for the real one; the only method that actually matters
 * to {@link MarketplaceParameterSupport} is {@code applicationId()}.
 */
class MarketplaceParameterSupportTest {

    private static ApplicationSpec specWithId(String applicationId) {
        return new ApplicationSpec() {
            @Override public String applicationId() { return applicationId; }
            @Override public int applicationPort() { return 8080; }
            @Override public String defaultContainerImage() { return "test-image:latest"; }
            @Override public String containerDataPath() { return "/data"; }
            @Override public String efsDataPath() { return "/efs"; }
            @Override public String volumeName() { return "data"; }
            @Override public String containerUser() { return "1000:1000"; }
            @Override public String efsPermissions() { return "750"; }
            @Override public String ebsDeviceName() { return "/dev/xvdh"; }
            @Override public String ec2DataPath() { return "/var/lib/data"; }
            @Override public List<String> ec2LogPaths() { return List.of(); }
            @Override public void configureUserData(UserDataBuilder builder, Ec2Context context) { }
        };
    }

    private static Stack stackWithContext(String stackId, Map<String, Object> cfcContext) {
        App app = new App();
        Stack stack = new Stack(app, stackId, StackProps.builder().build());
        stack.getNode().setContext("cfc", cfcContext);
        return stack;
    }

    private static Map<String, Object> synth(Stack stack) {
        return Template.fromStack(stack).toJSON();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parameters(Stack stack) {
        Map<String, Object> params = (Map<String, Object>) synth(stack).get("Parameters");
        return params == null ? Map.of() : params;
    }

    private static DeploymentConfig appliedConfig(
            Stack stack, MarketplaceVariant variant) {
        Construct scope = MarketplaceParameterSupport.applyIfApplicable(
            stack,
            DeploymentContext.from(stack),
            specWithId(ManagerOperatorIamSupport.APPLICATION_ID),
            variant);
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = (Map<String, Object>) scope.getNode().tryGetContext("cfc");
        return DeploymentConfig.fromMap(raw);
    }

    @Test
    void ssoVariantRequiresAdminEmailAndDomainPrefixAndProvisionsCognito() {
        Stack stack = stackWithContext("ManagerMarketplaceSso", new HashMap<>(Map.of("stackName", "ManagerMarketplaceSso")));

        var config = appliedConfig(stack, MarketplaceVariant.SSO);

        assertEquals(AuthMode.APPLICATION_OIDC, config.authMode);
        assertTrue(Boolean.TRUE.equals(config.cognitoAutoProvision));
        assertEquals(ManagerEnvKeys.DEFAULT_OIDC_ADMIN_GROUP, config.cognitoAdminGroupName,
            "Cognito admin group must be the name Manager maps to its admin role");
        assertEquals(ManagerEnvKeys.DEFAULT_OIDC_MANAGER_GROUP, config.cognitoUserGroupName,
            "Cognito user group must be the name Manager maps to its manager role");

        Map<String, Object> parameters = parameters(stack);
        assertTrue(parameters.containsKey("AdminEmail"), "got: " + parameters.keySet());
        assertTrue(parameters.containsKey("CognitoDomainPrefix"), "got: " + parameters.keySet());
        assertTrue(parameters.containsKey("LicenseKey"), "got: " + parameters.keySet());
        @SuppressWarnings("unchecked")
        Map<String, Object> adminEmail = (Map<String, Object>) parameters.get("AdminEmail");
        assertFalse(adminEmail.containsKey("Default"), "AdminEmail must be required (no default)");
        @SuppressWarnings("unchecked")
        Map<String, Object> prefix = (Map<String, Object>) parameters.get("CognitoDomainPrefix");
        assertFalse(prefix.containsKey("Default"), "CognitoDomainPrefix must be required (no default)");
        @SuppressWarnings("unchecked")
        Map<String, Object> licenseKey = (Map<String, Object>) parameters.get("LicenseKey");
        assertTrue(Boolean.TRUE.equals(licenseKey.get("NoEcho")),
            "LicenseKey must be NoEcho -- it's a sensitive value typed into the AWS Console");

        String description = (String) synth(stack).get("Description");
        assertTrue(description != null && description.contains("Cognito"), "description: " + description);
        assertFalse(description.contains("X-CFC-TOKEN"), "SSO description must not carry token setup: " + description);
    }

    @Test
    void tokenVariantHasNoCognitoParametersAndDescribesTokenSetup() {
        Stack stack = stackWithContext("ManagerMarketplaceToken", new HashMap<>(Map.of("stackName", "ManagerMarketplaceToken")));

        var config = appliedConfig(stack, MarketplaceVariant.TOKEN);

        assertEquals(AuthMode.NONE, config.authMode);
        assertFalse(Boolean.TRUE.equals(config.cognitoAutoProvision));
        assertTrue(config.cognitoAdminGroupName == null && config.cognitoUserGroupName == null,
            "token variant creates no Cognito groups, so it must not set group names");

        Map<String, Object> parameters = parameters(stack);
        assertFalse(parameters.containsKey("AdminEmail"), "got: " + parameters.keySet());
        assertFalse(parameters.containsKey("CognitoDomainPrefix"), "got: " + parameters.keySet());
        assertTrue(parameters.containsKey("LicenseKey"), "got: " + parameters.keySet());

        String description = (String) synth(stack).get("Description");
        assertTrue(description != null && description.contains("X-CFC-TOKEN"), "description: " + description);
    }

    @Test
    void managerNotInMarketplaceModeGetsNoCfnParameters() {
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "ManagerNoMarketplaceParams");
        Stack stack = stackWithContext("ManagerNoMarketplaceParams", cfcContext);

        MarketplaceParameterSupport.applyIfApplicable(
            stack,
            DeploymentContext.from(stack),
            specWithId(ManagerOperatorIamSupport.APPLICATION_ID),
            null);

        Template template = Template.fromStack(stack);
        Map<String, Object> json = template.toJSON();
        // Not "no Parameters block at all" -- CDK always injects its own BootstrapVersion
        // parameter regardless of this feature.
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) json.getOrDefault("Parameters", Map.of());
        assertFalse(parameters.containsKey("AdminEmail") || parameters.containsKey("LicenseKey"),
            "non-Marketplace Manager deploys must not gain these parameters, got: " + parameters);
    }

    @Test
    void nonManagerAppInMarketplaceModeGetsNoCfnParameters() {
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "JenkinsMarketplaceParams");
        Stack stack = stackWithContext("JenkinsMarketplaceParams", cfcContext);

        MarketplaceParameterSupport.applyIfApplicable(
            stack,
            DeploymentContext.from(stack),
            specWithId("jenkins"),
            MarketplaceVariant.SSO);

        Template template = Template.fromStack(stack);
        Map<String, Object> json = template.toJSON();
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) json.getOrDefault("Parameters", Map.of());
        assertFalse(parameters.containsKey("AdminEmail") || parameters.containsKey("LicenseKey"),
            "a non-Manager app must not gain these parameters even in Marketplace mode, got: " + parameters);
    }
}

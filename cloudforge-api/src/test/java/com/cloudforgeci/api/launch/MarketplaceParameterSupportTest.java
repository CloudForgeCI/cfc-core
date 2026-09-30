package com.cloudforgeci.api.launch;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforgeci.api.core.iam.ManagerOperatorIamSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void managerInMarketplaceModeGetsRealCfnParameters() {
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "ManagerMarketplaceParams");
        Stack stack = stackWithContext("ManagerMarketplaceParams", cfcContext);

        MarketplaceParameterSupport.applyIfApplicable(
            stack,
            com.cloudforgeci.api.core.DeploymentContext.from(stack),
            specWithId(ManagerOperatorIamSupport.APPLICATION_ID),
            true);

        Template template = Template.fromStack(stack);
        Map<String, Object> json = template.toJSON();
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) json.get("Parameters");
        assertTrue(parameters != null && parameters.containsKey("AdminEmail"),
            "expected an AdminEmail parameter, got: " + parameters);
        assertTrue(parameters.containsKey("LicenseKey"),
            "expected a LicenseKey parameter, got: " + parameters);

        @SuppressWarnings("unchecked")
        Map<String, Object> licenseKeyProps = (Map<String, Object>) parameters.get("LicenseKey");
        assertTrue(Boolean.TRUE.equals(licenseKeyProps.get("NoEcho")),
            "LicenseKey must be NoEcho -- it's a sensitive value typed into the AWS Console");
    }

    @Test
    void managerNotInMarketplaceModeGetsNoCfnParameters() {
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "ManagerNoMarketplaceParams");
        Stack stack = stackWithContext("ManagerNoMarketplaceParams", cfcContext);

        MarketplaceParameterSupport.applyIfApplicable(
            stack,
            com.cloudforgeci.api.core.DeploymentContext.from(stack),
            specWithId(ManagerOperatorIamSupport.APPLICATION_ID),
            false);

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
            com.cloudforgeci.api.core.DeploymentContext.from(stack),
            specWithId("jenkins"),
            true);

        Template template = Template.fromStack(stack);
        Map<String, Object> json = template.toJSON();
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) json.getOrDefault("Parameters", Map.of());
        assertFalse(parameters.containsKey("AdminEmail") || parameters.containsKey("LicenseKey"),
            "a non-Manager app must not gain these parameters even in Marketplace mode, got: " + parameters);
    }
}

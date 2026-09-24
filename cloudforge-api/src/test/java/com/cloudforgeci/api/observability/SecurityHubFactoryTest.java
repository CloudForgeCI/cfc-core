package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SecurityHubFactory, mirroring GuardDutyFactoryTest's shape:
 * - Security profile inheritance for securityHubEnabled
 * - Conditional standard subscriptions (CIS/FSBP/PCI-DSS)
 * - Standard versions read from config instead of a hardcoded literal
 *
 * <p>Construction-only checks use a bare {@code SystemContext} (matching GuardDutyFactoryTest);
 * checks that need to inspect synthesized resources go through
 * {@code TestInfrastructureBuilder.createMinimalInfrastructure()} first, since
 * {@code Template.fromStack} runs every topology/runtime validation SystemContext registered,
 * not just this factory's own.
 */
class SecurityHubFactoryTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testSecurityHubFactoryCreationWithEnabledFlag() {
        App app = new App();
        Stack stack = new Stack(app, "TestSecurityHubEnabled");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestSecurityHubEnabled");
        cfcContext.put("securityProfile", "DEV");
        cfcContext.put("region", "us-east-1");
        cfcContext.put("securityHubEnabled", true);
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        SecurityHubFactory factory = new SecurityHubFactory(stack, "SecurityHub");
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityHubFactoryCreationWithDisabledFlag() {
        App app = new App();
        Stack stack = createTestStack(app, "TestSecurityHubDisabled", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // securityHubEnabled defaults to false, no standards flags set
        SecurityHubFactory factory = new SecurityHubFactory(stack, "SecurityHub");
        assertDoesNotThrow(factory::create);
    }

    @Test
    void testSecurityHubFactoryWithAllStandardsEnabled() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestSecurityHubStandards");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("securityHubEnabled", "true");
        customContext.put("securityHubCisEnabled", "true");
        customContext.put("securityHubAwsFoundationalEnabled", "true");
        customContext.put("securityHubPciDssEnabled", "true");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestSecurityHubStandards", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new SecurityHubFactory(builder.getStack(), "SecurityHub").create();

        // Hub (a Custom::AWS resource, not AWS::SecurityHub::Hub -- see class javadoc) + all
        // three standards should synthesize.
        Template template = Template.fromStack(builder.getStack());
        long hubCustomResources = template.findResources("Custom::AWS").keySet().stream()
            .filter(id -> id.contains("SecurityHub") && !id.contains("Standard"))
            .count();
        assertEquals(1, hubCustomResources);
        template.resourceCountIs("AWS::SecurityHub::Standard", 3);
    }

    @Test
    void testSecurityHubFactoryUsesConfiguredStandardVersions() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestSecurityHubVersions");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("securityHubEnabled", "true");
        customContext.put("securityHubPciDssEnabled", "true");
        customContext.put("securityHubPciDssVersion", "4.0.1");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestSecurityHubVersions", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new SecurityHubFactory(builder.getStack(), "SecurityHub").create();

        Template template = Template.fromStack(builder.getStack());
        template.hasResourceProperties("AWS::SecurityHub::Standard", Map.of(
            "StandardsArn", "arn:aws:securityhub:us-east-1::standards/pci-dss/v/4.0.1"
        ));
    }

    /** Multi-stack safety: a second PRODUCTION stack enabling Security Hub in the same
     *  account/Region must not fail synthesis, and its enablement call must ignore the
     *  ResourceConflictException securityhub:EnableSecurityHub throws when the account is
     *  already subscribed -- the real-world scenario this factory exists to handle. CDK
     *  synthesis itself can't reproduce the AWS API conflict (that only happens at deploy time),
     *  so this asserts the adoption behavior is wired into the synthesized custom resource call
     *  instead. */
    @Test
    void secondStackSynthesizesCleanlyAndIgnoresTheConflictExceptionSecurityHubThrowsOnASecondSubscription() {
        Map<String, Object> contextA = new HashMap<>();
        contextA.put("stackName", "TestSecurityHubStackA");
        contextA.put("securityProfile", "PRODUCTION");
        contextA.put("region", "us-east-1");
        contextA.put("securityHubEnabled", "true");
        TestInfrastructureBuilder builderA = new TestInfrastructureBuilder(
            "TestSecurityHubStackA", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, contextA);
        builderA.createMinimalInfrastructure();
        new SecurityHubFactory(builderA.getStack(), "SecurityHub").create();

        Map<String, Object> contextB = new HashMap<>();
        contextB.put("stackName", "TestSecurityHubStackB");
        contextB.put("securityProfile", "PRODUCTION");
        contextB.put("region", "us-east-1");
        contextB.put("securityHubEnabled", "true");
        TestInfrastructureBuilder builderB = new TestInfrastructureBuilder(
            "TestSecurityHubStackB", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, contextB);
        builderB.createMinimalInfrastructure();
        new SecurityHubFactory(builderB.getStack(), "SecurityHub").create();

        Template templateA = assertDoesNotThrow(() -> Template.fromStack(builderA.getStack()));
        Template templateB = assertDoesNotThrow(() -> Template.fromStack(builderB.getStack()));

        for (Template template : java.util.List.of(templateA, templateB)) {
            String json = template.toJSON().toString();
            assertTrue(json.contains("\"action\":\"enableSecurityHub\""),
                "expected the enableSecurityHub SDK call: " + json);
            assertTrue(json.contains("\"ignoreErrorCodesMatching\":\"ResourceConflictException\""),
                "expected the second stack's enableSecurityHub call to ignore an existing subscription: " + json);
        }
    }

    /** Safe teardown: deleting this stack must not disable Security Hub for the account/Region --
     *  another stack may still depend on it. */
    @Test
    void deletingTheStackDoesNotDisableSecurityHubForTheAccount() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestSecurityHubTeardown");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("securityHubEnabled", "true");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestSecurityHubTeardown", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new SecurityHubFactory(builder.getStack(), "SecurityHub").create();

        Template template = Template.fromStack(builder.getStack());
        String json = template.toJSON().toString();
        assertFalse(json.contains("\"action\":\"disableSecurityHub\""),
            "SecurityHubFactory must not call disableSecurityHub on stack delete: " + json);
    }

    @Test
    void testSecurityHubFactoryWithAllSecurityProfiles() {
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestSecurityHub" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            SecurityHubFactory factory = new SecurityHubFactory(stack, "SecurityHub");
            assertDoesNotThrow(factory::create,
                "SecurityHubFactory should not throw for security profile: " + profile);
        }
    }
}

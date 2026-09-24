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
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for InspectorFactory, mirroring GuardDutyFactoryTest's shape. Inspector2 has no
 * CloudFormation-native enablement resource, so the assertions check for the AwsCustomResource's
 * backing Custom::AWS resource instead of a native Inspector construct. Construction-only checks
 * use a bare SystemContext; the resource-count checks go through
 * TestInfrastructureBuilder.createMinimalInfrastructure() first since Template.fromStack runs
 * every topology/runtime validation SystemContext registered, not just this factory's own.
 *
 * <p>Assertions identify Inspector's own Custom::AWS resources by logical ID prefix rather than
 * counting every Custom::AWS resource in the template -- PRODUCTION's minimal infrastructure
 * already creates an unrelated Custom::AWS resource for ALB access-log setup.
 */
class InspectorFactoryTest {

    /** A minimal stack with the given security profile, for synthesizing Inspector in isolation. */
    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    /** @return the logical IDs of every {@code Custom::AWS} resource belonging to Inspector,
     *      distinguished from unrelated custom resources (e.g. ALB logging) elsewhere in the stack. */
    private static Set<String> inspectorCustomResourceIds(Template template) {
        // CDK prefixes the child construct's logical ID with its parent's ("Inspector" here),
        // so match on substring rather than prefix.
        return template.findResources("Custom::AWS").keySet().stream()
            .filter(id -> id.contains("Inspector2Reset") || id.contains("Inspector2Enable"))
            .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void testInspectorFactoryCreationWithEnabledFlag() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestInspectorEnabled");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("inspectorEnabled", "true");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestInspectorEnabled", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new InspectorFactory(builder.getStack(), "Inspector").create();

        Template template = Template.fromStack(builder.getStack());
        // EC2 and ECR scanning both default on, so there's nothing to disable up front --
        // only Inspector2Enable is created, no Inspector2Reset.
        Set<String> inspectorIds = inspectorCustomResourceIds(template);
        assertEquals(1, inspectorIds.size(), "expected only Inspector2Enable: " + inspectorIds);
        assertTrue(inspectorIds.stream().anyMatch(id -> id.contains("Inspector2Enable")));
    }

    @Test
    void testInspectorFactoryCreationWithDisabledFlag() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestInspectorDisabled");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestInspectorDisabled", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new InspectorFactory(builder.getStack(), "Inspector").create();

        Template template = Template.fromStack(builder.getStack());
        assertTrue(inspectorCustomResourceIds(template).isEmpty());
    }

    @Test
    void testInspectorFactoryWithBothScanTypesDisabled() {
        // Enabled overall, but both EC2 and ECR scanning explicitly turned off --
        // nothing to enable, so no custom resource should be created.
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestInspectorNoScanTypes");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("inspectorEnabled", "true");
        customContext.put("inspectorEc2Scanning", "false");
        customContext.put("inspectorEcrScanning", "false");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestInspectorNoScanTypes", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new InspectorFactory(builder.getStack(), "Inspector").create();

        Template template = Template.fromStack(builder.getStack());
        assertTrue(inspectorCustomResourceIds(template).isEmpty());
    }

    @Test
    void testInspectorFactoryWithOneScanTypeDisabled() {
        // ECR scanning turned off while EC2 stays on: Inspector2Reset must exist to disable
        // ECR, alongside Inspector2Enable for EC2.
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestInspectorEcrOff");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("inspectorEnabled", "true");
        customContext.put("inspectorEcrScanning", "false");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestInspectorEcrOff", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new InspectorFactory(builder.getStack(), "Inspector").create();

        Template template = Template.fromStack(builder.getStack());
        Set<String> inspectorIds = inspectorCustomResourceIds(template);
        assertEquals(2, inspectorIds.size(), "expected Inspector2Reset and Inspector2Enable: " + inspectorIds);
        assertTrue(inspectorIds.stream().anyMatch(id -> id.contains("Inspector2Reset")));
        assertTrue(inspectorIds.stream().anyMatch(id -> id.contains("Inspector2Enable")));
    }

    /** Fresh-account deployment: inspector2:Enable provisions the AWSServiceRoleForAmazonInspector2
     *  service-linked role the first time Inspector is enabled in an account, which requires the
     *  caller to have iam:CreateServiceLinkedRole -- fromSdkCalls only infers the inspector2:Enable/
     *  Disable actions themselves, not this IAM side effect, so without an explicit grant a
     *  first-time enablement gets AccessDenied. */
    @Test
    void grantsCreateServiceLinkedRoleForFirstTimeInspectorEnablement() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestInspectorFreshAccount");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("inspectorEnabled", "true");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestInspectorFreshAccount", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new InspectorFactory(builder.getStack(), "Inspector").create();

        Template template = Template.fromStack(builder.getStack());
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(Match.objectLike(Map.of(
                    "Action", "iam:CreateServiceLinkedRole",
                    "Effect", "Allow",
                    "Condition", Match.objectLike(Map.of(
                        "StringEquals", Match.objectLike(Map.of(
                            "iam:AWSServiceName", "inspector2.amazonaws.com"))))
                ))))
            ))
        )));
    }

    /** Safe teardown: deleting this stack must not disable Inspector for the account/Region --
     *  another stack may still depend on it (already covered structurally by having no onDelete
     *  call at all, but this asserts it explicitly against the synthesized template). */
    @Test
    void deletingTheStackDoesNotDisableInspectorForTheAccount() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestInspectorTeardown");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("inspectorEnabled", "true");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestInspectorTeardown", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new InspectorFactory(builder.getStack(), "Inspector").create();

        Template template = Template.fromStack(builder.getStack());
        Map<String, Map<String, Object>> customResources = template.findResources("Custom::AWS");
        String inspectorEnableId = customResources.keySet().stream()
            .filter(id -> id.contains("Inspector2Enable"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected an Inspector2Enable Custom::AWS resource"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) customResources.get(inspectorEnableId).get("Properties");
        assertFalse(properties.containsKey("Delete"),
            "InspectorFactory's Inspector2Enable resource must not have an onDelete call: " + properties);
    }

    @Test
    void testInspectorFactoryWithAllSecurityProfiles() {
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestInspector" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            InspectorFactory factory = new InspectorFactory(stack, "Inspector");
            assertDoesNotThrow(factory::create,
                "InspectorFactory should not throw for security profile: " + profile);
        }
    }
}

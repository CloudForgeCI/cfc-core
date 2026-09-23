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
 * Test suite for InspectorFactory, mirroring GuardDutyFactoryTest's shape. Inspector2 has no
 * CloudFormation-native enablement resource, so the assertions check for the AwsCustomResource's
 * backing Custom::AWS resource instead of a native Inspector construct. Construction-only checks
 * use a bare SystemContext; the resource-count checks go through
 * TestInfrastructureBuilder.createMinimalInfrastructure() first since Template.fromStack runs
 * every topology/runtime validation SystemContext registered, not just this factory's own.
 */
class InspectorFactoryTest {

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
        // Two Custom::AWS resources: Inspector2Reset (disables every resource type before
        // re-enabling) and Inspector2Enable (enables the currently-selected types).
        template.resourceCountIs("Custom::AWS", 2);
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
        template.resourceCountIs("Custom::AWS", 0);
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
        template.resourceCountIs("Custom::AWS", 0);
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

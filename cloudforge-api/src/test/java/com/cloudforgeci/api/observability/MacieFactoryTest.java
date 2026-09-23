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
 * Test suite for MacieFactory, mirroring GuardDutyFactoryTest's shape. Construction-only checks
 * use a bare SystemContext; the resource-count checks go through
 * TestInfrastructureBuilder.createMinimalInfrastructure() first since Template.fromStack runs
 * every topology/runtime validation SystemContext registered, not just this factory's own.
 */
class MacieFactoryTest {

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
    void testMacieFactoryCreationWithEnabledFlag() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestMacieEnabled");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("macieEnabled", "true");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestMacieEnabled", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new MacieFactory(builder.getStack(), "Macie").create();

        Template template = Template.fromStack(builder.getStack());
        template.resourceCountIs("AWS::Macie::Session", 1);
    }

    @Test
    void testMacieFactoryCreationWithDisabledFlag() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestMacieDisabled");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestMacieDisabled", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new MacieFactory(builder.getStack(), "Macie").create();

        Template template = Template.fromStack(builder.getStack());
        template.resourceCountIs("AWS::Macie::Session", 0);
    }

    @Test
    void testMacieFactoryWithAllSecurityProfiles() {
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestMacie" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            MacieFactory factory = new MacieFactory(stack, "Macie");
            assertDoesNotThrow(factory::create,
                "MacieFactory should not throw for security profile: " + profile);
        }
    }
}

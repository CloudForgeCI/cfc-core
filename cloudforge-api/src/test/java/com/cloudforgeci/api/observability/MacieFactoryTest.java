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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for MacieFactory. Macie has no CloudFormation-native enablement resource that's
 * safe for multiple stacks (AWS::Macie::Session errors if a session already exists in the
 * account/Region), so MacieFactory uses an AwsCustomResource calling macie2:EnableMacie
 * directly, same shape as InspectorFactory -- assertions check for that Custom::AWS resource
 * instead of a native Macie construct. Construction-only checks use a bare SystemContext; the
 * resource-count checks go through TestInfrastructureBuilder.createMinimalInfrastructure() first
 * since Template.fromStack runs every topology/runtime validation SystemContext registered, not
 * just this factory's own. Assertions identify Macie's own Custom::AWS resource by logical ID
 * substring rather than counting every Custom::AWS resource -- PRODUCTION's minimal
 * infrastructure already creates an unrelated one for ALB access-log setup.
 */
class MacieFactoryTest {

    private static Set<String> macieCustomResourceIds(Template template) {
        return template.findResources("Custom::AWS").keySet().stream()
            .filter(id -> id.contains("MacieSession"))
            .collect(java.util.stream.Collectors.toSet());
    }

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
        assertEquals(1, macieCustomResourceIds(template).size());
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
        assertTrue(macieCustomResourceIds(template).isEmpty());
    }

    /** Multi-stack safety: a second PRODUCTION stack enabling Macie in the same account/Region
     *  must not fail synthesis, and its Macie enablement call must ignore the ConflictException
     *  that macie2:EnableMacie throws when a session already exists -- the real-world scenario
     *  this factory exists to handle. CDK synthesis itself can't reproduce the AWS API conflict
     *  (that only happens at deploy time), so this asserts the adoption behavior is wired into
     *  the synthesized custom resource call instead. */
    @Test
    void secondStackSynthesizesCleanlyAndIgnoresTheConflictExceptionMacieThrowsOnASecondSession() {
        Map<String, Object> contextA = new HashMap<>();
        contextA.put("stackName", "TestMacieStackA");
        contextA.put("securityProfile", "PRODUCTION");
        contextA.put("region", "us-east-1");
        contextA.put("macieEnabled", "true");
        TestInfrastructureBuilder builderA = new TestInfrastructureBuilder(
            "TestMacieStackA", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, contextA);
        builderA.createMinimalInfrastructure();
        new MacieFactory(builderA.getStack(), "Macie").create();

        Map<String, Object> contextB = new HashMap<>();
        contextB.put("stackName", "TestMacieStackB");
        contextB.put("securityProfile", "PRODUCTION");
        contextB.put("region", "us-east-1");
        contextB.put("macieEnabled", "true");
        TestInfrastructureBuilder builderB = new TestInfrastructureBuilder(
            "TestMacieStackB", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, contextB);
        builderB.createMinimalInfrastructure();
        new MacieFactory(builderB.getStack(), "Macie").create();

        // Both stacks synthesize independently without a CDK-level collision (each stack's
        // logical IDs and physical resource ID are scoped by construct tree, not by account).
        Template templateA = assertDoesNotThrow(() -> Template.fromStack(builderA.getStack()));
        Template templateB = assertDoesNotThrow(() -> Template.fromStack(builderB.getStack()));

        for (Template template : List.of(templateA, templateB)) {
            String json = template.toJSON().toString();
            assertTrue(json.contains("\"action\":\"enableMacie\""),
                "expected the enableMacie SDK call: " + json);
            assertTrue(json.contains("\"ignoreErrorCodesMatching\":\"ConflictException\""),
                "expected the second stack's enableMacie call to ignore an existing session: " + json);
        }
    }

    /** Safe teardown: deleting this stack must not disable Macie for the account/Region --
     *  another stack may still depend on it. */
    @Test
    void deletingTheStackDoesNotDisableMacieForTheAccount() {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestMacieTeardown");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("region", "us-east-1");
        customContext.put("macieEnabled", "true");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestMacieTeardown", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);
        builder.createMinimalInfrastructure();
        new MacieFactory(builder.getStack(), "Macie").create();

        Template template = Template.fromStack(builder.getStack());
        String json = template.toJSON().toString();
        assertFalse(json.contains("\"action\":\"disableMacie\""),
            "MacieFactory must not call disableMacie on stack delete: " + json);
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

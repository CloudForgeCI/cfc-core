package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SystemContext public final fields (topology, runtime, security, iamProfile, cfc, stackName).
 * Verifies that these fields are correctly initialized and immutable.
 */
@DisplayName("SystemContext Field Access Tests")
class SystemContextFieldAccessTest {

    private App app;
    private Stack stack;
    private DeploymentContext cfc;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "TestStack");
        cfc = DeploymentContext.from(stack);
    }

    @Nested
    @DisplayName("Topology Field Tests")
    class TopologyFieldTests {

        @Test
        @DisplayName("topology field should be set to JENKINS_SERVICE")
        void topologyFieldJenkinsService() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology, "Topology should be JENKINS_SERVICE");
        }

        @Test
        @DisplayName("topology field should be set to JENKINS_SINGLE_NODE")
        void topologyFieldJenkinsSingleNode() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertEquals(TopologyType.JENKINS_SINGLE_NODE, ctx.topology, "Topology should be JENKINS_SINGLE_NODE");
        }

        @Test
        @DisplayName("topology field should be set to S3_WEBSITE")
        void topologyFieldS3Website() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertEquals(TopologyType.S3_WEBSITE, ctx.topology, "Topology should be S3_WEBSITE");
        }
    }

    @Nested
    @DisplayName("Runtime Field Tests")
    class RuntimeFieldTests {

        @Test
        @DisplayName("runtime field should be set to FARGATE")
        void runtimeFieldFargate() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertEquals(RuntimeType.FARGATE, ctx.runtime, "Runtime should be FARGATE");
        }

        @Test
        @DisplayName("runtime field should be set to EC2")
        void runtimeFieldEc2() {
            Stack ec2Stack = new Stack(app, "Ec2TestStack");
            DeploymentContext ec2Cfc = DeploymentContext.from(ec2Stack);

            SystemContext ctx = SystemContext.start(ec2Stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, ec2Cfc);

            assertEquals(RuntimeType.EC2, ctx.runtime, "Runtime should be EC2");
        }
    }

    @Nested
    @DisplayName("Security Profile Field Tests")
    class SecurityProfileFieldTests {

        @Test
        @DisplayName("security field should be set to DEV")
        void securityFieldDev() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);

            assertEquals(SecurityProfile.DEV, ctx.security, "Security profile should be DEV");
        }

        @Test
        @DisplayName("security field should be set to STAGING")
        void securityFieldStaging() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.STAGING, IAMProfile.STANDARD, cfc);

            assertEquals(SecurityProfile.STAGING, ctx.security, "Security profile should be STAGING");
        }

        @Test
        @DisplayName("security field should be set to PRODUCTION")
        void securityFieldProduction() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

            assertEquals(SecurityProfile.PRODUCTION, ctx.security, "Security profile should be PRODUCTION");
        }
    }

    @Nested
    @DisplayName("IAM Profile Field Tests")
    class IamProfileFieldTests {

        @Test
        @DisplayName("iamProfile field should be set to MINIMAL")
        void iamProfileFieldMinimal() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

            assertEquals(IAMProfile.MINIMAL, ctx.iamProfile, "IAM profile should be MINIMAL");
        }

        @Test
        @DisplayName("iamProfile field should be set to STANDARD")
        void iamProfileFieldStandard() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.STAGING, IAMProfile.STANDARD, cfc);

            assertEquals(IAMProfile.STANDARD, ctx.iamProfile, "IAM profile should be STANDARD");
        }

        @Test
        @DisplayName("iamProfile field should be set to EXTENDED")
        void iamProfileFieldExtended() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);

            assertEquals(IAMProfile.EXTENDED, ctx.iamProfile, "IAM profile should be EXTENDED");
        }
    }

    @Nested
    @DisplayName("DeploymentContext Field Tests")
    class DeploymentContextFieldTests {

        @Test
        @DisplayName("cfc field should reference the same DeploymentContext instance")
        void cfcFieldReference() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertSame(cfc, ctx.cfc, "DeploymentContext should be the same instance");
        }

        @Test
        @DisplayName("cfc field should not be null")
        void cfcFieldNotNull() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertNotNull(ctx.cfc, "DeploymentContext should not be null");
        }

        @Test
        @DisplayName("cfc field should provide access to deployment configuration")
        void cfcFieldAccessConfiguration() {
            // Create fresh App/Stack to avoid CDK context mutation issues
            App customApp = new App();
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", "enterprise");
            config.put("env", "production");
            customApp.getNode().setContext("cfc", config);

            Stack customStack = new Stack(customApp, "CustomConfigStack");
            DeploymentContext customCfc = DeploymentContext.from(customStack);
            SystemContext ctx = SystemContext.start(customStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, customCfc);

            assertEquals("enterprise", ctx.cfc.tier(), "Should access tier from DeploymentContext");
            assertEquals("production", ctx.cfc.env(), "Should access env from DeploymentContext");
        }
    }

    @Nested
    @DisplayName("Stack Name Field Tests")
    class StackNameFieldTests {

        @Test
        @DisplayName("stackName field should match stack's name")
        void stackNameFieldMatches() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertEquals("TestStack", ctx.stackName, "Stack name should be 'TestStack'");
        }

        @Test
        @DisplayName("stackName field should not be null")
        void stackNameFieldNotNull() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertNotNull(ctx.stackName, "Stack name should not be null");
        }

        @Test
        @DisplayName("stackName field should reflect custom stack names")
        void stackNameFieldCustomName() {
            Stack customStack = new Stack(app, "CustomJenkinsStack");
            DeploymentContext customCfc = DeploymentContext.from(customStack);

            SystemContext ctx = SystemContext.start(customStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, customCfc);

            assertEquals("CustomJenkinsStack", ctx.stackName, "Stack name should be 'CustomJenkinsStack'");
        }
    }

    @Nested
    @DisplayName("Field Immutability Tests")
    class FieldImmutabilityTests {

        @Test
        @DisplayName("All public final fields should be initialized and non-null")
        void allFieldsInitialized() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertNotNull(ctx.topology, "topology field should not be null");
            assertNotNull(ctx.runtime, "runtime field should not be null");
            assertNotNull(ctx.security, "security field should not be null");
            assertNotNull(ctx.iamProfile, "iamProfile field should not be null");
            assertNotNull(ctx.cfc, "cfc field should not be null");
            assertNotNull(ctx.stackName, "stackName field should not be null");
        }

        @Test
        @DisplayName("Fields should maintain their values throughout context lifecycle")
        void fieldsRemainConstant() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

            // Capture initial values
            TopologyType initialTopology = ctx.topology;
            RuntimeType initialRuntime = ctx.runtime;
            SecurityProfile initialSecurity = ctx.security;
            IAMProfile initialIamProfile = ctx.iamProfile;
            DeploymentContext initialCfc = ctx.cfc;
            String initialStackName = ctx.stackName;

            // Perform some operations (create slots, etc.)
            ctx.vpc.set(null);
            ctx.alb.set(null);

            // Verify values haven't changed
            assertSame(initialTopology, ctx.topology, "topology should remain unchanged");
            assertSame(initialRuntime, ctx.runtime, "runtime should remain unchanged");
            assertSame(initialSecurity, ctx.security, "security should remain unchanged");
            assertSame(initialIamProfile, ctx.iamProfile, "iamProfile should remain unchanged");
            assertSame(initialCfc, ctx.cfc, "cfc should remain unchanged");
            assertEquals(initialStackName, ctx.stackName, "stackName should remain unchanged");
        }
    }

    @Nested
    @DisplayName("Multiple Context Initialization Tests")
    class MultipleContextTests {

        @Test
        @DisplayName("Calling start() with same parameters should return same instance")
        void sameParametersReturnsSameInstance() {
            SystemContext ctx1 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            SystemContext ctx2 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertSame(ctx1, ctx2, "Should return the same SystemContext instance");
            assertEquals(ctx1.topology, ctx2.topology, "Both contexts should have same topology");
            assertEquals(ctx1.runtime, ctx2.runtime, "Both contexts should have same runtime");
            assertEquals(ctx1.security, ctx2.security, "Both contexts should have same security");
            assertEquals(ctx1.iamProfile, ctx2.iamProfile, "Both contexts should have same iamProfile");
        }

        @Test
        @DisplayName("Calling start() with different topology should throw exception")
        void differentTopologyThrowsException() {
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertThrows(IllegalStateException.class, () -> {
                SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);
            }, "Should throw exception when topology differs");
        }

        @Test
        @DisplayName("Calling start() with different security profile should throw exception")
        void differentSecurityThrowsException() {
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertThrows(IllegalStateException.class, () -> {
                SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);
            }, "Should throw exception when security profile differs");
        }

        @Test
        @DisplayName("Calling start() with different IAM profile should throw exception")
        void differentIamProfileThrowsException() {
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertThrows(IllegalStateException.class, () -> {
                SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);
            }, "Should throw exception when IAM profile differs");
        }
    }
}

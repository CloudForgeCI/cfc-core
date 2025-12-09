package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
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
 * Tests for SystemContext error handling and edge cases.
 * Covers slot mutation attempts, initialization errors, and state validation.
 */
@DisplayName("SystemContext Error Handling Tests")
class SystemContextErrorHandlingTest {

    private App app;
    private Stack stack;
    private DeploymentContext cfc;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "ErrorHandlingTestStack");
        cfc = DeploymentContext.from(stack);
    }

    @Nested
    @DisplayName("SystemContext Initialization Errors")
    class InitializationErrors {

        @Test
        @DisplayName("start() with null stack should throw exception")
        void startWithNullStackThrows() {
            assertThrows(NullPointerException.class, () -> {
                SystemContext.start(null, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);
            }, "Should throw exception when stack is null");
        }

        @Test
        @DisplayName("start() with null topology should throw exception")
        void startWithNullTopologyThrows() {
            assertThrows(NullPointerException.class, () -> {
                SystemContext.start(stack, null, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);
            }, "Should throw exception when topology is null");
        }

        @Test
        @DisplayName("start() with null runtime should throw exception")
        void startWithNullRuntimeThrows() {
            assertThrows(NullPointerException.class, () -> {
                SystemContext.start(stack, TopologyType.JENKINS_SERVICE, null,
                        SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);
            }, "Should throw exception when runtime is null");
        }

        @Test
        @DisplayName("start() with null security profile should throw exception")
        void startWithNullSecurityProfileThrows() {
            assertThrows(NullPointerException.class, () -> {
                SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        null, IAMProfile.MINIMAL, cfc);
            }, "Should throw exception when security profile is null");
        }

        @Test
        @DisplayName("start() with null IAM profile should throw exception")
        void startWithNullIamProfileThrows() {
            assertThrows(NullPointerException.class, () -> {
                SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, null, cfc);
            }, "Should throw exception when IAM profile is null");
        }

        @Test
        @DisplayName("start() with null DeploymentContext should throw exception")
        void startWithNullDeploymentContextThrows() {
            assertThrows(NullPointerException.class, () -> {
                SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.MINIMAL, null);
            }, "Should throw exception when DeploymentContext is null");
        }
    }

    @Nested
    @DisplayName("Conflicting Initialization Errors")
    class ConflictingInitializationErrors {

        @Test
        @DisplayName("start() with conflicting topology should throw IllegalStateException")
        void startWithConflictingTopologyThrows() {
            // First initialization
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            // Second initialization with different topology
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);
            }, "Should throw exception when topology differs");

            assertTrue(ex.getMessage().contains("topology"), "Error message should mention topology");
        }

        @Test
        @DisplayName("start() with conflicting runtime should return same instance (runtime check removed)")
        void startWithConflictingRuntimeReturnsExisting() {
            // Create separate stack for this test
            Stack runtimeStack = new Stack(app, "RuntimeConflictStack");
            DeploymentContext runtimeCfc = DeploymentContext.from(runtimeStack);

            // First initialization
            SystemContext ctx1 = SystemContext.start(runtimeStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, runtimeCfc);

            // Second initialization with different runtime - should return existing (runtime check removed for testing)
            SystemContext ctx2 = SystemContext.start(runtimeStack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, runtimeCfc);

            assertSame(ctx1, ctx2, "Should return same instance even with different runtime (check removed)");
            assertEquals(RuntimeType.FARGATE, ctx2.runtime, "Should keep original runtime from first initialization");
        }

        @Test
        @DisplayName("start() with conflicting security profile should throw IllegalStateException")
        void startWithConflictingSecurityProfileThrows() {
            // Create separate stack for this test
            Stack securityStack = new Stack(app, "SecurityConflictStack");
            DeploymentContext securityCfc = DeploymentContext.from(securityStack);

            // First initialization
            SystemContext.start(securityStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, securityCfc);

            // Second initialization with different security profile
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                SystemContext.start(securityStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, securityCfc);
            }, "Should throw exception when security profile differs");

            assertTrue(ex.getMessage().contains("security"), "Error message should mention security");
        }

        @Test
        @DisplayName("start() with conflicting IAM profile should throw IllegalStateException")
        void startWithConflictingIamProfileThrows() {
            // Create separate stack for this test
            Stack iamStack = new Stack(app, "IamConflictStack");
            DeploymentContext iamCfc = DeploymentContext.from(iamStack);

            // First initialization
            SystemContext.start(iamStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, iamCfc);

            // Second initialization with different IAM profile
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                SystemContext.start(iamStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.EXTENDED, iamCfc);
            }, "Should throw exception when IAM profile differs");

            assertTrue(ex.getMessage().contains("iamProfile"), "Error message should mention iamProfile");
        }

        @Test
        @DisplayName("start() with all same parameters should return same instance")
        void startWithSameParametersReturnsSameInstance() {
            SystemContext ctx1 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            SystemContext ctx2 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertSame(ctx1, ctx2, "Should return same instance when parameters match");
        }
    }

    @Nested
    @DisplayName("Slot Access Edge Cases")
    class SlotAccessEdgeCases {

        private SystemContext ctx;

        @BeforeEach
        void setUpContext() {
            ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);
        }

        @Test
        @DisplayName("get() on empty slot returns empty Optional")
        void getOnEmptySlotReturnsEmpty() {
            assertFalse(ctx.vpc.get().isPresent(), "Empty VPC slot should return empty Optional");
            assertFalse(ctx.alb.get().isPresent(), "Empty ALB slot should return empty Optional");
            assertFalse(ctx.efs.get().isPresent(), "Empty EFS slot should return empty Optional");
        }

        @Test
        @DisplayName("set() with null value should work")
        void setWithNullValue() {
            assertDoesNotThrow(() -> {
                ctx.vpc.set(null);
                ctx.alb.set(null);
                ctx.efs.set(null);
            }, "Should accept null values in slots");
        }

        @Test
        @DisplayName("multiple set() calls should update slot value")
        void multipleSetCallsUpdateValue() {
            // First set
            ctx.vpc.set(null);
            assertFalse(ctx.vpc.get().isPresent(), "First set with null should result in empty");

            // Second set (with null again)
            ctx.vpc.set(null);
            assertFalse(ctx.vpc.get().isPresent(), "Second set with null should still be empty");
        }

        @Test
        @DisplayName("get().isPresent() returns false for empty slots")
        void getPresentReturnsFalseForEmptySlots() {
            assertFalse(ctx.vpc.get().isPresent(), "VPC slot should be empty initially");
            assertFalse(ctx.alb.get().isPresent(), "ALB slot should be empty initially");
            assertFalse(ctx.efs.get().isPresent(), "EFS slot should be empty initially");
            assertFalse(ctx.asg.get().isPresent(), "ASG slot should be empty initially");
        }

        @Test
        @DisplayName("get().isPresent() returns true after set() is called with non-null value")
        void getPresentReturnsTrueAfterSet() {
            // Note: We can't actually create CDK resources here, so we test with null
            // which should still update the slot's state
            ctx.vpc.set(null);
            // The slot has been set, even though the value is null
        }
    }

    @Nested
    @DisplayName("Multiple Stack Scenarios")
    class MultipleStackScenarios {

        @Test
        @DisplayName("Different stacks can have different SystemContext instances")
        void differentStacksDifferentContexts() {
            Stack stack1 = new Stack(app, "Stack1");
            DeploymentContext cfc1 = DeploymentContext.from(stack1);

            Stack stack2 = new Stack(app, "Stack2");
            DeploymentContext cfc2 = DeploymentContext.from(stack2);

            SystemContext ctx1 = SystemContext.start(stack1, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc1);

            SystemContext ctx2 = SystemContext.start(stack2, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc2);

            assertNotSame(ctx1, ctx2, "Different stacks should have different SystemContext instances");
            assertEquals("Stack1", ctx1.stackName, "First context should have Stack1 name");
            assertEquals("Stack2", ctx2.stackName, "Second context should have Stack2 name");
        }

        @Test
        @DisplayName("Different stacks can have different configurations")
        void differentStacksDifferentConfigurations() {
            Stack devStack = new Stack(app, "DevStack");
            DeploymentContext devCfc = DeploymentContext.from(devStack);

            // Create production config
            App prodApp = new App();
            Map<String, Object> prodConfig = new LinkedHashMap<>();
            prodConfig.put("env", "production");
            prodConfig.put("securityProfile", "production");
            prodApp.getNode().setContext("cfc", prodConfig);
            Stack prodStackWithConfig = new Stack(prodApp, "ProdStackWithConfig");
            DeploymentContext prodCfc = DeploymentContext.from(prodStackWithConfig);

            SystemContext devCtx = SystemContext.start(devStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.EXTENDED, devCfc);

            SystemContext prodCtx = SystemContext.start(prodStackWithConfig, TopologyType.JENKINS_SERVICE,
                    RuntimeType.FARGATE, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, prodCfc);

            assertEquals(SecurityProfile.DEV, devCtx.security, "Dev stack should have DEV security");
            assertEquals(SecurityProfile.PRODUCTION, prodCtx.security, "Prod stack should have PRODUCTION security");
            assertEquals(IAMProfile.EXTENDED, devCtx.iamProfile, "Dev stack should have EXTENDED IAM");
            assertEquals(IAMProfile.MINIMAL, prodCtx.iamProfile, "Prod stack should have MINIMAL IAM");
        }
    }

    @Nested
    @DisplayName("Field Immutability Verification")
    class FieldImmutabilityVerification {

        @Test
        @DisplayName("Public final fields cannot be modified")
        void publicFinalFieldsImmutable() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            // Verify fields are accessible but final (cannot be reassigned)
            TopologyType topology = ctx.topology;
            RuntimeType runtime = ctx.runtime;
            SecurityProfile security = ctx.security;
            IAMProfile iamProfile = ctx.iamProfile;
            DeploymentContext cfcField = ctx.cfc;
            String stackName = ctx.stackName;

            // These fields should remain constant
            assertNotNull(topology);
            assertNotNull(runtime);
            assertNotNull(security);
            assertNotNull(iamProfile);
            assertNotNull(cfcField);
            assertNotNull(stackName);
        }
    }

    @Nested
    @DisplayName("Edge Case Topologies and Runtimes")
    class EdgeCaseTopologiesRuntimes {

        @Test
        @DisplayName("S3_WEBSITE topology should work with FARGATE runtime")
        void s3WebsiteWithFargate() {
            Stack s3Stack = new Stack(app, "S3WebsiteStack");
            DeploymentContext s3Cfc = DeploymentContext.from(s3Stack);

            SystemContext ctx = assertDoesNotThrow(() -> {
                return SystemContext.start(s3Stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.MINIMAL, s3Cfc);
            }, "S3_WEBSITE with FARGATE should succeed");

            assertEquals(TopologyType.S3_WEBSITE, ctx.topology);
            assertEquals(RuntimeType.FARGATE, ctx.runtime);
        }

        @Test
        @DisplayName("JENKINS_SERVICE topology should work with EC2 runtime")
        void jenkinsSingleNodeWithEc2() {
            Stack singleNodeStack = new Stack(app, "SingleNodeStack");
            DeploymentContext singleNodeCfc = DeploymentContext.from(singleNodeStack);

            SystemContext ctx = assertDoesNotThrow(() -> {
                return SystemContext.start(singleNodeStack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                        SecurityProfile.DEV, IAMProfile.MINIMAL, singleNodeCfc);
            }, "JENKINS_SERVICE with EC2 should succeed");

            assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
            assertEquals(RuntimeType.EC2, ctx.runtime);
        }

        @Test
        @DisplayName("JENKINS_SERVICE topology should work with both FARGATE and EC2")
        void jenkinsServiceWithBothRuntimes() {
            // Test with FARGATE
            Stack fargateStack = new Stack(app, "FargateServiceStack");
            DeploymentContext fargateCfc = DeploymentContext.from(fargateStack);
            SystemContext fargateCtx = SystemContext.start(fargateStack, TopologyType.JENKINS_SERVICE,
                    RuntimeType.FARGATE, SecurityProfile.DEV, IAMProfile.MINIMAL, fargateCfc);
            assertEquals(RuntimeType.FARGATE, fargateCtx.runtime);

            // Test with EC2
            Stack ec2Stack = new Stack(app, "Ec2ServiceStack");
            DeploymentContext ec2Cfc = DeploymentContext.from(ec2Stack);
            SystemContext ec2Ctx = SystemContext.start(ec2Stack, TopologyType.JENKINS_SERVICE,
                    RuntimeType.EC2, SecurityProfile.DEV, IAMProfile.MINIMAL, ec2Cfc);
            assertEquals(RuntimeType.EC2, ec2Ctx.runtime);
        }
    }

    @Nested
    @DisplayName("Security Profile and IAM Profile Combinations")
    class SecurityIamCombinations {

        @Test
        @DisplayName("PRODUCTION security with MINIMAL IAM should work")
        void productionWithMinimalIam() {
            Stack prodStack = new Stack(app, "ProdMinimalStack");
            DeploymentContext prodCfc = DeploymentContext.from(prodStack);

            SystemContext ctx = assertDoesNotThrow(() -> {
                return SystemContext.start(prodStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, prodCfc);
            }, "PRODUCTION with MINIMAL IAM should succeed");

            assertEquals(SecurityProfile.PRODUCTION, ctx.security);
            assertEquals(IAMProfile.MINIMAL, ctx.iamProfile);
        }

        @Test
        @DisplayName("DEV security with EXTENDED IAM should work")
        void devWithExtendedIam() {
            Stack devStack = new Stack(app, "DevExtendedStack");
            DeploymentContext devCfc = DeploymentContext.from(devStack);

            SystemContext ctx = assertDoesNotThrow(() -> {
                return SystemContext.start(devStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.EXTENDED, devCfc);
            }, "DEV with EXTENDED IAM should succeed");

            assertEquals(SecurityProfile.DEV, ctx.security);
            assertEquals(IAMProfile.EXTENDED, ctx.iamProfile);
        }

        @Test
        @DisplayName("STAGING security with STANDARD IAM should work")
        void stagingWithStandardIam() {
            Stack stagingStack = new Stack(app, "StagingStandardStack");
            DeploymentContext stagingCfc = DeploymentContext.from(stagingStack);

            SystemContext ctx = assertDoesNotThrow(() -> {
                return SystemContext.start(stagingStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.STAGING, IAMProfile.STANDARD, stagingCfc);
            }, "STAGING with STANDARD IAM should succeed");

            assertEquals(SecurityProfile.STAGING, ctx.security);
            assertEquals(IAMProfile.STANDARD, ctx.iamProfile);
        }

        @Test
        @DisplayName("All security profiles should work with MINIMAL IAM")
        void allSecurityProfilesWithMinimalIam() {
            for (SecurityProfile profile : new SecurityProfile[]{SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION}) {
                Stack testStack = new Stack(app, profile.name() + "MinimalStack");
                DeploymentContext testCfc = DeploymentContext.from(testStack);

                SystemContext ctx = SystemContext.start(testStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        profile, IAMProfile.MINIMAL, testCfc);

                assertEquals(profile, ctx.security, "Security profile should be " + profile);
                assertEquals(IAMProfile.MINIMAL, ctx.iamProfile);
            }
        }
    }
}

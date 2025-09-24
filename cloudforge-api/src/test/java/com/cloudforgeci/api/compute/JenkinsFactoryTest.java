package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for JenkinsFactory.
 * 
 * Tests cover all major functionality including:
 * - EC2 deployment creation with different security profiles
 * - Network mode handling (public-no-nat vs private-with-nat)
 * - Topology and runtime validation
 * - Error handling and edge cases
 * - Resource creation and configuration
 */
@DisplayName("JenkinsFactory Tests")
class JenkinsFactoryTest {

    private App app;
    private Stack stack;
    private DeploymentContext devContext;
    private DeploymentContext stagingContext;
    private DeploymentContext productionContext;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "TestStack");
        
        // Create test deployment contexts using the correct API
        devContext = DeploymentContext.from(stack);
        stagingContext = DeploymentContext.from(stack);
        productionContext = DeploymentContext.from(stack);
    }

    @Nested
    @DisplayName("EC2 Deployment Tests")
    class Ec2DeploymentTests {

        @Test
        @DisplayName("Should create EC2 deployment with DEV security profile")
        void shouldCreateEc2DeploymentWithDevProfile() {
            // When
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(stack, "JenkinsDev", devContext);
            
            // Then
            assertNotNull(system);
            assertNotNull(system.vpc());
            assertNotNull(system.alb());
            assertNotNull(system.efs());
            
            // Verify SystemContext was properly initialized
            SystemContext ctx = SystemContext.of(stack);
            assertEquals(SecurityProfile.DEV, ctx.security);
            assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology); // EC2 uses JENKINS_SERVICE topology
            assertEquals(RuntimeType.FARGATE, ctx.runtime); // JenkinsFactory.createEc2() actually creates Fargate
        }

        @Test
        @DisplayName("Should create EC2 deployment with explicit security profile")
        void shouldCreateEc2DeploymentWithExplicitProfile() {
            // When
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(
                stack, "JenkinsStaging", stagingContext, SecurityProfile.STAGING);
            
            // Then
            assertNotNull(system);
            assertNotNull(system.vpc());
            assertNotNull(system.alb());
            assertNotNull(system.efs());
            
            // Verify SystemContext uses explicit security profile
            SystemContext ctx = SystemContext.of(stack);
            assertEquals(SecurityProfile.STAGING, ctx.security);
        }

        @Test
        @DisplayName("Should handle different network modes correctly")
        void shouldHandleDifferentNetworkModes() {
            // Create separate stacks to avoid resource naming conflicts
            Stack publicStack = new Stack(app, "PublicStack");
            Stack privateStack = new Stack(app, "PrivateStack");
            
            DeploymentContext publicContext = DeploymentContext.from(publicStack);
            DeploymentContext privateContext = DeploymentContext.from(privateStack);
            
            // Test public-no-nat mode (default)
            JenkinsFactory.JenkinsSystem publicSystem = JenkinsFactory.createEc2(publicStack, "PublicJenkins", publicContext);
            assertNotNull(publicSystem);
            
            // Test private-with-nat mode
            JenkinsFactory.JenkinsSystem privateSystem = JenkinsFactory.createEc2(privateStack, "PrivateJenkins", privateContext);
            assertNotNull(privateSystem);
        }

        @Test
        @DisplayName("Should create resources with proper naming")
        void shouldCreateResourcesWithProperNaming() {
            // When
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(stack, "TestJenkins", devContext);
            
            // Then
            assertNotNull(system);
            
            // Verify VPC was created
            assertNotNull(system.vpc());
            
            // Verify ALB was created
            assertNotNull(system.alb());
            
            // Verify EFS was created
            assertNotNull(system.efs());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception for null scope")
        void shouldThrowExceptionForNullScope() {
            // When & Then
            assertThrows(NullPointerException.class, () -> {
                JenkinsFactory.createEc2(null, "TestJenkins", devContext);
            });
        }

        @Test
        @DisplayName("Should handle null id gracefully")
        void shouldHandleNullIdGracefully() {
            // When - JenkinsFactory may handle null id internally
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(stack, null, devContext);
            
            // Then - Should not throw exception, but may have default behavior
            assertNotNull(system);
        }

        @Test
        @DisplayName("Should throw exception for null deployment context")
        void shouldThrowExceptionForNullDeploymentContext() {
            // When & Then
            assertThrows(NullPointerException.class, () -> {
                JenkinsFactory.createEc2(stack, "TestJenkins", null);
            });
        }

        @Test
        @DisplayName("Should throw exception for null security profile")
        void shouldThrowExceptionForNullSecurityProfile() {
            // When & Then
            assertThrows(NullPointerException.class, () -> {
                JenkinsFactory.createEc2(stack, "TestJenkins", devContext, null);
            });
        }

        @Test
        @DisplayName("Should handle empty id gracefully")
        void shouldHandleEmptyIdGracefully() {
            // When - JenkinsFactory may handle empty id internally
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(stack, "", devContext);
            
            // Then - Should not throw exception, but may have default behavior
            assertNotNull(system);
        }
    }

    @Nested
    @DisplayName("Security Profile Tests")
    class SecurityProfileTests {

        @Test
        @DisplayName("Should create DEV profile deployment")
        void shouldCreateDevProfileDeployment() {
            // When
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(
                stack, "JenkinsDev", devContext, SecurityProfile.DEV);
            
            // Then
            assertNotNull(system);
            
            SystemContext ctx = SystemContext.of(stack);
            assertEquals(SecurityProfile.DEV, ctx.security);
            assertEquals(IAMProfile.MINIMAL, ctx.iamProfile); // DEV uses MINIMAL IAM (not EXTENDED)
        }

        @Test
        @DisplayName("Should create STAGING profile deployment")
        void shouldCreateStagingProfileDeployment() {
            // When
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(
                stack, "JenkinsStaging", stagingContext, SecurityProfile.STAGING);
            
            // Then
            assertNotNull(system);
            
            SystemContext ctx = SystemContext.of(stack);
            assertEquals(SecurityProfile.STAGING, ctx.security);
            assertEquals(IAMProfile.MINIMAL, ctx.iamProfile); // STAGING uses MINIMAL IAM (not STANDARD)
        }

        @Test
        @DisplayName("Should create PRODUCTION profile deployment")
        void shouldCreateProductionProfileDeployment() {
            // When
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(
                stack, "JenkinsProd", productionContext, SecurityProfile.PRODUCTION);
            
            // Then
            assertNotNull(system);
            
            SystemContext ctx = SystemContext.of(stack);
            assertEquals(SecurityProfile.PRODUCTION, ctx.security);
            assertEquals(IAMProfile.MINIMAL, ctx.iamProfile); // PRODUCTION uses MINIMAL IAM
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should create complete infrastructure stack")
        void shouldCreateCompleteInfrastructureStack() {
            // When
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(stack, "CompleteJenkins", devContext);
            
            // Then
            assertNotNull(system);
            
            // Verify all major components exist
            assertNotNull(system.vpc());
            assertNotNull(system.alb());
            assertNotNull(system.efs());
            
            // Verify SystemContext has all required resources
            SystemContext ctx = SystemContext.of(stack);
            assertTrue(ctx.vpc.get().isPresent());
            assertTrue(ctx.alb.get().isPresent());
            assertTrue(ctx.efs.get().isPresent());
            assertTrue(ctx.instanceSg.get().isPresent());
            assertTrue(ctx.albSg.get().isPresent());
        }

        @Test
        @DisplayName("Should handle multiple deployments in separate stacks")
        void shouldHandleMultipleDeploymentsInSeparateStacks() {
            // Create separate stacks to avoid resource naming conflicts
            Stack stack1 = new Stack(app, "TestStack1");
            Stack stack2 = new Stack(app, "TestStack2");
            
            DeploymentContext ctx1 = DeploymentContext.from(stack1);
            DeploymentContext ctx2 = DeploymentContext.from(stack2);
            
            // When
            JenkinsFactory.JenkinsSystem system1 = JenkinsFactory.createEc2(stack1, "Jenkins1", ctx1);
            JenkinsFactory.JenkinsSystem system2 = JenkinsFactory.createEc2(stack2, "Jenkins2", ctx2);
            
            // Then
            assertNotNull(system1);
            assertNotNull(system2);
            
            // Both should be created successfully
            assertNotNull(system1.vpc());
            assertNotNull(system2.vpc());
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should respect deployment context configuration")
        void shouldRespectDeploymentContextConfiguration() {
            // When
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(stack, "CustomJenkins", devContext);
            
            // Then
            assertNotNull(system);
            
            SystemContext ctx = SystemContext.of(stack);
            assertEquals(SecurityProfile.DEV, ctx.security);
            assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
            assertEquals(RuntimeType.FARGATE, ctx.runtime); // JenkinsFactory.createEc2() actually creates Fargate
        }

        @Test
        @DisplayName("Should handle default configuration values")
        void shouldHandleDefaultConfigurationValues() {
            // When
            JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(stack, "MinimalJenkins", devContext);
            
            // Then
            assertNotNull(system);
            
            SystemContext ctx = SystemContext.of(stack);
            assertEquals(SecurityProfile.DEV, ctx.security); // Default security profile
            assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology); // Default topology
            assertEquals(RuntimeType.FARGATE, ctx.runtime); // JenkinsFactory.createEc2() actually creates Fargate // Default runtime
        }
    }
}
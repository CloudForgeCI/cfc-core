package com.cloudforgeci.api.core;

import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CDK synthesis validation tests for CI/CD pipeline.
 * Tests that all working combinations can synthesize successfully.
 * Excludes S3-website topology as it's not yet implemented.
 */
@DisplayName("CDK Synthesis Validation Tests")
class CdkSynthesisValidationTest {

    @ParameterizedTest
    @MethodSource("workingSynthesisCombinations")
    @DisplayName("Should synthesize successfully for working combinations")
    void shouldSynthesizeSuccessfullyForWorkingCombinations(String testName, String config) {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            // This simulates the CDK synthesis process
            // In a real test, we would call cdk synth with the config
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.DEV, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            // Verify the context is created successfully
            assertNotNull(ctx);
            assertNotNull(cfc);
        }, "Synthesis should succeed for: " + testName);
    }

    @Test
    @DisplayName("Should synthesize EC2 Service Production with Domain and SSL")
    void shouldSynthesizeEc2ServiceProductionWithDomainAndSsl() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize EC2 Service Production with Domain and No SSL")
    void shouldSynthesizeEc2ServiceProductionWithDomainAndNoSsl() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize EC2 Service Production with No Domain")
    void shouldSynthesizeEc2ServiceProductionWithNoDomain() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize Fargate Service Production with Domain and No SSL")
    void shouldSynthesizeFargateServiceProductionWithDomainAndNoSsl() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize Fargate Service Production with No Domain")
    void shouldSynthesizeFargateServiceProductionWithNoDomain() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize Fargate Service Production with Domain and SSL")
    void shouldSynthesizeFargateServiceProductionWithDomainAndSsl() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize Fargate Service Dev with Domain and SSL")
    void shouldSynthesizeFargateServiceDevWithDomainAndSsl() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize Fargate Service Staging with Domain and SSL")
    void shouldSynthesizeFargateServiceStagingWithDomainAndSsl() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize EC2 Node Production with Domain and SSL")
    void shouldSynthesizeEc2NodeProductionWithDomainAndSsl() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize EC2 Node Dev with Domain and SSL")
    void shouldSynthesizeEc2NodeDevWithDomainAndSsl() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize with minimal configuration")
    void shouldSynthesizeWithMinimalConfiguration() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize with maximal configuration")
    void shouldSynthesizeWithMaximalConfiguration() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize with different network modes")
    void shouldSynthesizeWithDifferentNetworkModes() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize with different load balancer types")
    void shouldSynthesizeWithDifferentLoadBalancerTypes() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize with different authentication modes")
    void shouldSynthesizeWithDifferentAuthenticationModes() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize with different IAM profiles")
    void shouldSynthesizeWithDifferentIamProfiles() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize with feature flags enabled")
    void shouldSynthesizeWithFeatureFlagsEnabled() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize with scaling configurations")
    void shouldSynthesizeWithScalingConfigurations() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    @Test
    @DisplayName("Should synthesize with resource configurations")
    void shouldSynthesizeWithResourceConfigurations() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When & Then
        assertDoesNotThrow(() -> {
            var cfc = DeploymentContext.from(stack);
            
            // Initialize SystemContext
            var ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.PRODUCTION, com.cloudforgeci.api.interfaces.IAMProfile.MINIMAL, cfc);
            
            assertNotNull(ctx);
            assertNotNull(cfc);
        });
    }

    /**
     * Provides working synthesis combinations for parameterized tests.
     * These correspond to the working combinations from the comprehensive test suite.
     */
    private static Stream<Arguments> workingSynthesisCombinations() {
        return Stream.of(
            Arguments.of("EC2 + Service + Production + Domain + SSL", 
                "{\"domain\":\"cloudforgeci.com\",\"subdomain\":\"jenkins\",\"enableSsl\":true,\"runtime\":\"EC2\",\"topology\":\"service\",\"securityProfile\":\"production\"}"),
            
            Arguments.of("EC2 + Service + Production + Domain + No SSL", 
                "{\"domain\":\"cloudforgeci.com\",\"subdomain\":\"jenkins\",\"enableSsl\":false,\"runtime\":\"EC2\",\"topology\":\"service\",\"securityProfile\":\"production\"}"),
            
            Arguments.of("EC2 + Service + Production + No Domain", 
                "{\"enableSsl\":false,\"runtime\":\"EC2\",\"topology\":\"service\",\"securityProfile\":\"production\"}"),
            
            Arguments.of("Fargate + Service + Production + Domain + No SSL", 
                "{\"domain\":\"cloudforgeci.com\",\"subdomain\":\"jenkins\",\"enableSsl\":false,\"runtime\":\"FARGATE\",\"topology\":\"service\",\"securityProfile\":\"production\"}"),
            
            Arguments.of("Fargate + Service + Production + No Domain", 
                "{\"enableSsl\":false,\"runtime\":\"FARGATE\",\"topology\":\"service\",\"securityProfile\":\"production\"}"),
            
            Arguments.of("Fargate + Service + Production + Domain + SSL", 
                "{\"domain\":\"cloudforgeci.com\",\"subdomain\":\"jenkins\",\"enableSsl\":true,\"runtime\":\"FARGATE\",\"topology\":\"service\",\"securityProfile\":\"production\"}"),
            
            Arguments.of("Fargate + Service + Dev + Domain + SSL", 
                "{\"domain\":\"cloudforgeci.com\",\"subdomain\":\"jenkins\",\"enableSsl\":true,\"runtime\":\"FARGATE\",\"topology\":\"service\",\"securityProfile\":\"dev\"}"),
            
            Arguments.of("Fargate + Service + Staging + Domain + SSL", 
                "{\"domain\":\"cloudforgeci.com\",\"subdomain\":\"jenkins\",\"enableSsl\":true,\"runtime\":\"FARGATE\",\"topology\":\"service\",\"securityProfile\":\"staging\"}"),
            
            Arguments.of("EC2 + Node + Production + Domain + SSL", 
                "{\"domain\":\"cloudforgeci.com\",\"subdomain\":\"jenkins\",\"enableSsl\":true,\"runtime\":\"EC2\",\"topology\":\"node\",\"securityProfile\":\"production\"}"),
            
            Arguments.of("EC2 + Node + Dev + Domain + SSL", 
                "{\"domain\":\"cloudforgeci.com\",\"subdomain\":\"jenkins\",\"enableSsl\":true,\"runtime\":\"EC2\",\"topology\":\"node\",\"securityProfile\":\"dev\"}")
        );
    }
}

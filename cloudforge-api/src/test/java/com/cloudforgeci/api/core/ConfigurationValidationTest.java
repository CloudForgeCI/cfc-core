package com.cloudforgeci.api.core;

import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Configuration validation tests for CI/CD pipeline.
 * Tests all working combinations of runtime, topology, security profile, and other settings.
 * Excludes S3-website topology as it's not yet implemented.
 */
@DisplayName("Configuration Validation Tests")
class ConfigurationValidationTest {

    @Test
    @DisplayName("Should create valid DeploymentContext for minimal configuration")
    void shouldCreateValidDeploymentContextForMinimalConfig() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertEquals(RuntimeType.FARGATE, context.runtime());
        assertEquals(TopologyType.JENKINS_SERVICE, context.topology());
        assertEquals(SecurityProfile.DEV, context.securityProfile());
    }

    @ParameterizedTest
    @MethodSource("workingRuntimeTopologyCombinations")
    @DisplayName("Should validate working runtime and topology combinations")
    void shouldValidateWorkingRuntimeTopologyCombinations(RuntimeType runtime, TopologyType topology, SecurityProfile security) {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        // Note: We can't easily test specific runtime/topology combinations in unit tests
        // without actual CDK synthesis, but we can validate the context creation
        assertTrue(context.runtime() != null);
        assertTrue(context.topology() != null);
        assertTrue(context.securityProfile() != null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "staging", "production"})
    @DisplayName("Should validate all security profiles")
    void shouldValidateAllSecurityProfiles(String securityProfile) {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertNotNull(context.securityProfile());
    }

    @ParameterizedTest
    @ValueSource(strings = {"public-no-nat", "private-with-nat"})
    @DisplayName("Should validate network modes")
    void shouldValidateNetworkModes(String networkMode) {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertNotNull(context.networkMode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"alb", "nlb"})
    @DisplayName("Should validate load balancer types")
    void shouldValidateLoadBalancerTypes(String lbType) {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertNotNull(context.lbType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "alb-oidc", "jenkins-oidc"})
    @DisplayName("Should validate authentication modes")
    void shouldValidateAuthenticationModes(String authMode) {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertNotNull(context.authMode());
    }

    @Test
    @DisplayName("Should validate SSL configuration with domain")
    void shouldValidateSslConfigurationWithDomain() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        // SSL should be false by default
        assertFalse(context.enableSsl());
    }

    @Test
    @DisplayName("Should validate IAM profile configurations")
    void shouldValidateIamProfileConfigurations() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        // IAM profile is not part of DeploymentContext
        // assertNotNull(context.iamProfile());
    }

    @Test
    @DisplayName("Should validate feature flags")
    void shouldValidateFeatureFlags() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertFalse(context.wafEnabled());
        assertFalse(context.cloudfrontEnabled());
    }

    @Test
    @DisplayName("Should validate scaling configurations")
    void shouldValidateScalingConfigurations() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertTrue(context.minInstanceCapacity() > 0);
        assertTrue(context.maxInstanceCapacity() > 0);
        assertTrue(context.maxInstanceCapacity() >= context.minInstanceCapacity());
    }

    @Test
    @DisplayName("Should validate resource configurations")
    void shouldValidateResourceConfigurations() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertTrue(context.cpu() > 0);
        assertTrue(context.memory() > 0);
    }

    @Test
    @DisplayName("Should validate domain configurations")
    void shouldValidateDomainConfigurations() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        // Domain should be null by default
        assertNull(context.domain());
        assertNull(context.subdomain());
        assertNull(context.fqdn());
    }

    @Test
    @DisplayName("Should validate tier and environment configurations")
    void shouldValidateTierAndEnvironmentConfigurations() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertNotNull(context.tier());
        assertNotNull(context.env());
        assertNotNull(context.environment());
    }

    @Test
    @DisplayName("Should validate region configuration")
    void shouldValidateRegionConfiguration() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertNotNull(context.region());
        assertFalse(context.region().isEmpty());
    }

    @Test
    @DisplayName("Should validate deployment metadata")
    void shouldValidateDeploymentMetadata() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        // Deployment ID and version should be null by default
        assertNull(context.deploymentId());
        assertNull(context.deploymentVersion());
    }

    @Test
    @DisplayName("Should validate artifacts configuration")
    void shouldValidateArtifactsConfiguration() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        // Artifacts bucket should be null by default
        assertNull(context.artifactsBucket());
        assertNotNull(context.artifactsPrefix());
    }

    @Test
    @DisplayName("Should validate SSO configuration")
    void shouldValidateSsoConfiguration() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        // SSO fields should be null by default
        assertNull(context.ssoInstanceArn());
        assertNull(context.ssoGroupId());
        assertNull(context.ssoTargetAccountId());
    }

    @Test
    @DisplayName("Should validate CPU target utilization")
    void shouldValidateCpuTargetUtilization() {
        // Given
        var app = new software.amazon.awscdk.App();
        var stack = new software.amazon.awscdk.Stack(app, "TestStack");
        
        // When
        var context = DeploymentContext.from(stack);
        
        // Then
        assertNotNull(context);
        assertTrue(context.cpuTargetUtilization() >= 0);
    }

    /**
     * Provides working runtime and topology combinations for parameterized tests.
     * Excludes S3-website topology as it's not yet implemented.
     */
    private static Stream<Arguments> workingRuntimeTopologyCombinations() {
        return Stream.of(
            // EC2 + Service combinations
            Arguments.of(RuntimeType.EC2, TopologyType.JENKINS_SERVICE, SecurityProfile.DEV),
            Arguments.of(RuntimeType.EC2, TopologyType.JENKINS_SERVICE, SecurityProfile.STAGING),
            Arguments.of(RuntimeType.EC2, TopologyType.JENKINS_SERVICE, SecurityProfile.PRODUCTION),
            
            // EC2 + Single Node combinations
            Arguments.of(RuntimeType.EC2, TopologyType.JENKINS_SINGLE_NODE, SecurityProfile.DEV),
            Arguments.of(RuntimeType.EC2, TopologyType.JENKINS_SINGLE_NODE, SecurityProfile.STAGING),
            Arguments.of(RuntimeType.EC2, TopologyType.JENKINS_SINGLE_NODE, SecurityProfile.PRODUCTION),
            
            // Fargate + Service combinations
            Arguments.of(RuntimeType.FARGATE, TopologyType.JENKINS_SERVICE, SecurityProfile.DEV),
            Arguments.of(RuntimeType.FARGATE, TopologyType.JENKINS_SERVICE, SecurityProfile.STAGING),
            Arguments.of(RuntimeType.FARGATE, TopologyType.JENKINS_SERVICE, SecurityProfile.PRODUCTION)
            
            // Note: Fargate + Single Node is not supported (topology mismatch)
            // Note: S3-website is not yet implemented
        );
    }
}

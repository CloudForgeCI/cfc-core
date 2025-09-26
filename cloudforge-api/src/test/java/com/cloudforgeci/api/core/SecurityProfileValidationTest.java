package com.cloudforgeci.api.core;

import com.cloudforgeci.api.core.security.DevSecurityProfileConfiguration;
import com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration;
import com.cloudforgeci.api.core.security.StagingSecurityProfileConfiguration;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.RuntimeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security profile configuration tests for CI/CD pipeline.
 * Tests that all security profiles are properly configured and accessible.
 */
@DisplayName("Security Profile Validation Tests")
class SecurityProfileValidationTest {

    @Test
    @DisplayName("Should create valid Dev security profile configuration")
    void shouldCreateValidDevSecurityProfileConfiguration() {
        // Given
        var config = new DevSecurityProfileConfiguration();
        
        // When & Then
        assertNotNull(config);
        assertEquals(SecurityProfile.DEV, config.getSecurityProfile());
        
        // Verify key methods are accessible
        assertDoesNotThrow(() -> {
            config.isFlowLogsEnabled();
            config.getLogRetentionDays();
            config.getLogRemovalPolicy();
            config.isSecurityMonitoringEnabled();
            config.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "public-no-nat");
        });
    }

    @Test
    @DisplayName("Should create valid Staging security profile configuration")
    void shouldCreateValidStagingSecurityProfileConfiguration() {
        // Given
        var config = new StagingSecurityProfileConfiguration();
        
        // When & Then
        assertNotNull(config);
        assertEquals(SecurityProfile.STAGING, config.getSecurityProfile());
        
        // Verify key methods are accessible
        assertDoesNotThrow(() -> {
            config.isFlowLogsEnabled();
            config.getLogRetentionDays();
            config.getLogRemovalPolicy();
            config.isSecurityMonitoringEnabled();
            config.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "public-no-nat");
        });
    }

    @Test
    @DisplayName("Should create valid Production security profile configuration")
    void shouldCreateValidProductionSecurityProfileConfiguration() {
        // Given
        var config = new ProductionSecurityProfileConfiguration();
        
        // When & Then
        assertNotNull(config);
        assertEquals(SecurityProfile.PRODUCTION, config.getSecurityProfile());
        
        // Verify key methods are accessible
        assertDoesNotThrow(() -> {
            config.isFlowLogsEnabled();
            config.getLogRetentionDays();
            config.getLogRemovalPolicy();
            config.isSecurityMonitoringEnabled();
            config.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "public-no-nat");
        });
    }

    @Test
    @DisplayName("Should validate Dev security profile values")
    void shouldValidateDevSecurityProfileValues() {
        // Given
        var config = new DevSecurityProfileConfiguration();
        
        // When & Then
        assertFalse(config.isFlowLogsEnabled(), "Dev should not have flow logs enabled");
        assertNotNull(config.getLogRetentionDays(), "Dev should have log retention");
        assertNotNull(config.getLogRemovalPolicy(), "Dev should have removal policy");
        assertFalse(config.isSecurityMonitoringEnabled(), "Dev should not have security monitoring");
        assertEquals(0, config.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "public-no-nat"), "Dev should have 0 NAT gateways");
    }

    @Test
    @DisplayName("Should validate Staging security profile values")
    void shouldValidateStagingSecurityProfileValues() {
        // Given
        var config = new StagingSecurityProfileConfiguration();
        
        // When & Then
        assertTrue(config.isFlowLogsEnabled(), "Staging should have flow logs enabled");
        assertNotNull(config.getLogRetentionDays(), "Staging should have log retention");
        assertNotNull(config.getLogRemovalPolicy(), "Staging should have removal policy");
        assertTrue(config.isSecurityMonitoringEnabled(), "Staging should have security monitoring");
        assertEquals(0, config.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "public-no-nat"), "Staging should have 0 NAT gateways for public-no-nat");
    }

    @Test
    @DisplayName("Should validate Production security profile values")
    void shouldValidateProductionSecurityProfileValues() {
        // Given
        var config = new ProductionSecurityProfileConfiguration();
        
        // When & Then
        assertTrue(config.isFlowLogsEnabled(), "Production should have flow logs enabled");
        assertNotNull(config.getLogRetentionDays(), "Production should have log retention");
        assertNotNull(config.getLogRemovalPolicy(), "Production should have removal policy");
        assertTrue(config.isSecurityMonitoringEnabled(), "Production should have security monitoring");
        assertEquals(2, config.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "private-with-nat"), "Production should have 2 NAT gateways");
    }

    @Test
    @DisplayName("Should validate security profile progression")
    void shouldValidateSecurityProfileProgression() {
        // Given
        var devConfig = new DevSecurityProfileConfiguration();
        var stagingConfig = new StagingSecurityProfileConfiguration();
        var productionConfig = new ProductionSecurityProfileConfiguration();
        
        // When & Then
        // Dev should have minimal security
        assertFalse(devConfig.isFlowLogsEnabled());
        assertFalse(devConfig.isSecurityMonitoringEnabled());
        assertEquals(0, devConfig.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "public-no-nat"));
        
        // Staging should have moderate security
        assertTrue(stagingConfig.isFlowLogsEnabled());
        assertTrue(stagingConfig.isSecurityMonitoringEnabled());
        assertEquals(0, stagingConfig.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "public-no-nat"));
        
        // Production should have maximum security
        assertTrue(productionConfig.isFlowLogsEnabled());
        assertTrue(productionConfig.isSecurityMonitoringEnabled());
        assertEquals(2, productionConfig.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "private-with-nat"));
    }

    @Test
    @DisplayName("Should validate NAT gateway count progression")
    void shouldValidateNatGatewayCountProgression() {
        // Given
        var devConfig = new DevSecurityProfileConfiguration();
        var stagingConfig = new StagingSecurityProfileConfiguration();
        var productionConfig = new ProductionSecurityProfileConfiguration();
        
        // When & Then
        assertEquals(0, devConfig.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "public-no-nat"), "Dev should have 0 NAT gateways");
        assertEquals(0, stagingConfig.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "public-no-nat"), "Staging should have 0 NAT gateways");
        assertEquals(2, productionConfig.getNatGatewayCount(TopologyType.JENKINS_SERVICE, RuntimeType.EC2, "private-with-nat"), "Production should have 2 NAT gateways");
    }

    @Test
    @DisplayName("Should validate security profile kind method")
    void shouldValidateSecurityProfileKindMethod() {
        // Given
        var devConfig = new DevSecurityProfileConfiguration();
        var stagingConfig = new StagingSecurityProfileConfiguration();
        var productionConfig = new ProductionSecurityProfileConfiguration();
        
        // When & Then
        assertEquals(SecurityProfile.DEV, devConfig.getSecurityProfile());
        assertEquals(SecurityProfile.STAGING, stagingConfig.getSecurityProfile());
        assertEquals(SecurityProfile.PRODUCTION, productionConfig.getSecurityProfile());
    }

    @Test
    @DisplayName("Should validate security profile configuration interface")
    void shouldValidateSecurityProfileConfigurationInterface() {
        // Given
        var devConfig = new DevSecurityProfileConfiguration();
        var stagingConfig = new StagingSecurityProfileConfiguration();
        var productionConfig = new ProductionSecurityProfileConfiguration();
        
        // When & Then
        assertTrue(devConfig instanceof com.cloudforgeci.api.interfaces.SecurityProfileConfiguration);
        assertTrue(stagingConfig instanceof com.cloudforgeci.api.interfaces.SecurityProfileConfiguration);
        assertTrue(productionConfig instanceof com.cloudforgeci.api.interfaces.SecurityProfileConfiguration);
    }
}
package com.cloudforgeci.api.examples;

import com.cloudforgeci.api.compute.JenkinsFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.constructs.Construct;

/**
 * Example demonstrating how to use the Security Rules system with different security profiles.
 * This shows how to create Jenkins deployments with DEV, STAGING, and PRODUCTION security configurations.
 */
public class SecurityExample {

    /**
     * Example of creating a Jenkins deployment with development security settings.
     * Development security allows broader access for easier development and testing.
     */
    public static void createDevJenkins(Construct scope, String id, DeploymentContext cfc) {
        // Validate input parameters
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        
        // Uses SecurityProfile.DEV by default
        JenkinsFactory.createEc2(scope, id + "Dev", cfc);
        
        // Or explicitly specify DEV security profile
        JenkinsFactory.createEc2(scope, id + "DevExplicit", cfc, SecurityProfile.DEV);
        
        // Same for Fargate
        JenkinsFactory.createFargate(scope, id + "DevFargate", cfc, SecurityProfile.DEV);
    }

    /**
     * Example of creating a Jenkins deployment with staging security settings.
     * Staging security provides moderate restrictions suitable for testing environments.
     */
    public static void createStagingJenkins(Construct scope, String id, DeploymentContext cfc) {
        // Validate input parameters
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        
        // Staging security profile
        JenkinsFactory.createEc2(scope, id + "Staging", cfc, SecurityProfile.STAGING);
        JenkinsFactory.createFargate(scope, id + "StagingFargate", cfc, SecurityProfile.STAGING);
    }

    /**
     * Example of creating a Jenkins deployment with production security settings.
     * Production security implements hardened configurations for SOC/HIPAA compliance.
     */
    public static void createProductionJenkins(Construct scope, String id, DeploymentContext cfc) {
        // Validate input parameters
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        
        // Production security profile with maximum restrictions
        JenkinsFactory.createEc2(scope, id + "Production", cfc, SecurityProfile.PRODUCTION);
        JenkinsFactory.createFargate(scope, id + "ProductionFargate", cfc, SecurityProfile.PRODUCTION);
    }

    /**
     * Example showing how different security profiles affect the deployment:
     * 
     * DEV Security Profile:
     * - SSH access from anywhere (0.0.0.0/0)
     * - Jenkins port accessible from anywhere
     * - HTTP/HTTPS accessible from anywhere
     * - Minimal security restrictions for development convenience
     * 
     * STAGING Security Profile:
     * - SSH access restricted to VPC CIDR
     * - Jenkins port only accessible from ALB security group
     * - HTTP/HTTPS accessible from anywhere (needed for external testing)
     * - Moderate security restrictions
     * 
     * PRODUCTION Security Profile:
     * - SSH access restricted to specific bastion/VPN CIDR (10.0.1.0/24)
     * - Jenkins port only accessible from ALB security group
     * - HTTPS only (HTTP redirects to HTTPS)
     * - Maximum security restrictions for compliance
     * - WAF protection can be added (placeholder for future implementation)
     */
    public static void demonstrateSecurityProfiles(Construct scope, String id, DeploymentContext cfc) {
        // Validate input parameters
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        
        // Create deployments with different security profiles
        createDevJenkins(scope, id + "Dev", cfc);
        createStagingJenkins(scope, id + "Staging", cfc);
        createProductionJenkins(scope, id + "Production", cfc);
    }
}

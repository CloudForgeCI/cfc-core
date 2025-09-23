package com.cloudforgeci.api.examples;

import com.cloudforgeci.api.compute.JenkinsFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.core.iam.PermissionMatrix;
import software.constructs.Construct;

/**
 * Example demonstrating how to use the IAM Rules system with different permission profiles.
 * This shows how to create Jenkins deployments with minimal, standard, and extended IAM configurations.
 */
public class IAMExample {

    /**
     * Example of creating Jenkins deployments with automatic IAM profile mapping.
     * The IAM profile is automatically selected based on the security profile.
     */
    public static void createWithAutomaticIAM(Construct scope, String id, DeploymentContext cfc) {
        // Production deployment - automatically uses MINIMAL IAM profile
        JenkinsFactory.createEc2(scope, id + "Prod", cfc, SecurityProfile.PRODUCTION);
        
        // Staging deployment - automatically uses STANDARD IAM profile  
        JenkinsFactory.createFargate(scope, id + "Staging", cfc, SecurityProfile.STAGING);
        
        // Development deployment - automatically uses EXTENDED IAM profile
        JenkinsFactory.createEc2(scope, id + "Dev", cfc, SecurityProfile.DEV);
    }

    /**
     * Example of creating Jenkins deployments with explicit IAM profile selection.
     * This allows fine-grained control over permissions while maintaining security validation.
     */
    public static void createWithExplicitIAM(Construct scope, String id, DeploymentContext cfc) {
        // Production with minimal permissions (recommended)
        JenkinsFactory.createEc2(scope, id + "ProdMinimal", cfc, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL);
        
        // Staging with standard permissions (recommended)
        JenkinsFactory.createFargate(scope, id + "StagingStandard", cfc, SecurityProfile.STAGING, IAMProfile.STANDARD);
        
        // Development with extended permissions (recommended)
        JenkinsFactory.createEc2(scope, id + "DevExtended", cfc, SecurityProfile.DEV, IAMProfile.EXTENDED);
        
        // Example of production with standard permissions (allowed but not recommended)
        JenkinsFactory.createFargate(scope, id + "ProdStandard", cfc, SecurityProfile.PRODUCTION, IAMProfile.STANDARD);
    }

    /**
     * Example demonstrating IAM profile validation and mapping.
     */
    public static void demonstrateIAMValidation(Construct scope, String id, DeploymentContext cfc) {
        // Show automatic mapping
        System.out.println("Automatic IAM Profile Mapping:");
        System.out.println("PRODUCTION -> " + IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION));
        System.out.println("STAGING -> " + IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING));
        System.out.println("DEV -> " + IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV));
        
        // Show validation
        System.out.println("\nIAM Profile Validation:");
        System.out.println("PRODUCTION + MINIMAL: " + IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.MINIMAL));
        System.out.println("PRODUCTION + EXTENDED: " + IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.EXTENDED));
        System.out.println("DEV + MINIMAL: " + IAMProfileMapper.isValidCombination(SecurityProfile.DEV, IAMProfile.MINIMAL));
        
        // This would throw an exception due to invalid combination
        try {
            JenkinsFactory.createEc2(scope, id + "Invalid", cfc, SecurityProfile.PRODUCTION, IAMProfile.EXTENDED);
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
        }
    }

    /**
     * Example showing permission matrix usage.
     */
    public static void demonstratePermissionMatrix() {
        System.out.println("Permission Matrix Examples:");
        
        // Get required permissions for different combinations
        var ec2ProdPermissions = PermissionMatrix.getRequiredPermissions(
            com.cloudforgeci.api.interfaces.TopologyType.JENKINS_SERVICE,
            com.cloudforgeci.api.interfaces.RuntimeType.EC2,
            IAMProfile.MINIMAL
        );
        
        var fargateDevPermissions = PermissionMatrix.getRequiredPermissions(
            com.cloudforgeci.api.interfaces.TopologyType.JENKINS_SERVICE,
            com.cloudforgeci.api.interfaces.RuntimeType.FARGATE,
            IAMProfile.EXTENDED
        );
        
        System.out.println("EC2 Production (MINIMAL) permissions: " + ec2ProdPermissions.size() + " permissions");
        System.out.println("Fargate Development (EXTENDED) permissions: " + fargateDevPermissions.size() + " permissions");
        
        // Validate permissions
        var validationResult = PermissionMatrix.validatePermissions(
            com.cloudforgeci.api.interfaces.TopologyType.JENKINS_SERVICE,
            com.cloudforgeci.api.interfaces.RuntimeType.EC2,
            IAMProfile.MINIMAL,
            ec2ProdPermissions
        );
        
        System.out.println("Validation result: " + (validationResult.isValid() ? "VALID" : "INVALID"));
        if (validationResult.hasIssues()) {
            System.out.println("Issues: " + validationResult.getIssuesAsString());
        }
    }

    /**
     * Complete example showing all IAM features.
     */
    public static void demonstrateAllFeatures(Construct scope, String id, DeploymentContext cfc) {
        System.out.println("=== IAM Rules System Demonstration ===");
        
        // 1. Automatic IAM profile mapping
        createWithAutomaticIAM(scope, id + "Auto", cfc);
        
        // 2. Explicit IAM profile selection
        createWithExplicitIAM(scope, id + "Explicit", cfc);
        
        // 3. Validation and mapping demonstration
        demonstrateIAMValidation(scope, id + "Validation", cfc);
        
        // 4. Permission matrix demonstration
        demonstratePermissionMatrix();
        
        System.out.println("=== End Demonstration ===");
    }
}

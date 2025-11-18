# IAM Rules System

This document describes the comprehensive IAM (Identity and Access Management) Rules system that has been integrated into the CloudForge Community (CFC) core API. The system provides configurable permission profiles that minimize required roles and permissions while ensuring security best practices.

## Overview

The IAM Rules system follows the same pattern as the existing RuntimeRules, TopologyRules, and SecurityRules, providing a consistent and extensible approach to permission management. It integrates seamlessly with the SystemContext and RuleKit infrastructure, and automatically maps IAM profiles based on security profiles.

## Key Features

- **Principle of Least Privilege**: Only grants the minimum permissions required for each topology/runtime combination
- **Automatic Security Integration**: IAM profiles are automatically mapped based on security profiles
- **Validation System**: Prevents dangerous permission combinations (e.g., PRODUCTION + EXTENDED IAM)
- **Permission Matrix**: Comprehensive matrix defining required permissions for each combination
- **Compliance Ready**: Designed for SOC/HIPAA/PCI-DSS compliance requirements

## Components

### 1. IAMProfile Enum
Located at: `com.cloudforgeci.api.interfaces.IAMProfile`

```java
public enum IAMProfile {
    MINIMAL,    // Least privilege for production
    STANDARD,   // Balanced permissions for staging
    EXTENDED    // Broader permissions for development
}
```

### 2. IAMConfiguration Interface
Located at: `com.cloudforgeci.api.interfaces.IAMConfiguration`

Extends the base `IConfiguration` interface and provides the `kind()` method to return the IAM profile type.

### 3. IAM Configuration Implementations

#### MinimalIAMConfiguration
- **Purpose**: Production environments with strict compliance requirements
- **Permissions**:
  - Only essential SSM and CloudWatch permissions
  - Resource-specific permissions (no wildcards)
  - Minimal EFS access
  - No administrative permissions

#### StandardIAMConfiguration
- **Purpose**: Staging and development environments
- **Permissions**:
  - Standard operational permissions
  - Enhanced monitoring and logging
  - Backup and recovery permissions
  - Limited administrative access

#### ExtendedIAMConfiguration
- **Purpose**: Development environments only
- **Permissions**:
  - Additional debugging permissions
  - Extended monitoring capabilities
  - Development tools access
  - Administrative permissions for troubleshooting

### 4. IAMProfileMapper
Located at: `com.cloudforgeci.api.core.iam.IAMProfileMapper`

Automatically maps security profiles to appropriate IAM profiles:

| Security Profile | IAM Profile | Rationale |
|------------------|-------------|-----------|
| PRODUCTION | MINIMAL | Least privilege for production security |
| STAGING | STANDARD | Balanced permissions for testing |
| DEV | EXTENDED | Broader permissions for development |

### 5. PermissionMatrix
Located at: `com.cloudforgeci.api.core.iam.PermissionMatrix`

Defines the minimum required permissions for each topology/runtime combination and provides validation capabilities.

### 6. IAMRules Class
Located at: `com.cloudforgeci.api.core.rules.IAMRules`

Manages the installation and wiring of IAM configurations based on the selected IAM profile.

## Usage Examples

### Automatic IAM Profile Selection (Recommended)
```java
// IAM profile automatically mapped from security profile
JenkinsFactory.createEc2(scope, "ProdJenkins", cfc, SecurityProfile.PRODUCTION);
// Uses IAMProfile.MINIMAL automatically

JenkinsFactory.createFargate(scope, "StagingJenkins", cfc, SecurityProfile.STAGING);
// Uses IAMProfile.STANDARD automatically

JenkinsFactory.createEc2(scope, "DevJenkins", cfc, SecurityProfile.DEV);
// Uses IAMProfile.EXTENDED automatically
```

### Explicit IAM Profile Selection
```java
// Explicit IAM profile selection with validation
JenkinsFactory.createEc2(scope, "ProdJenkins", cfc, SecurityProfile.PRODUCTION, IAMProfile.MINIMAL);

// This would throw an exception due to invalid combination
JenkinsFactory.createEc2(scope, "ProdJenkins", cfc, SecurityProfile.PRODUCTION, IAMProfile.EXTENDED);
// Throws: IllegalArgumentException("Invalid combination: SecurityProfile=PRODUCTION with IAMProfile=EXTENDED")
```

### Permission Validation
```java
// Validate permissions for a specific combination
var result = PermissionMatrix.validatePermissions(
    TopologyType.JENKINS_SERVICE,
    RuntimeType.EC2,
    IAMProfile.MINIMAL,
    providedPermissions
);

if (!result.isValid()) {
    System.out.println("Permission issues: " + result.getIssuesAsString());
}
```

## Permission Matrix

### Core Permissions (All Deployments)
- `logs:CreateLogGroup`
- `logs:CreateLogStream`
- `logs:PutLogEvents`
- `logs:DescribeLogGroups`
- `logs:DescribeLogStreams`

### EC2-Specific Permissions

#### MINIMAL Profile
- `ssm:GetParameter`
- `ssm:GetParameters`
- `ssm:GetParametersByPath`
- `ssm:SendCommand`
- `ssm:ListCommandInvocations`
- `cloudwatch:PutMetricData`

#### STANDARD Profile
- All MINIMAL permissions plus:
- `ssm:DescribeInstanceInformation`
- `cloudwatch:GetMetricStatistics`
- `cloudwatch:ListMetrics`
- `s3:GetObject`
- `s3:PutObject`
- `s3:ListBucket`

#### EXTENDED Profile
- `ssm:*`
- `cloudwatch:*`
- `ec2:DescribeInstances`
- `ec2:DescribeVolumes`
- `ec2:DescribeSnapshots`
- `ec2:DescribeImages`
- `ec2:DescribeSecurityGroups`
- `ec2:DescribeVpcs`
- `ec2:DescribeSubnets`
- `s3:*`

### Fargate-Specific Permissions

#### MINIMAL Profile
- `ecr:GetAuthorizationToken`
- `ecr:BatchCheckLayerAvailability`
- `ecr:GetDownloadUrlForLayer`
- `ecr:BatchGetImage`

#### STANDARD Profile
- All MINIMAL permissions plus:
- `ecr:DescribeRepositories`
- `ecr:ListImages`
- `cloudwatch:PutMetricData`
- `cloudwatch:GetMetricStatistics`
- `cloudwatch:ListMetrics`
- `s3:GetObject`
- `s3:PutObject`
- `s3:ListBucket`

#### EXTENDED Profile
- `ecr:*`
- `ecs:DescribeClusters`
- `ecs:DescribeServices`
- `ecs:DescribeTasks`
- `ecs:DescribeTaskDefinition`
- `ecs:ListTasks`
- `ecs:ListServices`
- `cloudwatch:*`
- `s3:*`

### EFS Permissions

#### MINIMAL Profile
- `elasticfilesystem:ClientMount`
- `elasticfilesystem:ClientWrite`

#### STANDARD Profile
- All MINIMAL permissions plus:
- `elasticfilesystem:ClientRootAccess`
- `elasticfilesystem:DescribeMountTargets`

#### EXTENDED Profile
- `elasticfilesystem:*`

## Security Integration

The IAM system is tightly integrated with the Security Rules system:

1. **Automatic Mapping**: IAM profiles are automatically selected based on security profiles
2. **Validation**: Prevents dangerous combinations like PRODUCTION + EXTENDED IAM
3. **Consistency**: Ensures IAM permissions align with security requirements
4. **Compliance**: Designed to meet SOC/HIPAA/PCI-DSS requirements

## Best Practices

### 1. Use Automatic Mapping
```java
// Recommended: Let the system choose appropriate IAM profile
JenkinsFactory.createEc2(scope, "Jenkins", cfc, SecurityProfile.PRODUCTION);
```

### 2. Validate Combinations
```java
// Always validate IAM profile combinations
if (!IAMProfileMapper.isValidCombination(securityProfile, iamProfile)) {
    throw new IllegalArgumentException("Invalid combination");
}
```

### 3. Follow Least Privilege
- Use MINIMAL IAM profile for production
- Use STANDARD IAM profile for staging
- Use EXTENDED IAM profile only for development

### 4. Regular Permission Audits
```java
// Regularly validate permissions
var result = PermissionMatrix.validatePermissions(topology, runtime, iamProfile, actualPermissions);
if (!result.isValid()) {
    // Review and fix permission issues
}
```

## Integration Points

The IAM Rules system integrates with:

1. **Security Rules**: Automatic profile mapping and validation
2. **Runtime Rules**: EC2 and Fargate specific permissions
3. **Topology Rules**: Jenkins service and single node configurations
4. **SystemContext**: Centralized role management
5. **JenkinsFactory**: Automatic role assignment

## Migration Guide

Existing deployments will need to be updated to include the IAM profile parameter:

**Before:**
```java
SystemContext.start(scope, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.DEV, cfc);
```

**After:**
```java
SystemContext.start(scope, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);
```

The JenkinsFactory methods have been updated to maintain backward compatibility while providing the new IAM functionality.

## Compliance Features

### SOC 2 Compliance
- Principle of least privilege enforcement
- Regular permission validation
- Audit trail through CloudWatch logs
- Separation of duties through profile separation

### HIPAA Compliance
- Minimal permissions for production environments
- No unnecessary data access permissions
- Encrypted communication requirements
- Access logging and monitoring

### PCI-DSS Compliance
- Restricted administrative access
- Regular permission reviews
- Secure communication protocols
- Access control enforcement

## Future Enhancements

1. **Dynamic Permission Adjustment**: Automatic permission scaling based on usage patterns
2. **Cross-Account IAM**: Support for cross-account role assumptions
3. **Temporary Permissions**: Time-limited permission grants for debugging
4. **Permission Analytics**: Usage analytics and optimization recommendations
5. **Compliance Reporting**: Automated compliance report generation

## Troubleshooting

### Common Issues

1. **Invalid Combination Error**
   ```
   IllegalArgumentException: Invalid combination: SecurityProfile=PRODUCTION with IAMProfile=EXTENDED
   ```
   **Solution**: Use appropriate IAM profile for security level

2. **Missing Permissions**
   ```
   ValidationResult: Missing required permission: ssm:GetParameter
   ```
   **Solution**: Add missing permissions to IAM configuration

3. **Excessive Permissions**
   ```
   ValidationResult: Excessive permission for MINIMAL profile: s3:*
   ```
   **Solution**: Use more restrictive permissions for MINIMAL profile

### Debugging Tips

1. Use `SystemContext.debugPath()` to see current configuration
2. Check `PermissionMatrix.validatePermissions()` for detailed validation
3. Review IAM profile mapping with `IAMProfileMapper.mapFromSecurity()`
4. Enable CloudWatch logs for permission audit trails

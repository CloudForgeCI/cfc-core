# Security Rules System

This document describes the new Security Rules system that has been integrated into the CloudForge Community (CFC) core API. The system provides configurable security profiles that can be applied to Jenkins deployments on both EC2 and Fargate runtimes.

## Overview

The Security Rules system follows the same pattern as the existing RuntimeRules and TopologyRules, providing a consistent and extensible approach to security configuration. It integrates seamlessly with the SystemContext and RuleKit infrastructure.

## Components

### 1. SecurityProfile Enum
Located at: `com.cloudforgeci.api.interfaces.SecurityProfile`

```java
public enum SecurityProfile { 
    DEV, 
    STAGING, 
    PRODUCTION 
}
```

### 2. SecurityConfiguration Interface
Located at: `com.cloudforgeci.api.interfaces.SecurityConfiguration`

Extends the base `IConfiguration` interface and provides the `kind()` method to return the security profile type.

### 3. Security Configuration Implementations

#### DevSecurityConfiguration
- **Location**: `com.cloudforgeci.api.core.security.DevSecurityConfiguration`
- **Purpose**: Development environment with minimal security restrictions
- **Features**:
  - SSH access from anywhere (0.0.0.0/0)
  - Jenkins port accessible from anywhere
  - HTTP/HTTPS accessible from anywhere
  - NFS access from Jenkins instances

#### StagingSecurityConfiguration
- **Location**: `com.cloudforgeci.api.core.security.StagingSecurityConfiguration`
- **Purpose**: Testing environment with moderate security restrictions
- **Features**:
  - SSH access restricted to VPC CIDR
  - Jenkins port only accessible from ALB security group
  - HTTP/HTTPS accessible from anywhere (needed for external testing)
  - NFS access restricted to Jenkins instances

#### ProductionSecurityConfiguration
- **Location**: `com.cloudforgeci.api.core.security.ProductionSecurityConfiguration`
- **Purpose**: Production environment with hardened security for compliance
- **Features**:
  - SSH access restricted to bastion/VPN CIDR (10.0.1.0/24)
  - Jenkins port only accessible from ALB security group
  - HTTPS only (HTTP redirects to HTTPS)
  - NFS access restricted to Jenkins instances
  - Placeholder for WAF protection (future enhancement)

### 4. SecurityRules Class
Located at: `com.cloudforgeci.api.core.rules.SecurityRules`

Manages the installation and wiring of security configurations based on the selected security profile.

### 5. SystemContext Integration

The SystemContext has been updated to include:
- `SecurityProfile security` field
- `Slot<CfnWebACL> wafWebAcl` slot for future WAF integration
- Updated `start()` method to accept SecurityProfile parameter
- Updated `debugPath()` method to include security information

## Usage Examples

### Basic Usage (Default DEV Security)
```java
// Uses SecurityProfile.DEV by default
JenkinsFactory.createEc2(scope, "MyJenkins", cfc);
JenkinsFactory.createFargate(scope, "MyJenkins", cfc);
```

### Explicit Security Profile Selection
```java
// Development environment
JenkinsFactory.createEc2(scope, "DevJenkins", cfc, SecurityProfile.DEV);

// Staging environment
JenkinsFactory.createEc2(scope, "StagingJenkins", cfc, SecurityProfile.STAGING);

// Production environment
JenkinsFactory.createEc2(scope, "ProdJenkins", cfc, SecurityProfile.PRODUCTION);
```

### Complete Example
```java
public class MyJenkinsDeployment {
    public static void deploy(Construct scope, String id, DeploymentContext cfc) {
        // Development deployment
        JenkinsFactory.createEc2(scope, id + "Dev", cfc, SecurityProfile.DEV);
        
        // Staging deployment
        JenkinsFactory.createFargate(scope, id + "Staging", cfc, SecurityProfile.STAGING);
        
        // Production deployment
        JenkinsFactory.createEc2(scope, id + "Production", cfc, SecurityProfile.PRODUCTION);
    }
}
```

## Security Profiles Comparison

| Feature | DEV | STAGING | PRODUCTION |
|---------|-----|---------|------------|
| SSH Access | Anywhere (0.0.0.0/0) | VPC CIDR | Bastion/VPN CIDR (10.0.1.0/24) |
| Jenkins Port | Anywhere | ALB Security Group | ALB Security Group |
| HTTP Access | Anywhere | Anywhere | Redirects to HTTPS |
| HTTPS Access | Anywhere | Anywhere | Anywhere |
| NFS Access | Jenkins Instances | Jenkins Instances | Jenkins Instances |
| WAF Protection | None | None | Placeholder for future |

## Integration Points

The Security Rules system integrates with the following components:

1. **ALB (Application Load Balancer)**: Security group rules for HTTP/HTTPS access
2. **Domain/Subdomain**: DNS configuration for external access
3. **ACM Certificate**: SSL/TLS certificate management
4. **EFS**: Network File System security group rules
5. **VPC**: Virtual Private Cloud configuration
6. **Multi-AZ**: Multi-Availability Zone deployment
7. **Auto Scaling Group**: EC2 instance scaling
8. **Jenkins**: Application-specific security configurations
9. **S3**: Object storage security (future enhancement)
10. **Lambda**: Serverless function security (future enhancement)
11. **ECR**: Container registry security (future enhancement)
12. **EKS**: Kubernetes cluster security (future enhancement)
13. **CloudWatch**: Monitoring and logging security
14. **Backup**: Data backup security (future enhancement)
15. **WAF**: Web Application Firewall (placeholder for future)
16. **CloudFront**: CDN security (future enhancement)

## Future Enhancements

1. **WAF Integration**: Complete implementation of AWS WAF v2 for production environments
2. **Additional Security Profiles**: Custom security profiles for specific compliance requirements
3. **Security Monitoring**: Enhanced CloudWatch alarms and logging for security events
4. **Encryption**: Additional encryption configurations for data at rest and in transit
5. **IAM Integration**: Fine-grained IAM policies based on security profiles
6. **Compliance Frameworks**: Pre-configured profiles for SOC2, HIPAA, PCI-DSS, etc.

## Testing

The system includes comprehensive validation rules that ensure:
- Required security groups are present
- Security group rules are properly configured
- Network access is appropriately restricted based on the security profile
- Integration with existing runtime and topology configurations

## Migration Guide

Existing deployments using the old `SystemContext.start()` method will need to be updated to include the SecurityProfile parameter:

**Before:**
```java
SystemContext.start(scope, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, cfc);
```

**After:**
```java
SystemContext.start(scope, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.DEV, cfc);
```

The JenkinsFactory methods have been updated to maintain backward compatibility while providing the new security functionality.

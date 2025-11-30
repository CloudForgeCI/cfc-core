# Database Deployment Guide

**CloudForge 3.0 Database Integration**

This guide explains how to deploy applications with RDS databases in CloudForge, including automatic database provisioning, compliance enforcement, and automated remediation.

---

## Table of Contents

1. [Overview](#overview)
2. [Database Requirements by Application](#database-requirements-by-application)
3. [Deployment Configuration](#deployment-configuration)
4. [Compliance & Security](#compliance--security)
5. [Automated Remediation](#automated-remediation)
6. [Best Practices](#best-practices)
7. [Troubleshooting](#troubleshooting)

---

## Overview

CloudForge 3.0 introduces automatic database provisioning for applications that require persistent storage. Applications declare their database requirements through the `DatabaseSpec` interface, and CloudForge automatically provisions RDS instances with compliance-enforced configurations.

### Key Features

- ✅ **Automatic RDS provisioning** - CloudForge creates and configures RDS instances
- ✅ **Compliance enforcement** - Automatic encryption, backups, Multi-AZ for production
- ✅ **Secrets management** - Database credentials stored in AWS Secrets Manager
- ✅ **Automated remediation** - AWS Config rules detect and fix compliance violations
- ✅ **Framework-specific rules** - PCI-DSS, HIPAA, SOC2, GDPR validation
- ✅ **Optional provisioning** - Some applications support both RDS and embedded databases

---

## Database Requirements by Application

CloudForge applications have three types of database requirements:

### 1. REQUIRED Databases

These applications **MUST** have an RDS database to function:

| Application | Database | Instance | Storage | Backup Days | Notes |
|-------------|----------|----------|---------|-------------|-------|
| **GitLab** | PostgreSQL 14 | db.t3.medium | 50GB | 30 | Required for all environments |
| **Mattermost** | PostgreSQL 13 | db.t3.small | 30GB | 14 | No embedded DB support |
| **Superset** | PostgreSQL 13 | db.t3.small | 20GB | 14 | Metadata storage |
| **Harbor** | PostgreSQL 13 | db.t3.medium | 50GB | 30 | Registry metadata |

**Deployment:**
```java
// Database is automatically provisioned
Map<String, Object> cfc = new HashMap<>();
cfc.put("runtimeType", "FARGATE");
cfc.put("securityProfile", "production");
cfc.put("applicationId", "gitlab");  // Database created automatically
```

### 2. OPTIONAL Databases

These applications can use either RDS or embedded databases:

| Application | RDS Database | Embedded DB | Default | Notes |
|-------------|--------------|-------------|---------|-------|
| **Metabase** | PostgreSQL 15 | H2 | H2 (dev/staging) | Use RDS for HA |
| **Grafana** | PostgreSQL 14 | SQLite | SQLite (dev/staging) | Use RDS for multi-instance |

**Deployment with RDS:**
```java
Map<String, Object> cfc = new HashMap<>();
cfc.put("runtimeType", "FARGATE");
cfc.put("securityProfile", "production");
cfc.put("applicationId", "metabase");
cfc.put("provisionDatabase", true);  // ← Enable RDS provisioning
```

**Deployment without RDS (embedded):**
```java
Map<String, Object> cfc = new HashMap<>();
cfc.put("runtimeType", "FARGATE");
cfc.put("securityProfile", "dev");
cfc.put("applicationId", "metabase");
// provisionDatabase defaults to false → uses H2 embedded
```

⚠️ **Important:** Embedded databases (H2, SQLite) do **not** support multiple instances due to file locking. For high availability, use RDS.

### 3. NO Database

These applications do not require databases:

- Jenkins
- Gitea
- Drone
- Prometheus
- Nexus
- Vault
- Redis
- PostgreSQL

---

## Deployment Configuration

### Basic RDS Deployment

```java
import com.cloudforgeci.samples.app.InteractiveDeployer;

public class MyDeployment {
    public static void main(String[] args) {
        Map<String, Object> cfc = new HashMap<>();

        // Basic settings
        cfc.put("stackName", "my-gitlab");
        cfc.put("region", "us-east-1");
        cfc.put("runtimeType", "FARGATE");
        cfc.put("securityProfile", "production");
        cfc.put("applicationId", "gitlab");

        // Database automatically provisioned for GitLab
        InteractiveDeployer.deploy(cfc);
    }
}
```

### Custom Database Configuration

Override default database settings:

```java
// Custom instance size
cfc.put("dbInstanceClass", "db.t3.large");  // Default: varies by app

// Custom storage
cfc.put("dbAllocatedStorage", 100);  // Default: varies by app (20-50GB)

// Custom backup retention
cfc.put("dbBackupRetentionDays", 30);  // Default: 7-30 days

// Custom database name
cfc.put("dbName", "my_custom_db");  // Default: app-specific

// Custom PostgreSQL version
cfc.put("dbEngineVersion", "15.4");  // Default: varies by app (13-15)
```

### Optional Database Provisioning

For applications with OPTIONAL database support:

```java
// Metabase with RDS (production)
Map<String, Object> cfc = new HashMap<>();
cfc.put("applicationId", "metabase");
cfc.put("securityProfile", "production");
cfc.put("provisionDatabase", true);  // ← Use RDS PostgreSQL

// Metabase with H2 (development)
Map<String, Object> cfc = new HashMap<>();
cfc.put("applicationId", "metabase");
cfc.put("securityProfile", "dev");
// provisionDatabase defaults to false → uses H2
```

### Multi-AZ Deployment

For high availability in production:

```java
cfc.put("securityProfile", "production");
cfc.put("dbMultiAz", true);  // Enabled by default for PRODUCTION
```

---

## Compliance & Security

CloudForge enforces compliance requirements automatically based on your security profile and compliance frameworks.

### Encryption

**Encryption at Rest** (Automatic)
- All RDS instances use AWS KMS encryption
- Enforced for all security profiles
- Validates PCI-DSS Req 3.4, HIPAA §164.312(a)(2)(iv), GDPR Art.32

```java
// Encryption is automatic - no configuration needed
cfc.put("securityProfile", "production");
// RDS instance created with KMS encryption
```

**Encryption in Transit** (Automatic)
- SSL/TLS required for all database connections
- SSL mode: `require` (PostgreSQL)
- Enforced via connection strings

### Automated Backups

Backup retention is automatic and compliance-enforced:

| Security Profile | Default Retention | Compliance |
|------------------|-------------------|------------|
| **DEV** | 7 days | Optional |
| **STAGING** | 14 days | SOC2 recommended |
| **PRODUCTION** | 30 days | HIPAA, PCI-DSS required |

```java
// Custom backup retention
cfc.put("dbBackupRetentionDays", 30);  // Override default

// Backup window (optional)
cfc.put("dbBackupWindow", "03:00-04:00");  // UTC
cfc.put("dbMaintenanceWindow", "sun:04:00-sun:05:00");  // UTC
```

### Compliance Framework Enforcement

Enable compliance frameworks to activate framework-specific validation:

```java
cfc.put("complianceFrameworks", "HIPAA,SOC2");
cfc.put("awsConfigEnabled", true);  // Enable AWS Config monitoring
```

**Framework-Specific Requirements:**

#### PCI-DSS (Payment Card Industry)
- ✅ Req 3.4: Encryption at rest (KMS)
- ✅ Req 4.1: Encryption in transit (SSL/TLS)
- ✅ Req 6.2: Automatic minor version upgrades
- ✅ Req 10.2: Database activity logging

#### HIPAA (Healthcare)
- ✅ §164.312(a)(2)(iv): Encryption mechanisms
- ✅ §164.310(d): Automated backups (7-30 days)
- ✅ §164.308(a)(7)(ii)(B): High availability (Multi-AZ)
- ✅ §164.312(b): Audit controls

#### SOC2 (Service Organization Controls)
- ✅ CC6.8: Encryption at rest
- ✅ A1.2: Availability (Multi-AZ for production)
- ✅ A1.3: Backup procedures
- ✅ CC7.2: System monitoring

#### GDPR (Data Protection)
- ✅ Art.32: Security of processing (encryption)
- ✅ Art.32(1)(c): Backup and recovery capability
- ✅ Art.30: Records of processing activities

---

## Automated Remediation

CloudForge can automatically remediate database compliance violations using AWS Config rules.

### Available Remediations

#### 1. RDS Deletion Protection

Automatically enables deletion protection on RDS instances to prevent accidental deletion.

**Compliance:** HIPAA, SOC2, GDPR

**Enable:**
```java
cfc.put("awsConfigEnabled", true);
cfc.put("complianceFrameworks", "HIPAA");
cfc.put("enableRdsDeletionProtectionRemediation", true);
```

**What it does:**
- AWS Config rule detects RDS instances without deletion protection
- SSM Automation document automatically enables deletion protection
- Retries up to 5 times with 60-second intervals
- Works for all RDS instance types

**Framework-Specific Behavior:**

| Framework | Environments | Remediation |
|-----------|-------------|-------------|
| **SOC2** | PRODUCTION only | ✅ Enabled |
| **HIPAA** | All environments | ✅ Enabled |
| **GDPR** | All environments | ✅ Enabled |
| **PCI-DSS** | N/A | ❌ Not applicable |

#### 2. RDS Auto Minor Version Upgrade

Automatically enables automatic minor version upgrades for security patches.

**Compliance:** PCI-DSS, SOC2, HIPAA, GDPR

**Enable:**
```java
cfc.put("awsConfigEnabled", true);
cfc.put("complianceFrameworks", "PCI-DSS");
cfc.put("enableRdsAutoMinorVersionUpgradeRemediation", true);
```

**What it does:**
- AWS Config rule detects RDS instances without auto-upgrade enabled
- SSM Automation document enables automatic minor version upgrades
- Ensures security patches are applied automatically
- Retries up to 5 times with 60-second intervals

**Framework-Specific Behavior:**

| Framework | Environments | Remediation |
|-----------|-------------|-------------|
| **PCI-DSS** | All environments | ✅ Enabled (Req 6.2) |
| **SOC2** | All environments | ✅ Enabled (CC7.2) |
| **HIPAA** | All environments | ✅ Enabled (Security) |
| **GDPR** | All environments | ✅ Enabled (Art.32) |

### Example: Complete Remediation Setup

```java
import java.util.*;

public class ComplianceDeployment {
    public static void main(String[] args) {
        Map<String, Object> cfc = new HashMap<>();

        // Basic configuration
        cfc.put("stackName", "compliant-gitlab");
        cfc.put("region", "us-east-1");
        cfc.put("runtimeType", "FARGATE");
        cfc.put("securityProfile", "production");
        cfc.put("applicationId", "gitlab");

        // Compliance configuration
        cfc.put("complianceFrameworks", "HIPAA,SOC2");
        cfc.put("awsConfigEnabled", true);
        cfc.put("createConfigInfrastructure", true);

        // Database remediations
        cfc.put("enableRdsDeletionProtectionRemediation", true);
        cfc.put("enableRdsAutoMinorVersionUpgradeRemediation", true);

        // Deploy
        InteractiveDeployer.deploy(cfc);
    }
}
```

### Remediation Logs

When remediations are enabled, you'll see logs like:

```
INFO: Creating RDS deletion protection remediation for HIPAA
INFO:   Config Rule: hipaa-rds-deletion-protection-enabled
INFO:   SSM Document: enable-rds-deletion-protection
INFO:   Mode: Automatic (fixes non-compliant instances immediately)
INFO:   Max attempts: 5, Retry interval: 60 seconds
INFO: RDS deletion protection remediation created successfully
```

When remediations are disabled:

```
INFO: RDS deletion protection remediation disabled (enableRdsDeletionProtectionRemediation = false)
```

---

## Best Practices

### 1. Production Deployments

**Always use RDS for production:**
```java
cfc.put("securityProfile", "production");
cfc.put("provisionDatabase", true);  // For OPTIONAL apps
cfc.put("dbMultiAz", true);  // High availability
cfc.put("dbBackupRetentionDays", 30);  // Compliance
```

**Enable compliance frameworks:**
```java
cfc.put("complianceFrameworks", "SOC2,HIPAA,PCI-DSS");
cfc.put("awsConfigEnabled", true);
cfc.put("createConfigInfrastructure", true);
```

**Enable automated remediation:**
```java
cfc.put("enableRdsDeletionProtectionRemediation", true);
cfc.put("enableRdsAutoMinorVersionUpgradeRemediation", true);
```

### 2. Development Deployments

**Use embedded databases for cost savings:**
```java
cfc.put("securityProfile", "dev");
cfc.put("applicationId", "metabase");
// provisionDatabase defaults to false → uses H2
```

**Single instance only:**
```java
cfc.put("desiredCount", 1);  // H2/SQLite can't support multiple instances
```

### 3. Database Sizing

**Start small, scale up:**
```java
// Development
cfc.put("dbInstanceClass", "db.t3.micro");
cfc.put("dbAllocatedStorage", 20);

// Staging
cfc.put("dbInstanceClass", "db.t3.small");
cfc.put("dbAllocatedStorage", 50);

// Production
cfc.put("dbInstanceClass", "db.t3.medium");
cfc.put("dbAllocatedStorage", 100);
cfc.put("dbMultiAz", true);
```

### 4. Secrets Management

**Never hardcode passwords:**
```java
// ✅ GOOD: CloudForge manages secrets automatically
// Database password stored in AWS Secrets Manager
// Application receives: {{resolve:secretsmanager:...}}

// ❌ BAD: Never do this
cfc.put("dbPassword", "my-password");  // DON'T DO THIS
```

**Access database credentials:**
```bash
# Get database credentials from Secrets Manager
aws secretsmanager get-secret-value \
  --secret-id <stack-name>-db-credentials \
  --query SecretString \
  --output text | jq -r '.password'
```

### 5. Backup Strategy

**Automated backups (handled by CloudForge):**
- Daily automated backups
- Retention: 7-30 days (based on security profile)
- Point-in-time recovery enabled

**Manual snapshots (for major changes):**
```bash
# Create manual snapshot before major upgrade
aws rds create-db-snapshot \
  --db-instance-identifier <stack-name>-db \
  --db-snapshot-identifier <stack-name>-pre-upgrade-$(date +%Y%m%d)
```

### 6. Monitoring

**Enable Performance Insights:**
```java
cfc.put("dbEnablePerformanceInsights", true);
```

**Enhanced Monitoring:**
```java
cfc.put("dbMonitoringInterval", 60);  // 60 seconds
```

**CloudWatch Alarms (automatic):**
- CPU utilization > 80%
- Storage < 10% free
- Connection count > 80% of max
- Replication lag > 1000ms (Multi-AZ)

---

## Troubleshooting

### Issue: Database Creation Fails

**Symptom:**
```
Error: RDS instance creation failed: DB instance already exists
```

**Cause:** Stack name conflict or previous deployment not cleaned up

**Solution:**
```bash
# Check existing RDS instances
aws rds describe-db-instances --query 'DBInstances[*].[DBInstanceIdentifier,DBInstanceStatus]'

# Delete old instance if needed
aws rds delete-db-instance \
  --db-instance-identifier <stack-name>-db \
  --skip-final-snapshot
```

---

### Issue: Application Can't Connect to Database

**Symptom:**
```
Error: Could not connect to database: Connection refused
```

**Cause:** Security group misconfiguration or incorrect connection string

**Solution 1: Check security groups**
```bash
# Verify RDS security group allows connections from ECS
aws ec2 describe-security-groups \
  --filters "Name=tag:aws:cloudformation:stack-name,Values=<stack-name>" \
  --query 'SecurityGroups[*].[GroupName,GroupId,IpPermissions]'
```

**Solution 2: Verify environment variables**
```bash
# Check ECS task definition for correct DB_HOST
aws ecs describe-task-definition \
  --task-definition <task-family> \
  --query 'taskDefinition.containerDefinitions[0].environment'
```

**Solution 3: Test database connectivity**
```bash
# Connect to RDS from VPC (use bastion host or Cloud9)
psql -h <rds-endpoint> -U <username> -d <database>
```

---

### Issue: Embedded Database (H2/SQLite) File Locking

**Symptom:**
```
Error: Database is locked by another process
```

**Cause:** Multiple ECS tasks trying to access embedded database file

**Solution:**
```java
// Reduce to single instance
cfc.put("desiredCount", 1);

// Or switch to RDS for high availability
cfc.put("provisionDatabase", true);
cfc.put("dbMultiAz", true);
```

---

### Issue: Remediation Not Working

**Symptom:**
```
Config rule detects violation but remediation doesn't run
```

**Cause:** Remediation not enabled or IAM permissions missing

**Solution 1: Enable remediation**
```java
cfc.put("awsConfigEnabled", true);
cfc.put("enableRdsDeletionProtectionRemediation", true);
```

**Solution 2: Check Config Recorder**
```bash
# Verify Config Recorder is running
aws configservice describe-configuration-recorder-status

# Start recorder if stopped
aws configservice start-configuration-recorder \
  --configuration-recorder-name default
```

**Solution 3: Check IAM permissions**
```bash
# Verify Config service role has permissions
aws iam get-role --role-name <config-service-role>
aws iam list-attached-role-policies --role-name <config-service-role>
```

---

### Issue: High Database Costs

**Symptom:**
```
AWS bill shows high RDS charges
```

**Solution 1: Right-size instances**
```java
// Use smaller instances for dev/staging
cfc.put("securityProfile", "dev");
cfc.put("dbInstanceClass", "db.t3.micro");  // Instead of db.t3.medium
```

**Solution 2: Reduce Multi-AZ for non-production**
```java
// Disable Multi-AZ for dev/staging
cfc.put("securityProfile", "dev");
cfc.put("dbMultiAz", false);
```

**Solution 3: Use embedded databases for development**
```java
// Use H2/SQLite instead of RDS for dev
cfc.put("securityProfile", "dev");
cfc.put("applicationId", "metabase");
// provisionDatabase defaults to false → uses H2 (free)
```

**Solution 4: Reduce backup retention**
```java
// Minimum backup retention for dev
cfc.put("securityProfile", "dev");
cfc.put("dbBackupRetentionDays", 7);  // Minimum
```

---

### Issue: Database Performance Degradation

**Symptom:**
```
Application slow, database queries timing out
```

**Solution 1: Enable Performance Insights**
```java
cfc.put("dbEnablePerformanceInsights", true);
```

**Solution 2: Scale up instance class**
```java
// Upgrade to larger instance
cfc.put("dbInstanceClass", "db.t3.large");  // From db.t3.small
```

**Solution 3: Increase storage**
```java
// Add storage (cannot decrease)
cfc.put("dbAllocatedStorage", 200);  // From 50GB
```

**Solution 4: Add read replicas (manual)**
```bash
# Create read replica for read-heavy workloads
aws rds create-db-instance-read-replica \
  --db-instance-identifier <stack-name>-db-replica \
  --source-db-instance-identifier <stack-name>-db \
  --db-instance-class db.t3.medium
```

---

## Additional Resources

### Documentation
- [Database Security Rules](../../cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/DatabaseSecurityRules.java) - Compliance validation rules
- [DatabaseSpec Interface](../../cloudforge-core/src/main/java/com/cloudforge/core/interfaces/DatabaseSpec.java) - Database requirement specification
- [RdsFactory](../../cloudforge-api/src/main/java/com/cloudforgeci/api/database/RdsFactory.java) - RDS provisioning implementation

### Integration Summaries
- [DATABASE-INTEGRATION-SUMMARY.md](../../DATABASE-INTEGRATION-SUMMARY.md) - Complete integration overview
- [DATABASE-REMEDIATIONS.md](../../DATABASE-REMEDIATIONS.md) - Remediation implementation details
- [DATABASE-PHASE2-AND-REMEDIATIONS-COMPLETE.md](../../DATABASE-PHASE2-AND-REMEDIATIONS-COMPLETE.md) - Phase 2 completion status

### Compliance Frameworks
- [PCI-DSS Compliance](../compliance/PCI-DSS.md)
- [HIPAA Compliance](../compliance/HIPAA.md)
- [SOC2 Compliance](../compliance/SOC2.md)
- [GDPR Compliance](../compliance/GDPR.md)

---

## Support

For issues or questions:
1. Check the [Troubleshooting](#troubleshooting) section
2. Review [DATABASE-IMPLEMENTATION-GAPS.md](../../DATABASE-IMPLEMENTATION-GAPS.md) for known limitations
3. Open an issue at [github.com/anthropics/cloudforge](https://github.com/anthropics/cloudforge/issues)

---

**Document Version:** 1.0
**Last Updated:** November 30, 2025
**CloudForge Version:** 3.0.0
**Status:** ✅ Production Ready

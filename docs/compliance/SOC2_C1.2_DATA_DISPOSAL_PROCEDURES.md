# SOC2 C1.2 - Data Disposal Procedures

**Control**: C1.2 - Information Disposal
**Status**: Fully Documented
**Last Updated**: 2025-12-16
**Owner**: Infrastructure Administrator

---

## Overview

This document defines procedures for secure disposal of confidential information in CloudForge CI infrastructure. These procedures satisfy SOC2 C1.2 (Information Disposal) requirements.

---

## Data Classification

| Classification | Description | Disposal Method |
|---------------|-------------|-----------------|
| **Confidential** | PII, credentials, cardholder data, ePHI | Secure deletion, encryption key destruction |
| **Internal** | Business data, logs, configurations | Standard deletion with lifecycle policies |
| **Public** | Documentation, public APIs | No special disposal required |

---

## Automated Data Disposal

CloudForge CI implements automated data disposal through S3 lifecycle policies:

### S3 Bucket Lifecycle Policies

#### CloudTrail Logs (6-Year Retention)

```java
// AlbFactory and ComplianceFactory implement:
.lifecycleRules(List.of(
    LifecycleRule.builder()
        .transitions(List.of(
            Transition.builder()
                .storageClass(StorageClass.GLACIER)
                .transitionAfter(Duration.days(90))      // Archive after 90 days
                .build(),
            Transition.builder()
                .storageClass(StorageClass.DEEP_ARCHIVE)
                .transitionAfter(Duration.days(365))     // Deep archive after 1 year
                .build()
        ))
        .expiration(Duration.days(2190))                 // Delete after 6 years (HIPAA)
        .build()
))
```

**Compliance Mapping**:
- HIPAA §164.316(b)(2)(i): 6-year retention
- SOC2 C1.2: Automated deletion after retention period
- PCI-DSS Req 3.1: Data retention policy

#### Application Data (Configurable)

| Data Type | Default Retention | Disposal Method |
|-----------|------------------|-----------------|
| ALB Access Logs | 6 years | S3 lifecycle expiration |
| CloudTrail Logs | 6 years | S3 lifecycle expiration |
| CloudWatch Logs | Per security profile | Log group retention |
| RDS Snapshots | 30-90 days | Automatic deletion |
| EFS Backups | 90 days | AWS Backup lifecycle |

### CloudWatch Log Retention

```java
// ComplianceFactory sets retention per security profile:
Trail.Builder.create(this, "CloudTrail")
    .cloudWatchLogsRetention(config.getLogRetentionDays())
    .build();

// Security profiles define:
// - DEV: 30 days
// - STAGING: 90 days
// - PRODUCTION: 365 days (CloudWatch), 6 years (S3)
```

### Database Snapshot Disposal

```java
// RdsFactory configures backup retention:
.backupRetention(Duration.days(backupRetention))
// - PRODUCTION: 30 days
// - STAGING: 14 days
// - DEV: 7 days

// Old snapshots automatically deleted by RDS
```

---

## Manual Disposal Procedures

### 1. User Account Deletion

**Trigger**: User termination, GDPR/CCPA deletion request

**Procedure**:
1. Receive deletion request via ticketing system
2. Verify requestor identity and authorization
3. Identify all data locations:
   - Cognito user pool
   - Application databases
   - Log files (note: may be retained for compliance)
4. Delete user data from active systems
5. Document deletion in data subject request log
6. Respond to requestor within required timeframe

**Cognito User Deletion**:
```bash
aws cognito-idp admin-delete-user \
  --user-pool-id <pool-id> \
  --username <email>
```

### 2. Stack/Environment Deletion

**Trigger**: Environment decommissioning, project completion

**Procedure**:
1. Notify stakeholders of planned deletion
2. Export any required data/reports
3. For PRODUCTION stacks:
   - Verify backup retention is complete
   - Export final compliance reports
   - Document data lineage
4. Delete CloudFormation stack
5. Verify resource cleanup:
   - Check for retained resources (S3, RDS snapshots)
   - Delete retained resources per data classification
6. Document deletion in asset inventory

**CloudForge CI Removal Policies**:
```java
// PRODUCTION: Retain critical resources
.removalPolicy(security == SecurityProfile.PRODUCTION ?
    RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)

// Resources retained in PRODUCTION:
// - S3 buckets (logs)
// - RDS snapshots
// - KMS keys
// - Cognito user pools
```

### 3. Encryption Key Destruction

**Trigger**: Key rotation, data disposal requirement

**Procedure**:
1. Verify no active data encrypted with key
2. Schedule key deletion (7-30 day waiting period)
3. Document key destruction request
4. After waiting period, AWS permanently deletes key

**KMS Key Deletion**:
```bash
# Schedule key deletion (minimum 7 days)
aws kms schedule-key-deletion \
  --key-id <key-id> \
  --pending-window-in-days 7
```

**Note**: Once a KMS key is deleted, all data encrypted with that key becomes permanently unrecoverable. This is an effective cryptographic erasure method.

### 4. Physical Media (Not Applicable)

CloudForge CI operates entirely on AWS infrastructure. Physical media disposal is handled by AWS per their SOC 2 controls.

**AWS Responsibility**:
- Physical disk destruction
- Data center decommissioning
- Hardware disposal

**Reference**: AWS SOC 2 Type II Report, Section: Media Handling

---

## Disposal Verification

### Automated Verification

| Resource | Verification Method | Frequency |
|----------|--------------------| ----------|
| S3 Objects | Lifecycle policy reports | Daily |
| CloudWatch Logs | Retention policy check | Weekly |
| RDS Snapshots | Snapshot age audit | Weekly |
| EFS Backups | AWS Backup reports | Weekly |

### Manual Verification

**Quarterly Data Disposal Audit**:
1. Review S3 lifecycle policy effectiveness
2. Verify no data beyond retention period
3. Check for orphaned resources
4. Document audit results

**Template**: `docs/compliance/templates/data-disposal-audit.md`

---

## Disposal Evidence

### Automated Evidence

- S3 lifecycle policy configurations (exportable)
- CloudWatch log group retention settings
- AWS Backup retention policies
- RDS backup retention configurations

### Manual Evidence

| Evidence Type | Location | Retention |
|---------------|----------|-----------|
| Data deletion requests | Ticketing system | 7 years |
| Stack deletion records | CloudFormation events | 90 days (AWS) |
| Key destruction logs | CloudTrail | 6 years |
| Quarterly audit reports | SharePoint/Confluence | 7 years |

---

## Compliance Mapping

| Requirement | Implementation | Evidence |
|-------------|---------------|----------|
| **SOC2 C1.2** | S3 lifecycle, log retention | Lifecycle policies |
| **HIPAA §164.310(d)(2)(i)** | 6-year log retention | S3 lifecycle config |
| **PCI-DSS Req 3.1** | Data retention policy | This document |
| **GDPR Art. 17** | Deletion procedures | DSAR log |
| **GDPR Art. 5(1)(e)** | Storage limitation | Lifecycle policies |

---

## Exception Handling

### Legal Hold

If data is subject to legal hold:
1. Suspend automatic disposal for affected data
2. Document legal hold in ticketing system
3. Apply S3 Object Lock if needed
4. Resume disposal only after legal release

### Compliance Retention Override

Some data must be retained beyond standard periods:
- Financial records: 7 years
- Healthcare data: 6 years (HIPAA)
- Audit logs: Per regulatory requirement

Document any retention overrides in the data inventory.

---

## Responsibilities

| Role | Responsibility |
|------|---------------|
| **Infrastructure Administrator** | Define disposal policies, implement lifecycle policies, audit compliance |
| **Application Developer** | Application-level data deletion |
| **Legal Counsel** | Legal hold management, retention requirements |

---

**Document Control**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-12-16 | CloudForge CI | Initial release |

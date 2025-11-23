# Evidence Generation Script - Sample Output

This document shows the expected output when running the `generate-audit-evidence.sh` script.

## Command

```bash
./scripts/generate-audit-evidence.sh \
  --stack-name CloudForge-Prod-SOC2 \
  --framework SOC2 \
  --start-date 2024-01-01 \
  --end-date 2024-12-31
```

## Console Output

```
======================================
  Audit Evidence Generation Tool
======================================

Stack:      CloudForge-Prod-SOC2
Framework:  SOC2
Period:     2024-01-01 to 2024-12-31
Region:     us-east-1
Output:     audit-evidence-20241122-143052

[INFO] Checking prerequisites...
[SUCCESS] Prerequisites check passed
[INFO] Creating directory structure...
[SUCCESS] Directory structure created: audit-evidence-20241122-143052
[INFO] Exporting CloudFormation template...
[SUCCESS] CloudFormation template exported
[INFO] Exporting IAM configuration...
[SUCCESS] IAM configuration exported
[INFO] Exporting AWS Config configuration...
[SUCCESS] AWS Config configuration exported
[INFO] Exporting encryption configuration...
Checking bucket: cloudforge-cloudtrail-logs-123456789012
Checking bucket: cloudforge-config-logs-123456789012
Checking bucket: cloudforge-artifacts-123456789012
[SUCCESS] Encryption configuration exported
[INFO] Exporting logging configuration...
[INFO] Sampling CloudTrail events (this may take a while)...
[SUCCESS] Logging configuration exported
[INFO] Exporting monitoring configuration...
[SUCCESS] Monitoring configuration exported
[INFO] Exporting network configuration...
[WARNING] WAF not configured
[SUCCESS] Network configuration exported
[INFO] Generating compliance matrix...
[SUCCESS] Compliance matrix generated
[INFO] Generating summary report...
[SUCCESS] Summary report generated
[INFO] Creating evidence archive...
[SUCCESS] Evidence archive created: audit-evidence-20241122-143052.tar.gz
[INFO] Archive size: 2.3M

[SUCCESS] Audit evidence generation complete!

Evidence Package:
  Directory: audit-evidence-20241122-143052
  Archive:   audit-evidence-20241122-143052.tar.gz

Next Steps:
  1. Review: cat audit-evidence-20241122-143052/AUDIT_EVIDENCE_README.md
  2. Validate: cd audit-evidence-20241122-143052 && cat compliance/compliance-matrix.md
  3. Share: Send audit-evidence-20241122-143052.tar.gz to auditors
```

## Generated Directory Structure

After running the script, you'll get this directory structure:

```
audit-evidence-20241122-143052/
├── AUDIT_EVIDENCE_README.md
├── infrastructure/
│   ├── cloudformation-template.yaml         (1.2 MB)
│   ├── stack-metadata.json                  (15 KB)
│   └── stack-resources.json                 (45 KB)
├── iam/
│   ├── policies.json                        (125 KB)
│   ├── roles.json                           (89 KB)
│   ├── users.json                           (12 KB)
│   └── credential-report.csv                (3 KB)
├── encryption/
│   ├── kms-keys.json                        (8 KB)
│   ├── efs-filesystems.json                 (5 KB)
│   ├── acm-certificates.json                (7 KB)
│   └── s3-bucket-encryption.json            (18 KB)
├── logging/
│   ├── cloudtrail-trails.json               (12 KB)
│   ├── cloudtrail-status.json               (2 KB)
│   ├── log-groups.json                      (34 KB)
│   ├── vpc-flow-logs.json                   (9 KB)
│   └── cloudtrail-events-sample.json        (450 KB)
├── monitoring/
│   ├── cloudwatch-alarms.json               (28 KB)
│   ├── guardduty-detectors.json             (3 KB)
│   ├── guardduty-findings.json              (156 KB)
│   ├── sns-topics.json                      (8 KB)
│   └── securityhub-findings.json            (72 KB)
├── config/
│   ├── config-rules.json                    (89 KB)
│   ├── compliance-status.json               (45 KB)
│   ├── configuration-recorders.json         (6 KB)
│   ├── delivery-channels.json               (5 KB)
│   └── remediation-configurations.json      (23 KB)
├── network/
│   ├── vpcs.json                            (15 KB)
│   ├── security-groups.json                 (67 KB)
│   ├── network-acls.json                    (22 KB)
│   ├── load-balancers.json                  (18 KB)
│   └── waf-web-acls.json                    (0 KB - not configured)
├── compliance/
│   └── compliance-matrix.md                 (12 KB)
└── reports/
    └── (empty - for custom reports)

Total size: ~2.3 MB
Archive size: ~480 KB (compressed)
```

## AUDIT_EVIDENCE_README.md Contents

```markdown
# Audit Evidence Package

**Generated**: 2024-11-22 14:30:52
**Stack**: CloudForge-Prod-SOC2
**Region**: us-east-1
**Framework**: SOC2
**Audit Period**: 2024-01-01 to 2024-12-31

## Contents

This evidence package contains comprehensive documentation and configuration
exports to support SOC2 compliance auditing.

### Directory Structure

```
audit-evidence-*/
├── infrastructure/          # CloudFormation templates and stack metadata
├── iam/                     # IAM policies, roles, users, credential report
├── encryption/              # KMS, EFS, ACM, S3 encryption configuration
├── logging/                 # CloudTrail, CloudWatch Logs, VPC Flow Logs
├── monitoring/              # CloudWatch Alarms, GuardDuty, Security Hub
├── config/                  # AWS Config rules and compliance status
├── network/                 # VPC, Security Groups, Network ACLs, WAF
├── compliance/              # Compliance matrix and control mappings
└── reports/                 # Additional reports and analysis
```

### Evidence Statistics

- **IAM Policies**: 15
- **IAM Roles**: 23
- **AWS Config Rules**: 22
  - Compliant: 20
  - Non-Compliant: 2
- **CloudTrail Trails**: 1
- **CloudWatch Alarms**: 12
- **Security Groups**: 8

### How to Use This Evidence

1. **Review Compliance Matrix**: Start with `compliance/compliance-matrix.md`
2. **Verify Controls**: Use the validation queries in the compliance matrix
3. **Examine Evidence**: Navigate to specific directories for detailed configuration
4. **Answer Audit Questions**: Reference artifacts by file path in audit responses

### Key Documentation

- [Compliance Matrix](compliance/compliance-matrix.md)
- [CloudFormation Template](infrastructure/cloudformation-template.yaml)
- [Config Compliance Status](config/compliance-status.json)
- [IAM Credential Report](iam/credential-report.csv)

### Support

For questions about this evidence package, refer to:
- [Audit Readiness Guide](../docs/AUDIT_READINESS_GUIDE.md)
- [Auditor Compliance Mapping](../docs/AUDITOR_COMPLIANCE_MAPPING.md)
```

## Compliance Matrix Sample (compliance/compliance-matrix.md)

```markdown
# Compliance Matrix - SOC2
Generated: 2024-11-22 14:30:52
Stack: CloudForge-Prod-SOC2
Region: us-east-1
Audit Period: 2024-01-01 to 2024-12-31

## Executive Summary

This compliance evidence package demonstrates adherence to SOC2 requirements
for the CloudForge CI infrastructure deployment.

## Controls Implemented

### SOC 2 Trust Services Criteria

#### CC6.1 - Logical and Physical Access Controls
- **Evidence**: iam/policies.json, iam/roles.json
- **Status**: Implemented
- **Controls**: IAM policies, Cognito MFA, Security Groups

#### CC6.6 - Network Segmentation
- **Evidence**: network/security-groups.json, network/network-acls.json
- **Status**: Implemented
- **Controls**: VPC security groups, Network ACLs, Private subnets

#### CC6.7 - Transmission Security
- **Evidence**: encryption/acm-certificates.json, network/load-balancers.json
- **Status**: Implemented
- **Controls**: TLS 1.2+, ACM certificates, ALB HTTPS listeners

#### CC7.2 - System Monitoring
- **Evidence**: logging/cloudtrail-trails.json, monitoring/guardduty-detectors.json
- **Status**: Implemented
- **Controls**: CloudTrail, GuardDuty, CloudWatch Alarms

#### CC7.3 - Backup and Recovery
- **Evidence**: encryption/efs-filesystems.json, config/config-rules.json
- **Status**: Implemented
- **Controls**: EFS backups, S3 versioning, Retention policies

## Evidence Artifacts

### Infrastructure
- CloudFormation Template: infrastructure/cloudformation-template.yaml
- Stack Resources: infrastructure/stack-resources.json
- Stack Metadata: infrastructure/stack-metadata.json

### Identity & Access Management
- IAM Policies: iam/policies.json
- IAM Roles: iam/roles.json
- IAM Users: iam/users.json
- Credential Report: iam/credential-report.csv

### Encryption
- KMS Keys: encryption/kms-keys.json
- EFS Encryption: encryption/efs-filesystems.json
- ACM Certificates: encryption/acm-certificates.json
- S3 Bucket Encryption: encryption/s3-bucket-encryption.json

### Logging & Audit
- CloudTrail Configuration: logging/cloudtrail-trails.json
- CloudTrail Events (sample): logging/cloudtrail-events-sample.json
- CloudWatch Log Groups: logging/log-groups.json
- VPC Flow Logs: logging/vpc-flow-logs.json

### Monitoring
- CloudWatch Alarms: monitoring/cloudwatch-alarms.json
- GuardDuty Detectors: monitoring/guardduty-detectors.json
- GuardDuty Findings: monitoring/guardduty-findings.json
- SNS Topics: monitoring/sns-topics.json

### Network Security
- VPCs: network/vpcs.json
- Security Groups: network/security-groups.json
- Network ACLs: network/network-acls.json
- Load Balancers: network/load-balancers.json
- WAF Web ACLs: network/waf-web-acls.json

### AWS Config
- Config Rules: config/config-rules.json
- Compliance Status: config/compliance-status.json
- Remediation Configurations: config/remediation-configurations.json

## Validation Queries

### Verify MFA Enforcement
```bash
jq '.Users[] | select(.PasswordEnabled == true and .MfaActive == false)' iam/credential-report.csv
# Should return empty (all users have MFA)
```

### Verify Encryption at Rest
```bash
jq '.FileSystems[] | {FileSystemId, Encrypted}' encryption/efs-filesystems.json
# All FileSystems should show Encrypted: true
```

### Verify Compliant Config Rules
```bash
jq '.ComplianceByConfigRules[] | select(.Compliance.ComplianceType == "NON_COMPLIANT")' config/compliance-status.json
# Should return empty or minimal non-compliant resources
```

### Verify CloudTrail Logging
```bash
jq '.trailList[] | {Name, IsLogging, IsMultiRegionTrail}' logging/cloudtrail-trails.json
# IsLogging should be true
```

## Report Generated
- Date: 2024-11-22 14:30:52
- Stack: CloudForge-Prod-SOC2
- Region: us-east-1
- Framework: SOC2
- Audit Period: 2024-01-01 to 2024-12-31
```

## Sample Evidence File: config/compliance-status.json

```json
{
  "ComplianceByConfigRules": [
    {
      "ConfigRuleName": "s3-bucket-versioning-enabled",
      "Compliance": {
        "ComplianceType": "COMPLIANT",
        "ComplianceContributorCount": {
          "CappedCount": 3,
          "CapExceeded": false
        }
      }
    },
    {
      "ConfigRuleName": "cloudtrail-enabled",
      "Compliance": {
        "ComplianceType": "COMPLIANT"
      }
    },
    {
      "ConfigRuleName": "encrypted-volumes",
      "Compliance": {
        "ComplianceType": "COMPLIANT",
        "ComplianceContributorCount": {
          "CappedCount": 5,
          "CapExceeded": false
        }
      }
    },
    {
      "ConfigRuleName": "iam-password-policy",
      "Compliance": {
        "ComplianceType": "COMPLIANT"
      }
    },
    {
      "ConfigRuleName": "required-tags",
      "Compliance": {
        "ComplianceType": "NON_COMPLIANT",
        "ComplianceContributorCount": {
          "CappedCount": 2,
          "CapExceeded": false
        }
      }
    },
    {
      "ConfigRuleName": "s3-bucket-public-read-prohibited",
      "Compliance": {
        "ComplianceType": "COMPLIANT",
        "ComplianceContributorCount": {
          "CappedCount": 3,
          "CapExceeded": false
        }
      }
    }
  ]
}
```

## Sample Evidence File: iam/credential-report.csv

```csv
user,arn,user_creation_time,password_enabled,password_last_used,password_last_changed,password_next_rotation,mfa_active,access_key_1_active,access_key_1_last_rotated,access_key_2_active,access_key_2_last_rotated
<root_account>,arn:aws:iam::123456789012:root,2023-01-15T10:30:00+00:00,not_supported,2024-11-20T08:15:00+00:00,not_supported,not_supported,true,false,N/A,false,N/A
admin-user,arn:aws:iam::123456789012:user/admin-user,2023-06-10T14:22:00+00:00,true,2024-11-22T09:30:00+00:00,2024-10-01T12:00:00+00:00,2025-01-01T12:00:00+00:00,true,true,2024-09-15T10:00:00+00:00,false,N/A
jenkins-deploy,arn:aws:iam::123456789012:user/jenkins-deploy,2023-08-05T11:45:00+00:00,false,N/A,N/A,N/A,false,true,2024-10-20T14:30:00+00:00,false,N/A
auditor-external,arn:aws:iam::123456789012:user/auditor-external,2024-11-01T09:00:00+00:00,true,2024-11-22T10:00:00+00:00,2024-11-01T09:00:00+00:00,2025-02-01T09:00:00+00:00,true,false,N/A,false,N/A
```

## Archive Contents

When you extract the `.tar.gz` file:

```bash
tar -tzf audit-evidence-20241122-143052.tar.gz | head -20
```

Output:
```
audit-evidence-20241122-143052/
audit-evidence-20241122-143052/AUDIT_EVIDENCE_README.md
audit-evidence-20241122-143052/infrastructure/
audit-evidence-20241122-143052/infrastructure/cloudformation-template.yaml
audit-evidence-20241122-143052/infrastructure/stack-metadata.json
audit-evidence-20241122-143052/infrastructure/stack-resources.json
audit-evidence-20241122-143052/iam/
audit-evidence-20241122-143052/iam/policies.json
audit-evidence-20241122-143052/iam/roles.json
audit-evidence-20241122-143052/iam/users.json
audit-evidence-20241122-143052/iam/credential-report.csv
audit-evidence-20241122-143052/encryption/
audit-evidence-20241122-143052/encryption/kms-keys.json
audit-evidence-20241122-143052/encryption/efs-filesystems.json
audit-evidence-20241122-143052/encryption/acm-certificates.json
audit-evidence-20241122-143052/encryption/s3-bucket-encryption.json
audit-evidence-20241122-143052/logging/
audit-evidence-20241122-143052/logging/cloudtrail-trails.json
audit-evidence-20241122-143052/logging/cloudtrail-status.json
audit-evidence-20241122-143052/logging/log-groups.json
...
```

## How Auditors Use This Package

### Step 1: Extract and Review

```bash
tar -xzf audit-evidence-20241122-143052.tar.gz
cd audit-evidence-20241122-143052
cat AUDIT_EVIDENCE_README.md
```

### Step 2: Validate Controls

```bash
# Check MFA enforcement
jq '.Users[] | select(.PasswordEnabled == true and .MfaActive == false)' iam/credential-report.csv

# Check encryption
jq '.FileSystems[] | {FileSystemId, Encrypted}' encryption/efs-filesystems.json

# Check compliance
jq '.ComplianceByConfigRules[] | select(.Compliance.ComplianceType == "NON_COMPLIANT")' config/compliance-status.json
```

### Step 3: Review Specific Controls

```bash
# Review CloudFormation template for infrastructure controls
less infrastructure/cloudformation-template.yaml

# Review IAM policies for access controls
jq '.Policies[] | select(.PolicyName | contains("CloudForge"))' iam/policies.json

# Review CloudTrail for audit logging
jq '.trailList[] | {Name, IsLogging, IsMultiRegionTrail, LogFileValidationEnabled}' logging/cloudtrail-trails.json
```

### Step 4: Generate Audit Report

Auditors can use the provided evidence to complete their audit checklist:

- ✅ CC6.1: IAM policies reviewed → iam/policies.json
- ✅ CC6.6: Security groups reviewed → network/security-groups.json
- ✅ CC6.7: TLS certificates verified → encryption/acm-certificates.json
- ✅ CC7.2: CloudTrail enabled → logging/cloudtrail-trails.json
- ✅ CC7.3: EFS encryption enabled → encryption/efs-filesystems.json

## Execution Time

Typical execution times:
- **Small stack** (dev): ~2-3 minutes
- **Medium stack** (staging): ~4-6 minutes
- **Large stack** (production): ~8-12 minutes

Most time is spent on:
1. CloudTrail event sampling (if large date range)
2. S3 bucket encryption checking
3. GuardDuty findings retrieval
4. Config compliance status gathering

## Troubleshooting

### Common Issues

**Issue**: "AWS credentials not configured"
```bash
# Solution: Configure AWS CLI
aws configure
```

**Issue**: "Stack not found"
```bash
# Solution: Verify stack name and region
aws cloudformation list-stacks --region us-east-1 --query 'StackSummaries[?StackStatus!=`DELETE_COMPLETE`].StackName'
```

**Issue**: "Permission denied"
```bash
# Solution: Make script executable
chmod +x scripts/generate-audit-evidence.sh
```

**Issue**: "CloudTrail lookup failed"
```bash
# This is normal if date range exceeds 90 days (AWS limitation)
# The script continues with warning
```

## Next Steps After Generation

1. **Review the evidence package**:
   ```bash
   cd audit-evidence-20241122-143052
   cat AUDIT_EVIDENCE_README.md
   ```

2. **Validate compliance**:
   ```bash
   cat compliance/compliance-matrix.md
   ```

3. **Share with auditors**:
   ```bash
   # Upload to secure storage
   aws s3 cp audit-evidence-20241122-143052.tar.gz s3://audit-evidence-bucket/

   # Or send via secure email
   # (use encrypted email or secure file transfer)
   ```

4. **Keep evidence for retention period**:
   ```bash
   # Archive for required retention period (varies by framework)
   # SOC 2: 7 years
   # HIPAA: 6 years
   # PCI-DSS: 1 year minimum
   ```

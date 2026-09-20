# FedRAMP Controls Mapping

This document maps FedRAMP (Federal Risk and Authorization Management Program) NIST SP 800-53 Rev 5 controls to the CloudForge CI checks planned for them.

> **Status: in development.** FedRAMP support is incomplete and cannot be enabled yet:
> - `complianceFrameworks` accepts only `soc2`, `pci-dss`, `hipaa`, and `gdpr`; `FEDRAMP` and `FEDRAMP-HIGH` fail configuration parsing.
> - `FedRampRules` and `FedRampHighRules` are not registered in `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`, so `FrameworkLoader` does not discover them.
> - The `fedramp-*` AWS Config rules listed below are not created by `ComplianceFactory`.
> - No cdk-nag pack is applied for FedRAMP.
>
> This mapping describes the intended design. It is not evidence of FedRAMP authorization or of controls running in a deployment.

## Overview

The planned FedRAMP implementation consists of:
- **FedRampRules.java**: synthesis-time validation checks for the Moderate baseline (`FEDRAMP`, priority 25)
- **FedRampHighRules.java**: additional checks for the High baseline (`FEDRAMP-HIGH`, priority 26)
- **ComplianceFactory.java**: AWS Config rules for continuous monitoring (not yet implemented for FedRAMP)
- **ComplianceMatrix.java**: framework control mappings

Both rule classes apply only to `staging` and `production` security profiles.

## Baselines

| Baseline | Total Controls | Planned Coverage |
|----------|----------------|------------------|
| FedRAMP Low | 156 | Not planned |
| FedRAMP Moderate | 323 | `FedRampRules` |
| FedRAMP High | 410 | `FedRampRules` plus `FedRampHighRules` |

Most FedRAMP controls are procedural or organizational and cannot be checked from infrastructure configuration. The tables below list only the technical controls with a planned check.

## Control Family Mapping

A ✅ in the Status column means a check for the control is written in `FedRampRules` or `FedRampHighRules`, or an AWS Config rule is planned for it. None of these checks run until FedRAMP can be enabled.

### AC - Access Control

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| AC-2 | Account Management | IAM profile configuration, AWS Config rules | ✅ |
| AC-2(1) | Automated Account Management | `IAM_USER_UNUSED_CREDENTIALS_CHECK` Config rule | ✅ |
| AC-2(3) | Disable Inactive Accounts | AWS Config 90-day credential check | ✅ |
| AC-3 | Access Enforcement | Security groups, IAM policies, least privilege | ✅ |
| AC-4 | Information Flow Enforcement | VPC Flow Logs, network segmentation | ✅ |
| AC-5 | Separation of Duties | IAM role separation validation | ✅ |
| AC-6 | Least Privilege | `IAM_POLICY_NO_STATEMENTS_WITH_ADMIN_ACCESS` | ✅ |
| AC-6(10) | Prohibit Non-Privileged Execute | `IAM_ROOT_ACCESS_KEY_CHECK` | ✅ |
| AC-7 | Unsuccessful Logon Attempts | Cognito lockout configuration | ✅ |
| AC-17 | Remote Access | VPN/bastion requirement validation | ✅ |

### AU - Audit and Accountability

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| AU-2 | Audit Events | CloudTrail enabled, multi-region | ✅ |
| AU-3 | Content of Audit Records | CloudTrail log file validation | ✅ |
| AU-6 | Audit Review | CloudTrail → CloudWatch Logs integration | ✅ |
| AU-9 | Protection of Audit Info | S3 encryption, versioning for logs | ✅ |
| AU-11 | Audit Record Retention | 3-year minimum retention validation | ✅ |
| AU-12 | Audit Record Generation | CloudTrail, VPC Flow Logs, ALB logs | ✅ |

### CA - Assessment, Authorization, and Monitoring

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| CA-7 | Continuous Monitoring | AWS Config enabled validation | ✅ |

### CM - Configuration Management

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| CM-2 | Baseline Configuration | CDK/IaC deployment validation | ✅ |
| CM-3 | Configuration Change Control | CloudTrail for change tracking | ✅ |
| CM-6 | Configuration Settings | AWS Config compliance rules | ✅ |
| CM-8 | System Component Inventory | Resource tagging validation | ✅ |

### CP - Contingency Planning

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| CP-6 | Alternate Storage Site | Cross-region backup validation | ✅ |
| CP-9 | System Backup | `DB_INSTANCE_BACKUP_ENABLED` Config rule | ✅ |
| CP-10 | System Recovery | Multi-AZ deployment validation | ✅ |

### IA - Identification and Authentication

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| IA-2 | Identification and Authentication | OIDC/Cognito authentication validation | ✅ |
| IA-2(1) | MFA Privileged Accounts | `ROOT_ACCOUNT_MFA_ENABLED`, `MFA_ENABLED_FOR_IAM_CONSOLE_ACCESS` | ✅ |
| IA-2(2) | MFA Non-Privileged | Cognito MFA configuration | ✅ |
| IA-5 | Authenticator Management | `IAM_PASSWORD_POLICY` (12+ chars, complexity, 90-day rotation) | ✅ |

### IR - Incident Response

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| IR-4 | Incident Handling | GuardDuty enabled validation | ✅ |
| IR-5 | Incident Monitoring | GuardDuty, Security Hub findings | ✅ |
| IR-6 | Incident Reporting | Security Hub enabled validation | ✅ |

### MP - Media Protection

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| MP-4 | Media Storage | EBS encryption validation | ✅ |
| MP-5 | Media Transport | EFS encryption in transit | ✅ |

### RA - Risk Assessment

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| RA-5 | Vulnerability Monitoring | AWS Config vulnerability rules | ✅ |

### SC - System and Communications Protection

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| SC-7 | Boundary Protection | VPC, security groups, WAF validation | ✅ |
| SC-7(5) | Deny by Default | Security group default deny validation | ✅ |
| SC-8 | Transmission Confidentiality | TLS certificate validation, HTTPS enforcement | ✅ |
| SC-12 | Cryptographic Key Management | KMS with key rotation validation | ✅ |
| SC-13 | Cryptographic Protection | AES-256 encryption validation | ✅ |
| SC-28 | Protection of Info at Rest | `ENCRYPTED_VOLUMES`, `RDS_STORAGE_ENCRYPTED`, `S3_BUCKET_SSL_REQUESTS_ONLY` | ✅ |

### SI - System and Information Integrity

| Control | Name | Implementation | Status |
|---------|------|----------------|--------|
| SI-2 | Flaw Remediation | AWS Config remediation validation | ✅ |
| SI-3 | Malicious Code Protection | GuardDuty malware detection | ✅ |
| SI-4 | System Monitoring | `GUARDDUTY_ENABLED_CENTRALIZED`, CloudWatch | ✅ |
| SI-7 | Software Integrity | CloudTrail file validation | ✅ |

## Planned AWS Config Rules

These rule names are planned and are not yet created by `ComplianceFactory`.

### Access Control (AC)
- `fedramp-iam-user-unused-credentials` - AC-2(3)
- `fedramp-access-keys-rotated` - AC-2(1)
- `fedramp-iam-no-admin-policy` - AC-6
- `fedramp-root-access-key-check` - AC-6(10)

### Audit (AU)
- `fedramp-cloudtrail-enabled` - AU-2
- `fedramp-multi-region-cloudtrail` - AU-2
- `fedramp-cloudtrail-cloudwatch-logs` - AU-6

### Identification & Authentication (IA)
- `fedramp-root-account-mfa-enabled` - IA-2(1)
- `fedramp-mfa-enabled-for-iam-console` - IA-2(1)
- `fedramp-iam-password-policy` - IA-5(1)

### System & Communications (SC)
- `fedramp-vpc-default-sg-closed` - SC-7
- `fedramp-restricted-ssh` - SC-7
- `fedramp-elb-tls-https-only` - SC-8
- `fedramp-alb-http-to-https-redirect` - SC-8
- `fedramp-kms-key-rotation` - SC-12
- `fedramp-encrypted-volumes` - SC-28
- `fedramp-s3-bucket-ssl-requests-only` - SC-8/SC-28

### System Integrity (SI)
- `fedramp-guardduty-enabled` - SI-4
- `fedramp-securityhub-enabled` - SI-4/IR-6

### Contingency Planning (CP) - Database Rules
- `fedramp-db-instance-backup-enabled` - CP-9
- `fedramp-rds-storage-encrypted` - SC-28
- `fedramp-rds-instance-public-access-check` - SC-7
- `fedramp-rds-multi-az-support` - CP-10 (PRODUCTION only)

## Planned Usage

The following configuration is not accepted yet; it shows the intended interface.

### Enable FedRAMP Moderate
```json
{
  "complianceFrameworks": "FEDRAMP"
}
```

### Enable FedRAMP High
```json
{
  "complianceFrameworks": "FEDRAMP-HIGH"
}
```

### Enable Multiple Frameworks
```json
{
  "complianceFrameworks": "FEDRAMP,HIPAA,SOC2"
}
```

## Evidence Collection

For FedRAMP assessments, evidence can be collected from:

1. **AWS Config Dashboard**: Compliance status for all rules
2. **CloudTrail**: API call audit logs
3. **Security Hub**: Aggregated security findings
4. **GuardDuty**: Threat detection findings
5. **AWS Audit Manager**: Evidence collection with an AWS-provided FedRAMP framework (CloudForge does not create a FedRAMP assessment)

## POA&M Considerations

Controls that may require procedural/manual evidence:
- AT (Awareness & Training): Personnel training records
- PE (Physical & Environmental): AWS shared responsibility
- PL (Planning): Security documentation
- PS (Personnel Security): Background check records
- PM (Program Management): Governance documentation

## References

- [FedRAMP Official Website](https://www.fedramp.gov/)
- [NIST SP 800-53 Rev 5](https://csrc.nist.gov/publications/detail/sp/800-53/rev-5/final)
- [AWS FedRAMP Compliance](https://aws.amazon.com/compliance/fedramp/)
- [AWS Config FedRAMP Best Practices](https://docs.aws.amazon.com/config/latest/developerguide/operational-best-practices-for-fedramp-moderate.html)

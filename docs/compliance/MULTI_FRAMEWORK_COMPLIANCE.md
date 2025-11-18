# Multi-Framework Compliance

## Reality Check

This gives you **infrastructure controls only**. Not full compliance. You still need:

- Organizational policies and procedures
- Training programs
- Third-party audits (QSA for PCI-DSS, CPA for SOC 2, etc.)
- Application-level controls
- Documentation and evidence beyond what infrastructure provides

## What This Does

Automated validation for infrastructure controls across PCI-DSS, HIPAA, SOC 2, and GDPR. Deployment fails if required controls are missing.

When you enable AWS Audit Manager, the system creates **one assessment per framework** you specify. For example, if you set `complianceFrameworks: "HIPAA,SOC2,GDPR"`, you'll get:
- One HIPAA assessment actively collecting evidence
- One SOC2 assessment actively collecting evidence
- One GDPR assessment actively collecting evidence
- All using a shared S3 bucket and IAM role
- All evidence automatically collected from CloudTrail, Config, GuardDuty, WAF, etc.

**Lifecycle Management**: Assessments are CloudFormation-managed resources, so they:
- Appear in AWS Audit Manager console when created
- Are tracked as part of your CloudFormation stack
- Are automatically deleted when you run `cdk destroy`

## Quick Config

```json
{
  "securityProfile": "production",
  "networkMode": "private-with-nat",
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-mycompany",
  "wafEnabled": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "complianceFrameworks": "PCI-DSS,HIPAA,SOC2,GDPR"
}
```

Set `auditManagerEnabled=true` to enable both:
1. **Build-time validation** - Deployment fails if required controls are missing
2. **Runtime evidence collection** - Creates one AWS Audit Manager assessment per framework

GuardDuty is automatically enabled with PRODUCTION security profile.

## When Validation Runs

| Framework | DEV | STAGING | PRODUCTION |
|-----------|-----|---------|------------|
| **PCI-DSS** | No | No | Yes |
| **HIPAA** | No | Yes | Yes |
| **SOC 2** | No | Yes | Yes |
| **GDPR** | No | Yes | Yes |

## Control Mappings

Here's how infrastructure controls map to multiple frameworks:

| Control | PCI-DSS | HIPAA | SOC 2 | GDPR |
|---------|---------|-------|-------|------|
| **Encryption at Rest** | Req 3.4 | §164.312(a)(2)(iv) | CC6.1 | Art. 32(1)(a) |
| **Encryption in Transit** | Req 4.1 | §164.312(e)(1) | CC6.7 | Art. 32(1)(a) |
| **Network Segmentation** | Req 1.2-1.3 | §164.312(e)(1) | CC6.6 | Art. 25(1) |
| **Access Control** | Req 7.1-7.2 | §164.312(a)(1) | CC6.1-6.2 | Art. 25(2) |
| **Authentication** | Req 8.2-8.3 | §164.312(d) | CC6.2 | Art. 32(1)(b) |
| **Audit Logging** | Req 10.1-10.3 | §164.312(b) | CC7.2 | Art. 30 |
| **Security Monitoring** | Req 11.4-11.5 | §164.308(a)(1)(ii)(D) | CC7.2 | Art. 32(1)(d) |
| **WAF Protection** | Req 6.6 | §164.312(e)(1) | CC6.6 | Art. 32(1) |
| **High Availability** | Req 12.10.4 | §164.308(a)(7)(ii)(B) | A1.2 | Art. 32(1)(b) |

## Framework Details

### PCI-DSS v3.2.1

**Validator**: `PciDssRules.java`
**Enforced**: PRODUCTION only
**Scope**: Card data environment

Checks for firewall rules, encryption, WAF, GuardDuty (threat detection), authentication, audit logging (2-year retention), and security monitoring.

**Key requirements**:
- WAF for Req 6.6 (web application firewall)
- GuardDuty for Req 11.4 (intrusion detection)

### HIPAA Security Rule

**Validator**: `HipaaRules.java`
**Enforced**: PRODUCTION and STAGING
**Scope**: Protected health information (PHI)

Checks for encryption (at rest and in transit), authentication, audit controls, backups, and 6-year log retention.

**Key requirement**: 6-year retention means you need S3 archival beyond CloudWatch's 2-year limit.

### SOC 2

**Validator**: `Soc2Rules.java`
**Enforced**: PRODUCTION and STAGING
**Scope**: Trust Services Criteria

Covers Common Criteria (security), Availability (high availability, backups), and Confidentiality (encryption, access control).

**Key requirement**: Type II audit needs 6-12 months of evidence. Plan ahead.

### GDPR

**Validator**: `GdprRules.java`
**Enforced**: All profiles
**Scope**: EU personal data

Checks for encryption by default, access controls, logging (records of processing), breach detection (GuardDuty), and security monitoring.

**Key requirement**: Deploy in EU regions for EU data (data residency).

## Examples

### Healthcare SaaS (HIPAA + SOC 2 + GDPR)

```json
{
  "securityProfile": "production",
  "networkMode": "private-with-nat",
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "wafEnabled": true,
  "auditManagerEnabled": true,
  "complianceFrameworks": "HIPAA,SOC2,GDPR",
  "logRetentionDays": 2555,
  "region": "eu-west-1"
}
```

### Payment Processor (PCI-DSS + SOC 2)

```json
{
  "securityProfile": "production",
  "networkMode": "private-with-nat",
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "wafEnabled": true,
  "auditManagerEnabled": true,
  "complianceFrameworks": "PCI-DSS,SOC2",
  "logRetentionDays": 730
}
```

## Common Errors

**Error: "Private network mode required"**

```
❌ PCI-DSS Req 1.3: Public network mode prohibited
❌ HIPAA §164.312(e)(1): Private network mode required
```

Fix: `"networkMode": "private-with-nat"`

**Error: "Authentication must be enabled"**

```
❌ PCI-DSS Req 8.2: Authentication must be enabled
❌ HIPAA §164.312(d): Authentication required
```

Fix:
```json
{
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-mycompany"
}
```

**Error: "WAF recommended/required"**

```
❌ PCI-DSS Req 6.6: WAF strongly recommended
```

Fix: `"wafEnabled": true`

## Costs

Multi-framework compliance adds roughly $200-300/month:

| Service | Monthly Cost |
|---------|--------------|
| NAT Gateway | $45 |
| GuardDuty | $30-100 |
| AWS Config | $10-20 |
| WAF | $5+ |
| Audit Manager | $1/100k events |
| Additional Logging | $10-30 |
| **Total** | **$101-226/month** |

## Evidence Collection

AWS Audit Manager automatically collects evidence from:
- CloudTrail (API activity)
- AWS Config (configuration compliance)
- VPC Flow Logs (network traffic)
- CloudWatch Logs (application logs)
- GuardDuty (threat detection)
- WAF (web traffic)

You still need to provide:
- Security policies
- Risk assessments
- Training records
- Incident response plans
- Access review logs
- Penetration test reports (PCI-DSS)
- Business Associate Agreements (HIPAA)
- SOC 2 system description
- GDPR data processing agreements

## More Info

- [PCI_DSS_COMPLIANCE.md](PCI_DSS_COMPLIANCE.md) - PCI-DSS deployment guide
- [PCI_DSS_APPLICATION_SECURITY.md](PCI_DSS_APPLICATION_SECURITY.md) - Jenkins hardening for PCI
- [PCI_DSS_README.md](PCI_DSS_README.md) - PCI-DSS overview

## Files

**Validators**:
- `/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/PciDssRules.java`
- `/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/HipaaRules.java`
- `/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/Soc2Rules.java`
- `/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/GdprRules.java`
- `/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ComplianceMatrix.java`

# Multi-Framework Compliance

:::warning Infrastructure Controls Only
CloudForge provides **infrastructure controls only**, not certification or full compliance. You still need:

- Organizational policies and procedures
- Training programs
- Third-party audits (QSA for PCI-DSS, CPA for SOC 2, etc.)
- Application-level controls
- Documentation and evidence beyond what infrastructure provides
:::

## What This Does

Synthesis-time validation of infrastructure controls for PCI-DSS, HIPAA, SOC 2, and GDPR. In `enforce` mode (the default for `production`), synthesis fails when a required control is missing; in `advisory` mode, violations are reported as warnings.

When `auditManagerEnabled` is `true`, CloudForge creates **one AWS Audit Manager assessment per selected framework**. For example, `complianceFrameworks: "hipaa,soc2,gdpr"` creates HIPAA, SOC2, and GDPR assessments that share one report bucket and IAM role. Evidence comes from the data sources that Audit Manager collects, such as CloudTrail, AWS Config, and Security Hub.

Assessments are CloudFormation-managed resources: they are tracked in your stack and deleted by `cdk destroy`.

## AWS Config Rules Deployment

When `awsConfigEnabled` is `true`, CloudForge deploys AWS Config rules for continuous evaluation:

- **Base rules**, deployed for every framework selection: encryption (EBS, S3), S3 public access and versioning, the IAM password policy, and CloudTrail.
- **Framework-specific rules**, deployed through CloudFormation conditions (`EnablePciDssRules`, `EnableSoc2Rules`, `EnableHipaaRules`, `EnableGdprRules`) only for selected frameworks. Database rules also require `provisionDatabase`.
- **Collected rules**, registered by other factories (for example GuardDuty or VPC Flow Logs) only when the corresponding resource is created.

Rules required by several frameworks are deployed once.

## Quick Config

```json
{
  "securityProfile": "production",
  "networkMode": "private-with-nat",
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "my-app-auth",
  "wafEnabled": true,
  "guardDutyEnabled": true,
  "awsConfigEnabled": true,
  "auditManagerEnabled": true,
  "complianceFrameworks": "pci-dss,hipaa,soc2,gdpr"
}
```

Set `auditManagerEnabled=true` to enable both:
1. **Synthesis-time validation** - Installs the CloudForge `FrameworkRules` validators
2. **Evidence collection** - Creates one AWS Audit Manager assessment per framework

cdk-nag packs run for `production` stacks with at least one framework selected, independent of `auditManagerEnabled`.

## When Validation Runs

Framework validators run only when `auditManagerEnabled` is `true`, and each validator also checks the security profile:

| Framework | DEV | STAGING | PRODUCTION |
|-----------|-----|---------|------------|
| **PCI-DSS** | No | No | Yes |
| **HIPAA** | No | Yes | Yes |
| **SOC 2** | No | Yes | Yes |
| **GDPR** | No | Yes | Yes |

## alwaysLoad Frameworks

Validators annotated with `alwaysLoad = true` are installed whenever `auditManagerEnabled` is `true`, even when no framework is selected. The registered cross-framework validators are `KeyManagementRules`, `DatabaseSecurityRules`, `AdvancedMonitoringRules`, `ThreatProtectionRules`, `IncidentResponseRules`, `ComputeSecurityRules`, `LambdaSecurityRules`, `CdnApiSecurityRules`, `ElbSecurityRules`, `MessagingSecurityRules`, and `IamSecurityRules`.

### ConfigurationValidationRules

`ConfigurationValidationRules` is annotated as an `alwaysLoad` validator, but it is not listed in `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`, so `FrameworkLoader` does not currently discover it.

**Purpose**: Validate basic deployment configuration (for example, subdomain without domain, or OIDC without HTTPS) independently of compliance frameworks.

**Framework ID**: `CONFIG`
**Priority**: 1
**alwaysLoad**: true

**Validation Rules**:
1. **CONFIG-SUBDOMAIN-DOMAIN** - Subdomain requires parent domain
2. **CONFIG-OIDC-HTTPS** - ALB OIDC authentication requires HTTPS

**Implementation**: See [ConfigurationValidationRules.java](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ConfigurationValidationRules.java)

**Testing**: `compliance-test-matrix.csv` contains rows for these rules.

### Organizational and extended validators

`HipaaOrganizationalRules` (`HIPAA-Organizational`), `GdprOrganizationalRules` (`GDPR-Organizational`), and `Iso27001Rules` (`ISO-27001`) are registered but are installed only when their framework ID is selected. `complianceFrameworks` does not accept those IDs, so these validators do not currently run.

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

### PCI DSS v4.0.1

**Validator**: `PciDssRules.java`
**Enforced**: PRODUCTION only
**Scope**: Card data environment

Checks for firewall rules, encryption, WAF, GuardDuty (threat detection), authentication, audit logging (at least 1-year retention), and security monitoring. The cdk-nag pack applied for PCI-DSS is `PCIDSS321Checks`, the most recent PCI pack cdk-nag provides.

**Key requirements**:
- WAF for Req 6.6 (web application firewall)
- GuardDuty for Req 11.4 (intrusion detection)

### HIPAA Security Rule

**Validator**: `HipaaRules.java`
**Enforced**: PRODUCTION and STAGING
**Scope**: Protected health information (PHI)

Checks for encryption (at rest and in transit), authentication, audit controls, backups, and 6-year log retention.

**Key requirement**: Log retention of at least 6 years (2190 days).

### SOC 2

**Validator**: `Soc2Rules.java`
**Enforced**: PRODUCTION and STAGING
**Scope**: Trust Services Criteria

Covers Common Criteria (security), Availability (high availability, backups), and Confidentiality (encryption, access control).

**Key requirement**: A Type II audit examines controls over a period, typically 6-12 months, so evidence collection must start well before the audit.

### GDPR

**Validator**: `GdprRules.java`
**Enforced**: PRODUCTION and STAGING
**Scope**: EU personal data

Checks for encryption by default, access controls, logging (records of processing), breach detection (GuardDuty), and security monitoring.

**Key requirement**: Deploy in EU regions for EU data, or set `gdprDataTransferApproved` when a transfer mechanism is in place (data residency).

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
  "complianceFrameworks": "hipaa,soc2,gdpr",
  "logRetentionDays": "3653",
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
  "complianceFrameworks": "pci-dss,soc2",
  "logRetentionDays": "731"
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
  "cognitoDomainPrefix": "my-app-auth"
}
```

**Error: "WAF recommended/required"**

```
❌ PCI-DSS Req 6.6: WAF strongly recommended
```

Fix: `"wafEnabled": true`

## Costs

Multi-framework configurations typically add charges for NAT gateways, GuardDuty, AWS Config, WAF, Audit Manager, and additional logging. Charges depend on region and usage; use the [AWS Pricing Calculator](https://calculator.aws.amazon.com/) for an estimate.

## Evidence Collection

AWS Audit Manager collects evidence from the data sources configured for its frameworks, which can include:
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

- [PCI_DSS_COMPLIANCE.md](PCI_DSS_COMPLIANCE.md) - PCI-DSS deployment guide and overview
- [PCI_DSS_APPLICATION_SECURITY.md](PCI_DSS_APPLICATION_SECURITY.md) - Jenkins hardening for PCI

## Files

**Validators**:
- [PciDssRules.java](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/PciDssRules.java)
- [HipaaRules.java](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/HipaaRules.java)
- [Soc2Rules.java](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/Soc2Rules.java)
- [GdprRules.java](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/GdprRules.java)
- [ComplianceMatrix.java](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ComplianceMatrix.java)

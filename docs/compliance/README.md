# CloudForge CI Compliance Documentation

## Overview

CloudForge CI configures AWS infrastructure controls associated with HIPAA, SOC 2, PCI DSS, and GDPR. Selected frameworks affect resource settings, retention policies, AWS Config rules, and supported remediation actions.

These controls can contribute infrastructure evidence to a compliance program, but they do not provide certification or replace organizational policies, application controls, legal review, or an independent audit.

**Capabilities:**
- **Framework-based configuration** - Selected frameworks determine applicable infrastructure settings
- **Synthesis-time validation** - cdk-nag packs and CloudForge framework validators check the synthesized stack
- **Continuous evaluation** - AWS Config evaluates supported resources after deployment
- **Supported remediation** - Some findings can invoke configured SSM remediation actions
- **Evidence sources** - CloudTrail, AWS Config, and related services record infrastructure activity

### Framework status

| Framework | `complianceFrameworks` value | Status |
|-----------|------------------------------|--------|
| HIPAA Security Rule | `hipaa` | Supported |
| SOC 2 Trust Services Criteria | `soc2` | Supported |
| PCI DSS v4.0.1 | `pci-dss` | Supported |
| GDPR | `gdpr` | Supported |
| ISO/IEC 27001 | not accepted | A validator class (`Iso27001Rules`) and cfn-guard rules exist, but the value is not accepted by `complianceFrameworks` yet |
| FedRAMP Moderate / High | not accepted | In development; see [FedRAMP Controls Mapping](FEDRAMP_CONTROLS_MAPPING.md) |

Values are case-insensitive and may be separated by commas, spaces, or `+`. Any other value fails configuration parsing.

### Configuration flags

Selecting a framework alone does not create AWS Config or Audit Manager resources. The relevant flags default to `false`:

| Property | Effect |
|----------|--------|
| `securityProfile` | `dev`, `staging`, or `production`. CloudTrail is created for `staging` and `production`. cdk-nag packs run only for `production` with at least one framework selected. |
| `complianceMode` | `enforce`, `advisory`, or `disabled`. Defaults to `enforce` for `production` and `advisory` otherwise. |
| `awsConfigEnabled` | Deploys AWS Config rules, conformance packs, and remediation configurations. |
| `createConfigInfrastructure` | Creates the Config recorder and delivery channel. AWS allows one of each per account and region, so set this on only one stack. |
| `auditManagerEnabled` | Creates Audit Manager assessments and installs the CloudForge framework validators (`FrameworkRules`). |

---

## Quick Links

### For Developers
- **[Automated Compliance Features](AUTOMATED_COMPLIANCE.md)** - Technical deep-dive into implementation
- **[Deployment Guide](DEPLOYMENT_GUIDE.md)** - Step-by-step deployment instructions
- **[Multi-Framework Compliance](MULTI_FRAMEWORK_COMPLIANCE.md)** - Supporting multiple frameworks simultaneously
- **[Retained Resources](RETAINED_RESOURCES.md)** - Every deletion-protected / RemovalPolicy.RETAIN resource, why, and how to remove it

### For Compliance Teams
- **[Quick Start Guide](QUICK_START_GUIDE.md)** - Initial compliance-control configuration
- **[PCI-DSS Compliance](PCI_DSS_COMPLIANCE.md)** - PCI-DSS specific requirements
- **[Multi-Framework Compliance](MULTI_FRAMEWORK_COMPLIANCE.md)** - Detailed framework mapping

### Cost Planning
- **[Example Cost Estimate](#example-cost-estimate)** - Illustrative service usage for a production deployment

---

## Features

### 1. S3 Lifecycle Management

Compliance log buckets receive lifecycle rules that transition objects to colder storage classes and expire them after the retention period of the strictest selected framework:

```
Day 0-90    → S3 Standard           - Immediate availability
Day 90-365  → S3 Glacier            - Infrequent access
Day 365+    → S3 Glacier Deep Archive (when retention exceeds one year)
```

**Retention by Framework:**
| Framework | Retention Period |
|-----------|------------------|
| HIPAA | 6 years (2190 days) |
| SOC2 | 2 years (730 days) |
| PCI-DSS | 1 year (365 days) |

Without a selected framework, retention follows the security profile: 6 years for `production`, 2 years for `staging`, and 1 year for `dev`.

**What's Included:**
- CloudTrail audit logs
- AWS Config compliance data
- Application access logs (ALB)
- Audit Manager evidence

---

### 2. Automatic IAM Password Policy Enforcement

When `awsConfigEnabled` is `true`, an AWS Config rule evaluates the account password policy. When the stack also creates the Config recorder (`createConfigInfrastructure: true`), an automatic remediation applies the `AWSConfigRemediation-SetIAMPasswordPolicy` SSM document when the policy is missing or weaker than required.

**How It Works:**
1. Config detects a missing or weak password policy
2. SSM Automation applies the required policy
3. Config re-evaluates and confirms compliance
4. All actions logged to CloudTrail

**Password Requirements:**
| Framework | Minimum Length | Rotation | Reuse Prevention |
|-----------|----------------|----------|------------------|
| HIPAA | 14 characters | 90 days | 24 passwords |
| SOC2 | 12 characters | 90 days | 12 passwords |
| PCI-DSS | 8 characters | 90 days | 4 passwords |

All frameworks require uppercase, lowercase, numbers, and symbols. PCI DSS v4.0.1 Req 8.3.6 requires 12 characters; the 8-character IAM account policy for PCI-DSS alone predates v4.0 and is a known gap. Select an additional framework or adjust the policy if IAM users access the cardholder data environment. Without a selected framework, the minimum length is 14 characters for `production` and 12 otherwise.

---

### 3. Audit Log Protection

S3 versioning is enabled on compliance log buckets so that overwritten or deleted objects keep their prior versions. CloudTrail log file validation detects modification of delivered log files. Versioning alone does not prevent deletion; set `s3ObjectLockEnabled` when write-once retention is required.

**Applied To:**
- CloudTrail logs
- Config snapshots
- ALB access logs
- Audit Manager evidence

---

### 4. Multi-Framework Support

When several frameworks are selected, CloudForge applies the strictest requirement for each setting.

**Example:**
```json
{
  "complianceFrameworks": "hipaa,pci-dss,soc2"
}
```

**Result:**
- **Retention**: 6 years (HIPAA is strictest)
- **Password**: 14 characters (HIPAA is strictest)
- **Reuse**: 24 passwords (HIPAA is strictest)

---

## Supported Compliance Frameworks

### HIPAA - Health Insurance Portability and Accountability Act
**Industry:** Healthcare
**Key Requirements:**
- 6-year data retention
- Encryption at rest and in transit
- Access controls and audit logging
- Breach notification procedures

**CloudForge Implementation:**
- 6-year S3 lifecycle policies
- 14-character passwords with complexity
- API activity logging via CloudTrail
- Encryption using S3-managed keys (SSE-S3)

---

### SOC2 - Service Organization Control 2
**Industry:** SaaS, Cloud Services
**Key Requirements:**
- Security, availability, processing integrity
- Confidentiality and privacy controls
- Periodic independent audits

**CloudForge Implementation:**
- 2-year log retention for audit evidence
- 12-character passwords
- Continuous monitoring via AWS Config
- Evidence collection through AWS Audit Manager (when `auditManagerEnabled` is `true`)

---

### PCI-DSS - Payment Card Industry Data Security Standard
**Industry:** E-commerce, Payment Processing
**Key Requirements:**
- 1-year log retention (3 months immediately available)
- Network security controls
- Regular vulnerability scanning
- Incident response procedures

**CloudForge Implementation:**
- 1-year retention, 90 days in S3 Standard
- 8-character minimum passwords
- WAF protection on the ALB
- CloudWatch alarms for security events

---

### GDPR - General Data Protection Regulation
**Industry:** EU Operations, Privacy-Focused
**Key Requirements:**
- Data minimization and retention limits
- Right to erasure ("right to be forgotten")
- Data breach notification (72 hours)
- Privacy by design

**CloudForge Implementation:**
- Data residency validation for the deployment region
- S3 versioning for data recovery
- Access controls and encryption
- CloudWatch alarms for security events

---

## How It Works

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     CloudForge CI Stack                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
            ┌─────────────────────────────────┐
            │   Compliance Framework Config    │
            │  (HIPAA, SOC2, PCI-DSS, GDPR)   │
            └─────────────────────────────────┘
                              │
                ┌─────────────┼─────────────┐
                │             │             │
                ▼             ▼             ▼
        ┌──────────┐   ┌──────────┐  ┌──────────┐
        │ S3 Rules │   │   IAM    │  │CloudTrail│
        │Lifecycle │   │ Password │  │  Logging │
        │Versioning│   │  Policy  │  │ Retention│
        └──────────┘   └──────────┘  └──────────┘
                              │
                              ▼
                ┌─────────────────────────────┐
                │       AWS Config            │
                │  (Continuous Monitoring)    │
                └─────────────────────────────┘
                              │
                ┌─────────────┼─────────────┐
                │             │             │
                ▼             ▼             ▼
        ┌──────────┐   ┌──────────┐  ┌──────────┐
        │ Detect   │   │Remediate │  │  Verify  │
        │Non-      │──▶│  Using   │──▶│Compliance│
        │Compliant │   │   SSM    │   │  Status  │
        └──────────┘   └──────────┘  └──────────┘
```

### Deployment Flow

1. **Configure frameworks in the deployment context**
   ```json
   {
     "securityProfile": "production",
     "complianceFrameworks": "hipaa,soc2",
     "awsConfigEnabled": true
   }
   ```

2. **CDK synthesizes CloudFormation**
   - Determines strictest requirements
   - Generates Config rules with parameters
   - Creates remediation configurations

3. **CloudFormation deploys resources**
   - S3 buckets with lifecycle rules
   - Config rules and remediation
   - IAM roles for automation

4. **AWS Config monitors compliance**
   - Continuous evaluation of resources
   - Automatic remediation when non-compliant
   - Compliance status dashboard

5. **Review evaluation results**
   - Config dashboard shows compliance
   - CloudWatch alarms for violations
   - Audit Manager collects evidence

---

## Example Cost Estimate

### Monthly Cost Breakdown (PRODUCTION with HIPAA)

**AWS Services:**
| Service | Usage | Cost |
|---------|-------|------|
| AWS Config | 10 rules, 50 resources | $25 |
| S3 Storage | 100 GB initial | $2.30 |
| S3 Glacier | 200 GB | $0.80 |
| S3 Deep Archive | 500 GB | $0.50 |
| CloudTrail | All events | $5 |
| Systems Manager | Automation | $2 |
| CloudWatch | Alarms & Logs | $5 |
| **Total** | | **~$40/month** |

This example is not a quote or savings projection. Actual charges depend on region, resource count, evaluation frequency, log volume, retention, and current AWS pricing. Use the [AWS Pricing Calculator](https://calculator.aws.amazon.com/) for a workload-specific estimate.

---

## Getting Started

### Prerequisites

- AWS account with permissions to deploy CloudFormation, IAM, AWS Config, and CloudTrail resources
- AWS CDK CLI
- The JDK version set in the root `pom.xml`, and Maven

### Initial Setup

```bash
# 1. Clone repository
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core

# 2. Build all modules
mvn -DskipTests install

# 3. Deploy with the Interactive Deployer, which prompts for configuration
cd cfc-testing
cdk deploy

# When prompted, select:
# - Compliance Frameworks: HIPAA, SOC2
# - Security Profile: PRODUCTION
# - Enable AWS Config: Yes
# - Enable ALB Access Logging: Yes

# 4. Review Config rule evaluations
aws configservice describe-compliance-by-config-rule \
  --query 'ComplianceByConfigRules[*].[ConfigRuleName,Compliance.ComplianceType]' \
  --output table
```

**Example output** (rule names vary by stack):
```
----------------------------------------
|  DescribeComplianceByConfigRule       |
+----------------------------------+----+
|  IAMPasswordPolicyRule           | COMPLIANT |
|  S3VersioningRule                | COMPLIANT |
|  CloudTrailEnabledRule           | COMPLIANT |
+----------------------------------+----+
```

### Next Steps

1. **[Read Deployment Guide](DEPLOYMENT_GUIDE.md)** - Detailed deployment instructions
2. **[Configure Monitoring](#monitoring-setup)** - Set up alerts and dashboards
3. **[Schedule Audits](#compliance-audits)** - Establish regular compliance reviews

---

## Monitoring Setup

### CloudWatch Dashboard

CloudForge does not ship a compliance dashboard definition. To create one, write a dashboard body and publish it:

```bash
aws cloudwatch put-dashboard \
  --dashboard-name CloudForgeCompliance \
  --dashboard-body file://compliance-dashboard.json
```

**Suggested widgets:**
- Config rule compliance status
- S3 bucket sizes and costs
- CloudTrail event counts
- Remediation execution history

### SNS Notifications

Subscribe to get alerts for compliance violations:

```bash
# Get SNS topic ARN
aws sns list-topics --query 'Topics[?contains(TopicArn, `config`)].TopicArn' --output text

# Subscribe to email
aws sns subscribe \
  --topic-arn <TOPIC_ARN> \
  --protocol email \
  --notification-endpoint compliance@yourcompany.com
```

---

## Compliance Audits

### Monthly Checklist

- [ ] Review Config rule compliance dashboard
- [ ] Check S3 storage costs in Cost Explorer
- [ ] Verify CloudTrail is logging all events
- [ ] Review IAM users (ensure no direct policy attachments)
- [ ] Check remediation execution history
- [ ] Verify backup retention policies

### Quarterly Review

- [ ] Run AWS Audit Manager assessment
- [ ] Review access logs for anomalies
- [ ] Update compliance documentation
- [ ] Test disaster recovery procedures
- [ ] Review and update security policies

### Annual Audit

- [ ] Complete SOC2 Type 2 audit (if applicable)
- [ ] HIPAA risk assessment
- [ ] PCI-DSS vulnerability scans
- [ ] Review all compliance documentation
- [ ] Update business continuity plan

---

## FAQs

**Q: What happens if I delete the CloudFormation stack?**
A: Account-level settings (password policy, EBS encryption) persist. S3 buckets are retained in PRODUCTION (RemovalPolicy.RETAIN).

**Q: Can I customize the retention periods?**
A: Yes, but ensure you meet minimum compliance requirements for your frameworks. Customization requires code changes.

**Q: Does this work with AWS Organizations?**
A: Organization-wide deployment requires separate StackSets or account-provisioning configuration; validate that workflow for your environment.

**Q: How often does Config evaluate rules?**
A: Continuously for configuration changes, plus periodic evaluations every 24 hours.

**Q: Can I disable auto-remediation?**
A: The optional remediations (`enableS3VersioningRemediation`, `enableCloudTrailBucketAccessRemediation`, `enableRdsDeletionProtectionRemediation`, `enableRdsAutoMinorVersionUpgradeRemediation`) are off by default. The password-policy remediation is attached automatically when the stack creates the Config recorder. For `production` stacks where the stack also creates the Config recorder, the framework Config rules add account-level remediations that re-enable GuardDuty (PCI-DSS), Security Hub, Inspector, and Macie (SOC2). `ComplianceFactory` reads `enableGuardDutyRemediation`, `enableSecurityHubRemediation`, `enableInspectorRemediation`, and `enableMacieRemediation` to control these, but the keys are not yet exposed through `DeploymentContext`, so they cannot be turned off from configuration. Changing either behavior currently requires a code change in `ComplianceFactory`.

**Q: What if remediation fails?**
A: The password-policy and S3-versioning remediations retry up to 5 times at 60-second intervals; the CloudTrail bucket-access and RDS remediations retry up to 3 times at 120-second intervals. Check the SSM Automation execution history for errors.

**Q: How do I provide infrastructure evidence to auditors?**
A: Configure AWS Audit Manager and the relevant logging services, then review exported evidence with your compliance team or auditor.

**Q: Can I add custom compliance rules?**
A: Yes. Add Config rules in `ComplianceFactory.java`, or add a `FrameworkRules` implementation as described in [Validation Architecture](VALIDATION_ARCHITECTURE.md).

---

## Troubleshooting

### Common Issues

**Issue:** Config rules show INSUFFICIENT_DATA
**Fix:** Trigger manual evaluation: `aws configservice start-config-rules-evaluation`

**Issue:** Password policy not updating
**Fix:** Check SSM Automation role has `iam:UpdateAccountPasswordPolicy` permission

**Issue:** S3 lifecycle not applied
**Fix:** Verify `complianceFrameworks` is set in the deployment context

**Issue:** High AWS costs
**Fix:** Review S3 storage class distributions. Ensure lifecycle transitions are working.

For more troubleshooting, see **[Deployment Guide - Troubleshooting](DEPLOYMENT_GUIDE.md#troubleshooting-common-issues)**.

---

## Documentation Index

### Getting Started
- [README](README.md) - This file
- [Quick Start Guide](QUICK_START_GUIDE.md) - Initial configuration
- [Deployment Guide](DEPLOYMENT_GUIDE.md) - Detailed deployment

### Technical Documentation
- [Automated Compliance](AUTOMATED_COMPLIANCE.md) - Feature deep-dive
- [Multi-Framework Compliance](MULTI_FRAMEWORK_COMPLIANCE.md) - Multiple frameworks
- [AWS Config Multi-Stack](AWS_CONFIG_MULTI_STACK.md) - Several stacks in one account and region
- [Validation Architecture](VALIDATION_ARCHITECTURE.md) - Validation layers
- [CSV Parameterized Testing](CSV_PARAMETERIZED_TESTING.md) - Compliance test matrix
- [Validator Exceptions](VALIDATOR_EXCEPTIONS.md) - Documented validator exceptions

### Framework-Specific
- [PCI-DSS Compliance](PCI_DSS_COMPLIANCE.md) - PCI-DSS requirements
- [PCI-DSS Application Security](PCI_DSS_APPLICATION_SECURITY.md) - App security
- [Multi-Framework Compliance](MULTI_FRAMEWORK_COMPLIANCE.md) - Framework mapping
- [FedRAMP Controls Mapping](FEDRAMP_CONTROLS_MAPPING.md) - In development

---

## Support & Contributing

### Get Help
- **Issues**: [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
- **Email**: support@cloudforgeci.com
- **Documentation**: [docs/compliance/](.)

### Contributing
See [CONTRIBUTING.md](https://github.com/CloudForgeCI/cfc-core/blob/develop/CONTRIBUTING.md) for guidelines.

### License
Apache 2.0 - See [LICENSE](https://github.com/CloudForgeCI/cfc-core/blob/develop/LICENSE) for details.

Release history is maintained in [CHANGELOG.md](https://github.com/CloudForgeCI/cfc-core/blob/develop/CHANGELOG.md).

# CloudForge CI Compliance Documentation

## Overview

CloudForge CI provides **automated compliance enforcement** for HIPAA, SOC2, PCI-DSS, and GDPR. Our intelligent compliance system automatically configures security controls, retention policies, and remediation actions based on your selected frameworks.

**Key Benefits:**
- ✅ **Zero Manual Configuration** - Compliance settings auto-adapt to your frameworks
- ✅ **Continuous Enforcement** - Automatic remediation fixes non-compliant resources
- ✅ **Cost Optimized** - Intelligent lifecycle policies reduce storage costs by up to 90%
- ✅ **Audit Ready** - Complete audit trail with immutable logs

---

## Quick Links

### For Developers
- **[Automated Compliance Features](AUTOMATED_COMPLIANCE.md)** - Technical deep-dive into implementation
- **[Deployment Guide](DEPLOYMENT_GUIDE.md)** - Step-by-step deployment instructions
- **[Multi-Framework Compliance](MULTI_FRAMEWORK_COMPLIANCE.md)** - Supporting multiple frameworks simultaneously

### For Compliance Teams
- **[Quick Start Guide](QUICK_START_GUIDE.md)** - Fast path to compliance
- **[Compliance Analysis Index](COMPLIANCE_ANALYSIS_INDEX.md)** - Detailed framework mapping
- **[PCI-DSS Compliance](PCI_DSS_COMPLIANCE.md)** - PCI-DSS specific requirements

### For Customers
- **[This README](#features)** - Feature overview and benefits
- **[Cost Breakdown](#cost-analysis)** - Transparent pricing information

---

## Features

### 1. Intelligent S3 Lifecycle Management

**Problem:** Compliance requires years of log retention, but storing everything in S3 Standard is expensive.

**Solution:** Automatic lifecycle policies transition data through cost-effective storage tiers:

```
Day 0-90    → S3 Standard      ($23/TB/month)  - Immediate availability
Day 90-365  → Glacier          ($4/TB/month)   - Infrequent access
Day 365+    → Glacier Deep Archive ($1/TB/month) - Long-term compliance
```

**Retention by Framework:**
| Framework | Retention Period | Annual Cost per TB* |
|-----------|------------------|---------------------|
| HIPAA | 6 years | $246 |
| SOC2 | 2 years | $132 |
| PCI-DSS | 1 year | $96 |

*Compared to $276/year for S3 Standard

**What's Included:**
- CloudTrail audit logs
- AWS Config compliance data
- Application access logs (ALB)
- Audit Manager evidence

---

### 2. Automatic IAM Password Policy Enforcement

**Problem:** Compliance frameworks require strict password policies, but manual enforcement is error-prone.

**Solution:** AWS Config continuously monitors password policy and automatically fixes non-compliance.

**How It Works:**
1. Config detects missing/weak password policy
2. SSM Automation immediately applies correct policy
3. Config re-evaluates and confirms compliance
4. All actions logged to CloudTrail

**Password Requirements:**
| Framework | Minimum Length | Rotation | Reuse Prevention |
|-----------|----------------|----------|------------------|
| HIPAA | 14 characters | 90 days | 24 passwords |
| SOC2 | 12 characters | 90 days | 12 passwords |
| PCI-DSS | 8 characters | 90 days | 4 passwords |

All frameworks require uppercase, lowercase, numbers, and symbols.

---

### 3. Immutable Audit Trail

**Problem:** Audit logs must be tamper-proof to meet compliance requirements.

**Solution:** S3 versioning prevents deletion or modification of audit logs.

**Benefits:**
- **Immutability**: Cannot overwrite previous versions
- **Recovery**: Restore accidentally deleted files
- **Compliance**: Meets regulatory requirements for audit trails

**Applied To:**
- ✅ CloudTrail logs
- ✅ Config snapshots
- ✅ ALB access logs
- ✅ Audit Manager evidence

---

### 4. Multi-Framework Support

**Problem:** Many organizations must meet multiple compliance frameworks simultaneously.

**Solution:** Enable multiple frameworks and the system automatically applies the **strictest requirement**.

**Example:**
```java
// Enable HIPAA + PCI-DSS + SOC2
cfc.put("complianceFrameworks", "HIPAA,PCI-DSS,SOC2");
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
- ✅ 6-year S3 lifecycle policies
- ✅ 14-character passwords with complexity
- ✅ Complete audit trail via CloudTrail
- ✅ Encryption using S3-managed keys (SSE-S3)

---

### SOC2 - Service Organization Control 2
**Industry:** SaaS, Cloud Services
**Key Requirements:**
- Security, availability, processing integrity
- Confidentiality and privacy controls
- Annual audits required

**CloudForge Implementation:**
- ✅ 2-year log retention for audit evidence
- ✅ 12-character passwords
- ✅ Continuous monitoring via AWS Config
- ✅ Automated evidence collection (Audit Manager)

---

### PCI-DSS - Payment Card Industry Data Security Standard
**Industry:** E-commerce, Payment Processing
**Key Requirements:**
- 1-year log retention (3 months immediately available)
- Network security controls
- Regular vulnerability scanning
- Incident response procedures

**CloudForge Implementation:**
- ✅ 1-year retention, 90 days in S3 Standard
- ✅ 8-character minimum passwords
- ✅ WAF protection on ALB
- ✅ CloudWatch alarms for security events

---

### GDPR - General Data Protection Regulation
**Industry:** EU Operations, Privacy-Focused
**Key Requirements:**
- Data minimization and retention limits
- Right to erasure ("right to be forgotten")
- Data breach notification (72 hours)
- Privacy by design

**CloudForge Implementation:**
- ✅ Configurable retention periods
- ✅ S3 versioning for data recovery
- ✅ Access controls and encryption
- ✅ CloudWatch alarms for breach detection

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

1. **Developer configures frameworks**
   ```java
   cfc.put("complianceFrameworks", "HIPAA,SOC2");
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

5. **Results visible immediately**
   - Config dashboard shows compliance
   - CloudWatch alarms for violations
   - Audit Manager collects evidence

---

## Cost Analysis

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

**ROI Analysis:**
- **Without Automation**: $5,000-20,000/year for manual compliance management
- **With CloudForge**: $480/year in AWS costs
- **Savings**: 90-98% cost reduction

**Hidden Costs Eliminated:**
- No manual policy configuration
- No dedicated compliance engineer needed
- No audit preparation time
- Reduced audit costs (evidence pre-collected)

---

## Getting Started

### Prerequisites

- AWS Account with admin access
- AWS CDK installed
- Java 17+ and Maven

### 5-Minute Quick Start

```bash
# 1. Clone repository
git clone https://github.com/cloudforgeci/cfc-core.git
cd cfc-core

# 2. Configure compliance
cat > cfc-testing/deployment-context.properties <<EOF
complianceFrameworks=HIPAA,SOC2
security=PRODUCTION
awsConfigEnabled=true
albAccessLogging=true
EOF

# 3. Build and deploy
cd cloudforge-api
mvn clean install
cdk deploy jenkinsTSoc

# 4. Verify compliance
aws configservice describe-compliance-by-config-rule \
  --query 'ComplianceByConfigRules[*].[ConfigRuleName,Compliance.ComplianceType]' \
  --output table
```

**Expected Output:**
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

Create a compliance dashboard to monitor all metrics:

```bash
aws cloudwatch put-dashboard \
  --dashboard-name CloudForgeCompliance \
  --dashboard-body file://compliance-dashboard.json
```

**Includes:**
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
A: Yes! Deploy via CloudFormation StackSets to apply compliance across all accounts.

**Q: How often does Config evaluate rules?**
A: Continuously for configuration changes, plus periodic evaluations every 24 hours.

**Q: Can I disable auto-remediation?**
A: Yes, set `.automatic(false)` in the remediation configuration. Manual approval will be required.

**Q: What if remediation fails?**
A: Config will retry up to 5 times with 60-second intervals. Check SSM Automation execution history for errors.

**Q: How do I prove compliance to auditors?**
A: Use AWS Audit Manager to automatically collect evidence and generate reports for your chosen framework.

**Q: Can I add custom compliance rules?**
A: Yes! Add custom Config rules in `ComplianceFactory.java`. Follow existing patterns.

---

## Troubleshooting

### Common Issues

**Issue:** Config rules show INSUFFICIENT_DATA
**Fix:** Trigger manual evaluation: `aws configservice start-config-rules-evaluation`

**Issue:** Password policy not updating
**Fix:** Check SSM Automation role has `iam:UpdateAccountPasswordPolicy` permission

**Issue:** S3 lifecycle not applied
**Fix:** Verify `complianceFrameworks` is set in deployment context

**Issue:** High AWS costs
**Fix:** Review S3 storage class distributions. Ensure lifecycle transitions are working.

For more troubleshooting, see **[Deployment Guide - Troubleshooting](DEPLOYMENT_GUIDE.md#troubleshooting-common-issues)**.

---

## Documentation Index

### Getting Started
- [README](README.md) - This file
- [Quick Start Guide](QUICK_START_GUIDE.md) - Fast path to compliance
- [Deployment Guide](DEPLOYMENT_GUIDE.md) - Detailed deployment

### Technical Documentation
- [Automated Compliance](AUTOMATED_COMPLIANCE.md) - Feature deep-dive
- [Multi-Framework Compliance](MULTI_FRAMEWORK_COMPLIANCE.md) - Multiple frameworks
- [AWS Config Multi-Stack](AWS_CONFIG_MULTI_STACK.md) - Multi-account setup

### Framework-Specific
- [PCI-DSS Compliance](PCI_DSS_COMPLIANCE.md) - PCI-DSS requirements
- [PCI-DSS Application Security](PCI_DSS_APPLICATION_SECURITY.md) - App security
- [Compliance Analysis Index](COMPLIANCE_ANALYSIS_INDEX.md) - Framework mapping

---

## Support & Contributing

### Get Help
- **Issues**: [GitHub Issues](https://github.com/cloudforgeci/cfc-core/issues)
- **Email**: support@cloudforgeci.com
- **Documentation**: [docs/compliance/](.)

### Contributing
We welcome contributions! See [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines.

### License
Apache 2.0 - See [LICENSE](../../LICENSE) for details

---

## Changelog

### Version 2.0.0 (Current)
- ✅ Automated S3 lifecycle policies based on compliance frameworks
- ✅ S3 versioning enabled on all compliance buckets
- ✅ IAM password policy auto-remediation via AWS Config
- ✅ Multi-framework support (HIPAA, SOC2, PCI-DSS, GDPR)
- ✅ Strictest-requirement logic for multiple frameworks
- ✅ Comprehensive documentation

### Version 1.0.0
- Initial compliance features
- Manual configuration required

---

*Last Updated: 2025*
*CloudForge CI - Automated Compliance for AWS*

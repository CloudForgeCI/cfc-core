# Compliance Deployment Guide

## Quick Start

This guide describes deploying CloudForge CI with its compliance-related features enabled.

### Prerequisites

1. **AWS account** with permissions to deploy CloudFormation, IAM, AWS Config, CloudTrail, and S3 resources
2. **AWS CDK CLI** (`npm install -g aws-cdk`)
3. **JDK** matching the version in the root `pom.xml`, and Maven
4. **AWS CLI** configured with credentials

### Step 1: Configure Compliance Frameworks

Set the compliance properties in your deployment context. With the sample project in `cfc-testing`, this is `deployment-context.json`, which the Interactive Deployer writes for you:

```json
{
  "securityProfile": "production",
  "complianceFrameworks": "hipaa,pci-dss",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "albAccessLogging": true
}
```

Set `createConfigInfrastructure` to `true` on only one stack per account and region; see [AWS Config Multi-Stack](AWS_CONFIG_MULTI_STACK.md).

**Available Frameworks** (case-insensitive):
- `hipaa` - Healthcare (6-year retention)
- `soc2` - Service organizations (2-year retention)
- `pci-dss` - Payment cards (1-year retention)
- `gdpr` - EU data protection

### Step 2: Build the Project

```bash
mvn -DskipTests install
```

### Step 3: Deploy the Stack

```bash
cd cfc-testing
cdk deploy
```

### Step 4: Verify Deployment

Check that compliance features are active:

```bash
# Verify Config rules
aws configservice describe-config-rules \
  --query 'ConfigRules[*].[ConfigRuleName,ComplianceType]' \
  --output table

# Verify password policy remediation (Config rule names are generated
# by CloudFormation from the IAMPasswordPolicyRule logical ID)
aws configservice describe-remediation-configurations \
  --config-rule-names <password-policy-rule-name>

# Verify S3 lifecycle policies
aws s3api list-buckets --query 'Buckets[*].Name' --output text | \
  while read bucket; do
    echo "=== $bucket ==="
    aws s3api get-bucket-lifecycle-configuration --bucket $bucket 2>/dev/null || echo "No lifecycle"
  done
```

---

## Deployment Scenarios

### Scenario 1: HIPAA Healthcare Application

**Requirements:**
- 6-year data retention
- Strict password policy (14 chars, complexity required)
- Audit logging
- Encryption at rest and in transit

**Configuration:**
```json
{
  "complianceFrameworks": "hipaa",
  "securityProfile": "production",
  "awsConfigEnabled": true,
  "auditManagerEnabled": true,
  "albAccessLogging": true
}
```

**Expected Results:**
- S3 buckets: 6-year retention, versioning enabled
- IAM password: 14 chars minimum, 24 password reuse prevention
- CloudTrail: management events and S3 data events logged
- Config: Continuous evaluation of supported resources

**Verification:**
```bash
# Check password policy
aws iam get-account-password-policy | jq '.PasswordPolicy'

# Expected output:
# {
#   "MinimumPasswordLength": 14,
#   "RequireSymbols": true,
#   "RequireNumbers": true,
#   "RequireUppercaseCharacters": true,
#   "RequireLowercaseCharacters": true,
#   "MaxPasswordAge": 90,
#   "PasswordReusePrevention": 24,
#   "AllowUsersToChangePassword": true
# }
```

---

### Scenario 2: SOC2 SaaS Platform

**Requirements:**
- 2-year data retention
- Regular security audits
- Access logging and monitoring
- Change management controls

**Configuration:**
```json
{
  "complianceFrameworks": "soc2",
  "securityProfile": "production",
  "awsConfigEnabled": true,
  "auditManagerEnabled": true
}
```

**Expected Results:**
- S3 buckets: 2-year retention
- IAM password: 12 chars minimum, 12 password reuse prevention
- Audit Manager assessment for evidence collection

---

### Scenario 3: PCI-DSS E-commerce

**Requirements:**
- 1-year log retention
- 3 months immediately available
- Network security controls
- Regular vulnerability scanning

**Configuration:**
```json
{
  "complianceFrameworks": "pci-dss",
  "securityProfile": "production",
  "awsConfigEnabled": true,
  "wafEnabled": true
}
```

**Expected Results:**
- S3 buckets: 1-year retention, 90 days in S3 Standard
- IAM password: 8 chars minimum (see the PCI DSS v4.0.1 note in [Automated Compliance](AUTOMATED_COMPLIANCE.md#iam-password-policy-auto-remediation))
- WAF enabled on ALB for attack protection

---

### Scenario 4: Multi-Framework Compliance

**Use Case:** Organization must meet HIPAA, SOC2, and PCI-DSS simultaneously

**Configuration:**
```json
{
  "complianceFrameworks": "hipaa,soc2,pci-dss",
  "securityProfile": "production",
  "awsConfigEnabled": true
}
```

**How It Works:**
CloudForge selects the strictest requirement from the selected frameworks:

| Setting | HIPAA | SOC2 | PCI-DSS | **Selected** |
|---------|-------|------|---------|--------------|
| Retention | 6y | 2y | 1y | **6 years (HIPAA)** |
| Password Length | 14 | 12 | 8 | **14 chars (HIPAA)** |
| Reuse Prevention | 24 | 12 | 4 | **24 passwords (HIPAA)** |

---

## Post-Deployment Configuration

### Subscribe to Compliance Notifications

```bash
# Get SNS topic ARN
TOPIC_ARN=$(aws sns list-topics --query 'Topics[?contains(TopicArn, `alb-alarms`)].TopicArn' --output text)

# Subscribe to email notifications
aws sns subscribe \
  --topic-arn $TOPIC_ARN \
  --protocol email \
  --notification-endpoint compliance@yourcompany.com
```

### Enable AWS Audit Manager (Optional)

Audit Manager must be enabled in the account before `auditManagerEnabled` can create assessments. See [AWS Audit Manager Integration](../AUDIT_MANAGER.md). To enable it manually:

1. Navigate to the AWS Audit Manager console
2. Choose **Enable Audit Manager**
3. Confirm the data sources: CloudTrail, AWS Config, and Security Hub
4. Create an assessment:
   - Framework: Select your compliance framework (HIPAA/SOC2/PCI-DSS)
   - Scope: Select your AWS account
   - Evidence collection: Automatic

### Configure GuardDuty (Recommended)

Set `guardDutyEnabled: true`. If no detector exists in the account and region, also set `createGuardDutyDetector: true`, or create one manually:

```bash
aws guardduty create-detector --enable
```

---

## Monitoring Compliance

### Daily Checks

Example script:

```bash
#!/bin/bash

echo "=== Daily Compliance Check ==="
echo ""

# 1. Check Config rule compliance
echo "Config Rules Status:"
aws configservice describe-compliance-by-config-rule \
  --query 'ComplianceByConfigRules[?Compliance.ComplianceType!=`COMPLIANT`].[ConfigRuleName,Compliance.ComplianceType]' \
  --output table

# 2. Check remediation status
echo ""
echo "Recent Remediation Executions:"
aws configservice describe-remediation-execution-status \
  --config-rule-name <password-policy-rule-name> \
  --query 'RemediationExecutionStatuses[0:5].[ResourceKey.ResourceId,State,StepExecutions[0].State]' \
  --output table

# 3. Check CloudTrail status
echo ""
echo "CloudTrail Status:"
aws cloudtrail get-trail-status --name cloudforge-cloudtrail-<stack-name> \
  --query '[IsLogging,LatestDeliveryTime]' \
  --output table
```

### Weekly Audits

Review the following weekly:

1. **Config Rule Compliance**
   - All rules should be COMPLIANT
   - Investigate any NON_COMPLIANT resources

2. **S3 Bucket Lifecycle**
   - Verify transitions are working
   - Check storage costs in Cost Explorer

3. **Password Policy Compliance**
   - Ensure policy hasn't been manually changed
   - Review IAM user list for direct policy attachments

4. **Access Logs**
   - Review ALB access logs for anomalies
   - Check CloudTrail for unauthorized API calls

---

## Troubleshooting Common Issues

### Issue 1: Password Policy Remediation Failed

**Symptom:**
```
RemediationExecutionStatus: FAILED
ErrorMessage: Access Denied
```

**Solution:**
Check the SSM Automation role created from the `PasswordPolicyRemediationRole` logical ID (its physical name is generated by CloudFormation). Its policy should include `iam:UpdateAccountPasswordPolicy` and `iam:GetAccountPasswordPolicy`. If it is missing, redeploy the stack:

```bash
cdk deploy <stack-name>
```

---

### Issue 2: S3 Lifecycle Not Applied

**Symptom:** Buckets don't show lifecycle rules

**Solution:**
Lifecycle rules are applied to compliance buckets created by `ComplianceFactory`. Confirm that `complianceFrameworks` is set in the deployment context and look for `Lifecycle:` lines in the synthesis log, then redeploy:

```bash
cdk deploy <stack-name>
```

---

### Issue 3: Config Rules Show INSUFFICIENT_DATA

**Symptom:** Config rules not evaluating

**Solution:**
```bash
# Trigger manual evaluation
aws configservice start-config-rules-evaluation \
  --config-rule-names <password-policy-rule-name>

# Wait 60 seconds, then check status
sleep 60
aws configservice describe-compliance-by-config-rule \
  --config-rule-names <password-policy-rule-name>
```

---

## Updating Compliance Settings

### Changing Frameworks

To add or remove frameworks, update `complianceFrameworks` in the deployment context (for example from `hipaa` to `hipaa,gdpr`) and redeploy:

```bash
cdk deploy <stack-name>

# This will:
# 1. Update Config rule parameters (if needed)
# 2. Update S3 lifecycle policies (if stricter)
# 3. Trigger remediation for password policy (if stricter)
```

### Upgrading Security Profile

Change `securityProfile` from `staging` to `production` in the deployment context and redeploy:

```bash
cdk deploy <stack-name>

# This will:
# 1. Change removal policies to RETAIN for the resources listed in RETAINED_RESOURCES.md
# 2. Apply stricter defaults (for example, a 14-character password minimum when no framework is selected)
# 3. Enable cdk-nag validation and additional monitoring
```

---

## Cleanup and Deprovisioning

### Stack Deletion

```bash
# Delete the CloudFormation stack
cdk destroy <stack-name>
```

**What Gets Deleted:**
- Config rules and remediation configurations
- CloudWatch alarms
- IAM roles created by the stack

**What Persists:**
- **IAM password policy** (account-level setting)
- **S3 buckets and other resources** retained in `production`; see [Retained Resources](RETAINED_RESOURCES.md)
- **Config recorder and delivery channel**, if other stacks still reference them

### Complete Cleanup

To remove all compliance settings:

```bash
# 1. Delete any remaining Config rules
aws configservice delete-config-rule --config-rule-name <rule-name>

# 2. Empty and delete retained buckets (review the list before deleting)
aws s3 rb s3://<bucket-name> --force

# 3. Reset IAM password policy (optional)
aws iam delete-account-password-policy
```

Deleting retained buckets permanently removes audit evidence. Confirm your retention obligations first.

---

## Cost Considerations

The compliance features add charges for AWS Config (configuration items and rule evaluations), CloudTrail data events, S3 storage and lifecycle transitions, SSM Automation executions, and CloudWatch alarms. Charges depend on region, resource count, and log volume; use the [AWS Pricing Calculator](https://calculator.aws.amazon.com/) for an estimate.

**Cost Reduction Tips:**
- Use S3 Intelligent-Tiering for unpredictable access patterns
- Archive old Config snapshots to Glacier
- Use periodic Config rules instead of continuous (where acceptable)

---

## Next Steps

1. Review [AUTOMATED_COMPLIANCE.md](AUTOMATED_COMPLIANCE.md) for feature details
2. Set up monitoring dashboards in CloudWatch
3. Subscribe to SNS topics for alerts
4. Schedule weekly compliance reviews
5. Document your compliance procedures for auditors

---

## Support

For deployment assistance:
- GitHub Issues: [cfc-core/issues](https://github.com/CloudForgeCI/cfc-core/issues)
- Documentation: [docs/compliance/](.)
- Email: support@cloudforgeci.com

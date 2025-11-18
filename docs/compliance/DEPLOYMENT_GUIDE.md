# Compliance Deployment Guide

## Quick Start

This guide walks you through deploying CloudForge CI with automated compliance features.

### Prerequisites

1. **AWS Account** with administrator access
2. **AWS CDK** installed (`npm install -g aws-cdk`)
3. **Java 17+** and Maven installed
4. **AWS CLI** configured with credentials

### Step 1: Configure Compliance Frameworks

Edit your deployment context to enable desired frameworks:

```java
// Example: HIPAA + PCI-DSS compliance
DeploymentContext cfc = new DeploymentContext();
cfc.put("complianceFrameworks", "HIPAA,PCI-DSS");
cfc.put("security", "PRODUCTION");
cfc.put("awsConfigEnabled", true);
cfc.put("albAccessLogging", true);
```

**Available Frameworks:**
- `HIPAA` - Healthcare (6-year retention)
- `SOC2` - Service organizations (2-year retention)
- `PCI-DSS` - Payment cards (1-year retention)
- `GDPR` - EU data protection

### Step 2: Build the Project

```bash
cd cloudforge-api
mvn clean install
```

### Step 3: Deploy the Stack

```bash
cdk deploy jenkinsTSoc \
  --context security=PRODUCTION \
  --context complianceFrameworks=HIPAA,PCI-DSS
```

### Step 4: Verify Deployment

Check that compliance features are active:

```bash
# Verify Config rules
aws configservice describe-config-rules \
  --query 'ConfigRules[*].[ConfigRuleName,ComplianceType]' \
  --output table

# Verify password policy remediation
aws configservice describe-remediation-configurations \
  --config-rule-names IAMPasswordPolicyRule

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
- Complete audit trail
- Encryption at rest and in transit

**Configuration:**
```java
cfc.put("complianceFrameworks", "HIPAA");
cfc.put("security", "PRODUCTION");
cfc.put("awsConfigEnabled", true);
cfc.put("auditManagerEnabled", true);
cfc.put("albAccessLogging", true);
```

**Expected Results:**
- S3 buckets: 6-year retention, versioning enabled
- IAM password: 14 chars minimum, 24 password reuse prevention
- CloudTrail: All S3 data events logged
- Config: Continuous compliance monitoring

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
```java
cfc.put("complianceFrameworks", "SOC2");
cfc.put("security", "PRODUCTION");
cfc.put("awsConfigEnabled", true);
cfc.put("auditManagerEnabled", true);
```

**Expected Results:**
- S3 buckets: 2-year retention
- IAM password: 12 chars minimum, 12 password reuse prevention
- Automated compliance reports via Audit Manager

---

### Scenario 3: PCI-DSS E-commerce

**Requirements:**
- 1-year log retention
- 3 months immediately available
- Network security controls
- Regular vulnerability scanning

**Configuration:**
```java
cfc.put("complianceFrameworks", "PCI-DSS");
cfc.put("security", "PRODUCTION");
cfc.put("awsConfigEnabled", true);
cfc.put("enableWaf", true);  // WAF for network protection
```

**Expected Results:**
- S3 buckets: 1-year retention, 90 days in S3 Standard
- IAM password: 8 chars minimum (PCI-DSS minimum)
- WAF enabled on ALB for attack protection

---

### Scenario 4: Multi-Framework Compliance

**Use Case:** Organization must meet HIPAA, SOC2, and PCI-DSS simultaneously

**Configuration:**
```java
cfc.put("complianceFrameworks", "HIPAA,SOC2,PCI-DSS");
cfc.put("security", "PRODUCTION");
```

**How It Works:**
The system automatically selects the **strictest requirement** from all frameworks:

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

1. Navigate to AWS Audit Manager console
2. Click **Enable Audit Manager**
3. Configure data sources:
   - CloudTrail: ✅
   - AWS Config: ✅
   - Security Hub: ✅

4. Create assessment:
   - Framework: Select your compliance framework (HIPAA/SOC2/PCI-DSS)
   - Scope: Select your AWS account
   - Evidence collection: Automatic

### Configure GuardDuty (Recommended)

```bash
# Enable GuardDuty for threat detection
aws guardduty create-detector --enable
```

---

## Monitoring Compliance

### Daily Checks

```bash
#!/bin/bash
# daily-compliance-check.sh

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
  --config-rule-name IAMPasswordPolicyRule \
  --query 'RemediationExecutionStatuses[0:5].[ResourceKey.ResourceId,State,StepExecutions[0].State]' \
  --output table

# 3. Check CloudTrail status
echo ""
echo "CloudTrail Status:"
aws cloudtrail get-trail-status --name cloudforge-trail \
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
```bash
# Check SSM Automation role permissions
aws iam get-role --role-name PasswordPolicyRemediationRole

# Expected policy should include:
# - iam:UpdateAccountPasswordPolicy
# - iam:GetAccountPasswordPolicy

# If missing, redeploy the stack
cdk deploy jenkinsTSoc
```

---

### Issue 2: S3 Lifecycle Not Applied

**Symptom:** Buckets don't show lifecycle rules

**Solution:**
```bash
# Check if compliance frameworks are configured
aws cloudformation describe-stacks --stack-name jenkinsTSoc \
  --query 'Stacks[0].Parameters[?ParameterKey==`complianceFrameworks`].ParameterValue'

# If empty, update stack with frameworks:
cdk deploy jenkinsTSoc \
  --context complianceFrameworks=HIPAA,SOC2
```

---

### Issue 3: Config Rules Show INSUFFICIENT_DATA

**Symptom:** Config rules not evaluating

**Solution:**
```bash
# Trigger manual evaluation
aws configservice start-config-rules-evaluation \
  --config-rule-names IAMPasswordPolicyRule

# Wait 60 seconds, then check status
sleep 60
aws configservice describe-compliance-by-config-rule \
  --config-rule-names IAMPasswordPolicyRule
```

---

## Updating Compliance Settings

### Changing Frameworks

To add or remove frameworks:

```bash
# Add GDPR to existing HIPAA deployment
cdk deploy jenkinsTSoc \
  --context complianceFrameworks=HIPAA,GDPR

# This will:
# 1. Update Config rule parameters (if needed)
# 2. Update S3 lifecycle policies (if stricter)
# 3. Trigger remediation for password policy (if stricter)
```

### Upgrading Security Profile

```bash
# Upgrade from STAGING to PRODUCTION
cdk deploy jenkinsTSoc \
  --context security=PRODUCTION \
  --context complianceFrameworks=HIPAA

# This will:
# 1. Change S3 removal policy to RETAIN
# 2. Apply stricter password requirements
# 3. Enable additional monitoring
```

---

## Cleanup and Deprovisioning

### Stack Deletion

```bash
# Delete the CloudFormation stack
cdk destroy jenkinsTSoc
```

**What Gets Deleted:**
- Config rules and remediation configurations
- CloudWatch alarms
- IAM roles created by the stack

**What Persists:**
- **IAM password policy** (account-level setting)
- **S3 buckets** (if RemovalPolicy is RETAIN in PRODUCTION)
- **EBS encryption by default** (account-level setting)

### Complete Cleanup

To remove all compliance settings:

```bash
# 1. Delete Config rules
./cleanup-config-rules.sh

# 2. Delete S3 buckets (if retained)
aws s3 ls | grep -E "(cloudtrail|config|audit)" | \
  awk '{print $3}' | \
  xargs -I {} aws s3 rb s3://{} --force

# 3. Reset IAM password policy (optional)
aws iam delete-account-password-policy

# 4. Disable EBS encryption by default (optional)
aws ec2 disable-ebs-encryption-by-default
```

---

## Cost Estimation

### Monthly Costs (Typical PRODUCTION Deployment)

| Service | Usage | Monthly Cost |
|---------|-------|--------------|
| **AWS Config** | 10 rules, 50 resources | $25 |
| **CloudTrail** | All events | $5 |
| **S3 Storage** | 100 GB logs/month | $30 |
| **S3 Lifecycle Transitions** | 10,000 objects/month | $1 |
| **Systems Manager** | 50 automation executions | $2 |
| **CloudWatch Alarms** | 10 alarms | $1 |
| **Total** | | **~$64/month** |

**Cost Reduction Tips:**
- Use S3 Intelligent-Tiering for unpredictable access patterns
- Archive old Config snapshots to Glacier
- Use periodic Config rules instead of continuous (where acceptable)

---

## Next Steps

1. ✅ Review [AUTOMATED_COMPLIANCE.md](AUTOMATED_COMPLIANCE.md) for feature details
2. ✅ Set up monitoring dashboards in CloudWatch
3. ✅ Subscribe to SNS topics for alerts
4. ✅ Schedule weekly compliance reviews
5. ✅ Document your compliance procedures for auditors

---

## Support

For deployment assistance:
- GitHub Issues: [cfc-core/issues](https://github.com/cloudforgeci/cfc-core/issues)
- Documentation: [docs/compliance/](.)
- Email: support@cloudforgeci.com

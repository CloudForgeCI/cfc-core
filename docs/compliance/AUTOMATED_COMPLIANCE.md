# Automated Compliance Features

## Overview

CloudForge configures selected security controls, retention policies, and remediation
actions based on enabled compliance frameworks (HIPAA, SOC2, PCI-DSS, and GDPR). When
framework requirements differ, the configuration applies the strictest encoded value.
These controls can support a compliance program but do not establish certification.

## Key Features

### 1. Compliance-Driven Configuration
- **Policy Selection**: Configuration values derive from enabled frameworks
- **Strictest-Wins Logic**: When multiple frameworks are enabled, the strictest requirement is applied
- **Overrides**: Deployment configuration can be reviewed and adjusted for organization-specific requirements

### 2. Continuous Enforcement
- **AWS Config Monitoring**: Continuously monitors resources for compliance
- **Automatic Remediation**: Fixes non-compliant resources without human intervention
- **Persistent Settings**: Account-level settings survive stack deletion

### 3. Audit Trail
- **Action Logging**: Supported compliance actions are logged to CloudTrail
- **Lifecycle Management**: Automatic log retention and archival based on compliance requirements
- **Version Control**: S3 versioning enabled on all compliance buckets

---

## Implemented Features

### Config Recorder Auto-Start

**What It Does:**
Automatically starts the AWS Config Recorder immediately upon deployment, ensuring compliance monitoring begins without manual intervention.

**Why It's Required:**
- **SOC2**: Requires continuous compliance monitoring from deployment
- **HIPAA**: Zero-gap compliance recording for PHI-related resources
- **PCI-DSS**: Immediate monitoring of cardholder data environment
- **GDPR**: Continuous monitoring for data protection compliance

**How It Works:**
1. Config Recorder and Delivery Channel are created via CloudFormation
2. Custom resource automatically calls `StartConfigurationRecorder` API
3. Recording begins immediately upon deployment completion
4. Idempotent operation - safe to re-run on updates

**Technical Details:**
```java
// Auto-start implemented via AWS SDK custom resource
AwsSdkCall startRecorderCall = AwsSdkCall.builder()
    .service("ConfigService")
    .action("startConfigurationRecorder")
    .parameters(Map.of("ConfigurationRecorderName", "cloudforge-config-recorder"))
    .build();
```

**Benefits:**
- **Zero Compliance Gap**: No delay between deployment and monitoring
- **Automatic**: No manual start command required
- **Idempotent**: Safe to re-deploy without side effects
- **Auditable**: Start action logged in CloudTrail

**Code Location:**
- [ComplianceFactory.java:475-534](../../cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java#L475-L534)

---

### S3 Lifecycle Policies

**What It Does:**
Automatically manages the lifecycle of audit logs and compliance data based on regulatory retention requirements.

**How It Works:**
1. System detects which compliance frameworks are enabled
2. Determines the strictest retention requirement
3. Configures S3 lifecycle rules with appropriate transitions and expiration

**Retention Requirements by Framework:**

| Framework | Retention Period | Immediate Access | Archive Tiers |
|-----------|------------------|------------------|---------------|
| **HIPAA** | 6 years (2190 days) | N/A | Glacier (90d), Deep Archive (1y) |
| **SOC2** | 2 years (730 days) | N/A | Glacier (90d), Deep Archive (1y) |
| **PCI-DSS** | 1 year (365 days) | 3 months | Glacier (90d) |
| **Default** | Based on security profile | N/A | Glacier (90d), Deep Archive (varies) |

**Storage Class Transitions:**
```
0-90 days     → S3 Standard (immediate availability for PCI-DSS)
90-365 days   → Glacier (cost optimization)
365+ days     → Glacier Deep Archive (long-term compliance)
Delete after  → Framework-specific retention period
```

**Affected Buckets:**
- CloudTrail audit logs
- AWS Config compliance data
- AWS Audit Manager evidence
- ALB access logs

**Code Location:**
- [`ComplianceFactory.java:2040-2154`](../../cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java#L2040-L2154)
- [`AlbFactory.java:164`](../../cloudforge-api/src/main/java/com/cloudforgeci/api/ingress/AlbFactory.java#L164)

---

### S3 Bucket Versioning

**What It Does:**
Enables versioning on all compliance-related S3 buckets to maintain immutable audit trails and prevent accidental deletion.

**Why It's Required:**
- **HIPAA**: Required for audit trail integrity
- **SOC2**: Required for evidence preservation
- **PCI-DSS**: Required for log file integrity
- **GDPR**: Required for data protection and accountability

**How It Works:**
All compliance buckets are created with `.versioned(true)`:
```java
Bucket bucket = Bucket.Builder.create(this, "ComplianceBucket")
    .versioned(true)  // Required for compliance
    .encryption(BucketEncryption.S3_MANAGED)
    .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
    .lifecycleRules(lifecycleRules)
    .build();
```

**Benefits:**
- **Immutability**: Previous versions cannot be overwritten
- **Audit Trail**: Complete history of all changes
- **Recovery**: Ability to restore previous versions
- **Compliance**: Meets regulatory requirements for data retention

**Code Location:**
- [`ComplianceFactory.java:2056`](../../cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java#L2056)
- [`AlbFactory.java:164`](../../cloudforge-api/src/main/java/com/cloudforgeci/api/ingress/AlbFactory.java#L164)

---

### S3 Versioning Auto-Remediation (Optional)

**What It Does:**
Automatically enables versioning on S3 buckets that fail the AWS Config versioning compliance check.

**Configuration:**
This feature is **optional** and can be enabled via deployment context:

```json
{
  "enableS3VersioningRemediation": true,
  "scopeConfigRulesToDeployment": true
}
```

**Configuration Options:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `enableS3VersioningRemediation` | `false` | Enable automatic versioning remediation |
| `scopeConfigRulesToDeployment` | `false` | Only monitor buckets from this stack |

**How It Works:**

1. **AWS Config Rule**: Monitors S3 bucket versioning (all buckets or scoped to stack)
2. **Detection**: Config rule detects buckets without versioning enabled
3. **Automatic Remediation**: SSM Automation enables versioning on the bucket
4. **Verification**: Config re-evaluates and confirms compliance

**Scoping Behavior:**

- **Default (scopeConfigRulesToDeployment=false)**: Monitors ALL S3 buckets in the account
  - Useful for organization-wide compliance enforcement
  - May report non-compliant buckets from other projects

- **Scoped (scopeConfigRulesToDeployment=true)**: Only monitors buckets created by this CloudFormation stack
  - Uses CloudFormation tag: `aws:cloudformation:stack-name`
  - Only shows compliance for this specific deployment
  - Useful for focused compliance reporting

**AWS Services Used:**
- **AWS Config**: Monitors S3 bucket versioning
- **AWS Systems Manager**: Executes remediation using `AWS-ConfigureS3BucketVersioning`
- **Amazon S3**: Updates bucket versioning configuration

**Remediation Settings:**
- **Mode**: Automatic (no manual approval required)
- **Max Attempts**: 5
- **Retry Interval**: 60 seconds

**Important Considerations:**

⚠️ **Cost Implications**: Enabling versioning increases storage costs as S3 retains all object versions

⚠️ **Irreversible**: Once enabled, versioning cannot be fully disabled (only suspended)

⚠️ **Storage Growth**: Versioned objects consume additional storage for each version

**Best Practices:**
1. Enable scoping for development/testing environments
2. Use organization-wide monitoring for production compliance
3. Configure lifecycle policies to manage version retention
4. Monitor storage costs when enabling automatic remediation

**Code Location:**
- [`ComplianceFactory.java:501-536`](../../cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java#L501-L536) - Config rule with scoping
- [`ComplianceFactory.java:721-770`](../../cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java#L721-L770) - Remediation configuration

**Example Deployment Logs:**
```
S3 versioning rule scoped to stack: jenkinsTSoc
S3 bucket versioning automatic remediation enabled
  SSM Document: AWS-ConfigureS3BucketVersioning
  Mode: Automatic (enables versioning on non-compliant buckets)
  WARNING: This has cost implications - versioned objects consume additional storage
  Max attempts: 5, Retry interval: 60 seconds
```

---

### IAM Password Policy Auto-Remediation

**What It Does:**
Automatically enforces IAM password policy requirements based on compliance frameworks using AWS Config and AWS Systems Manager.

**How It Works:**

1. **AWS Config Rule**: Monitors IAM password policy compliance
2. **Detection**: Config rule detects missing or non-compliant policy
3. **Automatic Remediation**: SSM Automation document updates the policy
4. **Verification**: Config re-evaluates and confirms compliance

**Password Requirements by Framework:**

| Framework | Min Length | Max Age | Reuse Prevention | Complexity |
|-----------|------------|---------|------------------|------------|
| **HIPAA** | 14 characters | 90 days | 24 passwords | All required† |
| **SOC2** | 12 characters | 90 days | 12 passwords | All required† |
| **PCI-DSS** | 8 characters | 90 days | 4 passwords | All required† |
| **Default (PROD)** | 14 characters | 90 days | 12 passwords | All required† |
| **Default (STAGING/DEV)** | 12 characters | 90 days | 12 passwords | All required† |

† Complexity requirements include:
- Uppercase letters (A-Z)
- Lowercase letters (a-z)
- Numbers (0-9)
- Symbols (!@#$%^&*)

**AWS Services Used:**
- **AWS Config**: Monitors password policy compliance
- **AWS Systems Manager**: Executes remediation using `AWSConfigRemediation-SetIAMPasswordPolicy`
- **IAM**: Updates account password policy

**Remediation Settings:**
- **Mode**: Automatic (no manual approval required)
- **Max Attempts**: 5
- **Retry Interval**: 60 seconds
- **Persistence**: Account-level setting survives stack deletion

**Code Location:**
- [`ComplianceFactory.java:512-705`](../../cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java#L512-L705)

**Example Deployment Logs:**
```
IAM password policy requirements (HIPAA):
  Minimum length: 14 characters
  Max password age: 90 days
  Password reuse prevention: 24 passwords
  Complexity: Uppercase, lowercase, numbers, symbols required

IAM password policy automatic remediation enabled
  SSM Document: AWSConfigRemediation-SetIAMPasswordPolicy
  Mode: Automatic (fixes non-compliant policies immediately)
  Max attempts: 5, Retry interval: 60 seconds
```

---

## Configuration

### Enabling Compliance Frameworks

Configure compliance frameworks in your deployment context:

```java
DeploymentContext cfc = new DeploymentContext();
cfc.put("complianceFrameworks", "HIPAA,SOC2,PCI-DSS");
```

**Supported Values:**
- `HIPAA` - Health Insurance Portability and Accountability Act
- `SOC2` - Service Organization Control 2
- `PCI-DSS` (or `PCIDSS`) - Payment Card Industry Data Security Standard
- `GDPR` - General Data Protection Regulation

**Multiple Frameworks:**
Separate multiple frameworks with commas:
```java
cfc.put("complianceFrameworks", "HIPAA,PCI-DSS,SOC2");
```

When multiple frameworks are enabled, the **strictest requirement** is automatically applied.

---

## Compliance Matrix

### Feature Coverage by Framework

| Feature | HIPAA | SOC2 | PCI-DSS | Implementation |
|---------|-------|------|---------|----------------|
| **S3 Retention** | ✅ 6 years | ✅ 2 years | ✅ 1 year | Auto-lifecycle |
| **S3 Versioning** | ✅ Required | ✅ Required | ✅ Required | Enabled by default |
| **S3 Encryption** | ✅ S3-managed | ✅ S3-managed | ✅ S3-managed | SSE-S3 |
| **Password Length** | ✅ 14 chars | ✅ 12 chars | ✅ 8 chars | Config + SSM |
| **Password Complexity** | ✅ All | ✅ All | ✅ All | Config + SSM |
| **Password Rotation** | ✅ 90 days | ✅ 90 days | ✅ 90 days | Config + SSM |
| **Password Reuse** | ✅ 24 | ✅ 12 | ✅ 4 | Config + SSM |
| **Auto-Remediation** | ✅ Enabled | ✅ Enabled | ✅ Enabled | AWS Config |
| **CloudTrail Logging** | ✅ All events | ✅ All events | ✅ All events | Advanced selectors |
| **ALB Access Logs** | ✅ Required | ✅ Required | ✅ Required | S3 bucket |
| **Encryption in Transit** | ✅ TLS 1.2+ | ✅ TLS 1.2+ | ✅ TLS 1.2+ | ALB listener |

---

## Monitoring and Verification

### Checking Compliance Status

**View Config Rule Compliance:**
```bash
aws configservice describe-compliance-by-config-rule \
  --config-rule-names $(aws configservice describe-config-rules \
    --query 'ConfigRules[*].ConfigRuleName' --output text)
```

**Check Password Policy:**
```bash
aws iam get-account-password-policy
```

**View S3 Bucket Lifecycle:**
```bash
aws s3api get-bucket-lifecycle-configuration --bucket <bucket-name>
```

**Check S3 Bucket Versioning:**
```bash
aws s3api get-bucket-versioning --bucket <bucket-name>
```

### AWS Config Dashboard

1. Navigate to **AWS Config** in AWS Console
2. Select **Rules** to view compliance status
3. Click on specific rules to see:
   - Compliance timeline
   - Non-compliant resources
   - Remediation history

### CloudWatch Alarms

Compliance-related alarms are created with SNS notifications:
- ALB 5xx errors
- ALB 4xx errors
- High response times

Subscribe to the SNS topic to receive alerts:
```bash
aws sns subscribe \
  --topic-arn arn:aws:sns:region:account:alb-alarms-production \
  --protocol email \
  --notification-endpoint your-email@example.com
```

---

## Troubleshooting

### Password Policy Not Applied

**Symptom:** IAM password policy Config rule shows NON_COMPLIANT

**Possible Causes:**
1. SSM Automation role lacks permissions
2. Remediation configuration not created
3. Manual policy changes override automation

**Solution:**
1. Check SSM Automation execution:
   ```bash
   aws ssm describe-automation-executions \
     --filters Key=DocumentName,Values=AWSConfigRemediation-SetIAMPasswordPolicy
   ```

2. Manually trigger remediation:
   ```bash
   aws configservice start-remediation-execution \
     --config-rule-name <rule-name> \
     --resource-keys resourceType=AWS::Account,resourceId=<account-id>
   ```

### S3 Lifecycle Not Applied

**Symptom:** Buckets don't have lifecycle rules

**Possible Causes:**
1. Compliance frameworks not configured
2. Bucket created before lifecycle implementation

**Solution:**
1. Verify compliance frameworks are set in deployment context
2. Redeploy stack to apply lifecycle rules to existing buckets
3. Check deployment logs for lifecycle configuration messages

### Config Rules Not Created

**Symptom:** AWS Config rules missing after deployment

**Possible Causes:**
1. AWS Config not enabled in the account
2. Config recorder not created
3. Insufficient IAM permissions

**Solution:**
1. Verify AWS Config is enabled:
   ```bash
   aws configservice describe-configuration-recorders
   ```

2. Check deployment logs for Config-related errors
3. Verify IAM role has `config:PutConfigRule` permission

---

## Cost Optimization

### S3 Storage Costs

Lifecycle policies automatically optimize storage costs:

**Example Cost Savings (1 TB of logs):**
- Month 1-3 (S3 Standard): $23/month
- Month 3-12 (Glacier): $4/month
- Year 2-6 (Deep Archive): $1/month

**Annual Savings:** ~$200/TB compared to keeping all data in S3 Standard

### AWS Config Costs

- **Rule Evaluations**: $0.001 per evaluation
- **Configuration Items**: $0.003 per item
- **Estimated Monthly Cost**: $20-50 for typical deployment

**Cost Reduction Tips:**
- Use periodic evaluation instead of continuous where acceptable
- Disable rules in DEV environments
- Archive Config snapshots to S3 Glacier

---

## Security Considerations

### Least Privilege Access

All remediation actions use dedicated IAM roles with minimal permissions:

```java
Role ssmAutomationRole = Role.Builder.create(this, "RemediationRole")
    .assumedBy(new ServicePrincipal("ssm.amazonaws.com"))
    .inlinePolicies(Map.of(
        "RemediationPermissions",
        PolicyDocument.Builder.create()
            .statements(List.of(
                PolicyStatement.Builder.create()
                    .effect(Effect.ALLOW)
                    .actions(List.of("iam:UpdateAccountPasswordPolicy"))
                    .resources(List.of("*"))
                    .build()
            ))
            .build()
    ))
    .build();
```

### Audit Trail

All compliance actions are logged:
- **CloudTrail**: API calls and account activity
- **Config Timeline**: Resource configuration changes
- **SSM Automation**: Remediation execution history

### Data Protection

- **Encryption at Rest**: S3-managed encryption (SSE-S3)
- **Encryption in Transit**: TLS 1.2+ for all data transfer
- **Access Control**: Bucket policies and IAM policies restrict access
- **Versioning**: Immutable audit trail

---

## Best Practices

1. **Enable All Relevant Frameworks**: Configure all compliance frameworks your organization must meet
2. **Monitor Regularly**: Subscribe to Config rule notifications and review compliance dashboard weekly
3. **Test Before Production**: Deploy to staging environment first to verify compliance settings
4. **Document Exceptions**: If manual overrides are needed, document them for auditors
5. **Regular Audits**: Review Config rule compliance quarterly
6. **Cost Monitoring**: Track AWS Config and S3 storage costs using AWS Cost Explorer

---

## Additional Resources

- [AWS Config Developer Guide](https://docs.aws.amazon.com/config/latest/developerguide/)
- [AWS Systems Manager Automation](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-automation.html)
- [S3 Lifecycle Configuration](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html)
- [IAM Password Policy](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_passwords_account-policy.html)

---

## Support

For issues or questions:
- GitHub Issues: [cfc-core/issues](https://github.com/cloudforgeci/cfc-core/issues)
- Documentation: [docs/compliance/](.)
- Contact: support@cloudforgeci.com

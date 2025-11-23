# CloudTrail Bucket Access Auto-Remediation

## Overview

The CloudForge CI compliance system now includes automatic remediation for CloudTrail S3 bucket access errors. This feature automatically fixes common CloudTrail logging issues when AWS Config detects non-compliance.

## What It Does

When AWS Config detects that CloudTrail cannot write to its S3 bucket (due to incorrect bucket policies or permissions), the system automatically:

1. **Detects the Issue**: AWS Config rule `CLOUD_TRAIL_ENABLED` identifies that CloudTrail is not functioning properly
2. **Triggers Remediation**: AWS Config automatically initiates the remediation workflow
3. **Fixes Bucket Policy**: SSM Automation updates the S3 bucket policy with correct CloudTrail permissions
4. **Restores Compliance**: CloudTrail resumes logging audit events to the bucket

## Common Issues Fixed

This remediation automatically resolves:

- ✅ Missing bucket policy for CloudTrail service principal
- ✅ Incorrect bucket ACL permissions
- ✅ Bucket policies that inadvertently deny CloudTrail access
- ✅ Policy drift after manual bucket configuration changes

## How to Enable

### Method 1: Deployment Context (Recommended)

Add to your `deployment-context.json`:

```json
{
  "enableCloudTrailBucketAccessRemediation": true,
  "awsConfigEnabled": true
}
```

### Method 2: Programmatic Configuration

```java
DeploymentContext context = new DeploymentContext();
context.put("enableCloudTrailBucketAccessRemediation", true);
context.put("awsConfigEnabled", true);
```

## Prerequisites

- **AWS Config must be enabled** in your account
- **CloudTrail must be configured** (the system creates this automatically in PRODUCTION security profile)
- **IAM permissions** for SSM Automation to update S3 bucket policies

## How It Works

### Architecture

```
┌─────────────────┐
│   CloudTrail    │──┐ Cannot write to bucket
└─────────────────┘  │
                     ▼
┌─────────────────────────────────────────┐
│     AWS Config Rule                     │
│   (CLOUD_TRAIL_ENABLED)                 │
│   Detects: NON_COMPLIANT                │
└─────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│  Config Auto-Remediation                │
│  Triggers SSM Automation                │
└─────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│  SSM Automation Document                │
│  "fix-cloudtrail-bucket-access"         │
│                                         │
│  1. Get CloudTrail configuration        │
│  2. Update S3 bucket policy             │
│  3. Grant CloudTrail permissions        │
└─────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│  CloudTrail Resumes Logging             │
│  Status: COMPLIANT                      │
└─────────────────────────────────────────┘
```

### Remediation Configuration

- **Type**: Automatic
- **Max Attempts**: 3
- **Retry Interval**: 120 seconds
- **SSM Document**: Custom automation document created per stack

### IAM Permissions (Least Privilege)

The remediation creates an IAM role with **scoped permissions** following AWS security best practices. No wildcard (*) permissions are used.

```json
{
  "S3BucketPolicyManagement": {
    "Actions": [
      "s3:GetBucketPolicy",
      "s3:PutBucketPolicy",
      "s3:GetBucketAcl",
      "s3:PutBucketAcl"
    ],
    "Resources": [
      "arn:aws:s3:::cloudforge-cloudtrail-*-{region}"
    ],
    "Note": "Scoped to CloudForge CloudTrail buckets only - NOT wildcard"
  },
  "CloudTrailReadAccess": {
    "Actions": [
      "cloudtrail:GetTrail",
      "cloudtrail:DescribeTrails",
      "cloudtrail:GetEventSelectors"
    ],
    "Resources": [
      "arn:aws:cloudtrail:{region}:{account}:trail/cloudforge-cloudtrail-*"
    ],
    "Note": "Scoped to CloudForge trails only - NOT wildcard"
  }
}
```

**Security Note:** All IAM permissions are scoped to specific resource ARNs. The automation role cannot modify arbitrary S3 buckets or CloudTrail resources.

## Bucket Policy Applied

The remediation applies this S3 bucket policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AWSCloudTrailAclCheck",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudtrail.amazonaws.com"
      },
      "Action": "s3:GetBucketAcl",
      "Resource": "arn:aws:s3:::YOUR-BUCKET-NAME"
    },
    {
      "Sid": "AWSCloudTrailWrite",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudtrail.amazonaws.com"
      },
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::YOUR-BUCKET-NAME/AWSLogs/*",
      "Condition": {
        "StringEquals": {
          "s3:x-amz-acl": "bucket-owner-full-control"
        }
      }
    }
  ]
}
```

## Monitoring & Logging

### View Remediation Status

Check AWS Config Console:
```
AWS Config → Rules → cloud-trail-enabled → Remediation actions
```

### View SSM Automation Executions

Check Systems Manager Console:
```
Systems Manager → Automation → Executions → Filter by document name
```

### CloudWatch Logs

Remediation logs are available in CloudWatch Logs under:
```
/aws/ssm/automation/
```

## Compliance Impact

This feature supports the following compliance requirements:

| Framework | Requirement | Description |
|-----------|-------------|-------------|
| **PCI-DSS** | Req 10.2 | Automated audit trail protection |
| **HIPAA** | §164.308(a)(1)(ii)(D) | Information system activity review |
| **SOC 2** | CC8.1 | Change management and audit logging |
| **GDPR** | Art. 32 | Security measures for data processing |

## Security Considerations

### Least Privilege

The SSM Automation role follows least privilege principles:
- Only has permissions to read CloudTrail configuration
- Only can modify S3 bucket policies (not delete or create buckets)
- Scoped to specific automation tasks

### Audit Trail

All remediation actions are logged:
- **CloudTrail**: Records all S3 PutBucketPolicy API calls
- **AWS Config Timeline**: Shows remediation trigger and completion
- **SSM Automation History**: Detailed execution logs with timestamps

### Policy Preservation

The remediation:
- ✅ Only adds necessary CloudTrail permissions
- ✅ Does not remove existing bucket policy statements
- ✅ Merges with existing policies when possible
- ❌ Does not grant public access
- ❌ Does not weaken existing security controls

## Error Handling & Safety

### Pre-Deployment Validation

The system performs comprehensive null guards and validation checks **before** creating remediation:

1. **CloudTrail Existence Check**: Verifies CloudTrail is configured before enabling remediation
2. **S3 Bucket Existence Check**: Confirms CloudTrail S3 bucket exists and is accessible
3. **Trail Name Validation**: Ensures CloudTrail has a valid name assigned

If any of these checks fail, the deployment will **fail fast** with a clear error message:

```
IllegalStateException: Cannot configure CloudTrail bucket access remediation:
CloudTrail is not configured. Ensure CloudTrail is enabled in the security profile configuration.
```

This prevents silent failures and ensures remediation only runs when resources exist.

### Audit Logging

All remediation actions are automatically logged:

- **CloudTrail**: Records all S3 PutBucketPolicy API calls with full request/response details
- **AWS Config Timeline**: Shows when remediation was triggered and completed
- **SSM Automation History**: Provides step-by-step execution logs with timestamps
- **CloudWatch Logs**: Contains detailed automation execution output under `/aws/ssm/automation/`

## Troubleshooting

### Remediation Not Triggering

**Problem**: Config rule shows NON_COMPLIANT but remediation doesn't run

**Solutions**:
1. Check that `enableCloudTrailBucketAccessRemediation` is `true` in deployment context
2. Verify AWS Config is enabled: `aws configservice describe-configuration-recorders`
3. Ensure the Config rule exists: `aws configservice describe-config-rules --config-rule-names cloud-trail-enabled`

### Deployment Fails with "CloudTrail is not configured"

**Problem**: CDK deployment fails during stack synthesis

**Solutions**:
1. This is expected behavior if CloudTrail is disabled in your security profile
2. Either enable CloudTrail: `cfc.put("security", "PRODUCTION")` (CloudTrail enabled by default)
3. Or disable auto-remediation: `cfc.put("enableCloudTrailBucketAccessRemediation", false)`
4. Check your security profile configuration implements `isCloudTrailEnabled()` correctly

### Remediation Fails

**Problem**: Remediation executes but fails

**Solutions**:
1. Check SSM Automation execution logs in Systems Manager console
2. Verify IAM role has correct permissions
3. Ensure S3 bucket exists and is in the same region
4. Check for bucket policies that explicitly deny CloudTrail

### Permission Denied Errors

**Problem**: SSM Automation fails with "Access Denied"

**Solutions**:
1. Verify SSM Automation role has `s3:PutBucketPolicy` permission
2. Check S3 bucket policy doesn't deny SSM principal
3. Ensure no SCPs blocking S3 policy updates

## Cost Implications

- **AWS Config Rule Evaluations**: Minimal cost (periodic evaluations)
- **SSM Automation Executions**: ~$0.002 per execution
- **CloudTrail Logging**: Standard CloudTrail pricing applies

Typical monthly cost for auto-remediation: **< $1**

## Disabling Auto-Remediation

To disable automatic remediation while keeping Config monitoring:

```json
{
  "enableCloudTrailBucketAccessRemediation": false,
  "awsConfigEnabled": true
}
```

You can also disable it by removing the deployment context property entirely.

## Operational Procedures

### For Production Deployments

**Pre-Deployment Checklist:**

1. ✅ **Verify CloudTrail is Enabled**: Check `isCloudTrailEnabled()` in security profile
2. ✅ **Test in Staging First**: Deploy to staging environment with `enableCloudTrailBucketAccessRemediation=true`
3. ✅ **Review IAM Permissions**: Confirm automation role has scoped permissions (not wildcard)
4. ✅ **Set Up Monitoring**: Configure CloudWatch alarms for failed remediations
5. ✅ **Document Override Rationale**: If disabling remediation, document why in deployment context

**Post-Deployment Verification:**

```bash
# 1. Verify CloudTrail is logging
aws cloudtrail get-trail-status --name cloudforge-cloudtrail-us-east-1

# 2. Check Config rule compliance
aws configservice describe-compliance-by-config-rule \
  --config-rule-names cloud-trail-enabled

# 3. Verify remediation configuration exists
aws configservice describe-remediation-configurations \
  --config-rule-names cloud-trail-enabled

# 4. Test remediation trigger (optional - requires breaking CloudTrail)
# Do NOT run in production without approval
aws s3api put-bucket-policy --bucket cloudforge-cloudtrail-... \
  --policy '{"Version":"2012-10-17","Statement":[]}'
```

**Safe Operational Procedures:**

- **Changing Bucket Policies Manually**: Auto-remediation will overwrite manual changes after ~15 minutes
  - To prevent: Disable auto-remediation, make changes, re-enable
  - Better approach: Use Infrastructure as Code (IaC) to manage policies

- **Decommissioning CloudTrail**: Disable auto-remediation before deleting CloudTrail
  ```json
  {
    "enableCloudTrailBucketAccessRemediation": false
  }
  ```
  - Re-deploy stack
  - Then delete CloudTrail via Console or CLI

- **Multi-Region Deployments**: Each region requires separate auto-remediation configuration
  - Automation roles are region-specific
  - SSM documents are region-specific
  - S3 buckets can be shared across regions (but shouldn't be for compliance)

### Scope of Auto-Remediation

**What Auto-Remediation WILL Fix:**

- ✅ Missing CloudTrail service principal in bucket policy
- ✅ Incorrect bucket policy statement structure
- ✅ Bucket policy denying CloudTrail access
- ✅ Missing `s3:GetBucketAcl` permission for CloudTrail
- ✅ Missing `s3:PutObject` permission for CloudTrail
- ✅ Incorrect ACL conditions on `s3:PutObject`

**What Auto-Remediation WILL NOT Fix:**

- ❌ CloudTrail doesn't exist (will fail deployment - requires CloudTrail creation)
- ❌ S3 bucket doesn't exist (will fail deployment - requires bucket creation)
- ❌ S3 bucket policy size exceeds 20KB limit (requires manual intervention)
- ❌ Bucket encrypted with KMS key that CloudTrail can't access (requires KMS policy update)
- ❌ Bucket in different account (cross-account CloudTrail requires separate setup)
- ❌ Bucket in wrong region (CloudTrail requires bucket in same region)
- ❌ AWS Organizations service control policies (SCPs) blocking S3 policy updates

**Remediation Frequency:**

- Triggers: When AWS Config detects NON_COMPLIANT status
- Config evaluation: Every 24 hours OR on configuration change
- Max attempts: 3 per Config rule evaluation
- Retry interval: 120 seconds between attempts
- Total remediation window: ~6 minutes maximum (3 attempts × 120 seconds)

### Security Considerations for Operations Teams

**Least Privilege Verification:**

The automation role has these permissions (verify in IAM console):
```
arn:aws:iam::ACCOUNT_ID:role/CloudTrailBucketAccessRemediationRole

Permissions:
- s3:GetBucketPolicy on arn:aws:s3:::cloudforge-cloudtrail-*
- s3:PutBucketPolicy on arn:aws:s3:::cloudforge-cloudtrail-*
- cloudtrail:GetTrail on arn:aws:cloudtrail:*:ACCOUNT_ID:trail/cloudforge-cloudtrail-*
```

**Audit Trail Review:**

All remediation actions are logged. Review monthly:

```bash
# Check CloudTrail logs for S3 PutBucketPolicy calls
aws cloudtrail lookup-events \
  --lookup-attributes AttributeKey=EventName,AttributeValue=PutBucketPolicy \
  --start-time $(date -u -d '30 days ago' +%Y-%m-%dT%H:%M:%S) \
  --max-items 50

# Check SSM Automation execution history
aws ssm describe-automation-executions \
  --filters Key=DocumentNamePrefix,Values=fix-cloudtrail-bucket-access \
  --max-results 50

# Check Config compliance timeline
aws configservice get-compliance-details-by-config-rule \
  --config-rule-name cloud-trail-enabled
```

**Incident Response:**

If auto-remediation is causing issues:

1. **Immediate Action**: Disable auto-remediation in deployment context
2. **Diagnosis**: Review SSM Automation execution logs
3. **Mitigation**: Fix underlying issue (e.g., KMS key permissions)
4. **Re-enable**: Once root cause addressed, re-enable remediation
5. **Document**: Update runbook with issue and resolution

## Best Practices

1. **Test in Non-Production First**: Enable in DEV/STAGING before PRODUCTION
2. **Monitor Remediation Logs**: Review SSM Automation executions regularly (see Operational Procedures above)
3. **Set Up Alerts**: Create CloudWatch alarms for failed remediations (see example below)
4. **Document Exceptions**: If you need custom bucket policies, document them in IaC comments
5. **Review Compliance Reports**: Check AWS Config compliance dashboard weekly
6. **Audit Automation Roles**: Verify IAM permissions are scoped (not wildcard) quarterly
7. **Test Remediation**: Periodically test remediation in staging by intentionally breaking bucket policy

### CloudWatch Alarm Example

```json
{
  "AlarmName": "CloudTrailRemediationFailed",
  "MetricName": "ExecutionsFailed",
  "Namespace": "AWS/SSM-Automation",
  "Dimensions": [
    {
      "Name": "DocumentName",
      "Value": "fix-cloudtrail-bucket-access"
    }
  ],
  "Statistic": "Sum",
  "Period": 300,
  "EvaluationPeriods": 1,
  "Threshold": 1,
  "ComparisonOperator": "GreaterThanOrEqualToThreshold",
  "TreatMissingData": "notBreaching"
}
```

## Related Features

- [S3 Versioning Auto-Remediation](./S3_VERSIONING_REMEDIATION.md)
- [AWS Config Rules](./AWS_CONFIG_RULES.md)
- [CloudTrail Configuration](./CLOUDTRAIL_SETUP.md)
- [Compliance Frameworks](./AUDITOR_COMPLIANCE_MAPPING.md)

## Example Deployment

Complete example with CloudTrail remediation enabled:

```json
{
  "security": "PRODUCTION",
  "awsConfigEnabled": true,
  "enableCloudTrailBucketAccessRemediation": true,
  "complianceFrameworks": "PCI-DSS,SOC2,HIPAA",
  "createConfigInfrastructure": true
}
```

## Support

For issues or questions:
- GitHub Issues: https://github.com/CloudForgeCI/cfc-core/issues
- Documentation: [docs/](../docs/)
- Compliance Guide: [AUDITOR_COMPLIANCE_MAPPING.md](./AUDITOR_COMPLIANCE_MAPPING.md)

# CloudTrail Bucket Access Auto-Remediation

## Overview

CloudForge CI can attach an AWS Config remediation that restores the CloudTrail S3 bucket policy when CloudTrail cannot write to its bucket. The remediation is off by default.

## What It Does

When AWS Config detects that CloudTrail cannot write to its S3 bucket (due to incorrect bucket policies or permissions), the system automatically:

1. **Detects the Issue**: The AWS Config managed rule `CLOUD_TRAIL_ENABLED` reports the account as non-compliant
2. **Triggers Remediation**: AWS Config automatically initiates the remediation workflow
3. **Fixes Bucket Policy**: SSM Automation updates the S3 bucket policy with correct CloudTrail permissions
4. **Restores Compliance**: CloudTrail resumes logging audit events to the bucket

## Common Issues Fixed

The remediation writes a bucket policy that grants CloudTrail `s3:GetBucketAcl` and `s3:PutObject`. This resolves:

- Missing bucket policy for the CloudTrail service principal
- Bucket policies that deny CloudTrail access
- Policy drift after manual bucket policy changes

## How to Enable

Add to your deployment context (for example `deployment-context.json`):

```json
{
  "securityProfile": "production",
  "awsConfigEnabled": true,
  "enableCloudTrailBucketAccessRemediation": true
}
```

## Prerequisites

- **`securityProfile: production`**: the `CLOUD_TRAIL_ENABLED` rule and this remediation are created only for `production` stacks
- **`awsConfigEnabled: true`**, and a Config recorder in the account and region (created by one stack with `createConfigInfrastructure: true`)
- **CloudTrail created by the stack**: CloudForge creates a trail for `staging` and `production` profiles

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
│  "<stackName>-fix-cloudtrail-bucket-    │
│   access"                               │
│  1. GetCloudTrailBucket (GetTrail)      │
│  2. FixBucketPolicy (PutBucketPolicy)   │
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
- **SSM Document**: `<stackName>-fix-cloudtrail-bucket-access`, created per stack

### IAM Permissions (Least Privilege)

The remediation creates an IAM role (logical ID `CloudTrailBucketAccessRemediationRole`) whose inline policy is scoped to the exact trail and bucket of this stack:

| Statement | Actions | Resource |
|-----------|---------|----------|
| `S3BucketPolicyManagement` | `s3:GetBucketPolicy`, `s3:PutBucketPolicy`, `s3:GetBucketAcl`, `s3:PutBucketAcl` | The stack's CloudTrail bucket ARN |
| `CloudTrailReadAccess` | `cloudtrail:GetTrail`, `cloudtrail:DescribeTrails`, `cloudtrail:GetEventSelectors` | The stack's trail ARN |

The automation role cannot modify other S3 buckets or trails.

## Bucket Policy Applied

The remediation replaces the bucket policy with this policy:

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
AWS Config → Rules → <rule generated from the CloudTrailEnabledRule logical ID> → Remediation actions
```

### View SSM Automation Executions

Check Systems Manager Console:
```
Systems Manager → Automation → Executions → Filter by document name
```

### CloudWatch Logs

SSM Automation writes step output to CloudWatch Logs only when Automation logging is enabled for the account. CloudForge does not enable it.

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

The SSM Automation role:
- Can read the stack's trail configuration
- Can read and write the policy and ACL of the stack's CloudTrail bucket, but cannot create or delete buckets

### Audit Trail

All remediation actions are logged:
- **CloudTrail**: Records all S3 PutBucketPolicy API calls
- **AWS Config Timeline**: Shows remediation trigger and completion
- **SSM Automation History**: Detailed execution logs with timestamps

### Policy Replacement

The remediation calls `PutBucketPolicy` with the two CloudTrail statements shown above. This **replaces** the existing bucket policy: any other statements on the bucket, such as a TLS-only (`aws:SecureTransport`) deny statement, are removed. The policy it writes does not grant public access. If the CloudTrail bucket needs additional statements, leave this remediation disabled and manage the policy in code.

## Error Handling & Safety

### Pre-Deployment Validation

`ComplianceFactory` checks the following **before** creating the remediation:

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
- **CloudWatch Logs**: Automation output, if SSM Automation CloudWatch logging is enabled in the account

## Troubleshooting

### Remediation Not Triggering

**Problem**: Config rule shows NON_COMPLIANT but remediation doesn't run

**Solutions**:
1. Check that `enableCloudTrailBucketAccessRemediation` is `true` in deployment context
2. Verify AWS Config is enabled: `aws configservice describe-configuration-recorders`
3. Ensure the stack uses the `production` profile and that the Config rule exists: `aws configservice describe-config-rules` (look for the rule generated from the `CloudTrailEnabledRule` logical ID)

### Deployment Fails with "CloudTrail is not configured"

**Problem**: CDK deployment fails during stack synthesis

**Solutions**:
1. This is expected if the stack did not create a trail
2. Use `"securityProfile": "production"` (CloudTrail is created for `staging` and `production`)
3. Or disable auto-remediation: `"enableCloudTrailBucketAccessRemediation": false`

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

- **AWS Config rule evaluations**: charged per evaluation
- **SSM Automation executions**: charged per step beyond the free tier
- **CloudTrail logging**: standard CloudTrail pricing applies

## Disabling Auto-Remediation

To disable automatic remediation while keeping Config monitoring:

```json
{
  "enableCloudTrailBucketAccessRemediation": false,
  "awsConfigEnabled": true
}
```

The property defaults to `false`, so removing it also disables the remediation.

## Operational Procedures

### For Production Deployments

**Pre-Deployment Checklist:**

1. **Verify CloudTrail is created**: The stack uses the `staging` or `production` profile
2. **Review the bucket policy**: The remediation replaces it (see [Policy Replacement](#policy-replacement))
3. **Review IAM permissions**: Confirm the automation role is scoped to the stack's trail and bucket
4. **Set up monitoring**: Configure CloudWatch alarms for failed remediations
5. **Document the decision**: Record why the remediation is enabled or disabled

**Post-Deployment Verification:**

```bash
# 1. Verify CloudTrail is logging
aws cloudtrail get-trail-status --name cloudforge-cloudtrail-<stack-name>

# 2. Check Config rule compliance
aws configservice describe-compliance-by-config-rule \
  --config-rule-names <cloudtrail-rule-name>

# 3. Verify remediation configuration exists
aws configservice describe-remediation-configurations \
  --config-rule-names <cloudtrail-rule-name>

# 4. Test remediation trigger (optional - requires breaking CloudTrail)
# Do NOT run in production without approval
aws s3api put-bucket-policy --bucket <cloudtrail-bucket-name> \
  --policy '{"Version":"2012-10-17","Statement":[]}'
```

**Safe Operational Procedures:**

- **Changing Bucket Policies Manually**: Auto-remediation overwrites manual changes the next time Config evaluates the rule as non-compliant
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

- Missing CloudTrail service principal in bucket policy
- Bucket policy denying CloudTrail access
- Missing `s3:GetBucketAcl` or `s3:PutObject` permission for CloudTrail
- Incorrect ACL conditions on `s3:PutObject`

**What Auto-Remediation WILL NOT Fix:**

- CloudTrail doesn't exist (synthesis fails when the remediation is enabled without a trail)
- S3 bucket doesn't exist
- Bucket encrypted with a KMS key that CloudTrail can't use (requires a KMS key policy update)
- Bucket in a different account (cross-account CloudTrail requires separate setup)
- AWS Organizations service control policies (SCPs) blocking S3 policy updates

**Remediation Frequency:**

- Triggers: When AWS Config detects NON_COMPLIANT status
- Config evaluation: Every 24 hours OR on configuration change
- Max attempts: 3 per Config rule evaluation
- Retry interval: 120 seconds between attempts
- Retry window: about 6 minutes (3 attempts at 120-second intervals)

### Security Considerations for Operations Teams

**Least Privilege Verification:**

The automation role has these permissions (verify in IAM console):
```
Role: <stack>-CloudTrailBucketAccessRemediationRole... (generated name)

Permissions:
- s3:GetBucketPolicy, s3:PutBucketPolicy, s3:GetBucketAcl, s3:PutBucketAcl
  on the stack's CloudTrail bucket
- cloudtrail:GetTrail, cloudtrail:DescribeTrails, cloudtrail:GetEventSelectors
  on arn:aws:cloudtrail:<region>:<account-id>:trail/cloudforge-cloudtrail-<stack-name>
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
  --filters Key=DocumentNamePrefix,Values=<stack-name>-fix-cloudtrail-bucket-access \
  --max-results 50

# Check Config compliance timeline
aws configservice get-compliance-details-by-config-rule \
  --config-rule-name <cloudtrail-rule-name>
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
      "Value": "<stack-name>-fix-cloudtrail-bucket-access"
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

- [S3 Versioning Auto-Remediation](compliance/S3_VERSIONING_REMEDIATION.md)
- [Automated Compliance](compliance/AUTOMATED_COMPLIANCE.md)
- [AWS Config Multi-Stack](compliance/AWS_CONFIG_MULTI_STACK.md)
- [Compliance Frameworks](./AUDITOR_COMPLIANCE_MAPPING.md)

## Example Deployment

Complete example with CloudTrail remediation enabled:

```json
{
  "securityProfile": "production",
  "awsConfigEnabled": true,
  "enableCloudTrailBucketAccessRemediation": true,
  "complianceFrameworks": "pci-dss,soc2,hipaa",
  "createConfigInfrastructure": true
}
```

## Support

For issues or questions:
- GitHub Issues: https://github.com/CloudForgeCI/cfc-core/issues
- Documentation: [docs/](https://github.com/CloudForgeCI/cfc-core/tree/develop/.)
- Compliance Guide: [AUDITOR_COMPLIANCE_MAPPING.md](./AUDITOR_COMPLIANCE_MAPPING.md)

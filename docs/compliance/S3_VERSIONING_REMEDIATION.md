# S3 Versioning Automatic Remediation

## Overview

CloudForge CI can automatically enable S3 bucket versioning on non-compliant buckets using AWS Config and AWS Systems Manager. This feature is **optional** and configurable to suit different deployment scenarios.

## Quick Start

### Option 1: Scope to Deployment Only (Recommended for Testing)

Monitor and remediate only buckets created by your CloudFormation stack:

```json
{
  "stackName": "jenkinsTSoc",
  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "scopeConfigRulesToDeployment": true,
  "enableS3VersioningRemediation": true
}
```

**Result:**
- ✅ Only monitors buckets from your stack
- ✅ Shows 100% compliance for your deployment
- ✅ Won't attempt to modify buckets from other projects

### Option 2: Account-Wide Monitoring (Recommended for Production)

Monitor all S3 buckets in your AWS account:

```json
{
  "stackName": "jenkinsTSoc",
  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "scopeConfigRulesToDeployment": false,
  "enableS3VersioningRemediation": false
}
```

**Result:**
- ✅ Monitors ALL buckets in the account
- ✅ Provides visibility into all S3 resources
- ⚠️ May show non-compliant buckets from other projects
- ⚠️ Manual remediation required (no automatic changes)

### Option 3: Account-Wide with Auto-Remediation (Organization-Wide Compliance)

Monitor and automatically remediate all buckets:

```json
{
  "stackName": "jenkinsTSoc",
  "complianceFrameworks": "SOC2,HIPAA",
  "awsConfigEnabled": true,
  "scopeConfigRulesToDeployment": false,
  "enableS3VersioningRemediation": true
}
```

**Result:**
- ✅ Monitors ALL buckets in the account
- ✅ Automatically enables versioning on non-compliant buckets
- ⚠️ Will modify buckets from other projects
- ⚠️ Storage costs increase due to versioning

## Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `scopeConfigRulesToDeployment` | boolean | `false` | Limit monitoring to buckets from this stack |
| `enableS3VersioningRemediation` | boolean | `false` | Automatically enable versioning on non-compliant buckets |
| `stackName` | string | required | CloudFormation stack name (used for scoping) |
| `awsConfigEnabled` | boolean | required | Must be `true` to enable Config rules |

## How It Works

### 1. AWS Config Rule Creation

CloudForge creates an AWS Config rule that monitors S3 bucket versioning:

```
AWS Config Rule: S3_BUCKET_VERSIONING_ENABLED
├─ Owner: AWS (Managed Rule)
├─ Scope: All buckets OR Stack-specific buckets
└─ Evaluation: Continuous (on configuration change)
```

### 2. Scoping (Optional)

When `scopeConfigRulesToDeployment=true`:

```
Config Rule Scope:
├─ Resource Type: AWS::S3::Bucket
├─ Tag Key: aws:cloudformation:stack-name
└─ Tag Value: {stackName}
```

CloudFormation automatically tags all resources it creates, so this scope filter automatically limits the rule to your deployment's buckets.

### 3. Remediation (Optional)

When `enableS3VersioningRemediation=true`:

```
Remediation Configuration:
├─ Target: AWS-ConfigureS3BucketVersioning (SSM Document)
├─ Mode: Automatic
├─ Max Attempts: 5
├─ Retry Interval: 60 seconds
└─ IAM Role: S3VersioningRemediationRole (auto-created)
```

**Remediation Flow:**
1. Config detects non-compliant bucket
2. Triggers SSM Automation document
3. SSM calls `s3:PutBucketVersioning`
4. Config re-evaluates compliance
5. Reports compliant status

## Cost Implications

### Storage Costs

Enabling versioning affects storage costs:

| Scenario | Impact |
|----------|--------|
| **Low-churn buckets** | Minimal cost increase (~5-10%) |
| **High-churn buckets** | Significant cost increase (2-3x or more) |
| **Frequently updated objects** | Each update creates a new version |

### Example Cost Calculation

```
Bucket with 1,000 objects (1 GB total):
├─ Without versioning: $0.023/month (S3 Standard)
├─ With versioning (1 update/day): $0.69/month (30 versions)
└─ Cost increase: 30x
```

### Mitigation Strategies

1. **Lifecycle Policies**: Automatically delete old versions
2. **Scoping**: Only enable for compliance-critical buckets
3. **Manual Review**: Use `scopeConfigRulesToDeployment=true` initially

## Compliance Requirements

Different frameworks have different versioning requirements:

| Framework | Versioning Required? | Scope |
|-----------|---------------------|-------|
| **SOC2** | Yes | Audit logs and critical data |
| **HIPAA** | Yes | All PHI-containing buckets |
| **PCI-DSS** | Yes | Cardholder data and audit logs |
| **GDPR** | Recommended | Personal data buckets |

## Troubleshooting

### Issue: Rule shows 20+ non-compliant resources

**Cause:** Default monitoring includes all account buckets

**Solution:**
```json
{
  "scopeConfigRulesToDeployment": true
}
```

### Issue: Remediation not triggering

**Check:**
1. Verify `enableS3VersioningRemediation: true`
2. Check IAM role permissions in CloudFormation
3. Review SSM Automation execution history
4. Confirm Config rule is in "COMPLIANT" state

**AWS Console Path:**
```
AWS Config → Rules → S3VersioningRule → Remediation actions
```

### Issue: Cost increase after enabling versioning

**Mitigation:**
```json
{
  "scopeConfigRulesToDeployment": true,
  "enableS3VersioningRemediation": false
}
```

Then manually enable versioning only on critical buckets.

## Security Considerations

### IAM Permissions

The remediation role requires:
```json
{
  "Effect": "Allow",
  "Action": [
    "s3:PutBucketVersioning",
    "s3:GetBucketVersioning"
  ],
  "Resource": "*"
}
```

### Audit Trail

All remediation actions are logged:
- CloudTrail: `PutBucketVersioning` API calls
- Config: Compliance evaluation history
- SSM: Automation execution logs

## Best Practices

1. **Start Scoped**: Use `scopeConfigRulesToDeployment: true` initially
2. **Test First**: Deploy to dev/staging before production
3. **Monitor Costs**: Set up billing alerts for S3 storage
4. **Document Decisions**: Record why auto-remediation is enabled/disabled
5. **Review Regularly**: Audit which buckets have versioning enabled

## Migration Path

### Phase 1: Monitoring Only
```json
{
  "scopeConfigRulesToDeployment": false,
  "enableS3VersioningRemediation": false
}
```
**Goal:** Understand which buckets lack versioning

### Phase 2: Scoped Remediation
```json
{
  "scopeConfigRulesToDeployment": true,
  "enableS3VersioningRemediation": true
}
```
**Goal:** Ensure deployment buckets are compliant

### Phase 3: Organization-Wide Enforcement
```json
{
  "scopeConfigRulesToDeployment": false,
  "enableS3VersioningRemediation": true
}
```
**Goal:** Enforce versioning across all buckets

## Related Documentation

- [Automated Compliance Features](./AUTOMATED_COMPLIANCE.md)
- [Multi-Framework Compliance](./MULTI_FRAMEWORK_COMPLIANCE.md)
- [Deployment Guide](./DEPLOYMENT_GUIDE.md)
- [AWS Config Multi-Stack](./AWS_CONFIG_MULTI_STACK.md)

## Support

For issues or questions:
1. Check [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
2. Review AWS Config rule evaluation history
3. Check SSM Automation execution logs
4. Verify IAM role permissions

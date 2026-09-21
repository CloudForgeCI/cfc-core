# S3 Versioning Automatic Remediation

## Overview

CloudForge CI can enable S3 bucket versioning on non-compliant buckets using AWS Config and AWS Systems Manager. This feature is **optional** and off by default.

## Requirements and Current Limitations

- `awsConfigEnabled` must be `true`.
- The remediation is attached only when this stack also creates the Config recorder (`createConfigInfrastructure: true`). Stacks that reference an existing recorder create the versioning rule without remediation.
- `scopeConfigRulesToDeployment` is read by `ComplianceFactory` but is not yet exposed through `DeploymentContext`, so it cannot be set from configuration. The rule currently monitors **all S3 buckets in the account**, and remediation, when enabled, applies to all of them.

## Configurations

### Monitoring Only

```json
{
  "stackName": "my-stack",
  "complianceFrameworks": "soc2",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "enableS3VersioningRemediation": false
}
```

**Result:**
- Monitors all buckets in the account
- May report non-compliant buckets from other projects
- No automatic changes

### Account-Wide Auto-Remediation

```json
{
  "stackName": "my-stack",
  "complianceFrameworks": "soc2,hipaa",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "enableS3VersioningRemediation": true
}
```

**Result:**
- Monitors all buckets in the account
- Enables versioning on non-compliant buckets, including buckets from other projects
- Storage costs increase for buckets that become versioned

### Stack-Scoped Monitoring (once `scopeConfigRulesToDeployment` is exposed)

```json
{
  "stackName": "my-stack",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "scopeConfigRulesToDeployment": true,
  "enableS3VersioningRemediation": true
}
```

With scoping, the rule would evaluate only buckets tagged with this stack's name.

## Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enableS3VersioningRemediation` | boolean | `false` | Automatically enable versioning on non-compliant buckets |
| `scopeConfigRulesToDeployment` | boolean | `false` | Limit monitoring to buckets from this stack (not yet settable; see above) |
| `stackName` | string | required | CloudFormation stack name (used for scoping) |
| `awsConfigEnabled` | boolean | `false` | Must be `true` to create Config rules |
| `createConfigInfrastructure` | boolean | `false` | Must be `true` for the remediation to be attached |

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

CloudFormation tags the resources it creates with `aws:cloudformation:stack-name`, so this scope filter limits the rule to the deployment's buckets.

### 3. Remediation (Optional)

When `enableS3VersioningRemediation=true`:

```
Remediation Configuration:
├─ Target: AWS-ConfigureS3BucketVersioning (SSM Document)
├─ Mode: Automatic
├─ Max Attempts: 5
├─ Retry Interval: 60 seconds
└─ IAM Role: created from the S3VersioningRemediationRole logical ID
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
| **Low-churn buckets** | Small increase |
| **High-churn buckets** | Storage can grow by a multiple of the current size |
| **Frequently updated objects** | Each update creates a new stored version |

For example, an object overwritten once a day keeps about 30 versions after a month unless a lifecycle rule expires noncurrent versions.

### Mitigation Strategies

1. **Lifecycle Policies**: Automatically delete old versions
2. **Manual Review**: Leave remediation off and enable versioning on selected buckets manually

## Compliance Requirements

None of the frameworks names S3 versioning explicitly. Versioning is one way to support the integrity and availability requirements below; how it applies to your data is an assessment decision.

| Framework | Related Requirement | Typical Scope |
|-----------|---------------------|---------------|
| **SOC2** | Integrity and availability of evidence | Audit logs and critical data |
| **HIPAA** | Integrity controls (§164.312(c)(1)) | PHI-containing buckets |
| **PCI-DSS** | Protection of audit logs (Req 10) | Audit logs |
| **GDPR** | Availability and resilience (Art. 32) | Personal data buckets |

## Troubleshooting

### Issue: Rule shows 20+ non-compliant resources

**Cause:** The rule monitors all buckets in the account. Stack scoping is not yet configurable (see [Requirements and Current Limitations](#requirements-and-current-limitations)).

### Issue: Remediation not triggering

**Check:**
1. Verify `enableS3VersioningRemediation: true`
2. Verify the stack creates the Config recorder (`createConfigInfrastructure: true`)
3. Check IAM role permissions in CloudFormation
4. Review SSM Automation execution history

**AWS Console Path:**
```
AWS Config → Rules → S3VersioningRule → Remediation actions
```

### Issue: Cost increase after enabling versioning

**Mitigation:** Set `"enableS3VersioningRemediation": false`, then enable versioning manually only on critical buckets, and add lifecycle rules that expire noncurrent versions.

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

1. **Start with monitoring only**: Review the non-compliant buckets before enabling remediation
2. **Test First**: Deploy to dev/staging before production
3. **Monitor Costs**: Set up billing alerts for S3 storage
4. **Document Decisions**: Record why auto-remediation is enabled/disabled
5. **Review Regularly**: Audit which buckets have versioning enabled

## Adoption Path

1. **Monitoring only** (`enableS3VersioningRemediation: false`): identify which buckets lack versioning.
2. **Manual fixes**: enable versioning on the buckets that need it, with lifecycle rules for noncurrent versions.
3. **Account-wide remediation** (`enableS3VersioningRemediation: true`): enforce versioning across all buckets in the account.

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

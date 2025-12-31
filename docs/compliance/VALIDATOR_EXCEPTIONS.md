# CloudForge Compliance Validator Exceptions

This document explains findings that are expected from third-party compliance validators and why they cannot or should not be addressed.

## CDK Framework Resources (Cannot Fix)

These resources are created internally by AWS CDK and cannot be modified without significant escape hatches that would break CDK functionality.

### Inline IAM Policies

| Resource Pattern | Reason |
|-----------------|--------|
| `*CustomResourcePolicy*` | CDK AwsCustomResource creates inline policies for AWS SDK calls |
| `*DefaultPolicy*` | CDK's default policy attachment pattern for IAM roles |
| `CognitoSmsRole` | CDK Cognito construct creates inline policy for SMS |
| `UserPool/smsRole` | CDK Cognito UserPool SMS role |
| `VpcFlowlog/IAMRole/DefaultPolicy` | CDK VPC Flow Log construct |
| `CloudTrail/LogsRole/DefaultPolicy` | CDK CloudTrail construct |

**Mitigation**: Application IAM roles use Customer Managed Policies. These are deployment-time framework policies only.

### Lambda Functions (CDK Custom Resources)

| Resource Pattern | Findings | Reason |
|-----------------|----------|--------|
| `AWS679f53fac002430cb0da5b7982bd2287` | Not in VPC, no DLQ, no code signing, no concurrency limit | CDK Custom Resource Provider Lambda |
| `LogRetentionaae0aa3c5b4d4f87b02d85b201efdd8a` | Not in VPC, no DLQ, no code signing, no concurrency limit | CDK Log Retention Lambda |

**Mitigation**: These are deployment-time framework functions that:
- Only make AWS API calls (no VPC access needed)
- Failures visible in CloudFormation stack events (no DLQ needed)
- Run briefly during deploy/update only
- Do not process application data

---

## Expected Architecture Patterns

### ALB Security Group - 0.0.0.0/0 Ingress

| Resource | Finding | Reason |
|----------|---------|--------|
| `*AlbSg*` | IPv4 address cannot be 0.0.0.0/0 | Public-facing ALB must accept traffic from internet |

**Mitigation**:
- Only ports 80 and 443 are open
- HTTP redirects to HTTPS when SSL enabled
- WAF can be enabled for additional protection
- Application runs in private subnets

### Public Subnet Configuration

| Resource | Finding | Reason |
|----------|---------|--------|
| `publicSubnet*/Subnet` | MapPublicIpOnLaunch is enabled | Required for NAT Gateway and ALB placement |

**Mitigation**:
- Application workloads run in private subnets
- Public subnets only contain NAT Gateway and ALB
- No application instances receive public IPs

### Secrets Cross-Region Replication

| Resource | Finding | Reason |
|----------|---------|--------|
| `*dbSecret*` | Cross region replication disabled | Single-region deployment |
| `*CognitoClientSecret*` | Cross region replication disabled | Single-region deployment |

**Mitigation**:
- Secrets can be recreated from source (RDS, Cognito)
- Cross-region replication adds ~$1/secret/month
- Enable via `secretsReplicationEnabled` flag if required

---

## Known AWS Service Limitations

### ALB Access Logs Bucket - No KMS

| Resource | Finding | Reason |
|----------|---------|--------|
| `*AlbLogsBucket*` | KMS encryption not used | **AWS Limitation**: ALB access logs do not support KMS encryption |

**Mitigation**:
- SSE-S3 (AES-256) encryption is enabled
- SSL enforcement via bucket policy
- Bucket has versioning enabled
- Block public access enabled

### S3 Bucket Server Access Logging

| Resource | Finding | Reason |
|----------|---------|--------|
| `CloudTrailBucket` | Logging not configured | Circular dependency - CloudTrail logs to this bucket |
| `AlbLogsBucket` | Logging not configured | Would create second log bucket chain |

**Mitigation**:
- CloudTrail S3 data events provide comprehensive audit logging
- All S3 API calls are logged via CloudTrail
- Bucket access is logged at API level, not file level

### S3 Object Lock

| Resource | Finding | Reason |
|----------|---------|--------|
| `CloudTrailBucket` | ObjectLockEnabled not set | WORM compliance is optional |
| `AlbLogsBucket` | ObjectLockEnabled not set | WORM compliance is optional |

**Mitigation**:
- Versioning is enabled (prevents accidental deletion)
- Lifecycle policies archive to Glacier
- Object Lock adds complexity and cost
- Enable if regulatory WORM requirement exists

### Organization Trail

| Resource | Finding | Reason |
|----------|---------|--------|
| `CloudTrail` | IsOrganizationTrail not configured | Single-account deployment |

**Mitigation**:
- Organization trail requires AWS Organizations
- Each account has its own CloudTrail
- Centralized logging achieved via S3 bucket sharing

---

## ConfigurationValidationRules Exceptions

### When alwaysLoad Frameworks Cannot Be Bypassed

**Important**: ConfigurationValidationRules has `alwaysLoad = true`, meaning it runs **even when no compliance frameworks are specified**.

**No Exception Scenarios** (these configurations will ALWAYS fail):
1. ❌ Subdomain without domain - No exception (deployment will fail)
2. ❌ OIDC without HTTPS - No exception (deployment will fail)

| Rule | Finding | Why No Exception |
|------|---------|-----------------|
| CONFIG-SUBDOMAIN-DOMAIN | Subdomain requires parent domain | Fundamental DNS requirement |
| CONFIG-OIDC-HTTPS | OIDC requires HTTPS | Security - tokens exposed over HTTP |

**Rationale**: These are fundamental configuration errors that would cause deployment failures regardless of compliance requirements. They protect against misconfigurations even in DEV environments.

**Alternative for SSL Without Public Domain**:
If you need SSL without a public domain, use AWS Private CA with self-signed certificates (don't use subdomain configuration).

---

## Configuration Flags Reference

| Finding | Flag to Enable | Effect |
|---------|---------------|--------|
| KMS encryption for logs | `cloudWatchLogsKmsEncryptionEnabled: true` | Encrypts CloudWatch Logs with KMS |
| Security group egress restriction | `restrictSecurityGroupEgress: true` | Limits egress to VPC CIDR only |
| WAF protection | `wafEnabled: true` | Adds AWS WAF to ALB |
| Secrets replication | `secretsReplicationEnabled: true` | Cross-region secret replication |
| S3 Object Lock | `s3ObjectLockEnabled: true` | WORM compliance for buckets |

---

## Validator-Specific Notes

### CFN Validator

This validator parses CloudFormation templates directly and does not recognize:
- cdk-nag suppressions (metadata-based)
- CDK construct patterns (treats all resources equally)
- Deployment-time vs runtime distinction

**Recommendation**: Configure validator exception rules for resource patterns listed above.

### cdk-nag

CDK-nag suppressions are applied in `InteractiveDeployer.applyProductionNagSuppressions()` for:
- HIPAA.Security-* rules
- PCI.DSS.321-* rules
- AwsSolutions-* rules

---

## Summary

| Category | Count | Action |
|----------|-------|--------|
| CDK Framework (inline policies) | ~12 | Accept - cannot modify |
| CDK Framework (lambdas) | 2 | Accept - deployment-time only |
| Architecture (ALB SG, public subnets) | ~5 | Accept - expected behavior |
| AWS Limitations (ALB logs KMS) | 1 | Accept - service limitation |
| Optional Features (Object Lock, replication) | ~4 | Configure if required |

**Total Expected Exceptions**: ~24 findings

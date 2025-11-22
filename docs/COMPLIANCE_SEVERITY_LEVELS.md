# Compliance Check Severity Levels

This document clarifies which compliance checks are **advisory** (warnings) vs. **blocking** (hard failures) that prevent deployment.

## Overview

CloudForge CI implements compliance checks at two levels:

1. **Advisory (Warnings)**: Recommendations that don't block deployment but should be addressed
2. **Blocking (Hard Failures)**: Critical issues that prevent deployment from proceeding

## Severity Classification

### 🔴 Blocking (Hard Fail) - Deployment Prevented

These checks **must pass** for deployment to succeed:

| Check | Security Profile | Rationale |
|-------|-----------------|-----------|
| **AWS Config Enabled** | PRODUCTION | Required for audit evidence collection (SOC2, HIPAA, PCI-DSS) |
| **CloudTrail Enabled** | PRODUCTION | Mandatory API audit logging for compliance frameworks |
| **EBS Encryption** | PRODUCTION | Data at rest encryption required by most compliance frameworks |
| **S3 Bucket Encryption** | PRODUCTION | Prevents accidental exposure of sensitive data |
| **IAM Password Policy** | PRODUCTION | Enforces minimum password security standards |
| **Root Account Access Keys** | ALL | Critical security vulnerability if present |
| **VPC Flow Logs** | PRODUCTION | Network traffic logging required for security monitoring |

### 🟡 Advisory (Warnings) - Deployment Allowed

These checks generate warnings but don't block deployment:

| Check | Security Profile | Rationale | Migration Notes |
|-------|-----------------|-----------|----------------|
| **Customer-Managed KMS Keys** | PRODUCTION | Recommended but AWS-managed keys acceptable | Previously blocking, now advisory as of v2.5.0 |
| **S3 Versioning** | PRODUCTION | Best practice for data recovery | Can be enabled via auto-remediation if needed |
| **Multi-AZ Deployment** | PRODUCTION | High availability recommendation | May increase costs, left to operator discretion |
| **WAF Enabled** | PRODUCTION | DDoS protection recommended | Not all workloads require WAF |
| **GuardDuty Enabled** | STAGING, PRODUCTION | Threat detection recommended | Optional for cost control |

### ⚪ Informational - No Action Required

These checks are informational only:

| Check | Purpose |
|-------|---------|
| **ALB Access Logging** | Performance monitoring and debugging |
| **Detailed Billing** | Cost tracking and optimization |
| **Auto-Scaling Configuration** | Capacity planning information |

## Changes from Previous Versions

### v2.5.0 (Current Release)

**Breaking Change - Advisory to Blocking:**
- None in this release

**Breaking Change - Blocking to Advisory:**
- **Customer-Managed KMS Keys**: Now advisory instead of blocking
  - **Reason**: AWS-managed keys provide adequate encryption for many use cases
  - **Migration**: Existing deployments with AWS-managed keys will now succeed
  - **Recommendation**: Still use customer-managed keys for sensitive data (HIPAA, PCI-DSS)

**New Checks:**
- **CloudTrail Bucket Access Remediation**: Optional auto-fix for bucket policy issues
- **VPC Default Security Group**: Blocking in PRODUCTION (best practice enforcement)

### v2.4.0 (Previous Release)

**Changed from Advisory to Blocking:**
- **EBS Encryption**: Now blocks deployment if disabled in PRODUCTION
  - **Reason**: Required by most compliance frameworks
  - **Migration Path**: Enable EBS encryption via deployment context: `cfc.put("ebsEncryption", true)`

## How to Override Severity Levels

### Option 1: Deployment Context Override

```json
{
  "security": "PRODUCTION",
  "complianceOverrides": {
    "allowAwsManagedKmsKeys": true,
    "allowUnencryptedEbs": false,
    "allowMissingWaf": true
  }
}
```

### Option 2: Custom Security Profile

```java
public class CustomProductionProfile implements SecurityProfileConfiguration {
    @Override
    public boolean isKmsCustomerManagedKeysRequired() {
        return false;  // Advisory instead of blocking
    }
}
```

### Option 3: Disable Specific Checks

```json
{
  "awsConfigEnabled": true,
  "skipComplianceChecks": ["KMS_CMK_CHECK", "WAF_ENABLED"]
}
```

**⚠️ Warning**: Overriding blocking checks may violate compliance requirements. Document all overrides for audit review.

## Auto-Remediation Impact

Auto-remediation **does not change severity levels**. It only automatically fixes non-compliant resources:

- **Blocking checks with auto-remediation**: Still block initial deployment, but auto-fix subsequent drift
- **Advisory checks with auto-remediation**: Generate warnings, then auto-fix in background

Example: CloudTrail bucket access remediation
- Check: CloudTrail enabled (BLOCKING in PRODUCTION)
- If CloudTrail exists but can't write to S3: **Auto-remediation fixes bucket policy**
- If CloudTrail doesn't exist: **Deployment blocked** (can't remediate what doesn't exist)

## Security Profile Defaults

| Check | DEV | STAGING | PRODUCTION |
|-------|-----|---------|------------|
| CloudTrail | Advisory | Advisory | **Blocking** |
| AWS Config | Advisory | Advisory | **Blocking** |
| EBS Encryption | Advisory | Advisory | **Blocking** |
| S3 Encryption | Advisory | Advisory | **Blocking** |
| KMS CMK | Informational | Advisory | Advisory |
| WAF | Informational | Advisory | Advisory |
| GuardDuty | Informational | Advisory | Advisory |

## Checking Compliance Before Deployment

### Dry Run Mode

```bash
cdk synth --context dryRunCompliance=true
```

This generates a compliance report without deploying:

```
[BLOCKING] CloudTrail not enabled (PRODUCTION profile requires CloudTrail)
[ADVISORY] Customer-managed KMS keys recommended for S3 buckets
[INFORMATIONAL] WAF not configured (consider enabling for DDoS protection)
```

### CI/CD Integration

```yaml
# .github/workflows/compliance-check.yml
- name: Check Compliance
  run: |
    cdk synth --context dryRunCompliance=true
    if grep "BLOCKING" compliance-report.txt; then
      echo "❌ Blocking compliance issues found"
      exit 1
    fi
```

## Migration Guide

### Upgrading from v2.4.0 to v2.5.0

**If you previously had KMS CMK check failures:**

1. **Before v2.5.0**: Deployment blocked if not using customer-managed KMS keys
2. **After v2.5.0**: Deployment succeeds with warning

**Action Required**: None - deployments will now succeed

**Recommendation**: Review warning and consider enabling customer-managed keys for sensitive data

**Compliance Impact**:
- **SOC2**: AWS-managed keys acceptable
- **HIPAA**: Customer-managed keys still recommended (use `complianceOverrides` to enforce)
- **PCI-DSS**: Customer-managed keys required (enable in security profile)

## Support & Questions

For questions about severity levels:
1. Check your security profile configuration: `ProductionSecurityProfileConfiguration.java`
2. Review deployment context: `deployment-context.json`
3. File an issue: https://github.com/CloudForgeCI/cfc-core/issues

**Before filing an issue, include:**
- Security profile (DEV/STAGING/PRODUCTION)
- Failed check name
- Whether you need to override the check or fix the underlying issue
- Compliance frameworks you're targeting (SOC2, HIPAA, PCI-DSS, etc.)

# Compliance Validation Quick Start Guide

Configure compliance validation for a CloudForge deployment.

---

## Overview

This guide shows how to select compliance mappings, configure validation behavior, and inspect validation output. These checks evaluate configured technical controls; they do not certify the deployed environment.

---

## Method 1: Interactive Deployment (Recommended)

### Step 1: Run Interactive Deployer

```bash
cd cfc-testing
cdk deploy
```

The Interactive Deployer will automatically activate if `deployment-context.json` is not found.

### Step 2: Answer Compliance Prompts

When prompted for **Advanced Configuration**:

```
🔧 Advanced Configuration:
==========================
Enable AWS Config Compliance Monitoring [y/N]: y
Enable AWS GuardDuty [Y/n]: y
Enable AWS Audit Manager [Y/n]: y

Select compliance frameworks:
  1. All Standard Frameworks (PCI-DSS, HIPAA, SOC2, GDPR)
  2. SOC 2 only
  3. HIPAA only
  4. PCI-DSS only
  5. GDPR only
  6. Healthcare (HIPAA + SOC2 + GDPR)
  7. Payment Processing (PCI-DSS + SOC2)
  8. Custom
Framework(s) [1]: 1
```

### Step 3: Deploy

```bash
# Synthesis will validate your infrastructure
# If compliant: Template generated
# If non-compliant: Errors shown with remediation steps

# Deploy to AWS
cdk deploy --require-approval never
```

After deployment, compliance validation runs with the selected frameworks and mode.

---

## Method 2: Manual Configuration File

### Step 1: Create `deployment-context.json`

```json
{
  "stackName": "my-application-stack",
  "context": {
    "applicationId": "jenkins",
    "runtime": "FARGATE",
    "topology": "APPLICATION_SERVICE",
    "securityProfile": "PRODUCTION",
    "domain": "example.com",
    "subdomain": "jenkins",
    "enableSsl": true,

    "auditManagerEnabled": true,
    "complianceFrameworks": "PCI-DSS,HIPAA,SOC2,GDPR",
    "complianceMode": "enforce",

    "enableEncryption": true,
    "enableMonitoring": true,
    "awsConfigEnabled": true,
    "guardDutyEnabled": true,
    "wafEnabled": true,

    "kmsKeyRotationEnabled": true,
    "securityHubEnabled": true,
    "inspectorEnabled": true,
    "macieEnabled": true,

    "enableS3VersioningRemediation": false,
    "enableCloudTrailBucketAccessRemediation": false
  }
}
```

### Step 2: Run CDK Synth

```bash
cd cfc-testing
mvn compile
cdk synth --app "java -cp target/classes:target/dependency/* com.cloudforgeci.samples.app.CloudForgeCommunitySample"
```

### Step 3: Deploy

```bash
cdk deploy --require-approval never
```

---

## Compliance Framework Selection Guide

### Option 1: All Supported Frameworks

```json
"complianceFrameworks": "PCI-DSS,HIPAA,SOC2,GDPR"
```

**Coverage**: 70% overall (170+ validation rules)
**Use Case**: Validate controls mapped across all listed frameworks
**Cost**: ~$150-300/month (Security Hub + Inspector + Macie + GuardDuty)

---

### Option 2: Healthcare (HIPAA-focused)

```json
"complianceFrameworks": "HIPAA,SOC2,GDPR"
```

**Coverage**: 68% (125+ validation rules)
**Use Case**: Healthcare applications with PHI data
**Required AWS Services**:
- AWS Config (compliance monitoring)
- Amazon Macie (PHI discovery)
- Security Hub (centralized dashboard)
- GuardDuty (threat detection)

**Additional Settings**:
```json
"awsBaaSigned": true,
"macieEnabled": true,
"gdprDpiaCompleted": true,
"incidentResponsePlan": true,
"breachNotificationProcedures": true
```

---

### Option 3: Payment Processing (PCI-DSS focused)

```json
"complianceFrameworks": "PCI-DSS,SOC2"
```

**Coverage**: 73% (95+ validation rules)
**Use Case**: E-commerce and payment processing applications
**Required AWS Services**:
- AWS WAF (application firewall)
- GuardDuty (intrusion detection)
- Inspector (vulnerability scanning)
- Security Hub (compliance dashboard)

**Additional Settings**:
```json
"wafEnabled": true,
"guardDutyEnabled": true,
"inspectorEnabled": true,
"antiMalwareProtectionEnabled": true,
"fileIntegrityMonitoringEnabled": true
```

---

### Option 4: Trust & Transparency (SOC 2 only)

```json
"complianceFrameworks": "SOC2"
```

**Coverage**: 94% (30+ validation rules)
**Use Case**: SaaS applications, vendor trust requirements
**Minimal Cost**: Can use DEV profile, no additional AWS services required

---

## Compliance Mode Selection

### ENFORCE Mode

```json
"complianceMode": "enforce"
```

**Behavior**:
- ❌ **Blocks** CDK synthesis if validation fails
- 🛑 **Prevents** synthesis when configured validation rules fail
- Intended for production environments where failed validation should block synthesis

**When to use**:
- Production deployments
- Environments subject to formal control review
- Regulatory compliance required

---

### ADVISORY Mode (Development)

```json
"complianceMode": "advisory"
```

**Behavior**:
- ⚠️ **Logs** warnings for validation failures
- ✅ **Allows** deployment to proceed
- Intended for development and staging environments

**When to use**:
- Development and testing
- Proof-of-concept deployments
- Gradual compliance adoption

---

## Configuration Parameter Reference

### Application Parameters

| Parameter | Type | Values | Description |
|-----------|------|--------|-------------|
| `applicationId` | string | jenkins, gitlab, metabase, grafana, mattermost, harbor, nexus, gitea, drone, superset, vault, prometheus, redis, postgresql | **Required**. Application to deploy |
| `applicationName` | string | Any | Display name for application (auto-set from applicationId) |
| `provisionDatabase` | boolean | true, false | **Optional apps only** (Metabase, Grafana). Use RDS instead of embedded DB. Default: false |

### Infrastructure Parameters

| Parameter | Type | Values | Description |
|-----------|------|--------|-------------|
| `runtime` | string | FARGATE, EC2 | Container runtime. Default: FARGATE |
| `topology` | string | APPLICATION_SERVICE, CMS_SERVICE, S3_WEBSITE | Deployment topology. Default: APPLICATION_SERVICE. Use `CMS_SERVICE` for PHP/CMS platforms (WordPress, Magento, Drupal, etc.) — auto-wires S3 media, Redis, and CloudFront. |
| `applicationId` | string | `wordpress`, `magento`, `drupal`, … | **Required with `CMS_SERVICE`.** Identifies the CMS plugin to deploy. See [CMS Deployment Guide](../applications/CMS.md) for all 19 platform IDs. |
| `securityProfile` | string | DEV, STAGING, PRODUCTION | Security configuration level |

### Database Parameters (RDS)

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `dbInstanceClass` | string | Varies by app | RDS instance type (e.g., db.t3.small) |
| `dbAllocatedStorage` | number | 20-50GB | Storage size in GB |
| `dbBackupRetentionDays` | number | 7-30 days | Backup retention period |
| `dbName` | string | App-specific | Database name |
| `dbEngineVersion` | string | 13-15 | PostgreSQL version |

### Compliance & Remediation Parameters

| Parameter | Type | Values | Description |
|-----------|------|--------|-------------|
| `complianceFrameworks` | string | PCI-DSS, HIPAA, SOC2, GDPR, ISO27001 | Comma-separated list |
| `complianceMode` | string | enforce, advisory | enforcement or warnings only |
| `auditManagerEnabled` | boolean | true, false | Enable AWS Audit Manager |
| `awsConfigEnabled` | boolean | true, false | Enable AWS Config monitoring |
| `createConfigInfrastructure` | boolean | true, false | Create Config Recorder/Delivery Channel |
| `enableS3VersioningRemediation` | boolean | true, false | Auto-enable S3 versioning |
| `enableCloudTrailBucketAccessRemediation` | boolean | true, false | Auto-enable CloudTrail logging |
| `enableRdsDeletionProtectionRemediation` | boolean | true, false | Auto-enable RDS deletion protection |
| `enableRdsAutoMinorVersionUpgradeRemediation` | boolean | true, false | Auto-enable RDS security patches |

### Available Applications

| Application | Category | Database Requirement | OIDC Support |
|-------------|----------|---------------------|--------------|
| **jenkins** | CI/CD | NONE | Yes |
| **gitlab** | CI/CD | REQUIRED (PostgreSQL) | Yes |
| **drone** | CI/CD | NONE | Yes |
| **metabase** | Analytics | OPTIONAL (H2 or PostgreSQL) | Yes |
| **superset** | Analytics | REQUIRED (PostgreSQL) | Yes |
| **grafana** | Monitoring | OPTIONAL (SQLite or PostgreSQL) | Yes |
| **mattermost** | Collaboration | REQUIRED (PostgreSQL) | Yes |
| **harbor** | Container Registry | REQUIRED (PostgreSQL) | Yes |
| **nexus** | Artifact Registry | NONE | Yes |
| **gitea** | VCS | NONE | Yes |
| **vault** | Secrets | NONE | No |
| **prometheus** | Monitoring | NONE | No |
| **redis** | Database | NONE | No |
| **postgresql** | Database | NONE | No |

---

## Essential Compliance Settings

### Minimum Configuration (All Frameworks)

```json
{
  "applicationId": "jenkins",
  "auditManagerEnabled": true,
  "complianceFrameworks": "PCI-DSS,HIPAA,SOC2,GDPR",
  "enableEncryption": true,
  "enableMonitoring": true,
  "awsConfigEnabled": true
}
```

**Cost**: ~$50/month (Config + basic monitoring)

---

### Production Configuration

```json
{
  "applicationId": "gitlab",
  "runtime": "FARGATE",
  "topology": "APPLICATION_SERVICE",
  "securityProfile": "PRODUCTION",
  "auditManagerEnabled": true,
  "complianceFrameworks": "PCI-DSS,HIPAA,SOC2,GDPR",
  "complianceMode": "enforce",

  "enableEncryption": true,
  "enableMonitoring": true,
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,

  "kmsKeyRotationEnabled": true,
  "kmsKeyRotationDays": 90,
  "secretsManagerRotationEnabled": true,

  "securityHubEnabled": true,
  "securityHubPciDssEnabled": true,
  "securityHubCisEnabled": true,

  "inspectorEnabled": true,
  "inspectorContinuousScanning": true,

  "macieEnabled": true,
  "macieAutomatedDiscovery": true,

  "enableS3VersioningRemediation": true,
  "enableCloudTrailBucketAccessRemediation": true,
  "enableRdsDeletionProtectionRemediation": true,
  "enableRdsAutoMinorVersionUpgradeRemediation": true
}
```

**Cost**: ~$150-300/month (all security services)

---

### Expanded Configuration

```json
{
  "applicationId": "mattermost",
  "runtime": "EC2",
  "topology": "APPLICATION_SERVICE",
  "securityProfile": "PRODUCTION",
  "auditManagerEnabled": true,
  "complianceFrameworks": "PCI-DSS,HIPAA,SOC2,GDPR",
  "complianceMode": "enforce",

  "enableEncryption": true,
  "enableMonitoring": true,
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,

  "kmsKeyRotationEnabled": true,
  "kmsKeyRotationDays": 90,
  "secretsManagerRotationEnabled": true,
  "certificateAutoRenewalEnabled": true,

  "securityHubEnabled": true,
  "securityHubPciDssEnabled": true,
  "securityHubCisEnabled": true,
  "securityHubAutoRemediation": true,

  "inspectorEnabled": true,
  "inspectorEc2Scanning": true,
  "inspectorEcrScanning": true,
  "inspectorContinuousScanning": true,

  "macieEnabled": true,
  "macieAutomatedDiscovery": true,

  "antiMalwareProtectionEnabled": true,
  "containerImageScanningEnabled": true,
  "fileIntegrityMonitoringEnabled": true,
  "intrusionDetectionAlertsEnabled": true,

  "incidentResponsePlanDocumented": true,
  "disasterRecoveryPlanDocumented": true,
  "backupRestoreTestingEnabled": true,
  "forensicLoggingEnabled": true,

  "awsBaaSigned": true,
  "workforceAuthorizationProcedures": true,
  "breachNotificationProcedures": true,

  "gdprLegalBasisDocumented": true,
  "gdprDataSubjectRequestProcedures": true,
  "gdprDpiaCompleted": true,
  "gdprInternationalTransferSafeguards": true,

  "enableS3VersioningRemediation": true,
  "enableCloudTrailBucketAccessRemediation": true,
  "enableRdsDeletionProtectionRemediation": true,
  "enableRdsAutoMinorVersionUpgradeRemediation": true
}
```

**Cost**: ~$200-400/month
**Coverage**: 70% overall (170+ validation rules)

---

## Validation Output Examples

### Success (Compliant)

```
INFO: Installing compliance validation for: PCI-DSS,HIPAA,SOC2,GDPR
INFO:   - PCI-DSS v3.2.1 validator enabled
INFO:   - HIPAA Security Rule validator enabled
INFO:   - HIPAA Organizational validator enabled
INFO:   - SOC 2 Trust Services Criteria validator enabled
INFO:   - GDPR Technical Safeguards validator enabled
INFO:   - GDPR Data Protection validator enabled
INFO:   - Key Management validator enabled
INFO:   - Advanced Monitoring validator enabled
INFO:   - Incident Response & DR validator enabled
INFO:   - Threat Protection validator enabled
INFO:   - Database Security validator enabled

INFO: PCI-DSS validation passed (24 checks)
INFO: HIPAA validation passed (31 checks)
INFO: SOC 2 validation passed (18 checks)
INFO: GDPR validation passed (29 checks)
INFO: Key Management validation passed (12 checks)
INFO: Advanced Monitoring validation passed (14 checks)

✅ All compliance validation passed!
✅ CDK Stack synthesized successfully!
```

---

### Failure (Non-compliant in ENFORCE mode)

```
INFO: Installing compliance validation for: PCI-DSS,HIPAA,SOC2,GDPR

WARNING: PCI-DSS validation found 3 failures
WARNING:   - PCI-DSS-Req-3.4-EBS-Encryption: EBS encryption must be enabled
WARNING:   - PCI-DSS-Req-10.1-CloudTrail: CloudTrail must be enabled for audit logging
WARNING:   - PCI-DSS-Req-11.4-GuardDuty: GuardDuty required for intrusion detection

ERROR: *** Compliance validation failed in ENFORCE mode ***
ERROR: Fix 3 violations or set complianceMode=advisory
ERROR:
ERROR: Remediation steps:
ERROR:   1. Set enableEncryption=true
ERROR:   2. Set enableMonitoring=true (includes CloudTrail)
ERROR:   3. Set guardDutyEnabled=true

❌ CDK synthesis failed - infrastructure is non-compliant
```

---

## Common Scenarios

### Scenario 1: I want to enable compliance without breaking my existing deployment

**Solution**: Use ADVISORY mode first

```json
{
  "auditManagerEnabled": true,
  "complianceFrameworks": "SOC2",
  "complianceMode": "advisory"
}
```

Review warnings, fix issues incrementally, then switch to ENFORCE mode.

---

### Scenario 2: I need HIPAA compliance but don't have all organizational policies yet

**Solution**: Enable technical controls, skip organizational (advisory only)

```json
{
  "complianceFrameworks": "HIPAA",
  "complianceMode": "enforce",
  "enableEncryption": true,
  "awsConfigEnabled": true,
  "macieEnabled": true
}
```

Organizational rules (BAA, training, procedures) will show as advisory warnings only.

---

### Scenario 3: I need to evaluate SOC 2-mapped controls

**Approach**: Enable SOC 2 validation in ENFORCE mode

```json
{
  "securityProfile": "PRODUCTION",
  "auditManagerEnabled": true,
  "complianceFrameworks": "SOC2",
  "complianceMode": "enforce",
  "enableEncryption": true,
  "enableMonitoring": true,
  "awsConfigEnabled": true
}
```

The current mapping reports 94% rule coverage for SOC 2. This validation result is not an audit opinion or certification.

---

### Scenario 4: I want to test compliance validation without deploying

**Solution**: Use dry-run script

```bash
cd cfc-testing
./scripts/deployment-dry-run-tracker.sh
```

This runs `cdk synth` for multiple configurations and reports validation results without deploying.

---

## Cost Estimation

### Synthesis-Time Validation

- **CDK Synthesis-time validation**: $0
- **170+ validation rules**: $0
- **Advisory mode logging**: $0

---

### AWS Services (Variable Cost)

| Service | Purpose | Estimated Cost |
|---------|---------|---------------|
| **AWS Config** | Compliance monitoring | ~$2/month (10 rules) to $10/month (50 rules) |
| **GuardDuty** | Threat detection | ~$30-100/month (based on data volume) |
| **Security Hub** | Centralized dashboard | $0.0010 per finding |
| **Inspector** | Vulnerability scanning | ~$1/EC2/month, $0.09/container image |
| **Macie** | Sensitive data discovery | ~$1/GB scanned |
| **Audit Manager** | Evidence collection | ~$1.00 per 100k evidence items |
| **CloudTrail** | Audit logging | First trail free, then $2/100k events |

**Total Estimated Cost**:
- **Minimal** (SOC2 only, DEV): ~$10-20/month
- **Recommended** (All frameworks, PRODUCTION): ~$150-300/month
- **Maximum** (All services, high volume): ~$300-500/month

---

## Troubleshooting

### Q: Validation not running?

**Check**:
```json
"auditManagerEnabled": true,
"complianceFrameworks": "PCI-DSS,HIPAA,SOC2,GDPR"
```

Both must be set for validation to run.

---

### Q: Too many advisory warnings?

**Solution**: Mark organizational controls as completed:
```json
"awsBaaSigned": true,
"gdprLegalBasisDocumented": true,
"incidentResponsePlanDocumented": true
```

---

### Q: Synthesis blocked by validation?

**Solution 1** (Fix issues):
```json
"enableEncryption": true,
"guardDutyEnabled": true,
"kmsKeyRotationEnabled": true
```

**Solution 2** (Switch to advisory):
```json
"complianceMode": "advisory"
```

---

## Operational Follow-up

1. **Enable compliance**: Choose a method above and configure your deployment
2. **Review validation output**: Check for any warnings or errors
3. **Deploy to AWS**: Run `cdk deploy` to create infrastructure
4. **Monitor compliance**: Check Security Hub, Config, Audit Manager dashboards
5. **Iterate**: Add more controls, switch to ENFORCE mode when ready

---

**Document Version**: 1.0
**Last Updated**: 2025-11-12

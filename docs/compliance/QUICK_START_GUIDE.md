# Compliance Validation Quick Start Guide

Configure compliance validation for a CloudForge deployment.

---

## Overview

This guide shows how to select compliance frameworks, configure validation behavior, and inspect validation output. These checks evaluate configured technical controls; they do not certify the deployed environment.

Two validation mechanisms run during `cdk synth`:

- **cdk-nag packs** run for `production` stacks with at least one framework selected.
- **CloudForge framework validators** (`FrameworkRules` implementations such as `PciDssRules` and `HipaaRules`) run when `auditManagerEnabled` is `true`.

---

## Method 1: Interactive Deployment (Recommended)

### Step 1: Run the Interactive Deployer

```bash
mvn -DskipTests install
cd cfc-testing
cdk deploy
```

The Interactive Deployer starts its prompts when `deployment-context.json` is not found, or when run with `--interactive`.

### Step 2: Answer the Compliance Prompts

The deployer asks for compliance frameworks and then for advanced settings such as AWS Config, GuardDuty, and Audit Manager. The framework menu is:

```
📋 Select Compliance Frameworks:
================================
  1. All Standard Frameworks (PCI-DSS, HIPAA, SOC2, GDPR)
  2. SOC 2 Only (SaaS applications)
  3. HIPAA Only (Healthcare)
  4. PCI-DSS Only (Payment Processing)
  5. GDPR Only (Data Protection)
  6. Healthcare Focused (HIPAA + SOC2 + GDPR)
  7. Payment Processing (PCI-DSS + SOC2)
  8. Custom (comma-separated list)
```

### Step 3: Deploy

Synthesis runs the validators. In `enforce` mode, a failed check stops synthesis and lists the failing rules; in `advisory` mode, failures are logged as warnings and the deployment continues.

---

## Method 2: Manual Configuration File

### Step 1: Create `deployment-context.json`

`deployment-context.json` contains a flat set of `DeploymentConfig` properties:

```json
{
  "stackName": "my-application-stack",
  "applicationId": "jenkins",
  "runtime": "FARGATE",
  "topology": "APPLICATION_SERVICE",
  "securityProfile": "production",
  "domain": "example.com",
  "subdomain": "jenkins",
  "enableSsl": true,

  "auditManagerEnabled": true,
  "complianceFrameworks": "pci-dss,hipaa,soc2,gdpr",
  "complianceMode": "enforce",

  "enableEncryption": true,
  "enableMonitoring": true,
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,

  "securityHubEnabled": true,
  "inspectorEnabled": true,
  "macieEnabled": true,

  "enableS3VersioningRemediation": false,
  "enableCloudTrailBucketAccessRemediation": false
}
```

### Step 2: Synthesize and Deploy

```bash
cd cfc-testing
cdk synth
cdk deploy
```

`cdk.json` runs the Interactive Deployer, which reads `deployment-context.json` without prompting. To use another file, set `CFC_CONTEXT_FILE` or pass `--context <file>` when running the deployer directly.

---

## Compliance Framework Selection Guide

`complianceFrameworks` accepts `pci-dss`, `hipaa`, `soc2`, and `gdpr` (case-insensitive), separated by commas, spaces, or `+`. Any other value fails configuration parsing. ISO 27001 and FedRAMP validators exist in the source tree but cannot be selected yet.

### Option 1: All Supported Frameworks

```json
"complianceFrameworks": "pci-dss,hipaa,soc2,gdpr"
```

**Use Case**: Validate controls mapped across all four frameworks.

---

### Option 2: Healthcare (HIPAA-focused)

```json
"complianceFrameworks": "hipaa,soc2,gdpr"
```

**Use Case**: Healthcare applications with PHI data
**Related AWS Services**:
- AWS Config (compliance monitoring)
- Amazon Macie (sensitive data discovery)
- Security Hub (centralized findings)
- GuardDuty (threat detection)

---

### Option 3: Payment Processing (PCI-DSS focused)

```json
"complianceFrameworks": "pci-dss,soc2"
```

**Use Case**: E-commerce and payment processing applications
**Related AWS Services**:
- AWS WAF (application firewall; required by `PciDssRules` in `production`)
- GuardDuty (intrusion detection)
- Inspector (vulnerability scanning)
- Security Hub (compliance dashboard)

**Additional Settings**:
```json
"wafEnabled": true,
"guardDutyEnabled": true,
"inspectorEnabled": true,
"antiMalwareEnabled": true,
"fileIntegrityMonitoring": true
```

---

### Option 4: SOC 2 only

```json
"complianceFrameworks": "soc2"
```

**Use Case**: SaaS applications, vendor trust requirements. `Soc2Rules` runs for `staging` and `production`.

---

## Compliance Mode Selection

`complianceMode` defaults to `enforce` for `production` and `advisory` for `dev` and `staging`.

### ENFORCE Mode

```json
"complianceMode": "enforce"
```

**Behavior**:
- Framework validators return their failures as CDK validation errors, which stop synthesis
- When synthesis runs through `CloudForgeSynthesizer`, cdk-nag findings in the generated `NagReport` files also stop synthesis
- The Interactive Deployer also runs `cfn-guard` against the synthesized template (when the binary is installed) and stops before deploying if it fails

**When to use**:
- Production deployments
- Environments subject to formal control review

---

### ADVISORY Mode

```json
"complianceMode": "advisory"
```

**Behavior**:
- Framework validators log failures as warnings
- Synthesis and deployment continue

**When to use**:
- Development and testing
- Gradual adoption of a framework

### DISABLED Mode

```json
"complianceMode": "disabled"
```

`ComplianceMatrix` no longer forces framework-required controls on, so profile methods fall back to the deployment context and profile defaults. The PCI-DSS, HIPAA, SOC2, and GDPR validators do not check for `disabled` and handle it the same way as `enforce`; set `auditManagerEnabled: false` to skip them.

---

## Configuration Parameter Reference

This section lists the parameters most relevant to compliance. The complete set of properties is defined in `DeploymentConfig` (`cloudforge-core/src/main/java/com/cloudforge/core/config/DeploymentConfig.java`).

### Application Parameters

| Parameter | Type | Values | Description |
|-----------|------|--------|-------------|
| `applicationId` | string | See [Applications](../applications/README.md) | **Required**. Application to deploy (for example `jenkins`, `gitlab`, `grafana`, `mattermost-team`, `wordpress`) |
| `applicationName` | string | Any | Display name for the application |
| `provisionDatabase` | boolean | true, false | Provision RDS for applications that support an external database. Default: false |

### Infrastructure Parameters

| Parameter | Type | Values | Description |
|-----------|------|--------|-------------|
| `runtime` | string | FARGATE, EC2 | Container runtime |
| `topology` | string | APPLICATION_SERVICE, CMS_SERVICE, JENKINS_SERVICE | Deployment topology. Use `CMS_SERVICE` for PHP/CMS platforms (WordPress, Magento, Drupal, and others); see the [CMS Deployment Guide](../applications/CMS.md) |
| `securityProfile` | string | dev, staging, production | Security configuration level. Default: dev |
| `networkMode` | string | public, private-with-nat, isolated | VPC topology. `public-no-nat` is accepted as an alias for `public` |

### Database Parameters (RDS)

| Parameter | Type | Description |
|-----------|------|-------------|
| `databaseEngine` | string | RDS engine |
| `databaseVersion` | string | Engine version |
| `databaseInstanceClass` | string | RDS instance type (for example `db.t3.small`) |
| `databaseAllocatedStorageGB` | number | Storage size in GB |
| `databaseBackupRetentionDays` | number | Backup retention period. Default: 7 |
| `databaseName` | string | Database name |
| `databaseMultiAz` | boolean | Multi-AZ deployment |

### Compliance and Remediation Parameters

| Parameter | Type | Values | Description |
|-----------|------|--------|-------------|
| `complianceFrameworks` | string | pci-dss, hipaa, soc2, gdpr | Delimited list |
| `complianceMode` | string | enforce, advisory, disabled | See [Compliance Mode Selection](#compliance-mode-selection) |
| `auditManagerEnabled` | boolean | true, false | Install framework validators and create Audit Manager assessments |
| `awsConfigEnabled` | boolean | true, false | Deploy AWS Config rules and remediation |
| `createConfigInfrastructure` | boolean | true, false | Create the Config recorder and delivery channel (one stack per account and region) |
| `logRetentionDays` | string | 1 to 3653 (CloudWatch values) | CloudWatch Logs retention override |
| `enableS3VersioningRemediation` | boolean | true, false | Enable versioning on non-compliant S3 buckets |
| `enableCloudTrailBucketAccessRemediation` | boolean | true, false | Restore CloudTrail bucket access and logging |
| `enableRdsDeletionProtectionRemediation` | boolean | true, false | Enable RDS deletion protection |
| `enableRdsAutoMinorVersionUpgradeRemediation` | boolean | true, false | Enable RDS automatic minor version upgrades |

### Validator Settings

Some validators read additional keys directly from the raw `cfc` context map instead of from `DeploymentConfig`:

| Key | Read by |
|-----|---------|
| `kmsKeyRotationEnabled` | `KeyManagementRules` |
| `securityHubPciDssEnabled`, `securityHubCisEnabled`, `securityHubAutoRemediation` | `AdvancedMonitoringRules` |
| `inspectorEc2Scanning`, `inspectorEcrScanning`, `inspectorContinuousScanning` | `AdvancedMonitoringRules` |
| `incidentResponsePlanDocumented`, `disasterRecoveryPlanDocumented` | `IncidentResponseRules` |
| `awsBaaSigned`, `workforceAuthorizationProcedures`, `breachNotificationProcedures`, `incidentResponsePlan` | `HipaaOrganizationalRules` |
| `gdprLegalBasisDocumented`, `gdprDataSubjectRequestProcedures`, `gdprDpiaCompleted`, `gdprInternationalTransferSafeguards` | `GdprOrganizationalRules` |

Because `DeploymentConfig` ignores unknown properties, these keys take effect only when supplied in the `cfc` object of the CDK context (for example in `cdk.json`); they are dropped when the configuration is loaded from `deployment-context.json`. `HipaaOrganizationalRules` and `GdprOrganizationalRules` are also not installed today, because their framework IDs cannot be selected in `complianceFrameworks`.

---

## Example Configurations

### Minimum Configuration

```json
{
  "applicationId": "jenkins",
  "securityProfile": "production",
  "auditManagerEnabled": true,
  "complianceFrameworks": "pci-dss,hipaa,soc2,gdpr",
  "enableEncryption": true,
  "enableMonitoring": true,
  "awsConfigEnabled": true
}
```

---

### Production Configuration

```json
{
  "applicationId": "gitlab",
  "runtime": "FARGATE",
  "topology": "APPLICATION_SERVICE",
  "securityProfile": "production",
  "auditManagerEnabled": true,
  "complianceFrameworks": "pci-dss,hipaa,soc2,gdpr",
  "complianceMode": "enforce",

  "enableEncryption": true,
  "enableMonitoring": true,
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,

  "securityHubEnabled": true,
  "inspectorEnabled": true,
  "macieEnabled": true,
  "macieAutomatedDiscovery": true,

  "antiMalwareEnabled": true,
  "containerImageScanning": true,
  "fileIntegrityMonitoring": true,

  "enableS3VersioningRemediation": true,
  "enableCloudTrailBucketAccessRemediation": true,
  "enableRdsDeletionProtectionRemediation": true,
  "enableRdsAutoMinorVersionUpgradeRemediation": true
}
```

---

## Validation Output Examples

### Validators Installed

```
INFO: Installing CloudForge FrameworkRules validation for: pci-dss,hipaa,soc2,gdpr
INFO: Discovered 18 compliance frameworks
INFO:   ✓ Key Management & Encryption (priority=-10)
INFO:   ✓ Database Security (priority=-5)
INFO:   ✓ Advanced Security Monitoring (priority=-5)
...
INFO:   ✓ HIPAA Security Rule (priority=10)
INFO:   ✓ PCI DSS v4.0.1 (priority=20)
INFO:   ✓ GDPR (priority=30)
INFO:   ✓ SOC 2 (priority=40)
INFO: Successfully installed 15 CloudForge FrameworkRules validators
```

### Passing Validation

```
INFO: PCI-DSS validation passed (<n> checks)
```

### Failing Validation (ENFORCE mode)

```
SEVERE: PCI-DSS validation failed with 2 violations (ENFORCE mode - blocking deployment)
SEVERE:   - PCI-DSS-Req-6.6-WAF: Web Application Firewall (WAF) REQUIRED for PCI-DSS compliance in PRODUCTION - ...
SEVERE:   - PCI-DSS-Req-11.4-GuardDuty: ...
```

The failures are returned as CDK validation errors and `cdk synth` exits with an error.

---

## Common Scenarios

### Scenario 1: Enable compliance without breaking an existing deployment

Use ADVISORY mode first:

```json
{
  "auditManagerEnabled": true,
  "complianceFrameworks": "soc2",
  "complianceMode": "advisory"
}
```

Review the warnings, fix issues incrementally, then switch to ENFORCE mode.

---

### Scenario 2: Evaluate HIPAA technical controls

```json
{
  "securityProfile": "production",
  "auditManagerEnabled": true,
  "complianceFrameworks": "hipaa",
  "complianceMode": "enforce",
  "enableEncryption": true,
  "awsConfigEnabled": true,
  "macieEnabled": true
}
```

`HipaaRules` checks technical safeguards only. Administrative safeguards such as a Business Associate Agreement, training, and procedures must be tracked outside CloudForge.

---

### Scenario 3: Evaluate SOC 2-mapped controls

```json
{
  "securityProfile": "production",
  "auditManagerEnabled": true,
  "complianceFrameworks": "soc2",
  "complianceMode": "enforce",
  "enableEncryption": true,
  "enableMonitoring": true,
  "awsConfigEnabled": true
}
```

A passing validation result is not an audit opinion or certification.

---

### Scenario 4: Test compliance validation without deploying

```bash
cd cfc-testing
./scripts/deployment-dry-run-tracker.sh
```

This runs `cdk synth` for multiple configurations and reports validation results without deploying.

---

## Cost Considerations

Synthesis-time validation has no AWS cost. The optional AWS services have usage-based charges:

| Service | Purpose |
|---------|---------|
| **AWS Config** | Configuration items and rule evaluations |
| **GuardDuty** | Threat detection, priced by analyzed data volume |
| **Security Hub** | Security checks and finding ingestion |
| **Inspector** | EC2 and container image scanning |
| **Macie** | Sensitive data discovery, priced by data scanned |
| **Audit Manager** | Evidence collection, priced by assessed resources |
| **CloudTrail** | Data events and additional trails |

Use the [AWS Pricing Calculator](https://calculator.aws.amazon.com/) for a workload-specific estimate.

---

## Troubleshooting

### Q: Validation not running?

Check that both are set:

```json
"auditManagerEnabled": true,
"complianceFrameworks": "pci-dss,hipaa,soc2,gdpr"
```

Also check `securityProfile`: `PciDssRules` runs only for `production`; `HipaaRules`, `Soc2Rules`, and `GdprRules` run for `staging` and `production`. cdk-nag packs run only for `production`.

---

### Q: Synthesis blocked by validation?

**Solution 1** (Fix issues): enable the controls named in the failing rule IDs, for example:
```json
"enableEncryption": true,
"guardDutyEnabled": true,
"wafEnabled": true
```

**Solution 2** (Switch to advisory):
```json
"complianceMode": "advisory"
```

---

## Operational Follow-up

1. **Enable compliance**: Choose a method above and configure your deployment
2. **Review validation output**: Check for warnings or errors
3. **Deploy to AWS**: Run `cdk deploy` to create infrastructure
4. **Monitor**: Review Security Hub, AWS Config, and Audit Manager
5. **Iterate**: Add controls, and switch to ENFORCE mode when ready

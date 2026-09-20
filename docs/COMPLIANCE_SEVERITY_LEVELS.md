# Compliance Check Severity Levels

This document explains which compliance checks block synthesis and which only produce warnings.

## Overview

Whether a failed check blocks a deployment depends on where the check runs and on `complianceMode`, not on a per-check severity setting:

| Check source | Blocking | Non-blocking |
|--------------|----------|--------------|
| Security profile structural rules (`SecurityRules`) | Always: missing VPC or required security groups fail synthesis | - |
| CloudForge framework validators (`FrameworkRules`, when `auditManagerEnabled` is `true`) | `enforce` mode: every failed `ComplianceRule` becomes a CDK validation error | `advisory` mode: failures are logged as warnings |
| cdk-nag packs (`production` with frameworks selected) | `enforce` mode, when synthesizing through `CloudForgeSynthesizer`: error-level findings fail synthesis | Otherwise findings are written to `NagReport` files |
| cfn-guard (Interactive Deployer) | `enforce` mode: a failed rule file stops the deployment | Skipped in other modes, or when `cfn-guard` is not installed |
| AWS Config rules | Never block deployment | Report `NON_COMPLIANT` after deployment and may trigger remediation |

`complianceMode` defaults to `enforce` for `production` and `advisory` for `dev` and `staging`.

## Control Requirement Levels

`ComplianceMatrix` assigns each security control a requirement level per framework:

| Level | Effect |
|-------|--------|
| **REQUIRED** | When any selected framework marks the control REQUIRED and `complianceMode` is not `disabled`, the security profile enables the control regardless of the deployment context value |
| **ADVISORY** | The control follows the deployment context value or profile default; `ComplianceMatrix.shouldWarnForControl` reports it when it is disabled |
| **NOT_APPLICABLE** | No effect |

See [Compliance Control Mapping](compliance/COMPLIANCE_CONTROL_MAPPING.md) for the controls and their levels.

## Framework Validator Rules

Each framework validator returns `ComplianceRule` results that either pass or fail. Some validators first call `ComplianceMatrix.validateControl`, which returns `PASS`, `WARN`, or `FAIL`; a `WARN` (the control is ADVISORY for the selected frameworks) is logged as a warning and recorded as a pass. Examples of rules that fail for `production` stacks:

| Rule ID | Validator | Condition |
|---------|-----------|-----------|
| `PCI-DSS-Req-6.6-WAF` | `PciDssRules` | `wafEnabled` is not `true` |
| `PCI-DSS-Req-1.3-Network` | `PciDssRules` | Public network mode |
| `PCI-DSS-Req-10.7-Retention` | `PciDssRules` | Log retention below 365 days |
| `HIPAA-164.312(b)-FlowLogs` | `HipaaRules` | VPC Flow Logs disabled |
| `SOC2-CC6.2-Auth` | `Soc2Rules` | `authMode` is `none` |
| `KMS-ROTATION` | `KeyManagementRules` | Customer-managed key rotation not confirmed (`kmsKeyRotationEnabled`) |

Organizational validators (`HipaaOrganizationalRules`, `GdprOrganizationalRules`) check attestation flags such as `awsBaaSigned`, but they are not installed today because their framework IDs cannot be selected.

## Changing Enforcement

| Goal | Setting |
|------|---------|
| Report framework validator failures without blocking | `"complianceMode": "advisory"` |
| Skip CloudForge framework validators entirely | `"auditManagerEnabled": false` |
| Stop `ComplianceMatrix` from forcing REQUIRED controls on | `"complianceMode": "disabled"` (the PCI-DSS, HIPAA, SOC2, and GDPR validators still block in this mode) |
| Suppress specific cdk-nag findings | `NagSuppressions`, as in `InteractiveDeployer.applyProductionNagSuppressions()` in `cfc-testing` |

There is no per-check override property. Overriding a blocking check may violate a framework requirement; document every override for audit review.

## Auto-Remediation

Auto-remediation does not change whether a check blocks. It fixes supported non-compliant resources after deployment:

- A blocking validator rule still stops synthesis; remediation only corrects drift after deployment.
- Example: the CloudTrail bucket access remediation (`enableCloudTrailBucketAccessRemediation`) restores the bucket policy when CloudTrail cannot write to its bucket. It cannot create a trail that does not exist.

## Checking Compliance Before Deployment

Run synthesis without deploying:

```bash
cd cfc-testing
cdk synth
```

Framework validator failures appear as `SEVERE` log lines and CDK validation errors in `enforce` mode, or `WARNING` log lines in `advisory` mode. cdk-nag findings are written to `cdk.out/*-NagReport.json` and `.csv`.

To run synthesis for several configurations, use `cfc-testing/scripts/deployment-dry-run-tracker.sh`.

## Support

For questions about severity levels:
1. Check the security profile configuration, for example `ProductionSecurityProfileConfiguration.java`
2. Review the deployment context (`deployment-context.json` or CDK context)
3. File an issue: https://github.com/CloudForgeCI/cfc-core/issues

Include:
- Security profile (`dev`, `staging`, or `production`)
- `complianceMode` and `complianceFrameworks`
- The failed rule ID

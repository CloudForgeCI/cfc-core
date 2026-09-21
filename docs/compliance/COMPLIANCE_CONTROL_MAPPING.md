# Compliance Control to Security Profile Mapping

Security profile configurations (`DevSecurityProfileConfiguration`, `StagingSecurityProfileConfiguration`, and `ProductionSecurityProfileConfiguration` in `cloudforge-api/src/main/java/com/cloudforgeci/api/core/security/`) consult [`ComplianceMatrix`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ComplianceMatrix.java) to decide whether a selected framework requires a control. `ComplianceMatrix.SecurityControl` is the source of truth for requirement levels; the table below summarizes it.

The matrix also contains a `NIST` column. It is used for reporting only; `complianceFrameworks` does not accept a NIST value.

## Mapping Table

| Profile Method | `ComplianceMatrix.SecurityControl` | Requirement Level |
|----------------|------------------------------------|-------------------|
| **Encryption** | | |
| `isEbsEncryptionEnabled()` | `ENCRYPTION_AT_REST` | Required: PCI-DSS, HIPAA, SOC2, GDPR |
| `isEfsEncryptionAtRestEnabled()` | `ENCRYPTION_AT_REST` | Required: PCI-DSS, HIPAA, SOC2, GDPR |
| `isS3EncryptionEnabled()` | `ENCRYPTION_AT_REST` | Required: PCI-DSS, HIPAA, SOC2, GDPR |
| `isEfsEncryptionInTransitEnabled()` | `ENCRYPTION_IN_TRANSIT` | Required: PCI-DSS, HIPAA, SOC2, GDPR |
| **Logging and Audit** | | |
| `isCloudTrailEnabled()` | `AUDIT_LOGGING` | Required: PCI-DSS, HIPAA, SOC2, GDPR |
| `isAlbAccessLoggingEnabled()` | `AUDIT_LOGGING` | Required: PCI-DSS, HIPAA, SOC2, GDPR |
| `isFlowLogsEnabled()` | `NETWORK_FLOW_LOGS` | Required: PCI-DSS, HIPAA, SOC2, GDPR |
| `isAuditManagerEnabled()` | `AUDIT_MANAGER` | Required: SOC2; Advisory: PCI-DSS, HIPAA, GDPR |
| **Monitoring and Threat Detection** | | |
| `isGuardDutyEnabled()` | `THREAT_DETECTION` | Required: PCI-DSS, HIPAA; Advisory: SOC2, GDPR |
| `isSecurityMonitoringEnabled()` | `SECURITY_MONITORING` | Required: PCI-DSS, HIPAA, GDPR; Advisory: SOC2 |
| `isSecurityHubEnabled()` | `SECURITY_HUB` | Advisory: all frameworks |
| `isInspectorEnabled()` | `VULNERABILITY_SCANNING` | Required: PCI-DSS; Advisory: HIPAA, SOC2, GDPR |
| `isMacieEnabled()` | `SENSITIVE_DATA_DISCOVERY` | Required: HIPAA, GDPR; Advisory: PCI-DSS |
| **Configuration** | | |
| `isAwsConfigEnabled()` | `VULNERABILITY_MANAGEMENT` | Required: all frameworks |
| **Backup and Recovery** | | |
| `isAutomatedBackupEnabled()` | `BACKUP_RECOVERY` | Required: all frameworks |
| `isCrossRegionBackupEnabled()` | `BACKUP_RECOVERY` | Required: all frameworks |
| **Network Security** | | |
| `isWafEnabled()` | `WAF_PROTECTION` | Required: PCI-DSS, SOC2, GDPR; Advisory: HIPAA |
| `isHttpsStrictEnabled()` | `HTTPS_STRICT` | Required: PCI-DSS; Advisory: HIPAA, SOC2, GDPR |
| `isRestrictSecurityGroupEgressEnabled()` | `NETWORK_SEGMENTATION` | Required: all frameworks |

`isMultiAzEnforced()` and `isAutoScalingEnabled()` are profile defaults and do not consult the matrix.

## Resolution Pattern

Each compliance-aware profile method follows the same order:

```java
@Override
public boolean isCloudTrailEnabled() {
    // 1. A selected framework marks the control as REQUIRED
    if (deploymentContext != null) {
        ComplianceMode mode = getEffectiveComplianceMode();
        String frameworks = deploymentContext.complianceFrameworks();

        if (ComplianceMatrix.isControlRequired(
                frameworks, mode, ComplianceMatrix.SecurityControl.AUDIT_LOGGING)) {
            return true;
        }
    }

    // 2. Explicit deployment context value
    if (deploymentContext != null && deploymentContext.cloudTrailEnabled() != null) {
        return Boolean.TRUE.equals(deploymentContext.cloudTrailEnabled());
    }

    // 3. Security profile default
    return true;
}
```

## Behavior Matrix

`ComplianceMatrix.isControlRequired` returns `true` when the mode is not `DISABLED` and any selected framework marks the control as REQUIRED. `ENFORCE` and `ADVISORY` differ in whether validation failures block synthesis, not in whether required controls are provisioned.

| complianceMode | complianceFrameworks | Control Level | Result |
|----------------|---------------------|---------------|--------|
| ENFORCE | pci-dss | REQUIRED | Control enabled |
| ENFORCE | pci-dss | ADVISORY | Deployment context value, then profile default |
| ADVISORY | pci-dss | REQUIRED | Control enabled |
| ADVISORY | pci-dss | ADVISORY | Deployment context value, then profile default |
| any | (empty) | any | Deployment context value, then profile default |
| DISABLED | any | any | Deployment context value, then profile default |

`ComplianceMatrix.shouldWarnForControl` reports whether a disabled control is REQUIRED or ADVISORY for any selected framework, so that callers can log a warning.

Framework lists may be separated by commas, spaces, or `+`, and are normalized to upper case with `_` replaced by `-` (`pci_dss` becomes `PCI-DSS`).

## Tests

The matrix behavior is covered by `ComplianceMatrixTest`, `ComplianceMatrixMethodsTest`, `ComplianceMatrixMethodsExtendedTest`, and `ComplianceMatrixSecurityControlTest` in `cloudforge-api/src/test/java/com/cloudforgeci/api/core/rules/`.

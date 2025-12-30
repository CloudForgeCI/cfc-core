# Compliance Control to SecurityProfileConfiguration Method Mapping

## Mapping Table

| SecurityProfileConfiguration Method | ComplianceMatrix.SecurityControl | Notes |
|-----------------------------------|----------------------------------|-------|
| **Encryption** |
| `isEbsEncryptionEnabled()` | `ENCRYPTION_AT_REST` | Required: PCI-DSS, HIPAA, SOC2, GDPR, NIST |
| `isEfsEncryptionAtRestEnabled()` | `ENCRYPTION_AT_REST` | Required: PCI-DSS, HIPAA, SOC2, GDPR, NIST |
| `isS3EncryptionEnabled()` | `ENCRYPTION_AT_REST` | Required: PCI-DSS, HIPAA, HIPAA, SOC2, GDPR, NIST |
| `isEfsEncryptionInTransitEnabled()` | `ENCRYPTION_IN_TRANSIT` | Required: PCI-DSS, HIPAA, SOC2, GDPR, NIST |
| **Logging & Audit** |
| `isCloudTrailEnabled()` | `AUDIT_LOGGING` | Required: PCI-DSS, HIPAA, SOC2, GDPR, NIST |
| `isFlowLogsEnabled()` | `AUDIT_LOGGING` | Required: PCI-DSS, HIPAA, SOC2, GDPR, NIST |
| `isAlbAccessLoggingEnabled()` | `AUDIT_LOGGING` | Required: PCI-DSS, HIPAA, SOC2, GDPR, NIST |
| **Monitoring & Threat Detection** |
| `isGuardDutyEnabled()` | `THREAT_DETECTION` | Required: PCI-DSS, HIPAA, NIST; Advisory: SOC2, GDPR |
| `isSecurityMonitoringEnabled()` | `SECURITY_MONITORING` | Required: PCI-DSS, HIPAA, GDPR, NIST; Advisory: SOC2 |
| `isSecurityHubEnabled()` | `SECURITY_HUB` | Advisory: All frameworks |
| `isInspectorEnabled()` | `VULNERABILITY_SCANNING` | Required: PCI-DSS, NIST; Advisory: HIPAA, SOC2, GDPR |
| `isMacieEnabled()` | `SENSITIVE_DATA_DISCOVERY` | Required: HIPAA, GDPR; Advisory: PCI-DSS, NIST |
| **Configuration & Compliance** |
| `isAwsConfigEnabled()` | `VULNERABILITY_MANAGEMENT` | Required: All frameworks |
| `isAuditManagerEnabled()` | **NEW: `AUDIT_MANAGER`** | Need to add this control |
| **Backup & Recovery** |
| `isCrossRegionBackupEnabled()` | `BACKUP_RECOVERY` | Required: All frameworks |
| `isAutomatedBackupEnabled()` | `BACKUP_RECOVERY` | Required: All frameworks |
| **High Availability** |
| `isMultiAzEnforced()` | `HIGH_AVAILABILITY` | Required: All frameworks |
| `isAutoScalingEnabled()` | `HIGH_AVAILABILITY` | Required: All frameworks |
| **Network Security** |
| `isWafEnabled()` | `WAF_PROTECTION` | Required: PCI-DSS, SOC2, GDPR, NIST; Advisory: HIPAA |

## Missing SecurityControls

Need to add to ComplianceMatrix:

### 1. AUDIT_MANAGER
```java
AUDIT_MANAGER(
    "Continuous audit evidence collection (AWS Audit Manager)",
    Map.of(
        "PCI-DSS", FrameworkRequirement.advisory("Req 10 - Continuous audit evidence"),
        "HIPAA", FrameworkRequirement.advisory("§164.308(a)(1)(ii)(D) - Audit evidence"),
        "SOC2", FrameworkRequirement.required("CC7.2 - Continuous monitoring and audit evidence"),
        "GDPR", FrameworkRequirement.advisory("Art. 30 - Documentation of compliance"),
        "NIST", FrameworkRequirement.required("AU-6 - Audit Review, Analysis, and Reporting")
    )
),
```

### 2. FLOW_LOGS (separate from general AUDIT_LOGGING)
```java
NETWORK_FLOW_LOGS(
    "VPC Flow Logs for network traffic monitoring",
    Map.of(
        "PCI-DSS", FrameworkRequirement.required("Req 10.2.2 - Log all network access"),
        "HIPAA", FrameworkRequirement.required("§164.312(b) - Audit network access"),
        "SOC2", FrameworkRequirement.required("CC7.2 - Network monitoring"),
        "GDPR", FrameworkRequirement.required("Art. 32(1)(d) - Network monitoring"),
        "NIST", FrameworkRequirement.required("AU-2 - Audit Events")
    )
),
```

## Implementation Pattern

### Standard Pattern for Compliance-Enforced Methods

```java
@Override
public boolean isFeatureEnabled() {
    // 1. Check if compliance matrix requires this control
    if (deploymentContext != null) {
        ComplianceMode mode = ComplianceMode.fromString(
            deploymentContext.complianceMode(),
            ComplianceMode.ADVISORY
        );
        String frameworks = deploymentContext.complianceFrameworks();

        if (ComplianceMatrix.isControlRequired(
            frameworks,
            mode,
            ComplianceMatrix.SecurityControl.CONTROL_NAME
        )) {
            LOG.info("PROFILE: Feature enforced by compliance frameworks: " + frameworks);
            return true;
        }
    }

    // 2. Check deployment context override
    if (deploymentContext != null && deploymentContext.featureEnabled() != null) {
        return Boolean.TRUE.equals(deploymentContext.featureEnabled());
    }

    // 3. Default based on security profile
    return profileDefault;
}
```

### Pattern for boolean (primitive) accessors

```java
@Override
public boolean isFeatureEnabled() {
    // 1. Check if compliance matrix requires this control
    if (deploymentContext != null) {
        ComplianceMode mode = ComplianceMode.fromString(
            deploymentContext.complianceMode(),
            ComplianceMode.ADVISORY
        );
        String frameworks = deploymentContext.complianceFrameworks();

        if (ComplianceMatrix.isControlRequired(
            frameworks,
            mode,
            ComplianceMatrix.SecurityControl.CONTROL_NAME
        )) {
            LOG.info("PROFILE: Feature enforced by compliance frameworks: " + frameworks);
            return true;
        }
    }

    // 2. Check deployment context override (no null check needed for boolean)
    if (deploymentContext != null) {
        return deploymentContext.featureEnabled();
    }

    // 3. Default based on security profile
    return profileDefault;
}
```

## Behavior Matrix

| complianceMode | complianceFrameworks | Control Level | Result |
|----------------|---------------------|---------------|---------|
| ENFORCE | PCI-DSS | REQUIRED | **ENFORCED** (return true) |
| ENFORCE | PCI-DSS | ADVISORY | Respect deployment context |
| ENFORCE | (empty) | ANY | Respect deployment context |
| ADVISORY | PCI-DSS | REQUIRED | Respect deployment context (warn only) |
| ADVISORY | PCI-DSS | ADVISORY | Respect deployment context |
| DISABLED | ANY | ANY | Respect deployment context |

## Helper Methods Needed in ComplianceMatrix

```java
/**
 * Check if a security control should be enforced based on compliance requirements.
 *
 * @param frameworksStr Comma-separated list of frameworks (e.g., "PCI-DSS,HIPAA")
 * @param mode Compliance mode (ENFORCE, ADVISORY, DISABLED)
 * @param control Security control to check
 * @return true if the control should be enforced (enabled)
 */
public static boolean isControlRequired(
    String frameworksStr,
    ComplianceMode mode,
    SecurityControl control
) {
    // DISABLED mode: never enforce
    if (mode == ComplianceMode.DISABLED) {
        return false;
    }

    // ADVISORY mode: never enforce (just warn)
    if (mode == ComplianceMode.ADVISORY) {
        return false;
    }

    // ENFORCE mode: check if any selected framework REQUIRES this control
    if (mode == ComplianceMode.ENFORCE) {
        if (frameworksStr == null || frameworksStr.isEmpty()) {
            return false; // No frameworks selected
        }

        for (String framework : frameworksStr.split(",")) {
            String normalized = framework.trim();
            if (control.isRequired(normalized)) {
                return true; // At least one framework requires it
            }
        }
    }

    return false;
}

/**
 * Check if a control should produce warnings based on compliance requirements.
 *
 * @param frameworksStr Comma-separated list of frameworks
 * @param mode Compliance mode
 * @param control Security control to check
 * @param isEnabled Whether the control is currently enabled
 * @return true if warnings should be logged
 */
public static boolean shouldWarnForControl(
    String frameworksStr,
    ComplianceMode mode,
    SecurityControl control,
    boolean isEnabled
) {
    if (mode == ComplianceMode.DISABLED || isEnabled) {
        return false;
    }

    if (frameworksStr == null || frameworksStr.isEmpty()) {
        return false;
    }

    // Check if any framework has requirements (REQUIRED or ADVISORY)
    for (String framework : frameworksStr.split(",")) {
        String normalized = framework.trim();
        RequirementLevel level = control.getRequirementLevel(normalized);
        if (level != RequirementLevel.NOT_APPLICABLE) {
            return true;
        }
    }

    return false;
}
```

## Testing Strategy

1. **Test ENFORCE mode with REQUIRED control:**
   - complianceMode="enforce", complianceFrameworks="PCI-DSS"
   - isCloudTrailEnabled() should return `true` even if deploymentContext.cloudTrailEnabled() is false
   - Validates enforcement overrides user settings

2. **Test ENFORCE mode with ADVISORY control:**
   - complianceMode="enforce", complianceFrameworks="SOC2"
   - isSecurityHubEnabled() should respect deploymentContext.securityHubEnabled()
   - Validates ADVISORY controls don't enforce

3. **Test ADVISORY mode:**
   - complianceMode="advisory", complianceFrameworks="PCI-DSS"
   - All methods should respect deployment context
   - Validates warnings but no enforcement

4. **Test DISABLED mode:**
   - complianceMode="disabled"
   - All methods should respect deployment context
   - Validates no enforcement or warnings

5. **Test empty frameworks:**
   - complianceMode="enforce", complianceFrameworks=""
   - All methods should respect deployment context
   - Validates enforcement only when frameworks are specified

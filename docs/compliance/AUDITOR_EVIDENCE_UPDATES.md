# Validation Evidence Reference

**Audience**: External auditors (QSA), internal audit teams, compliance officers

---

## Summary

This document describes selected CloudForge CI validation rules, where they are implemented, how they are tested, and how to reproduce the evidence. It covers:

| Control | Rule ID | Implementation |
|---------|---------|----------------|
| PCI DSS Req 6.6 - WAF in production | `PCI-DSS-Req-6.6-WAF` | `PciDssRules.validateWebApplicationSecurity` |
| PCI DSS Req 10 - VPC Flow Logs | `PCI-DSS-Req-10.11-FlowLogs`, `PCI-DSS-Req-10.3-FlowLogs` | `PciDssRules.validateAuditLogging` |
| HIPAA §164.312(b) - VPC Flow Logs | `HIPAA-164.312(b)-FlowLogs` | `HipaaRules` |
| Configuration validation | `CONFIG-SUBDOMAIN-DOMAIN`, `CONFIG-OIDC-HTTPS` | `ConfigurationValidationRules` (not currently registered; see below) |

These are checks on infrastructure configuration. Passing them is not a certification or an assessment result.

### Preconditions

The CloudForge framework validators run during `cdk synth` only when:

- `auditManagerEnabled` is `true`, and
- the framework is selected in `complianceFrameworks`, and
- the security profile matches the validator (`PciDssRules`: `production`; `HipaaRules`: `staging` and `production`).

In `enforce` mode (the default for `production`), a failed rule is returned as a CDK validation error and synthesis stops. In `advisory` mode, failures are logged as warnings. An operator who controls the deployment configuration can therefore turn the validators off; evidence of their use should include the deployment configuration for the assessed stack.

---

## 1. PCI DSS Req 6.6 - Web Application Firewall

### Behavior

- For `production` stacks with `pci-dss` selected, `PciDssRules` fails the `PCI-DSS-Req-6.6-WAF` rule when `wafEnabled` is not `true`.
- The failure message is `Web Application Firewall (WAF) REQUIRED for PCI-DSS compliance in PRODUCTION`.

### Evidence References

- Implementation: [`PciDssRules.java`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/PciDssRules.java), method `validateWebApplicationSecurity`
- Test data: [`compliance-test-matrix.csv`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/test/resources/compliance-test-matrix.csv), rows whose `configName` contains `no_WAF`

### Post-deployment changes

Synthesis-time validation does not detect a WAF that is removed after deployment. `ComplianceFactory` deploys an `ALB_WAF_ENABLED` AWS Config rule when HIPAA is selected; for PCI-DSS-only deployments, detecting that change depends on the AWS Config conformance pack or on controls outside CloudForge.

### Reproducing the Evidence

```bash
# Show the WAF rule
grep -n "PCI-DSS-Req-6.6-WAF" -A8 \
  cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/PciDssRules.java

# Count WAF negative test rows
grep -c "no_WAF" cloudforge-api/src/test/resources/compliance-test-matrix.csv

# Run a split PCI-DSS matrix
mvn -pl cloudforge-api test -Dtest=TruthTableValidationTest#testPciDssFargateFail
```

---

## 2. Configuration Validation Rules

### Behavior

`ConfigurationValidationRules` (framework ID `CONFIG`, priority 1, `alwaysLoad = true`) defines two rules:

- `CONFIG-SUBDOMAIN-DOMAIN`: a subdomain requires a parent domain.
- `CONFIG-OIDC-HTTPS`: `authMode: alb-oidc` requires `enableSsl: true`.

The class is **not** listed in [`META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/resources/META-INF/services/com.cloudforge.core.interfaces.FrameworkRules), so `FrameworkLoader` does not install it during synthesis. Until it is registered, these rules should not be presented as an active control.

### Evidence References

- Implementation: [`ConfigurationValidationRules.java`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/ConfigurationValidationRules.java)
- Test data: `compliance-test-matrix.csv` rows whose `configName` starts with `FAIL_CONFIG`

---

## 3. VPC Flow Logs

### Behavior

- `PciDssRules` and `HipaaRules` fail when VPC Flow Logs are disabled.
- For HIPAA and PCI-DSS, `ComplianceMatrix` marks `NETWORK_FLOW_LOGS` as REQUIRED, so the security profile enables flow logs whenever one of these frameworks is selected and `complianceMode` is not `disabled`. The validation rule therefore acts as a second check.
- The test matrix exercises the validation rule with `dev` and `staging` rows (pattern `*no-flow-logs`).

### Evidence References

- [`PciDssRules.java`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/PciDssRules.java), method `validateAuditLogging`
- [`HipaaRules.java`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/HipaaRules.java), rule `HIPAA-164.312(b)-FlowLogs`
- [`ProductionSecurityProfileConfiguration.java`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/security/ProductionSecurityProfileConfiguration.java), method `isFlowLogsEnabled`

---

## 4. Test Methodology

`TruthTableValidationTest` synthesizes stacks from CSV rows. Each row states an expected result (`PASS` or `FAIL`), and the test verifies that validation produces that outcome. Negative rows confirm that invalid configurations are rejected. See [CSV Parameterized Testing](CSV_PARAMETERIZED_TESTING.md).

### Evidence Artifacts

| Evidence Type | Location |
|--------------|----------|
| Validation code | `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/` |
| Test data | `cloudforge-api/src/test/resources/compliance-test-matrix.csv`, `cloudforge-api/src/test/resources/compliance-matrices/` |
| Test results | `cloudforge-api/target/surefire-reports/TEST-*.xml` (generated) |
| Truth table report | `cfc-testing/scripts/validation-results/compliance-truth-table-report.html` (generated by `compliance-truth-table-generator.py`) |
| Control mapping | [PCI DSS Controls Gap Analysis](PCI_DSS_CONTROLS_GAP_ANALYSIS.md), [SOC 2 Controls Gap Analysis](SOC2_CONTROLS_GAP_ANALYSIS.md) |

### Reproducing Test Results

```bash
# Run the truth table tests (long-running)
mvn -pl cloudforge-api test -Dtest=TruthTableValidationTest

# Summarize results
grep -h -E "tests=|failures=|errors=" cloudforge-api/target/surefire-reports/TEST-*TruthTable*.xml

# Regenerate the truth table report
cd cfc-testing
python3 scripts/compliance-truth-table-generator.py
open scripts/validation-results/compliance-truth-table-report.html
```

---

## 5. Validation Layers

| Layer | Mechanism | Scope |
|-------|-----------|-------|
| 1 | cdk-nag packs | `production` stacks with frameworks selected |
| 2 | CloudForge `FrameworkRules` validators | When `auditManagerEnabled` is `true` |
| 3 | cfn-guard | Interactive Deployer in `enforce` mode; test suite |
| 4 | AWS Config rules and remediation | When `awsConfigEnabled` is `true` |

`ComplianceMatrix` also enables REQUIRED controls through the security profile. See [Validation Architecture](VALIDATION_ARCHITECTURE.md).

---

## 6. Preparing for an Assessment

1. Record the deployment configuration (`deployment-context.json` or CDK context) for each in-scope stack, including `auditManagerEnabled`, `complianceFrameworks`, and `complianceMode`.
2. Run the relevant tests and archive the Surefire reports.
3. Regenerate the truth table report.
4. Review the evidence artifacts with the assessor.

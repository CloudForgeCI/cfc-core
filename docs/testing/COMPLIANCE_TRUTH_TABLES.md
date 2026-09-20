# Compliance Truth Table Testing

CloudForge uses parameterized "truth table" tests to exercise combinations of deployment settings
against the compliance rule classes. There are two layers:

| Layer | What it checks | Where |
|-------|----------------|-------|
| Deployment configurations | Every valid combination of runtime, profile, domain, TLS, auth, and network settings synthesizes and produces the expected resources | `TruthTableValidationTest`, driven by `cfc-testing/scripts/truth-table-generator.py` |
| Compliance rules | Each rule class passes compliant settings and rejects non-compliant ones, across security profiles and compliance modes | `cloudforge-api/src/test/java/com/cloudforgeci/api/core/rules/*RulesTest.java` |

The deployment layer shows that configurations synthesize. The rules layer shows that, for example,
`HipaaRules` rejects a PRODUCTION stack with `guardDutyEnabled=false` under `complianceMode: enforce`.

See also [Extended testing](../guides/EXTENDED-TESTING.md) for the scripts in `cfc-testing/scripts/`.

---

## Layer 1: deployment configuration truth table

`cfc-testing/scripts/truth-table-generator.py` enumerates these dimensions, marks invalid
combinations, and writes `truth-table.json` (plus HTML reports) to
`cfc-testing/scripts/validation-results/`:

- Runtime: `EC2`, `FARGATE`
- Topology: `APPLICATION_SERVICE`
- Security profile: `DEV`, `STAGING`, `PRODUCTION`
- Domain: with / without
- TLS: enabled / disabled
- Subdomain: with / without
- Auth mode: `none`, `alb-oidc`, `application-oidc`
- Network mode: `public-no-nat`, `private-with-nat`

```bash
python3 cfc-testing/scripts/truth-table-generator.py
```

[`TruthTableValidationTest`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/test/java/com/cloudforgeci/api/integration/deployment/TruthTableValidationTest.java)
loads that file, synthesizes each valid configuration, and checks the resulting template against the
expected resources (with `ComplianceValidationMatrix` for compliance-related resources). It writes each synthesized template to
`validation-results/cfn-templates/` and results to `validation-results/compliance-results-incremental.jsonl`.

`validation-results/` is gitignored. If `truth-table.json` is missing, the whole class is skipped
(JUnit assumption), so run the generator first.

---

## Layer 2: compliance rule truth tables

Each rule test class contains `@ParameterizedTest` methods backed by `@CsvSource` rows. There are two
kinds of method.

### Synthesized tests

Most methods build a stack with `TestInfrastructureBuilder`
(`cloudforge-api/src/test/java/com/cloudforgeci/api/test/`), install the rules, and call
`Template.fromStack(...)`. Rule classes register their checks with `addValidation`, so synthesis is
what runs them. The test then asserts the expected outcome:

```java
@ParameterizedTest
@CsvSource({
    "PRODUCTION,FARGATE,true,true,true",   // full monitoring: pass
    "PRODUCTION,FARGATE,false,true,true",  // no security monitoring: fail
    "PRODUCTION,FARGATE,true,false,true",  // no GuardDuty: fail
    "STAGING,FARGATE,false,false,true",    // staging, no monitoring: fail
    "DEV,FARGATE,true,true,false"          // DEV: HIPAA rules skipped
})
void testHipaaSecurityManagementCombinations(String profile, String runtime,
        boolean securityMonitoring, boolean guardDuty, boolean shouldEnforce) {
    // ... build context; add baseline settings so only the tested control can fail ...
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
        "TestHipaaSecMgmt", secProfile, runtimeType, customContext);
    builder.createMinimalInfrastructure();
    builder.createMockCertificate();
    new SecurityRules().install(builder.getSystemContext());
    new HipaaRules().install(builder.getSystemContext());

    if (shouldFail) {
        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    } else {
        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }
}
```

A row is expected to fail only when the compliance mode is `ENFORCE`, the profile is one the rule class
applies to, and the tested control is missing. In `ADVISORY` mode, violations are logged and synthesis
succeeds. When a row expects a pass, the test adds baseline settings for the framework's other controls
so that unrelated requirements do not cause a failure.

### Install-only tests

The `*Expanded*` methods in `HipaaRulesTest`, `PciDssRulesTest`, `GdprRulesTest`, and `Soc2RulesTest`,
plus several other PCI-DSS combination methods, only call `install(ctx)` inside `assertDoesNotThrow`.
They never synthesize, so the rule checks do not run and the rows do not assert pass or fail. They
check that each combination of settings is accepted by the context and rule setup. The same classes
also contain non-parameterized `@Test` methods.

---

## Coverage by rule class

The methods listed are the parameterized ones. "S" marks synthesized tests; "I" marks install-only
tests.

### `HipaaRulesTest`

HIPAA rules apply to STAGING and PRODUCTION; DEV returns early.

- S: `testHipaaSecurityManagementCombinations` (§164.308(a)(1)), `testHipaaPhysicalSafeguardsCombinations`
  (§164.310), `testHipaaAccessControlAuthModeCombinations` (§164.312(a)(1)),
  `testHipaaAuditControlsCombinations` (§164.312(b)), `testHipaaAuthenticationMfaCombinations`
  (§164.312(d)), `testHipaaTransmissionSecurityCombinations` (§164.312(e)(1)),
  `testHipaaRetentionRequirementsCombinations`, `testHipaaSecurityProfileBranches`,
  `testHipaaComprehensiveCombinations`, `testHipaaFlowLogsEnforcement`,
  `testHipaaEncryptionCombinations`, `testHipaaAuditLogRetention`, `testHipaaMultiViolationScenarios`
- I: `testHipaaExpanded*` (security management, encryption at rest, audit logging, authentication,
  transmission security, retention periods, physical safeguards, multi-requirement)

### `PciDssRulesTest`

- S: `testPciDssEncryptionCombinations`, `testPciDssAuditLoggingCombinations`,
  `testPciDssSecurityMonitoringCombinations`, `testPciDssRetentionPeriods`,
  `testPciDssNetworkSecurityCombinations`, `testPciDssWafEnforcementAcrossProfiles`,
  `testPciDssFlowLogsEnforcement`, `testPciDssLogRetentionRequirements`,
  `testPciDssAcrossApplicationTypes`, `testPciDssMultiViolationScenarios`
- I: `testPciDssVendorDefaultsCombinations`, `testPciDssAuthenticationCombinations`,
  `testPciDssNetworkModes`, `testPciDssComplianceModes`, `testPciDssSecurityProfileBranches`,
  `testPciDssWebApplicationSecurityCombinations`, `testPciDssAccessControlCombinations`,
  `testPciDssRetentionCombinations`, `testPciDssComprehensiveCombinations`,
  `testPciDssBackupAndDataProtection`, `testPciDssExpanded*` (encryption at rest, audit logging, key
  management, access control, network segmentation, retention, vendor defaults and DB security,
  transmission security, system monitoring, multi-requirement).
  `testPciDssEncryptionCombinations`, `testPciDssAuditLoggingCombinations`, and
  `testPciDssSecurityMonitoringCombinations` each also have an install-only overload with different
  parameters.

### `GdprRulesTest`

- S: `testGdprSecurityProfileBranches`, `testGdprDataProtectionByDesignEncryption` (Art. 25),
  `testGdprNetworkIsolation` (Art. 32(1)(b)), `testGdprProcessingRecordsLogging` (Art. 30),
  `testGdprSecurityOfProcessingTransit` (Art. 32(1)(a)), `testGdprSecurityMonitoringAndBackup`,
  `testGdprAwsConfig`, `testGdprBreachDetection` (Art. 33), `testGdprWafProtection`,
  `testGdprDataResidencyEnforcement`, `testGdprEncryptionRequirements`, `testGdprAuditTrailRetention`,
  `testGdprMultiViolationScenarios`
- I: `testGdprComprehensiveScenarios`, `testGdprExpanded*` (data protection encryption, audit
  logging and Config, monitoring and breach, transmission security, backup and availability,
  multi-article)

### `Soc2RulesTest`

- S: `testSoc2SecurityProfileBranches`, `testSoc2AccessControls` (CC6.1, CC6.2),
  `testSoc2NetworkSecurity` (CC6.6, CC6.7), `testSoc2SystemMonitoring` (CC7.2),
  `testSoc2ChangeManagement` (CC8.1), `testSoc2Availability` (A1.2, A1.3),
  `testSoc2Confidentiality` (C1.1, C1.2), `testSoc2ComprehensiveScenarios`,
  `testSoc2EncryptionCombinations`, `testSoc2NetworkSecurityCombinations`,
  `testSoc2LoggingAndAuditCombinations`, `testSoc2AvailabilityEdgeCases`,
  `testSoc2RuntimeTypeVariations`, `testSoc2ComplianceModeTransitions`,
  `testSoc2CombinedSecurityAvailability`, `testSoc2BackupAndRecovery`,
  `testSoc2AvailabilityMonitoring`, `testSoc2ConfidentialityEncryption`,
  `testSoc2ProcessingIntegrityAuditLogs`, `testSoc2MultiCriterionViolations`
- I: `testSoc2Expanded*` (logical access, system monitoring, availability, confidentiality, change
  management and risk, multi-criteria)

### Security rule classes

All parameterized methods in these classes synthesize.

| Test class | Methods |
|------------|---------|
| `ThreatProtectionRulesTest` | `testThreatExpandedMalwareProtection`, `...IntrusionDetection`, `...FileIntegrityMonitoring`, `...ContainerSecurity`, `...ComprehensiveScenarios` |
| `IncidentResponseRulesTest` | `testIRExpandedIncidentResponsePlan`, `...DisasterRecovery`, `...BackupRestore`, `...ForensicLogging`, `...ComprehensiveScenarios`; `testIncidentResponseBackupEdgeCases`, `...CloudTrailValidationEdgeCases`, `...SnsAlertsEdgeCases`, `...MultiViolations` |
| `AdvancedMonitoringRulesTest` | `testAMExpandedSecurityHub`, `...Inspector`, `...Macie`, `...CentralizedMonitoring`, `...ComprehensiveScenarios` |
| `DatabaseSecurityRulesTest` | `testDBExpandedRDSSecurity`, `...DynamoDBSecurity`, `...DatabaseMonitoring`, `...ComprehensiveScenarios`; `testRdsBackupRetentionEdgeCases`, `testPerformanceInsightsEncryptionEdgeCases`, `testRdsHighAvailabilityEdgeCases` |
| `KeyManagementRulesTest` | `testKMExpandedKMSKeyManagement`, `...CertificateManagement`, `...SecretsManagement`, `...ComprehensiveScenarios`; `testKmsKeyRotationEdgeCases`, `testCertificateManagementEdgeCases`, `testSecretsRotationEdgeCases` |

---

## Writing a truth table test

1. Read the rule class and list its branch points: profile early returns, compliance-mode handling,
   and each control check.
2. Choose rows that take each branch at least once, including the DEV (or other skipped profile) path
   and both `ENFORCE` and `ADVISORY`.
3. Build the stack with `TestInfrastructureBuilder`, passing the row's settings as the custom context.
   For rows expected to pass, add baseline settings for the framework's other controls.
4. Install the rule classes under test, then assert on `Template.fromStack(...)`: `assertThrows` for
   expected failures, `assertDoesNotThrow` otherwise. Tests that only call `install(...)` do not run
   the checks.

To add a case, add a CSV row with a short comment describing the expected outcome.

## Running

Tests are skipped by default in this build. Enable them with the `ci` profile:

```bash
# All rule truth tables
mvn -pl cloudforge-api -am test -Pci -Dtest='*RulesTest' -Dsurefire.failIfNoSpecifiedTests=false

# One class
mvn -pl cloudforge-api -am test -Pci -Dtest=HipaaRulesTest -Dsurefire.failIfNoSpecifiedTests=false

# Deployment truth table (generate truth-table.json first)
python3 cfc-testing/scripts/truth-table-generator.py
mvn -pl cloudforge-api -am test -Pci -Dtest=TruthTableValidationTest -Dsurefire.failIfNoSpecifiedTests=false
```

## Related documentation

- [Integration tests](INTEGRATION_TESTS.md)
- [Extended testing](../guides/EXTENDED-TESTING.md)
- [CSV parameterized testing](../compliance/CSV_PARAMETERIZED_TESTING.md)
- [Validation architecture](../compliance/VALIDATION_ARCHITECTURE.md)

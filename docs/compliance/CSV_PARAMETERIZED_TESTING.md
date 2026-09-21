# CSV-Parameterized Compliance Testing

## Overview

`TruthTableValidationTest` uses JUnit `@CsvFileSource` tests to synthesize stacks for combinations of compliance framework, runtime, network mode, and authentication mode, and to check each synthesized stack against several validation layers. Test data lives in version-controlled CSV files so that the matrix can be reviewed and diffed independently of the test code.

## Architecture

### Test Data Flow

```
cfc-testing/scripts/truth-table-generator.py
         ↓
cloudforge-api/src/test/resources/compliance-test-matrix.csv
cloudforge-api/src/test/resources/compliance-matrices/*.csv
         ↓
@CsvFileSource tests in TruthTableValidationTest
         ↓
Validation layers:
  - Layer 1: cdk-nag (construct-level)
  - Layer 2: CloudForge FrameworkRules (synthesis-time validators)
  - Layer 3: cfn-guard (template-level policy)
  - Layer 4: AWS Config (rule deployment checks)
```

### Test Matrix Files

- `compliance-test-matrix.csv` - The full matrix written by `truth-table-generator.py`.
- `compliance-matrices/` - The matrix split into smaller files by framework combination, runtime, and expected result (for example `hipaa_fargate_pass.csv`, `soc2_ec2_fail.csv`, `soc2,pci-dss_fargate_pass.csv`). Each file backs one test method.

The single-file test `testComplianceFrameworkIntegrationCsv` is `@Disabled`; the split test methods (`testGdprFargatePass`, `testHipaaFargatePass`, and so on) keep each JVM run small enough to avoid jsii runtime crashes during long synthesis runs.

Rows cover SOC2, PCI-DSS, HIPAA, GDPR, and combinations of them; EC2 and Fargate runtimes; the `alb-oidc` and `none` authentication modes; and `PASS` and `FAIL` expected results for negative testing.

## Usage

### Generating the CSV Test Matrix

```bash
cd cfc-testing
python3 scripts/truth-table-generator.py
```

The generator writes its output to `cfc-testing/scripts/validation-results/` (pass a directory as the first argument to override it) and copies `compliance-test-matrix.csv` into `cloudforge-api/src/test/resources/`. Pass `--with-validation` to also run the compliance tests and include their results in the HTML report.

### Running CSV-Based Tests

```bash
# One split matrix
mvn -pl cloudforge-api test -Dtest=TruthTableValidationTest#testHipaaFargatePass

# All truth table tests
mvn -pl cloudforge-api test -Dtest=TruthTableValidationTest
```

The full class synthesizes several hundred stacks and takes a long time; prefer individual methods during development.

### CSV Format

```csv
configName,runtime,securityProfile,domainConfig,sslConfig,subdomainConfig,authMode,networkMode,complianceFramework,logRetentionDaysOverride,flowLogsEnabledOverride,expectedResult,applicationId,provisionDatabase,region,gdprDataTransferApproved
EC2_PRODUCTION_SOC2_alb-oidc_private-with-nat,EC2,PRODUCTION,with-domain,ssl-enabled,no-subdomain,alb-oidc,private-with-nat,SOC2,,,PASS,jenkins,,,
...
```

Empty override columns fall back to the profile defaults. `expectedResult` is `PASS` or `FAIL`; `FAIL` rows assert that validation rejects the configuration.

## Test Implementation

```java
@ParameterizedTest(name = "{0}")
@CsvFileSource(
    resources = "/compliance-matrices/hipaa_fargate_pass.csv",
    numLinesToSkip = 1
)
void testHipaaFargatePass(String configName, String runtime, ...) {
    // 1. Build the deployment context from the row
    // 2. Synthesize the stack
    // 3. Run cdk-nag, FrameworkRules, cfn-guard, and AWS Config checks
    // 4. Assert the expected result, reporting known gaps as warnings
}
```

## Validation Layers

| Layer | Tool | Notes |
|-------|------|-------|
| Layer 1 | cdk-nag | Applied for `production` stacks with frameworks selected: `HIPAASecurityChecks` for HIPAA, `PCIDSS321Checks` for PCI-DSS, `AwsSolutionsChecks` for SOC2 and other frameworks |
| Layer 2 | CloudForge FrameworkRules | Installed only when `auditManagerEnabled` is `true` |
| Layer 3 | cfn-guard | Runs the rule files in `cloudforge-api/src/main/resources/cfn-guard/frameworks/` against the synthesized template. Skipped with a message when the `cfn-guard` binary is not on `PATH` |
| Layer 4 | AWS Config | Checks that the expected Config rules are present in the template |

### Validation Output Example

```
INFO: Applying cdk-nag validation (mode=enforce)
INFO:   ✓ Applied cdk-nag pack for SOC2
INFO: Applied 1 cdk-nag validation packs
INFO: Skipping CloudForge FrameworkRules validation (auditManagerEnabled = false)
```

## @CsvFileSource and @MethodSource

`TruthTableValidationTest` also contains `@MethodSource` tests (`testTruthTableConfiguration`, `testComplianceFrameworkIntegration`, `testAwsConfigRuleDeployment`) that build their parameters from `truth-table.json`.

- Use `@CsvFileSource` for stable, reviewable matrices such as the compliance framework combinations.
- Use `@MethodSource` when parameters are computed or the dimensions change often.

## Continuous Integration

`.github/workflows/localstack-compliance-verification.yml` installs cfn-guard and runs the truth table tests. `.github/workflows/cfc-validation.yml` runs the Maven build and tests.

## Troubleshooting

### CSV File Not Found

Regenerate the matrix; the generator copies `compliance-test-matrix.csv` into `cloudforge-api/src/test/resources/`:

```bash
cd cfc-testing
python3 scripts/truth-table-generator.py
```

Files under `compliance-matrices/` are checked in and are not rewritten by the generator.

### Test Failures

Check the output of each layer:
1. cdk-nag: `Applied N cdk-nag validation packs`
2. cfn-guard: `Layer 3 (cfn-guard): Validation passed` or the listed failure details
3. FrameworkRules: validator messages from the installed `FrameworkRules` classes

### Known Gaps and Failures

Violations tagged `[KNOWN GAP]` are reported as warnings; any other violation fails the test.

## Related Work

The following are not covered by the current matrix:

- ISO 27001 and FedRAMP rows (neither is accepted by `complianceFrameworks` yet)
- Custom frameworks added through the `FrameworkRules` plugin interface

## References

- [Multi-Framework Compliance](MULTI_FRAMEWORK_COMPLIANCE.md)
- [truth-table-generator.py](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/scripts/truth-table-generator.py)
- [compliance-truth-table-generator.py](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/scripts/compliance-truth-table-generator.py)
- [TruthTableValidationTest.java](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/test/java/com/cloudforgeci/api/integration/deployment/TruthTableValidationTest.java)
- [compliance-test-matrix.csv](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/test/resources/compliance-test-matrix.csv)
- [LocalStack compliance workflow](https://github.com/CloudForgeCI/cfc-core/blob/develop/.github/workflows/localstack-compliance-verification.yml)

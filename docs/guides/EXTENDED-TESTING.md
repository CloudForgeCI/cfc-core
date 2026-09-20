# Extended Testing

This guide describes the test suites and scripts that go beyond the default unit tests:
synthesis matrices, resource validation and drift detection, synthesis benchmarks, and
deployments to local AWS emulators.

## Maven Test Profiles

Tests are skipped by default (`skipTests=true` in the root `pom.xml`). Enable them with the
`ci` profile:

```bash
mvn clean verify -Pci
```

Tests that need a running emulator are tagged and excluded from the default run. Enable them
with the matching profile once the emulator is running:

| Profile | JUnit tag | Modules | Requires |
|---------|-----------|---------|----------|
| `ministack` | `ministack` | `cloudforge-ministack`, `cfc-testing` | MiniStack on port 4566 |
| `localstack` | `localstack` | `cloudforge-localstack`, `cfc-testing` | LocalStack on port 4566 |

For example:

```bash
mvn -pl cloudforge-ministack verify -Pci,ministack
```

Start an emulator from the Interactive Deployer's platform menu (`--platform`); see the
[Local Emulator Quick Start](LOCAL_EMULATOR_QUICK_START.md). The integration and compliance
test suites in `cloudforge-api` are described in
[Integration Tests](../testing/INTEGRATION_TESTS.md) and
[Compliance Truth Tables](../testing/COMPLIANCE_TRUTH_TABLES.md).

## Synthesis and Validation Scripts

The scripts live in `cfc-testing/scripts/`. They synthesize the sample application in
`cfc-testing`, so build the project first and have the AWS CDK CLI (`cdk`) on your `PATH`:

```bash
mvn clean install
mvn -f cfc-testing package -Dmaven.test.skip=true
cd cfc-testing
```

Several scripts write a temporary `deployment-context.json` and temporarily rewrite
`cdk.json` in `cfc-testing/`; do not run them concurrently.

| Script | Purpose |
|--------|---------|
| `comprehensive-synth-test.sh` | Synthesizes EC2 and Fargate stacks across the DEV, STAGING, and PRODUCTION security profiles and records cdk-nag findings. Results go to `scripts/synth-results/`. |
| `enhanced-synth-test.sh` | Same matrix with several authentication modes. Results go to `test-results/enhanced-synth-results/`. |
| `generate-synth-report.sh` | Writes an HTML dashboard of synthesis results to `scripts/validation-results/comprehensive-synth-report.html`. |
| `detailed-analysis.sh` | Compares synthesis results to find differences between EC2 and Fargate stacks. |
| `comprehensive-resource-validator.sh` | Builds a truth table of expected resources per configuration and checks the synthesized templates against it. Results go to `scripts/validation-results/`. |
| `drift-detector.sh` | Compares validation results against a saved baseline. Commands: `baseline`, `detect` (default), `history`, `archive`. |
| `capture-build-snapshot.sh` | Records template hashes, test results, and configuration for the current commit, for comparing builds. |
| `master-validation-system.sh` | Runs resource validation, drift detection, and reporting together. Commands: `full`, `validate`, `drift`, `smoke`, `baseline`, `report`, `strategy`. |
| `deployment-changeset-validator.sh` | Creates CloudFormation change sets with `cdk deploy --no-execute` without applying them. Requires AWS credentials. |
| `deployment-dry-run-tracker.sh` | Runs deployment dry runs and records timing. |

A typical drift-detection workflow:

```bash
bash scripts/master-validation-system.sh full       # initial validation
bash scripts/master-validation-system.sh baseline   # save a baseline
# ... make changes, rebuild ...
bash scripts/master-validation-system.sh validate
bash scripts/master-validation-system.sh drift
```

The CI workflows in `.github/workflows/` (`cfc-validation.yml`, `publish-reports.yml`) run
these scripts from `cfc-testing/`.

## Synthesis Benchmarks

The benchmark scripts time Interactive Deployer synthesis runs. Run them from `cfc-testing/`
after building (they run `mvn compile` if the classes are missing):

| Script | Purpose |
|--------|---------|
| `quick-synth-benchmark.sh` | A few representative configurations |
| `performance-synth-benchmark.sh` | A larger set of configurations; writes logs and a CSV to `benchmark-results/` |
| `command-line-benchmark.sh` | Configurations passed as command-line arguments |
| `run-all-benchmarks.sh` | Menu that runs the benchmarks above plus a repeated-synthesis stress test |

The benchmark scripts use paths relative to `cfc-testing/` (`target/classes`,
`benchmark-results/`), so run them as `bash scripts/<name>.sh` from that directory.
`run-all-benchmarks.sh` invokes the other benchmark scripts as `./<name>.sh`, so it
currently needs them to be reachable from the working directory as well.

Synthesis time depends on the machine, the configuration, and whether dependencies are
already cached, so compare results only against earlier runs on the same machine.

## Emulator Deployment Scripts

These scripts deploy saved configurations from `cfc-testing/deployment-contexts/` to a running
emulator. Start the emulator first.

| Script | Target | Notes |
|--------|--------|-------|
| `deploy-ministack-apps.sh` | MiniStack | Applications that do not need RDS; skips stacks that already exist |
| `deploy-localstack-apps.sh` | LocalStack | Applications with distinct host ports |
| `redeploy-localstack-history.sh` | LocalStack | Redeploys applications in place (`LOCALSTACK_PREFLIGHT=warn`) |
| `deploy-localstack-full-catalog.sh` | LocalStack | Deploys every catalog application one at a time, removing each stack before the next. Optional argument: output TSV path |
| `deploy-localstack-compliance-matrix.sh` | LocalStack | Deploys each compliance framework and security profile combination, restarting LocalStack between configurations |
| `deploy-localstack-template-batch.sh` | LocalStack | Deploys generated templates from `cfn-templates/` directly, restarting LocalStack between templates |

The LocalStack scripts that restart the emulator require `LOCALSTACK_AUTH_TOKEN`. See each
script's header comment for its full set of environment variables.

## Report Generators

The Python scripts in `cfc-testing/scripts/` turn test output into reports:

- `truth-table-generator.py` and `compliance-truth-table-generator.py` — truth tables of expected resources and compliance controls per configuration
- `compliance-report-generator.py` — compliance report from the compliance test run
- `localstack-compliance-comparison.py` — compares synthesized templates with what LocalStack deployed
- `deployment-metrics-dashboard.py` — dashboard of deployment timing metrics

## Adding a Configuration Option

When you add a configuration option, update:

1. The matrices in `comprehensive-synth-test.sh` and `comprehensive-resource-validator.sh`, if the option changes which resources are synthesized
2. The truth tables used by `TruthTableValidationTest` in `cloudforge-api`
3. The drift baseline (`master-validation-system.sh baseline`) once the new results are correct

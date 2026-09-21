# Integration Tests

The integration tests in
[`cloudforge-api/src/test/java/com/cloudforgeci/api/integration/`](https://github.com/CloudForgeCI/cfc-core/tree/develop/cloudforge-api/src/test/java/com/cloudforgeci/api/integration)
synthesize complete CloudFormation templates with CDK and assert on their contents with
`Template.fromStack(...)`. They run offline; nothing is deployed to AWS.

They cover:

- compliance controls for SOC 2, HIPAA, PCI-DSS, and GDPR,
- AWS Config rules and automatic remediation,
- security group chains, IAM trust relationships, and encryption across components,
- Cognito and OIDC authentication on the ALB,
- deployment workflows, runtime/topology combinations, and the deployment truth table.

## Layout

```
integration/
├── IntegrationTestBase.java                    # Shared stack setup and assertions
├── CompleteInfrastructureTest.java
├── compliance/
│   ├── Soc2ComplianceIntegrationTest.java      Soc2ComplianceExtendedTest.java
│   ├── HipaaComplianceIntegrationTest.java     HipaaComplianceExtendedTest.java
│   ├── PciDssComplianceIntegrationTest.java    PciDssComplianceExtendedTest.java
│   ├── GdprComplianceIntegrationTest.java      GdprComplianceExtendedTest.java
│   └── ConfigRulesDeploymentIntegrationTest.java
├── remediation/
│   └── RemediationIntegrationTest.java
├── security/
│   ├── CognitoAuthenticationIntegrationTest.java
│   ├── OidcAuthenticationIntegrationTest.java  # @Disabled
│   └── CrossComponentSecurityIntegrationTest.java
├── deployment/
│   ├── DeploymentWorkflowIntegrationTest.java
│   ├── RuntimeTopologyIntegrationTest.java
│   ├── SynthesisValidationIntegrationTest.java
│   ├── TruthTableValidationTest.java
│   ├── ComplianceValidationMatrix.java         # helper
│   └── ResourceValidationMatrix.java           # helper
└── runtime/
    └── RuntimeInfrastructureSynthesisTest.java
```

## Test classes

### Compliance

Each `*ComplianceIntegrationTest` synthesizes a stack for one framework and checks the resources behind
its controls. The matching `*ComplianceExtendedTest` goes further into specific settings (key rotation,
log retention, alarm thresholds, and so on).

| Class | Controls covered |
|-------|------------------|
| `Soc2ComplianceIntegrationTest` | Fargate and EC2 full stacks, network segmentation, encryption in transit, logging and audit trail, IAM, backup and recovery, high availability, threat detection, Config rules |
| `HipaaComplianceIntegrationTest` | §164.308 (security management, workforce security, information access), §164.310(d), §164.312 (access control, encryption, audit, integrity, authentication, transmission), business continuity |
| `PciDssComplianceIntegrationTest` | Requirements 1, 2, 3, 4, 5, 6, 8, 10, 11, plus network segmentation, access control lists, data retention and disposal, high availability |
| `GdprComplianceIntegrationTest` | Articles 5(1)(f), 25, 30, 32, 33; data minimization, erasure, portability, access control, data residency, accountability, threat detection |
| `ConfigRulesDeploymentIntegrationTest` | Which Config rules each framework activates, rule scoping, remediation attachments, recorder and delivery channel. Builds its own context instead of extending `IntegrationTestBase`. |

### Remediation

`RemediationIntegrationTest` checks S3 public-access-block and versioning remediation, CloudTrail
bucket access logging, encryption enforcement, retry settings, IAM permissions for the remediation
roles, scope tagging, framework-specific remediation, notifications, and multiple remediation actions
per rule.

### Security

- `CrossComponentSecurityIntegrationTest` — ALB → compute → EFS security group chains (Fargate and
  EC2), IAM trust relationships, network isolation, encryption across components, multi-AZ
  distribution, egress restrictions, target group health checks, listener configuration, EFS access
  points, VPC endpoints.
- `CognitoAuthenticationIntegrationTest` — user pool creation, email verification, app client,
  domain, MFA, password policy, threat protection, account recovery, token validity, and the ALB
  `authenticate-cognito` action. See [Cognito MFA setup](../setup/COGNITO_MFA_COMPLIANCE_SETUP.md).
- `OidcAuthenticationIntegrationTest` — ALB OIDC action, client secret in Secrets Manager, listener
  rule, session settings, scopes, multiple providers, Identity Center. The class is `@Disabled`
  and does not run. See [Identity Center setup](../setup/AWS_IDENTITY_CENTER_SETUP.md).

### Deployment and runtime

- `DeploymentWorkflowIntegrationTest` — `DeploymentContext` creation, `SystemContext` slot population,
  Fargate and EC2 stacks, security profile progression and IAM profile mapping, context validation,
  stack outputs, multiple stacks, naming, and factory ordering (VPC → ALB → EFS → compute).
- `RuntimeTopologyIntegrationTest` — runtime/topology combinations, TLS, autoscaling, DNS.
- `SynthesisValidationIntegrationTest` — resources created for combinations of autoscaling, DNS, WAF,
  and security/IAM profile settings.
- `RuntimeInfrastructureSynthesisTest` — full EC2 and Fargate synthesis across all security profiles.
- `CompleteInfrastructureTest` — complete infrastructure with all validation requirements met.
- `TruthTableValidationTest` — every valid configuration from the deployment truth table. Requires
  `cfc-testing/scripts/validation-results/truth-table.json`; the class is skipped when it is missing.
  One parameterized test in this class is `@Disabled`. See
  [Compliance truth tables](COMPLIANCE_TRUTH_TABLES.md).

## Running the tests

The root `pom.xml` sets `skipTests=true`, so a plain `mvn test` runs nothing. Use the `ci` profile.
The commands below run from the repository root; `-am` also builds `cloudforge-core`, and
`-Dsurefire.failIfNoSpecifiedTests=false` keeps modules without matching tests from failing.

```bash
# All integration tests
mvn -pl cloudforge-api -am test -Pci \
  -Dtest='com/cloudforgeci/api/integration/**/*' -Dsurefire.failIfNoSpecifiedTests=false

# One package
mvn -pl cloudforge-api -am test -Pci \
  -Dtest='com/cloudforgeci/api/integration/compliance/*' -Dsurefire.failIfNoSpecifiedTests=false

# One class
mvn -pl cloudforge-api -am test -Pci \
  -Dtest=HipaaComplianceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false

# The whole test suite
mvn clean install -Pci
```

Surefire runs `cloudforge-api` tests with `-Xss8m`; CDK synthesis in a long-lived fork needs the larger
thread stack.

## `IntegrationTestBase`

[`IntegrationTestBase`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/test/java/com/cloudforgeci/api/integration/IntegrationTestBase.java)
creates the stack, lets subclasses choose the runtime, security profile, and stack name
(`getRuntimeType()`, `getSecurityProfile()`, `getStackName()`), and synthesizes it with
`synthesizeTemplate()`. It provides these assertions:

| Area | Methods |
|------|---------|
| Security groups | `assertSecurityGroupHasIngressRule`, `assertSecurityGroupChain` |
| IAM | `assertRoleHasManagedPolicy`, `assertRoleTrustsService`, `assertRoleHasPermissions` |
| Encryption | `assertEfsEncrypted`, `assertLogGroupsEncrypted` |
| Network | `assertVpcFlowLogsEnabled`, `assertAlbPublic`, `assertAlbNotPublic` |
| Compliance services | `assertConfigRulesDeployed`, `assertCloudTrailEnabled`, `assertGuardDutyEnabled` |
| Backup | `assertBackupPoliciesConfigured`, `assertBackupVaultLockConfigured`, `assertEfsProtectedByBackupPlan` |
| Availability | `assertMultiAzDeployment`, `assertEfsMultiAzMountTargets` |
| Monitoring | `assertCriticalAlarmsConfigured`, `assertLogRetentionConfigured` |

## Adding a test

1. Extend `IntegrationTestBase` and override the runtime, security profile, or stack name as needed.
   Tests that must set deployment context before `SystemContext` starts (as
   `ConfigRulesDeploymentIntegrationTest` does) build their own stack instead.
2. Synthesize with `synthesizeTemplate()` and assert with the base-class helpers or
   `template.hasResourceProperties(...)`.
3. Cover both Fargate and EC2 where the behavior differs.
4. Name classes `<Feature>IntegrationTest` and methods `test<Control><Scenario>`, for example
   `testHipaaAuditControls` or `testPciDssRequirement1NetworkSecurityControls`, and state the
   control the test covers in its javadoc.

## Related documentation

- [Compliance truth tables](COMPLIANCE_TRUTH_TABLES.md)
- [Compliance documentation](../compliance/README.md)
- [Automated compliance](../compliance/AUTOMATED_COMPLIANCE.md)
- [Security rules](../guides/SECURITY_RULES_README.md)

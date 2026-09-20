# Deployment Context Examples

Example `deployment-context.json` files for common environments and compliance profiles. Every key is a field of
`DeploymentConfig` (`cloudforge-core/src/main/java/com/cloudforge/core/config/DeploymentConfig.java`); unknown keys
are ignored when the file is loaded, so a misspelled key is silently dropped rather than rejected.

Application-specific examples are in [applications/](applications/README.md).

## Using an example

With the sample consumer in [`cfc-testing/`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/README.md):

```bash
# Build once from the repository root
mvn clean install -DskipTests
mvn -f cfc-testing package -Dmaven.test.skip=true

# Copy an example and edit it
cp docs/examples/dev-minimal.json cfc-testing/deployment-context.json
cd cfc-testing
cdk deploy
```

`cdk.json` in `cfc-testing` runs `InteractiveDeployer`, which loads `deployment-context.json` from the working
directory. To load a different file, set `CFC_CONTEXT_FILE` or pass `--context <file>` when running
`InteractiveDeployer` directly.

The same keys can also be supplied as CDK context under a `cfc` object (for example in `cdk.json`):

```json
{
  "context": {
    "cfc": {
      "applicationId": "jenkins",
      "runtime": "fargate",
      "securityProfile": "dev"
    }
  }
}
```

For a standalone Maven project, start from [cloudforge-sample/pom.xml](cloudforge-sample/pom.xml) and the
[CloudForge sample BOM template](../architecture/cloudforge-sample-bom.template.md).

## Templates

All templates deploy Jenkins (`"applicationId": "jenkins"`). Change `applicationId` to deploy another application
from the [application catalog](../applications/README.md).

| File | Profile | Runtime | Network | Auth | Compliance |
|------|---------|---------|---------|------|------------|
| [dev-minimal.json](dev-minimal.json) | `dev` | Fargate | `public` | none | none |
| [dev-standard.json](dev-standard.json) | `dev` | Fargate, auto scaling 1–2 | `private-with-nat` | `alb-oidc`, Cognito | none |
| [dev-oidc-quick.json](dev-oidc-quick.json) | `dev` | Fargate | `private-with-nat` | `alb-oidc`, Cognito, no domain | none |
| [staging-oidc-quick.json](staging-oidc-quick.json) | `staging` | Fargate, auto scaling 1–2 | `private-with-nat` | `application-oidc`, Cognito MFA, no domain | SOC 2, `advisory` |
| [staging-soc2.json](staging-soc2.json) | `staging` | Fargate, auto scaling 2–4 | `private-with-nat` | `alb-oidc`, Cognito TOTP MFA | SOC 2 |
| [production-soc2.json](production-soc2.json) | `production` | EC2 `t3.medium`, 2–6 | `private-with-nat` | `alb-oidc`, Cognito TOTP MFA | SOC 2, `enforce` |
| [production-hipaa.json](production-hipaa.json) | `production` | EC2 `t3.large`, 2–8 | `private-with-nat` | `alb-oidc`, Cognito TOTP MFA | HIPAA + SOC 2, `enforce` |
| [production-pci-dss.json](production-pci-dss.json) | `production` | EC2 `t3.large`, 3–10 | `private-with-nat` | `alb-oidc`, Cognito TOTP MFA | PCI-DSS + HIPAA + SOC 2, `enforce` |
| [production-oidc-internal.json](production-oidc-internal.json) | `production` | EC2 `t3.medium`, 2–4 | `private-with-nat` | `application-oidc`, Cognito MFA, no domain | SOC 2 + HIPAA, `enforce` |

Log retention in the templates ranges from 7 days (`dev-minimal`) to 2190 days (`production-hipaa`, rounded up to
the six-year CloudWatch Logs retention class).

## Required changes before deploying

| Key | Notes |
|-----|-------|
| `stackName` | Must be unique within the target account and region. |
| `applicationId` | Any registered application ID; see the [catalog](../applications/README.md). |
| `region` | Target AWS region. |
| `domain`, `subdomain` | Your DNS zone and host name, or omit both to use a Private CA certificate (see below). Set `createZone: true` only if the Route 53 hosted zone does not exist yet. |
| `cognitoDomainPrefix` | Required when `cognitoAutoProvision` is `true`. Lowercase letters, digits, and hyphens. CloudForge appends the stack name to form the Cognito hosted UI domain. |

## TLS without a custom domain

When `enableSsl` is `true` and neither `domain` nor `certificateArn` is set, CloudForge creates an AWS Private CA
and issues a certificate for the load balancer DNS name:

```json
{
  "enableSsl": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "my-app"
}
```

- Traffic is encrypted with TLS, but browsers do not trust the private CA and show a certificate warning. Use a
  public domain for user-facing deployments.
- The CA is created in general-purpose mode and billed by AWS Private CA pricing for as long as it exists. It uses
  `RemovalPolicy.DESTROY`, so it is scheduled for deletion when the stack is deleted.
- Whether a private CA satisfies a specific audit requirement is for your assessor to decide.

## Common adjustments

Fargate task size (CPU units and MiB; must be a valid Fargate combination):

```json
{ "cpu": 2048, "memory": 4096 }
```

EC2 sizing and scaling:

```json
{
  "instanceType": "t3.medium",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 6,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 60
}
```

Compliance behavior:

```json
{
  "complianceFrameworks": "SOC2,HIPAA",
  "complianceMode": "advisory"
}
```

`complianceFrameworks` accepts `soc2`, `pci-dss`, `hipaa`, and `gdpr` (case-insensitive; comma, space, or `+`
separated). `complianceMode` is `enforce`, `advisory`, or `disabled`; when omitted it defaults to `enforce` for the
`production` profile and `advisory` otherwise. See [Application Compliance](../applications/COMPLIANCE.md).

Use `createConfigInfrastructure: false` when the account already has an AWS Config recorder in the region.

## Checking compliance after deployment

```bash
aws configservice describe-compliance-by-config-rule --compliance-types NON_COMPLIANT
```

## Related documentation

- [Compliance deployment guide](../compliance/DEPLOYMENT_GUIDE.md)
- [Compliance quick start](../compliance/QUICK_START_GUIDE.md)
- [AWS Config across multiple stacks](../compliance/AWS_CONFIG_MULTI_STACK.md)
- [Cognito MFA setup](../setup/COGNITO_MFA_COMPLIANCE_SETUP.md)
- [IAM rules](../guides/IAM_RULES.md)
- [OIDC authentication](../applications/OIDC.md)
- [Advanced configuration](../ADVANCED.md)

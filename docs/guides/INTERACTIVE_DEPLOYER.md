# Interactive Deployer

The Interactive Deployer (`com.cloudforgeci.samples.app.InteractiveDeployer` in `cfc-testing`)
is a sample command-line tool. It prompts for a deployment configuration, saves it as
`deployment-context.json`, and then synthesizes the stack or deploys it to AWS, MiniStack, or
LocalStack. It is also the CDK app configured in `cfc-testing/cdk.json`.

The tool is a thin entry point: the deployment logic is in the library modules, and your own
project can call the same APIs (see [Architecture](#architecture)).

## Prerequisites

- Java 25 and Maven 3.9+
- For AWS: the AWS CDK CLI (`npm install -g aws-cdk`), AWS credentials, and `cdk bootstrap`
  run once per account and region
- For MiniStack or LocalStack: Docker, and `LOCALSTACK_AUTH_TOKEN` for LocalStack
- Optional: the `cfn-guard` CLI, used when `complianceMode` is `enforce` and by option 7

## Build and run

```bash
mvn clean install
mvn -f cfc-testing/pom.xml package -Dmaven.test.skip=true
cd cfc-testing
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer
```

At startup the tool lists the applications discovered through `ServiceLoader`. Then:

- If the context file does not exist, it prompts for a new configuration, saves it to
  `deployment-context.json`, and shows the deploy menu.
- If the context file exists, it loads it and shows the deploy menu. The file must contain
  `applicationId`.

## Command-line options

| Argument | Effect |
|---|---|
| `--context <file>`, `-c <file>` | Context file to load (default: `$CFC_CONTEXT_FILE`, then `deployment-context.json`). |
| `--interactive`, `-i` | Prompt for a new configuration even if the context file exists. `INTERACTIVE=true` in the environment does the same. |
| `--force`, `-f` | Delete the context file, then prompt for a new configuration. With `--context`, the named file is deleted. |
| `--platform` | Open the emulator platform menu instead of the deploy flow. |
| `<digit>` | Run that deploy menu option without prompting. |
| `<name>` | Use `<name>` as the stack name. |
| `--ministack`, `-m` | Obsolete; ignored. MiniStack is always available as option 6. |

Examples:

```bash
alias cfc='java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer'

cfc --context deployment-contexts/Jenkins-Stack.json 1   # synthesize only
cfc --context deployment-contexts/Jenkins-Stack.json 6   # deploy to MiniStack
cfc my-jenkins 2                                         # deploy deployment-context.json to AWS as "my-jenkins"
cfc -i                                                   # start a new configuration
```

## Configuration prompts

The prompts depend on earlier answers:

1. **Stack name** and **environment** (`dev`, `staging`, `prod`).
2. **Application**, chosen by category or from the full list. Applications that require a
   database turn on RDS provisioning automatically.
3. **Security profile**: `DEV`, `STAGING`, or `PRODUCTION`. Later defaults follow the profile.
4. **Runtime**: `FARGATE` or `EC2`, limited to what the application supports.
5. **Domain**: optional domain and subdomain, and whether to enable TLS. Without a domain, TLS
   is off.
6. **Authentication**: offered only for applications with OIDC support. Choose Cognito,
   Cognito SAML, or an external identity provider, and an `authMode` the application supports.
7. **Resources**: instance type (EC2) or CPU and memory (Fargate). Invalid Fargate CPU and
   memory combinations are corrected.
8. **Scaling**: minimum and maximum capacity; scaling is enabled when the maximum is greater,
   with a CPU target.
9. **Network**: `public-no-nat` or `private-with-nat`, WAF, and CloudFront.
10. **Compliance**: encryption, monitoring, and, depending on the profile, AWS Config,
    GuardDuty, compliance frameworks, validation mode (`enforce` or `advisory`), and log
    retention.
11. **Advanced**: region, availability zones, database options, and optional application
    ports.

The deployer sets `topology` to `application-service`. Before the deploy menu it prints a
summary and any defaults it filled in (for example Cognito settings when an OIDC mode is
chosen).

## Deploy menu

| Option | Action |
|---|---|
| `1` | Synthesize only. The template is written to `cdk.out/`. |
| `2` | Deploy to AWS with `cdk deploy --require-approval never`. |
| `3` | Destroy the existing AWS stack, then deploy. |
| `4` | Dry run: adapt the template for MiniStack and report the result, and print the command for an AWS change set (`cdk deploy --no-execute --require-approval never`). |
| `5` | Export the CloudFormation template as JSON or YAML (prompted). |
| `6` | Deploy to MiniStack. |
| `7` | Validate with cfn-guard, deploy to MiniStack, and verify the stack. |
| `8` | Deploy to LocalStack. |
| `9` | Reconfigure (start a new interactive setup). |
| `0` | Cancel. |

Pressing Enter at the prompt selects option 1.

When compliance frameworks are selected and `complianceMode` is not `disabled`, cdk-nag packs
for those frameworks run during synthesis. In `enforce` mode, cfn-guard also validates the
template after synthesis for every option except 5; a failure stops before any deployment.

## Using the CDK CLI

`cfc-testing/cdk.json` runs the Interactive Deployer as the CDK app. When the CDK CLI starts it,
the tool detects that, loads `$CFC_CONTEXT_FILE` or `deployment-context.json`, and
synthesizes without prompting. If neither file exists, nothing is synthesized.

```bash
cdk synth
cdk diff
cdk deploy
CFC_CONTEXT_FILE=deployment-contexts/Jenkins-Stack.json cdk deploy
cdk destroy <stackName>
```

The account and region come from your credentials (`CDK_DEFAULT_ACCOUNT`,
`CDK_DEFAULT_REGION`) and the `region` property.

## Local emulators

Start an emulator from the platform menu, then deploy with option 6 (MiniStack) or 8
(LocalStack):

```bash
cfc --platform          # choose ministack or localstack, then start
export AWS_ENDPOINT_URL=http://localhost:4566
cfc                     # choose 6 or 8
```

Platform actions are `start`, `stop`, `restart`, `status`, and `reconcile_edge`. On an emulator
the stack is named `<stackName>-ministack` or `<stackName>-localstack`.

Before deploying, a preflight step checks the template against what the emulator supports.
MiniStack preflight blocks, for example, RDS-backed applications. Set `MINISTACK_PREFLIGHT` or
`LOCALSTACK_PREFLIGHT` to `warn` or `off` to relax it, or `CFC_LOCALSTACK_SKIP_PREFLIGHT=true`
to skip LocalStack preflight. If the configured `authMode` is not supported on the target (for
example `alb-oidc`, which the emulators cannot enforce), the tool prints a warning.

See the [Local Emulator Quick Start](LOCAL_EMULATOR_QUICK_START.md),
[application compatibility catalog](LOCAL_EMULATOR_APP_CATALOG.md), [MiniStack](../ministack/README.md),
and [LocalStack](../localstack/README.md).

## Architecture

```text
InteractiveDeployer        prompts, menu, console output (cfc-testing)
LocalDeploymentShell       sample helper for emulator deployments (cfc-testing)
CloudForgeDeployment       deployment API (cloudforge-api)
cloudforge-ministack /
cloudforge-localstack      target adapters and deployers
cloudforge-core            shared contracts (DeploymentConfig, ApplicationSpec, ...)
```

Emulator deployments (options 6-8) synthesize the canonical template and pass it to the target
module:

```java
DeploymentResult result = LocalDeploymentShell.deploy(
    config, DeploymentTarget.LOCALSTACK, cloudAssembly, DeployOptions.defaults());
DeploymentResultPrinter.printOutcome(result, "LocalStack", config.applicationId);
```

AWS deployments (options 2 and 3) run `cdk deploy` as a subprocess.

To build your own entry point, import the `cfc-core` BOM, depend on `cloudforge-api` (plus a
target module if you deploy to an emulator), and call `CloudForgeDeployment`. Add applications
by implementing `ApplicationSpec` and registering it in `META-INF/services`, as
`CraftCmsApplicationSpec` in `cfc-testing` does; see the
[Application Plugin Guide](../plugins/APPLICATION-PLUGIN-GUIDE.md).

## Troubleshooting

| Problem | Resolution |
|---|---|
| `No applicationId found in deployment-context.json` | Add `applicationId`, or run with `-i` to create a new configuration. |
| `Unknown application ID` | Use an ID from the startup list; see [application IDs](../ADVANCED.md#application-ids). |
| AWS deploy fails before creating resources | Check credentials, run `cdk bootstrap`, and confirm the region. |
| cfn-guard validation failed | Fix the reported rule, or set `"complianceMode": "advisory"` to review findings without blocking. |
| `cdk synth` produces no stack | Create `deployment-context.json` first, or set `CFC_CONTEXT_FILE`. |

For every configuration property, see the [Advanced Guide](../ADVANCED.md).

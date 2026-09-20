# cfc-testing: CloudForge sample application

`cfc-testing` is the reference consumer of the CloudForge CI libraries. It has the same layout
as the standalone [cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample)
project: it imports the `cfc-core` BOM, depends on the library modules, and adds only a thin
entry point, example plugins, sample deployment contexts, and validation scripts. It is not
part of the root Maven reactor and is not published.

## Contents

| Path | Purpose |
|---|---|
| `src/main/java/.../samples/app/InteractiveDeployer.java` | Command-line tool: prompts for a configuration, then synthesizes or deploys it. Also the CDK app in `cdk.json`. |
| `src/main/java/.../samples/app/CloudForgeCommunitySample.java` | Minimal CDK app that builds a stack from `deployment-context.json` or `cfc.*` CDK context values. |
| `src/main/java/.../samples/app/LocalDeploymentShell.java`, `DeploymentResultPrinter.java` | Helpers for deploying to MiniStack or LocalStack after synthesis; copy them into your own project. |
| `src/main/java/.../samples/launchers/` | `ApplicationFargateStack` and `ApplicationEc2Stack`, thin stacks that call `ApplicationFactory`. |
| `src/main/java/.../samples/plugins/` | Example plugins: `CraftCmsApplicationSpec` (application) and two compliance rule sets, registered in `src/main/resources/META-INF/services/`. |
| `deployment-contexts/` | Sample deployment contexts, including LocalStack and compliance-matrix variants. |
| `scripts/` | Synthesis, validation, benchmark, and emulator deployment scripts. |
| `cdk.json` | Runs `InteractiveDeployer` as the CDK app. |

Library code belongs in the modules, not here:

| Module | Owns |
|---|---|
| `cloudforge-core` | Contracts, `DeploymentConfig`, local-emulator interfaces |
| `cloudforge-api` | `CloudForgeDeployment`, application specifications, CDK factories |
| `cloudforge-ministack` | MiniStack adapter and deployer |
| `cloudforge-localstack` | LocalStack adapter and deployer |

`cfc-testing` also depends on `cloudforge-manager-deployment`, which makes CloudForge Manager
available as a deployable application.

## Using the BOM

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.cloudforgeci</groupId>
      <artifactId>cfc-core</artifactId>
      <version>${cloudforge.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Add `cloudforge-api`, and `cloudforge-ministack` or `cloudforge-localstack` if you deploy to
an emulator. `pom.xml` in this directory sets `cloudforge.version` to the current snapshot and
adds the Central snapshots repository. See the
[sample project BOM template](../docs/architecture/cloudforge-sample-bom.template.md).

## Build

From the repository root:

```bash
mvn clean install
mvn -f cfc-testing/pom.xml package -Dmaven.test.skip=true
```

`package` copies all dependencies to `target/dependency/`, which the commands below put on the
classpath.

## Run the Interactive Deployer

```bash
cd cfc-testing
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer
```

With no `deployment-context.json`, it prompts for a configuration and saves it. With an
existing file, it loads it and shows the deploy menu:

| Option | Action |
|---|---|
| 1 | Synthesize only |
| 2 | Deploy to AWS |
| 3 | Destroy the AWS stack, then deploy |
| 4 | Dry run |
| 5 | Export the template |
| 6 | Deploy to MiniStack |
| 7 | Validate with cfn-guard, deploy to MiniStack, and verify |
| 8 | Deploy to LocalStack |
| 9 | Reconfigure |
| 0 | Cancel |

Other forms:

```bash
# Use a specific context file and run option 6 without prompting
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/Jenkins-Stack.json 6

# Override the stack name
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer my-jenkins

# Start, stop, or check MiniStack or LocalStack
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer --platform
```

When a context file exists, the CDK CLI can be used directly: `cdk synth`, `cdk diff`,
`cdk deploy`, `cdk destroy <stackName>`. See the
[Interactive Deployer guide](../docs/guides/INTERACTIVE_DEPLOYER.md) for all flags and the
[Local Emulator Quick Start](../docs/guides/LOCAL_EMULATOR_QUICK_START.md) for MiniStack and
LocalStack.

## Tests

Unlike the root reactor, this project does not skip tests by default:

```bash
mvn -f cfc-testing/pom.xml test
mvn -f cfc-testing/pom.xml test -Dtest=DeploymentContextPropagationTest
mvn -f cfc-testing/pom.xml test -Dtest=InteractiveDeployerTest
```

These cover context propagation from `DeploymentConfig` through `deployment-context.json` to
`DeploymentContext`, enum parsing, plugin and platform discovery, and the example plugins. Put
behavior tests in the library module that owns the behavior; keep tests here focused on
entry-point wiring.

The default run includes every test. The `ministack` and `localstack` profiles run only the
tests tagged `ministack` or `localstack`:

```bash
mvn -f cfc-testing/pom.xml test -Pministack
mvn -f cfc-testing/pom.xml test -Plocalstack
```

## Validation scripts

Run from this directory:

```bash
scripts/comprehensive-synth-test.sh          # synthesize EC2 and Fargate across all profiles
scripts/comprehensive-resource-validator.sh  # compare resources with the expected matrix
scripts/drift-detector.sh baseline           # record a baseline
scripts/drift-detector.sh detect             # compare against it
scripts/quick-synth-benchmark.sh             # synthesis timing
```

See [Advanced commands](../docs/ADVANCED.md#cfc-testing-scripts) and
[Extended Testing](../docs/guides/EXTENDED-TESTING.md) for the full list.

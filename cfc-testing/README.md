# CloudForge Community Testing Platform (cloudforge-sample)

This directory is the **reference entrypoint** for CloudForge — the same layout as
[cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample): libraries in the
parent repo, **only the deploy shell and examples here**.

## Role in the monorepo

| This module | Library modules (reactor) |
|-------------|-------------------------|
| `InteractiveDeployer`, `CloudForgeCommunitySample` | `cloudforge-api` — deploy everything, CMS, factories |
| Thin CDK launchers (`ApplicationFargateStack`, …) | `cloudforge-core` — contracts, config, `local.*` interfaces |
| Example plugins (`samples/plugins/cms/…`) | `cloudforge-ministack` — MiniStack-only logic |
| Benchmark/validation scripts | `cloudforge-localstack` — LocalStack-only logic |
| `deployment-context.json` examples | `cloudforge-manager` — operations panel (deploy as an app) |

**Architecture:** [Sample BOM template](../docs/architecture/cloudforge-sample-bom.template.md)

**Rule:** Do not add MiniStack, LocalStack, Manager, or CMS **business logic** here.
Call `CloudForgeDeployment` via `LocalDeploymentShell` (or directly from `cloudforge-api`).

Sample helpers (copy into external projects):

- `LocalDeploymentShell` — thin wrapper after CDK synth
- `DeploymentResultPrinter` — optional console output

## BOM consumption

`cfc-testing` imports the root BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.cloudforgeci</groupId>
      <artifactId>cfc-core</artifactId>
      <version>3.2.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Add only the modules you need (`cloudforge-api` required; ministack/localstack optional).

## Purpose

Validate that CloudForge libraries work end-to-end. The Interactive Deployer is a **sample CLI** —
not the canonical deploy engine. External Java apps should mirror this module: BOM + api + thin entrypoint.

## MiniStack Local Deployment

Deploy synthesized CloudFormation to [MiniStack](https://github.com/ministackorg/ministack) (open-source AWS emulator) without an AWS account.

**From repository root:** [Local Emulator Quick Start](../docs/guides/LOCAL_EMULATOR_QUICK_START.md) · [MiniStack docs](../docs/ministack/README.md)

```bash
# Build (root)
mvn clean install -DskipTests
mvn -f cfc-testing package -Dmaven.test.skip=true

# Start MiniStack or LocalStack (choose the target and action in the platform menu)
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer --platform

# Deploy (cfc-testing)
cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
# Choose option 6 — Deploy to MiniStack
```

## LocalStack Local Deployment

**From repository root:** [Local Emulator Quick Start](../docs/guides/LOCAL_EMULATOR_QUICK_START.md) · [LocalStack docs](../docs/localstack/README.md)

```bash
export LOCALSTACK_AUTH_TOKEN=...
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer --platform
cd cfc-testing && java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
# Choose option 8 — Deploy to LocalStack
```

## Interactive Deployer (sample CLI)

Command-line tool for configuring and deploying CloudForge infrastructure. Target state: menu + prompts only, delegating to `CloudForgeDeployment` in `cloudforge-api`.

### Quick Start

```bash
# Simply run CDK deploy (uses saved configuration from deployment-context.json)
cdk deploy

# Or synthesize only
cdk synth
```

### Features

- **Modular Architecture**: Uses SystemContext orchestration layer for expandable deployment types
- **Strategy Pattern**: Easily extensible deployment strategies
- **Multiple Deployment Types**: 
  - Jenkins (Fargate/EC2) - ✅ Complete
  - S3 + CloudFront (Static Website) - 🚧 Coming Soon
  - S3 + CloudFront + SES + Lambda (Website + Mailer) - 🚧 Coming Soon
- **Interactive Configuration**: Prompts for all necessary parameters with sensible defaults
- **CDK Integration**: Generates proper CDK context and synthesizes stacks

### Prerequisites

1. **AWS CDK CLI**: `npm install -g aws-cdk`
2. **AWS Credentials**: `aws configure` (AWS deploy only; not required for MiniStack)
3. **Java 21+**: Required for compilation
4. **Maven**: For building the project
5. **Docker**: Required for MiniStack local deployment

### Testing

```bash
# Test the interactive deployer
./test-ec2-deploy.sh
```

### Usage Examples

#### With Custom Stack Name
```bash
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer my-jenkins-ec2
```

#### Interactive Mode
```bash
java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer
```

#### With deployment context file
```bash
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/Jenkins-Stack-LocalStack.json
```

#### Custom plugins

See `src/main/java/com/cloudforgeci/samples/plugins/cms/CraftCmsApplicationSpec.java` — copy this pattern in your own repo with `META-INF/services` registration.

---

For full Interactive Deployer documentation, see [docs/guides/INTERACTIVE_DEPLOYER.md](../docs/guides/INTERACTIVE_DEPLOYER.md).

## Unit Tests

```bash
# Run unit tests
mvn test

# Run specific test class
mvn test -Dtest=InteractiveDeployerTest
mvn test -Dtest=DeploymentContextPropagationTest
```

**Test Coverage:**
- Field propagation from `DeploymentConfig` → `deployment-context.json` → `DeploymentContext`
- JSON parsing and serialization
- Enum type conversions (RuntimeType, TopologyType, SecurityProfile)
- Validation rules (authMode requirements, topology constraints)

**Note:** Prefer adding behavior tests in the **owning library module** (see architecture plan). Keep `cfc-testing` tests focused on entrypoint wiring and context propagation.

## Comprehensive Testing & Validation

```bash
# Test synthesis across all security profiles
scripts/comprehensive-synth-test.sh

# Validate synthesized templates against expected resource truth table
scripts/comprehensive-resource-validator.sh

# Drift detection
scripts/drift-detector.sh baseline
scripts/drift-detector.sh detect

# Performance benchmarks
scripts/quick-synth-benchmark.sh
scripts/performance-synth-benchmark.sh
scripts/run-all-benchmarks.sh
```

See `scripts/` for the full validation suite (`master-validation-system.sh`, `enhanced-synth-test.sh`, etc.).

## Key Files

- `src/main/java/com/cloudforgeci/samples/app/InteractiveDeployer.java` — sample CLI (target: thin shell)
- `src/main/java/com/cloudforgeci/samples/app/CloudForgeCommunitySample.java` — CDK app entry
- `src/main/java/com/cloudforgeci/samples/launchers/` — thin stacks calling `ApplicationFactory`
- `src/main/java/com/cloudforgeci/samples/plugins/` — example custom plugins for external projects
- `deployment-context.json` / `deployment-contexts/` — example contexts
- `cdk.json` — CDK configuration

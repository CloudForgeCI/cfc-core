# Sample Project Template

Use this template to create a standalone deployment project that consumes CloudForge, such as
[cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample), the reference
implementation.

---

## Maven BOM

Import the root BOM, `com.cloudforgeci:cfc-core`. It manages the versions of the CloudForge
modules and of shared dependencies such as AWS CDK, constructs, cdk-nag, Jackson, and JUnit.

```xml
<properties>
  <maven.compiler.release>25</maven.compiler.release>
  <cloudforge.version>3.2.16</cloudforge.version>
</properties>

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

Set `cloudforge.version` to the release you target. CloudForge modules are compiled for
Java 25, so the consuming project must build and run on Java 25 or later.

A starter POM is in [docs/examples/cloudforge-sample/pom.xml](../examples/cloudforge-sample/pom.xml).

---

## Module dependencies

| Artifact | When to add |
|----------|-------------|
| `cloudforge-core` | Always: configuration, plugin interfaces, local-deployment contracts |
| `cloudforge-api` | Always: application specs, CDK factories, `CloudForgeDeployment` |
| `cloudforge-ministack` | To deploy to MiniStack |
| `cloudforge-localstack` | To deploy to LocalStack |

Minimal dependency set for deploying to LocalStack:

```xml
<dependencies>
  <dependency>
    <groupId>com.cloudforgeci</groupId>
    <artifactId>cloudforge-core</artifactId>
  </dependency>
  <dependency>
    <groupId>com.cloudforgeci</groupId>
    <artifactId>cloudforge-api</artifactId>
  </dependency>
  <dependency>
    <groupId>com.cloudforgeci</groupId>
    <artifactId>cloudforge-localstack</artifactId>
  </dependency>
  <dependency>
    <groupId>software.amazon.awscdk</groupId>
    <artifactId>aws-cdk-lib</artifactId>
  </dependency>
  <dependency>
    <groupId>software.constructs</groupId>
    <artifactId>constructs</artifactId>
  </dependency>
</dependencies>
```

---

## Project layout

A CDK app entry point is the only thing this template requires. Everything else — synthesis,
deployment, and emulator lifecycle — is handled by
[cloudforge-cli](https://github.com/CloudForgeCI/cloudforge-cli).

```text
your-sample/
├── pom.xml
├── cdk.json
├── deployment-context.json          # or deployment-contexts/*.json
└── src/
    ├── main/java/.../
    │   ├── app/
    │   │   └── YourSampleApp.java             # builds a stack from deployment-context.json
    │   ├── launchers/
    │   │   ├── ApplicationFargateStack.java
    │   │   └── ApplicationEc2Stack.java
    │   └── plugins/                          # custom ApplicationSpec / FrameworkRules plugins
    ├── main/resources/META-INF/services/
    │   ├── com.cloudforge.core.interfaces.ApplicationSpec
    │   └── com.cloudforge.core.interfaces.FrameworkRules
    └── test/java/...
```

See `cloudforge-sample`'s own `CloudForgeCommunitySample.java` for a working entry point: it
reads `deployment-context.json` (or `cfc.*` CDK context values) into a `DeploymentContext`, then
builds `ApplicationFargateStack`/`ApplicationEc2Stack` from it.

---

## Deploying after synthesis

Deploy with `cloudforge-cli` — it drives the same `CloudForgeDeployment` engine directly, no
custom deploy code needed:

```bash
cloudforge-cli deploy --context deployment-context.json --target localstack
```

Call `CloudForgeDeployment.deploy(DeploymentRequest)` from `cloudforge-api` directly instead if
your project needs deploy logic `cloudforge-cli` doesn't cover.

---

## Build and run

```bash
# Install cloudforge-cli
brew install CloudForgeCI/tap/cloudforge-cli

# Manage emulators (LocalStack and MiniStack both use port 4566, so run one at a time)
cloudforge-cli emulator start --target localstack

# Build and deploy your project
cd /path/to/your-sample
mvn package
cloudforge-cli deploy --context deployment-context.json --target localstack
```

### Emulator lifecycle

`cloudforge-ministack` and `cloudforge-localstack` expose lifecycle actions through
`PlatformRuntimeProvider`. `cloudforge-cli emulator` lists the available targets and offers
`start`, `stop`, `restart`, and `status`. Emulator companion containers are managed by the
target module, not by the root `docker-compose.yml`.

The same operations are available programmatically when `cloudforge-core` and the target
module are on the classpath:

```java
import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.local.EmulatorEdgeLifecycle;
import com.cloudforge.core.local.EmulatorEdgeLifecycleAction;
import com.cloudforge.core.local.EmulatorLifecycle;
import com.cloudforge.core.local.EmulatorLifecycleAction;
import com.cloudforge.core.local.LocalEmulatorRuntimes;
import com.cloudforge.core.local.StackPortLifecycle;
import com.cloudforge.core.local.StackPortLifecycleAction;
import com.cloudforge.core.local.StackPortRuntimes;

// Start an emulator; StackPort and the nginx edge start with it by default
EmulatorLifecycle.execute(DeploymentTarget.MINISTACK, EmulatorLifecycleAction.START);
LocalEmulatorRuntimes.forTarget(DeploymentTarget.MINISTACK).isHealthy();

// StackPort resource browser
StackPortLifecycle.execute(DeploymentTarget.MINISTACK, StackPortLifecycleAction.START);
StackPortRuntimes.forTarget(DeploymentTarget.MINISTACK).browserUrl();

// Shared nginx edge
EmulatorEdgeLifecycle.execute(EmulatorEdgeLifecycleAction.RECONCILE);
```

---

## Custom plugins

1. Implement `ApplicationSpec` (and `CmsSpec` or `DatabaseSpec` if needed), or
   `FrameworkRules<SystemContext>` for compliance rules.
2. Register the class under `src/main/resources/META-INF/services/`.

See the [plugin documentation](../plugins/README.md) for details and examples.

---

## Reporting issues

When filing a bug, select the owning module in the GitHub issue template. See
[CONTRIBUTING.md](../CONTRIBUTING.md#reporting-issues).

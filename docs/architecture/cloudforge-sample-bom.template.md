# Sample Project Template

Use this template to create a standalone deployment project that consumes CloudForge, such as
[cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample). The
[`cfc-testing`](https://github.com/CloudForgeCI/cfc-core/tree/develop/cfc-testing) module in this repository is the reference implementation.

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

`cfc-testing` is not published as a library; copy from it rather than depending on it.

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

```text
your-sample/
├── pom.xml
├── cdk.json
├── deployment-context.json          # or deployment-contexts/*.json
└── src/
    ├── main/java/.../
    │   ├── app/
    │   │   ├── InteractiveDeployer.java      # optional: copy from cfc-testing
    │   │   ├── LocalDeploymentShell.java
    │   │   ├── DeploymentResultPrinter.java
    │   │   └── CloudForgeCommunitySample.java
    │   ├── launchers/
    │   │   ├── ApplicationFargateStack.java
    │   │   └── ApplicationEc2Stack.java
    │   └── plugins/                          # custom ApplicationSpec / FrameworkRules plugins
    ├── main/resources/META-INF/services/
    │   ├── com.cloudforge.core.interfaces.ApplicationSpec
    │   └── com.cloudforge.core.interfaces.FrameworkRules
    └── test/java/...
```

---

## Deploying after synthesis

`LocalDeploymentShell` in `cfc-testing` wraps `CloudForgeDeployment` for local targets:

```java
DeploymentConfig config = DeploymentConfig.fromFile("deployment-context.json");
CloudAssembly assembly = app.synth();

DeploymentResult result = LocalDeploymentShell.deploy(
    config,
    DeploymentTarget.LOCALSTACK,
    assembly,
    DeployOptions.defaults());

DeploymentResultPrinter.printOutcome(result, "LocalStack", config.applicationId);
```

Call `CloudForgeDeployment.deploy(DeploymentRequest)` from `cloudforge-api` directly if you do
not need the sample shell.

---

## Build and run

```bash
# Build CloudForge from source (skip when using published artifacts)
cd /path/to/cfc-core
mvn clean install -DskipTests

# Manage emulators (LocalStack and MiniStack both use port 4566, so run one at a time)
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform

# Run the deployer from your project
cd /path/to/your-sample
mvn package
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-context.json
```

The runtime classpath above assumes the project copies its dependencies to
`target/dependency` during `package`, as the starter POM does. In the deployer menu, option 6
deploys to MiniStack and option 8 deploys to LocalStack.

### Emulator lifecycle

`cloudforge-ministack` and `cloudforge-localstack` expose lifecycle actions through
`PlatformRuntimeProvider`. `InteractiveDeployer --platform` lists the available targets and
offers `start`, `stop`, `restart`, `status`, and `reconcile_edge`. Emulator companion
containers are managed by the target module, not by the root `docker-compose.yml`.

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
3. Use [`CraftCmsApplicationSpec`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/cms/CraftCmsApplicationSpec.java)
   as a reference.

See the [plugin documentation](../plugins/README.md) for details.

---

## Reporting issues

When filing a bug, select the owning module in the GitHub issue template. See
[CONTRIBUTING.md](../CONTRIBUTING.md#reporting-issues).

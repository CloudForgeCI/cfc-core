# CloudForge Sample — BOM & Project Template

Use this template when creating a **standalone deployment project** (like
[cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample)) or when
extracting `cfc-testing` from this monorepo.

---

## Maven BOM

Import the root BOM (`com.cloudforgeci:cfc-core`) — it pins every CloudForge module
and shared dependencies (CDK, Jackson, JUnit).

```xml
<properties>
  <java.version>21</java.version>
  <maven.compiler.source>21</maven.compiler.source>
  <maven.compiler.target>21</maven.compiler.target>
  <cloudforge.version>3.2.0</cloudforge.version>
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

Copy the full starter POM from [docs/examples/cloudforge-sample/pom.xml](../examples/cloudforge-sample/pom.xml).

---

## Module dependencies (pick what you need)

| Dependency | When to add |
|------------|-------------|
| `cloudforge-core` | Always (config, interfaces, local contracts) |
| `cloudforge-api` | Always (ApplicationSpecs, factories, `CloudForgeDeployment`) |
| `cloudforge-ministack` | MiniStack local deploy (option 6) |
| `cloudforge-localstack` | LocalStack local deploy (option 8) |
| `cloudforge-manager` | Only if building Manager panel code — **not** required to deploy Manager as an app |

**Do not** depend on `cfc-testing` from a library — it is the sample entrypoint in this repo only.

Minimal runtime set for local Jenkins:

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

## Recommended project layout

```text
your-sample/
├── pom.xml                          # BOM import + dependencies above
├── cdk.json
├── deployment-context.json          # or deployment-contexts/*.json
├── src/main/java/.../
│   ├── app/
│   │   ├── InteractiveDeployer.java # optional: copy from cfc-testing
│   │   ├── LocalDeploymentShell.java
│   │   ├── DeploymentResultPrinter.java
│   │   └── CloudForgeCommunitySample.java
│   ├── launchers/
│   │   ├── ApplicationFargateStack.java
│   │   └── ApplicationEc2Stack.java
│   └── plugins/                     # your custom ApplicationSpec plugins
│       └── META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec
└── src/test/java/...
```

Reference implementation in this repo: **`cfc-testing/`** (monorepo cloudforge-sample).

---

## Deploy flow (after CDK synth)

```java
DeploymentConfig config = /* load deployment-context.json */;
CloudAssembly assembly = app.synth();

DeploymentResult result = LocalDeploymentShell.deploy(
    config,
    DeploymentTarget.LOCALSTACK,
    assembly,
    DeployOptions.defaults());

DeploymentResultPrinter.printOutcome(result, "LocalStack", config.applicationId);
```

Or call `CloudForgeDeployment` directly from `cloudforge-api` if you do not need the sample shell.

---

## Build & run

```bash
# From monorepo (libraries must be installed first)
cd /path/to/cfc-core
mvn clean install -DskipTests

# Start an emulator (mutually exclusive on :4566) through the platform menu.
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform

# From your sample project
cd your-sample
mvn package
cdk synth
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-context.json
```

### Emulator lifecycle

`cloudforge-ministack` and `cloudforge-localstack` expose lifecycle capabilities through
`PlatformRuntimeProvider`. Run `InteractiveDeployer --platform`, choose a target, then
select `start`, `stop`, `restart`, `status`, or `reconcile_edge`. Emulator companions are
target-owned and are not root `docker-compose.yml` services.

Programmatic lifecycle (any app with `cloudforge-core` + target module on classpath):

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

// Emulator (+ StackPort + edge by default)
EmulatorLifecycle.execute(DeploymentTarget.MINISTACK, EmulatorLifecycleAction.START);
LocalEmulatorRuntimes.forTarget(DeploymentTarget.LOCALSTACK).isHealthy();

// StackPort resource browser (also auto on emulator start; target module on classpath)
StackPortLifecycle.execute(DeploymentTarget.LOCALSTACK, StackPortLifecycleAction.START);
StackPortRuntimes.forTarget(DeploymentTarget.MINISTACK).browserUrl();

// Shared nginx edge (also auto on emulator start)
EmulatorEdgeLifecycle.execute(EmulatorEdgeLifecycleAction.RECONCILE);
```

When consuming **published** artifacts from Maven Central, skip the monorepo `install` step and set `cloudforge.version` to the release on Central.

---

## Custom application plugins

1. Implement `ApplicationSpec` (and `CmsSpec` / `DatabaseSpec` if needed).
2. Register in `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`.
3. See `cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/cms/CraftCmsApplicationSpec.java`.

---

## Issue triage (contributors)

When filing bugs, select the **owning module** in the GitHub issue template. See
[CONTRIBUTING.md](../CONTRIBUTING.md#issue-triage-and-module-labels).

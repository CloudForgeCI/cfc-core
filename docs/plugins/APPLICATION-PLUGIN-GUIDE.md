# Application Plugin Guide

An application plugin tells CloudForge how to run one application on AWS. It implements
`com.cloudforge.core.interfaces.ApplicationSpec` and is discovered through Java
`ServiceLoader`. CloudForge supplies the VPC, load balancer, storage, logging, and security
profile wiring; the plugin supplies the application-specific parts:

- container image, port, data paths, and environment for Fargate
- EC2 user data for installing and starting the application
- optional OIDC integration, database requirements, and PHP/CMS settings

Test every runtime you declare. Implementing the interface does not by itself validate an
application for production use.

---

## Quick start

### 1. Create a Maven project

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>vault-application</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>25</maven.compiler.release>
    </properties>

    <dependencies>
        <!-- Interfaces and annotations -->
        <dependency>
            <groupId>com.cloudforgeci</groupId>
            <artifactId>cloudforge-core</artifactId>
            <version>3.2.16</version>
            <scope>provided</scope>
        </dependency>
        <!-- Only needed if the plugin or its tests use cloudforge-api classes -->
        <dependency>
            <groupId>com.cloudforgeci</groupId>
            <artifactId>cloudforge-api</artifactId>
            <version>3.2.16</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

Replace `3.2.16` with the CloudForge release you target. CloudForge modules are compiled for
Java 25, so plugins must build with Java 25 or later.

### 2. Implement `ApplicationSpec`

The example below is a trimmed version of the built-in
[`VaultApplicationSpec`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/application/secrets/VaultApplicationSpec.java).
It uses the ID `my-vault` because application IDs must be unique: when two registered specs
return the same `applicationId()`, `ApplicationLoader` keeps the first one and logs a warning.

```java
package com.example.applications;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationPlugin(
    value = "my-vault",
    category = "secrets",
    displayName = "Vault (custom)",
    description = "HashiCorp Vault with file storage",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false
)
public class MyVaultApplicationSpec implements ApplicationSpec {

    // ---- Identity ----

    @Override
    public String applicationId() {
        return "my-vault";
    }

    // ---- Container (Fargate) ----

    @Override
    public String defaultContainerImage() {
        return "hashicorp/vault:latest";
    }

    @Override
    public int applicationPort() {
        return 8200;
    }

    @Override
    public String containerDataPath() {
        return "/vault/file";
    }

    @Override
    public String efsDataPath() {
        return "/vault";
    }

    @Override
    public String volumeName() {
        return "vaultData";
    }

    @Override
    public String containerUser() {
        return "100:1000";
    }

    @Override
    public String efsPermissions() {
        return "750";
    }

    @Override
    public String healthCheckPath() {
        // Report healthy while Vault is uninitialized or sealed so the target stays registered.
        return "/v1/sys/health?standbyok=true&uninitcode=200&sealedcode=200";
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        Map<String, String> env = new HashMap<>();
        if (fqdn != null && !fqdn.isBlank()) {
            env.put("VAULT_API_ADDR", (sslEnabled ? "https://" : "http://") + fqdn);
        }
        env.put("SKIP_SETCAP", "true");
        return env;
    }

    // ---- EC2 ----

    @Override
    public String ebsDeviceName() {
        return "/dev/xvdh";
    }

    @Override
    public String ec2DataPath() {
        return "/opt/vault/data";
    }

    @Override
    public List<String> ec2LogPaths() {
        return List.of("/var/log/vault/vault.log", "/var/log/userdata.log");
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        builder.addCommands(
            "dnf -y install dnf-plugins-core",
            "dnf config-manager --add-repo https://rpm.releases.hashicorp.com/AmazonLinux/hashicorp.repo",
            "dnf -y install vault"
        );

        builder.installCloudWatchAgent(
            "/aws/" + context.stackName() + "/" + context.runtimeType() + "/" + context.securityProfile(),
            ec2LogPaths());

        String[] ids = containerUser().split(":");
        if (context.hasEfs()) {
            builder.mountEfs(context.efsId().orElseThrow(), context.accessPointId().orElseThrow(),
                ec2DataPath(), ids[0], ids[1]);
        } else {
            builder.mountEbs(ebsDeviceName(), ec2DataPath(), ids[0], ids[1]);
        }

        builder.addCommands(
            "mkdir -p /etc/vault.d /var/log/vault",
            "cat > /etc/vault.d/vault.hcl <<'EOF'",
            "ui = true",
            "storage \"file\" { path = \"" + ec2DataPath() + "\" }",
            "listener \"tcp\" {",
            "  address     = \"0.0.0.0:8200\"",
            "  tls_disable = 1",
            "}",
            "EOF",
            "chown -R vault:vault /etc/vault.d /var/log/vault " + ec2DataPath(),
            "systemctl enable --now vault"
        );
    }
}
```

`tls_disable = 1` is appropriate only because the load balancer terminates TLS in this
example; adjust the listener for your network design.

### 3. Register the class

Create `src/main/resources/META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`:

```
com.example.applications.MyVaultApplicationSpec
```

The class must have a public no-argument constructor.

### 4. Build

```bash
mvn clean package
```

---

## Deploying a plugin application

Add the plugin JAR as a dependency of the deployment project (for example, a project based on
the [sample BOM template](../architecture/cloudforge-sample-bom.template.md)). `ApplicationLoader`
then finds it by ID, and it appears in the `InteractiveDeployer` application list.

Select it in `deployment-context.json`:

```json
{
  "stackName": "vault-dev",
  "applicationId": "my-vault",
  "runtime": "FARGATE",
  "topology": "application-service",
  "securityProfile": "dev",
  "domain": "example.com",
  "subdomain": "vault",
  "enableSsl": true
}
```

To build a stack in your own CDK code, use the `ApplicationFactory` helpers. They start the
`SystemContext` and create the infrastructure from the deployment context stored under the
`cfc` context key:

```java
import com.cloudforgeci.api.compute.ApplicationFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import com.example.applications.MyVaultApplicationSpec;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class VaultStack extends Stack {
    public VaultStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);
        DeploymentContext cfc = DeploymentContext.from(scope);
        ApplicationFactory.createFargate(this, id, cfc, new MyVaultApplicationSpec());
        // or: ApplicationFactory.createEc2(this, id, cfc, new MyVaultApplicationSpec());
    }
}
```

The sample launchers in
[`cfc-testing/src/main/java/com/cloudforgeci/samples/launchers/`](https://github.com/CloudForgeCI/cfc-core/tree/develop/cfc-testing/src/main/java/com/cloudforgeci/samples/launchers)
show the complete pattern, including tags and outputs.

---

## `ApplicationSpec` reference

### Required methods

| Method | Purpose | Example |
|--------|---------|---------|
| `applicationId()` | Unique ID used for lookup and resource naming | `"jenkins"` |
| `defaultContainerImage()` | Container image for Fargate | `"jenkins/jenkins:lts"` |
| `applicationPort()` | Port the application listens on | `8080` |
| `containerDataPath()` | Persistent data path inside the container | `"/var/jenkins_home"` |
| `efsDataPath()` | Directory on EFS for the access point | `"/jenkins"` |
| `volumeName()` | Task-definition volume name | `"jenkinsHome"` |
| `containerUser()` | `uid:gid` that owns the data | `"1000:1000"` |
| `efsPermissions()` | Access point directory permissions | `"750"` |
| `ebsDeviceName()` | EBS device used on EC2 when EFS is absent | `"/dev/xvdh"` |
| `ec2DataPath()` | Data path on EC2 | `"/var/lib/jenkins"` |
| `ec2LogPaths()` | Files the CloudWatch agent ships | `List.of("/var/log/app.log")` |
| `configureUserData(UserDataBuilder, Ec2Context)` | EC2 installation and startup | see above |

### Common optional methods

| Method | Default | Purpose |
|--------|---------|---------|
| `healthCheckPath()` | `"/"` | Target group health check path |
| `containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode)` | empty map | Container environment |
| `cpuArchitecture()` | `X86_64` | Container CPU architecture |
| `supportsOidcIntegration()` | `false` | Whether the application can use OIDC |
| `getOidcIntegration()` | `null` | Application-level OIDC handler |
| `getSupportedAuthModes()` | derived from the two methods above | Allowed `authMode` values |
| `protectedPaths()` / `publicPaths()` | empty | Path rules for `alb-oidc` |
| `optionalPorts()` | empty | Additional ports gated by config keys |
| `sidecarContainers()` | empty | Extra containers in the task |
| `defaultContainerCommand()` / `defaultContainerEntrypoint()` | image default | Container command overrides |
| `defaultHealthCheckGracePeriod()` | `300` | Health check grace period in seconds |

Metadata methods such as `category()`, `displayName()`, `description()`, `defaultCpu()`,
`defaultMemory()`, `defaultInstanceType()`, `supportsFargate()`, and `supportsEc2()` read
`@ApplicationPlugin` by default, so they usually do not need to be overridden. See
[`ApplicationSpec.java`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/interfaces/ApplicationSpec.java)
for every method.

### `@ApplicationPlugin` attributes

| Attribute | Default | Notes |
|-----------|---------|-------|
| `value` | required | Application ID |
| `category` | required | Grouping in the application list, e.g. `cicd`, `monitoring`, `secrets` |
| `displayName` | `""` | Falls back to the capitalized ID |
| `description` | `""` | |
| `defaultCpu` | `1024` | Fargate CPU units |
| `defaultMemory` | `2048` | Fargate memory (MiB) |
| `defaultInstanceType` | `"t3.small"` | EC2 instance type |
| `supportsFargate` | `true` | |
| `supportsEc2` | `true` | |
| `supportsOidc` | `false` | |
| `requiresDatabase` | `false` | |
| `supportsDatabase` | `false` | |

---

## Helper interfaces

### `UserDataBuilder`

| Method | Purpose |
|--------|---------|
| `addSystemUpdate()` | Update OS packages |
| `addCommands(String...)` / `addCommand(String)` | Append shell commands |
| `installCloudWatchAgent(String logGroupName, List<String> logFilePaths)` | Install and configure the CloudWatch agent |
| `mountEfs(String efsId, String accessPointId, String mountPath, String uid, String gid)` | Mount an EFS access point |
| `mountEbs(String deviceName, String mountPath, String uid, String gid)` | Format and mount an EBS volume |

### `Ec2Context`

| Method | Returns |
|--------|---------|
| `stackName()` | `String` |
| `runtimeType()` | `String` |
| `securityProfile()` | `String` |
| `hasEfs()` | `boolean` |
| `efsId()` / `accessPointId()` | `Optional<String>` |
| `authMode()` | `String`, default `"none"` |
| `fqdn()` | `String`, may be `null` |
| `sslEnabled()` | `boolean` |
| `autoAdminPasswordSecretArn()` | `String`, may be `null` |

---

## OIDC

`ApplicationSpec` supports three `authMode` values:

- `application-oidc`: the application performs the OIDC flow. Requires `getOidcIntegration()`
  to return an implementation.
- `alb-oidc`: the load balancer authenticates requests before they reach the application.
- `none`: no CloudForge-managed authentication.

By default, `getSupportedAuthModes()` returns `application-oidc`, `alb-oidc`, and `none` when
an integration is present; `alb-oidc` and `none` when `supportsOidcIntegration()` is `true`
without an integration; and only `none` otherwise.

An `OidcIntegration` must implement `isSupported()`, `getIntegrationMethod()`,
`getEnvironmentVariables(OidcConfiguration)`, and
`getUserDataCommands(OidcConfiguration, Ec2Context)`. The built-in integrations in
[`cloudforge-core/src/main/java/com/cloudforge/core/oidc/`](https://github.com/CloudForgeCI/cfc-core/tree/develop/cloudforge-core/src/main/java/com/cloudforge/core/oidc)
(for example `GitLabOidcIntegration` and `GrafanaOidcIntegration`) are working references.
See the [OIDC guide](../applications/OIDC.md) for deployment settings.

---

## Databases: `DatabaseSpec`

Implement `DatabaseSpec` alongside `ApplicationSpec` to request a managed RDS database. The only
required method is `databaseRequirement()`:

```java
@Override
public DatabaseRequirement databaseRequirement() {
    return DatabaseRequirement.required("postgres", "16")
        .withInstanceClass("db.t3.micro")
        .withStorage(20)
        .withDatabaseName("appdb");
}
```

`DatabaseRequirement.optional(...)` and `DatabaseRequirement.none()` are also available.
Optional methods cover init scripts, parameters, backup retention, and read replicas. When the
requirement is `REQUIRED`, `InteractiveDeployer` enables `provisionDatabase` automatically.

---

## PHP and CMS applications: `CmsSpec`

`CmsSpec` extends `ApplicationSpec` with PHP runtime, media storage, CDN, object cache, and cron
settings. CMS applications deploy with the `cms-service` topology. In addition to the
`ApplicationSpec` required methods, a `CmsSpec` must implement:

- `phpVersion()`
- `requiredPhpExtensions()`
- `mediaUploadPath()`

Annotate the class with `@CmsPlugin` instead of `@ApplicationPlugin`. Its attributes include
`value`, `category` (default `"cms"`), `phpVersion`, `supportsOidc`, `oidcMethod`,
`requiresDatabase` (default `true`), `supportedDatabases`, `supportsS3Media`,
`supportsObjectCache`, `supportsMultisite`, `websiteUrl`, and `defaultImage`; see
[`CmsPlugin.java`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/annotation/CmsPlugin.java).

CMS plugins register in the same `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`
file; `CmsLoader` selects the entries that implement `CmsSpec`.

[`CraftCmsApplicationSpec`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/cms/CraftCmsApplicationSpec.java)
is a complete external `CmsSpec` + `DatabaseSpec` plugin. The
[CMS guide](../applications/CMS.md) covers CMS deployment settings.

---

## Guidelines

- Prefer official images, and pin a tag for production use.
- Do not put credentials in environment variables or user data. Reference Secrets Manager
  instead; `ApplicationSpec` has hooks such as `databasePasswordEnvVar()` and
  `oidcClientSecretEnvVar()` for secret-backed values.
- Check `context.hasEfs()` in `configureUserData` and mount EFS or EBS accordingly.
- Write user-data progress to a log file listed in `ec2LogPaths()` so it reaches CloudWatch.

---

## Testing

Unit-test the spec's values directly:

```java
@Test
void environmentUsesHttpsWhenSslEnabled() {
    ApplicationSpec spec = new MyVaultApplicationSpec();

    Map<String, String> env = spec.containerEnvironmentVariables("vault.example.com", true, "none");

    assertEquals("https://vault.example.com", env.get("VAULT_API_ADDR"));
}

@Test
void isDiscoverable() {
    boolean found = ServiceLoader.load(ApplicationSpec.class).stream()
        .anyMatch(p -> p.type() == MyVaultApplicationSpec.class);
    assertTrue(found);
}
```

For synthesis tests, set a `cfc` context on the app and call `ApplicationFactory.createFargate`
or `createEc2`, then assert on `Template.fromStack(stack)`. The tests under
`cfc-testing/src/test/java/com/cloudforgeci/samples/plugins/` and
`cloudforge-api/src/test/java/com/cloudforgeci/api/application/` are working examples.

---

## Built-in applications

`cloudforge-api` registers 35 application specs. The
[application catalog](../applications/README.md) and the
[plugin ecosystem overview](PLUGIN-ECOSYSTEM.md) list them. Their sources under
[`cloudforge-api/src/main/java/com/cloudforgeci/api/application/`](https://github.com/CloudForgeCI/cfc-core/tree/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/application)
are the most complete reference for writing a new plugin.

See [Plugin System](PLUGIN-SYSTEM.md) for discovery and registration details.

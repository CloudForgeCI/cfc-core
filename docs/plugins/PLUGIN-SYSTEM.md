# Plugin System

CloudForge has two plugin types. Both are plain Java classes discovered through
`java.util.ServiceLoader`, so a plugin JAR only needs to be on the classpath of the process
that runs CDK synthesis.

| Plugin type | Interface | Annotation | Service file |
|-------------|-----------|------------|--------------|
| Application | `ApplicationSpec` (or `CmsSpec`, optionally with `DatabaseSpec`) | `@ApplicationPlugin` or `@CmsPlugin` | `META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec` |
| Compliance framework | `FrameworkRules<SystemContext>` | `@ComplianceFramework` (required) | `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules` |

Interfaces and annotations live in `cloudforge-core`
(`com.cloudforge.core.interfaces`, `com.cloudforge.core.annotation`). `SystemContext` and
`ComplianceRule` live in `cloudforge-api`.

---

## Application plugins

An application plugin describes how to run one application on Fargate and EC2: image, port,
data paths, environment, EC2 user data, and optional OIDC and database requirements.

```java
@ApplicationPlugin(value = "my-vault", category = "secrets", displayName = "Vault (custom)")
public class MyVaultApplicationSpec implements ApplicationSpec {
    @Override public String applicationId()         { return "my-vault"; }
    @Override public String defaultContainerImage() { return "hashicorp/vault:latest"; }
    @Override public int applicationPort()          { return 8200; }
    // ... remaining required methods
}
```

Full guide: [APPLICATION-PLUGIN-GUIDE.md](APPLICATION-PLUGIN-GUIDE.md)

---

## Compliance framework plugins

A compliance plugin registers CDK validations that run during synthesis and fail it when
rules are violated.

```java
@ComplianceFramework(
    value = "ACME-SECURITY",
    priority = 60,
    displayName = "Acme Internal Security Baseline"
)
public class AcmeRules implements FrameworkRules<SystemContext> {
    @Override
    public void install(SystemContext ctx) {
        ctx.getNode().addValidation(() -> {
            List<String> errors = new ArrayList<>();
            // add an error string for each violated rule
            return errors;
        });
    }
}
```

Full guide: [COMPLIANCE-PLUGIN-GUIDE.md](COMPLIANCE-PLUGIN-GUIDE.md)

---

## Discovery

```
your-plugin.jar
├── META-INF/services/
│   ├── com.cloudforge.core.interfaces.ApplicationSpec
│   └── com.cloudforge.core.interfaces.FrameworkRules
└── com/example/
    ├── MyVaultApplicationSpec.class
    └── AcmeRules.class
```

Each service file lists fully qualified class names, one per line. Every listed class needs a
public no-argument constructor.

- **Applications:** `ApplicationLoader.discover()` returns specs keyed by `applicationId()`;
  `CmsLoader` filters the same registrations to `CmsSpec` implementations. If two specs share
  an ID, the first registration wins.
- **Compliance:** `FrameworkLoader.discover()` returns frameworks sorted by priority.
  `SecurityRules.install(ctx)` installs the always-load frameworks and those named in
  `complianceFrameworks`, provided `auditManagerEnabled` is `true`.

---

## Compliance priorities

Lower priorities install first.

| Priority | Built-in frameworks |
|----------|---------------------|
| -10 | `KeyManagement` (always load) |
| -5 | `DatabaseSecurity`, `AdvancedMonitoring` (always load) |
| 0 | `ThreatProtection`, `IncidentResponse`, `ComputeSecurity`, `LambdaSecurity`, `CdnApiSecurity`, `ElbSecurity`, `MessagingSecurity`, `IamSecurity` (always load) |
| 10–50 | `HIPAA` (10), `HIPAA-Organizational` (15), `PCI-DSS` (20), `GDPR` (30), `GDPR-Organizational` (35), `SOC2` (40), `ISO-27001` (50) |

Custom frameworks default to priority 100. Use a value above 50 to install after the built-in
frameworks.

---

## Built-in plugins

`cloudforge-api` registers 35 application specs (16 `ApplicationSpec`, 19 `CmsSpec`) and 18
compliance frameworks. [PLUGIN-ECOSYSTEM.md](PLUGIN-ECOSYSTEM.md) lists them.

---

## Development workflow

1. Create a Maven project that depends on `cloudforge-core` (and `cloudforge-api` for
   compliance plugins) with `provided` scope:

   ```xml
   <dependency>
       <groupId>com.cloudforgeci</groupId>
       <artifactId>cloudforge-core</artifactId>
       <version>3.2.16</version>
       <scope>provided</scope>
   </dependency>
   ```

   CloudForge modules are compiled for Java 25.
2. Implement the interface and add the annotation.
3. Add the `META-INF/services/` file.
4. Build and test with `mvn clean package`.
5. Publish the JAR to a Maven repository, or install it locally, and add it as a dependency of
   the deployment project.

The [`cfc-testing`](https://github.com/CloudForgeCI/cfc-core/tree/develop/cfc-testing) module contains working sample plugins:
`CraftCmsApplicationSpec`, `CustomSecurityPolicyRules`, and `OpenSourceSecurityPolicyRules`.

---

## Reference

- [`cloudforge-core` interfaces](https://github.com/CloudForgeCI/cfc-core/tree/develop/cloudforge-core/src/main/java/com/cloudforge/core/interfaces)
- [`cloudforge-core` annotations](https://github.com/CloudForgeCI/cfc-core/tree/develop/cloudforge-core/src/main/java/com/cloudforge/core/annotation)
- [Application plugin guide](APPLICATION-PLUGIN-GUIDE.md)
- [Compliance plugin guide](COMPLIANCE-PLUGIN-GUIDE.md)
- Issues: https://github.com/CloudForgeCI/cfc-core/issues

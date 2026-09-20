# Compliance Framework Plugin Guide

Compliance framework plugins add validation rules to a CloudForge stack without modifying
CloudForge itself. A plugin implements `FrameworkRules<SystemContext>`, carries a
`@ComplianceFramework` annotation, and is discovered through Java `ServiceLoader`.

---

## Quick start

### 1. Implement `FrameworkRules<SystemContext>`

```java
package com.example.compliance;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.rules.ComplianceRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ComplianceFramework(
    value = "ACME-SECURITY",   // framework ID
    priority = 60,             // lower values install first
    displayName = "Acme Internal Security Baseline",
    description = "Organization-specific infrastructure controls"
)
public class AcmeRules implements FrameworkRules<SystemContext> {

    @Override
    public void install(SystemContext ctx) {
        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // The security profile configuration is available once validation runs.
            var config = ctx.securityProfileConfig.get().orElseThrow();

            if (config.isGuardDutyEnabled()) {
                rules.add(ComplianceRule.pass("ACME-AC-2", "GuardDuty enabled"));
            } else {
                rules.add(ComplianceRule.fail(
                    "ACME-AC-2",
                    "GuardDuty required for account monitoring",
                    "Enable AWS GuardDuty (guardDutyEnabled = true)"));
            }

            // Return failures as error strings; an empty list means the validation passed.
            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();
        });
    }
}
```

### 2. Register the class

Create `src/main/resources/META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`:

```
com.example.compliance.AcmeRules
```

The class must have a public no-argument constructor.

### 3. Add the plugin to the deployment classpath

Package the plugin as a JAR and add it as a dependency of the project that runs CDK synthesis.
See [Distribution](#distribution).

---

## How frameworks are loaded

`SecurityRules.install(ctx)` runs during synthesis and does the following:

1. Sets `ctx.securityProfileConfig` from the security profile (`DEV`, `STAGING`, `PRODUCTION`).
2. For `PRODUCTION` deployments with `complianceFrameworks` set, adds the matching cdk-nag
   rule packs.
3. Returns without installing any `FrameworkRules` unless `auditManagerEnabled` is `true`.
4. Calls `FrameworkLoader.discover()`, which loads every registered `FrameworkRules`
   implementation through `ServiceLoader` and sorts by `priority()`, then by `frameworkId()`.
5. Installs a framework when `alwaysLoad()` is `true` or when its ID appears in
   `complianceFrameworks`. The ID comparison is case-insensitive.

Validations registered with `ctx.getNode().addValidation(...)` run when the stack is
synthesized. Any error string returned fails synthesis. Handling advisory mode is the
framework's responsibility (see [Compliance modes](#compliance-modes)).

### Enabling a framework

```json
{
  "securityProfile": "PRODUCTION",
  "complianceFrameworks": "hipaa,soc2",
  "auditManagerEnabled": true
}
```

> **Limitation:** `DeploymentConfig` parses `complianceFrameworks` into the
> `ComplianceFrameworkType` enum, which accepts only `soc2`, `pci-dss`, `hipaa`, and `gdpr`.
> Any other ID, including `ISO-27001`, `HIPAA-Organizational`, `GDPR-Organizational`, and
> custom plugin IDs, is rejected with `IllegalArgumentException` when the deployment context
> is loaded. Until that restriction is lifted, a custom framework runs only if it sets
> `alwaysLoad = true`, or if your own code installs it directly.

---

## `@ComplianceFramework` attributes

| Attribute | Type | Default | Purpose |
|-----------|------|---------|---------|
| `value` | `String` | required | Framework ID matched against `complianceFrameworks` |
| `priority` | `int` | `100` | Install order; lower values install first |
| `alwaysLoad` | `boolean` | `false` | Install regardless of `complianceFrameworks` |
| `displayName` | `String` | `""` (falls back to `value`) | Name used in logs |
| `description` | `String` | `""` | Free-text description |

`FrameworkRules` exposes these values through default methods (`frameworkId()`,
`priority()`, `alwaysLoad()`, `displayName()`, `description()`). The only method you must
implement is `install(T ctx)`.

### Framework-required configuration

Override `getRequiredConfiguration()` to supply deployment-context defaults that apply when
the framework is enabled. Explicit user configuration still takes precedence; security-profile
defaults apply last.

```java
@Override
public Map<String, Object> getRequiredConfiguration() {
    return Map.of(
        "logRetentionDays", 2190,
        "guardDutyEnabled", true
    );
}
```

The keys documented on `FrameworkRules#getRequiredConfiguration()` are `logRetentionDays`,
`guardDutyEnabled`, `macieEnabled`, `securityHubEnabled`, `inspectorEnabled`,
`cloudTrailEnabled`, `wafEnabled`, and `albAccessLogging`.

---

## Priorities

Built-in frameworks registered in `cloudforge-api`:

| Priority | Framework IDs | Always load |
|----------|---------------|-------------|
| -10 | `KeyManagement` | yes |
| -5 | `DatabaseSecurity`, `AdvancedMonitoring` | yes |
| 0 | `ThreatProtection`, `IncidentResponse`, `ComputeSecurity`, `LambdaSecurity`, `CdnApiSecurity`, `ElbSecurity`, `MessagingSecurity`, `IamSecurity` | yes |
| 10 | `HIPAA` | no |
| 15 | `HIPAA-Organizational` | no |
| 20 | `PCI-DSS` | no |
| 30 | `GDPR` | no |
| 35 | `GDPR-Organizational` | no |
| 40 | `SOC2` | no |
| 50 | `ISO-27001` | no |

Use a priority above 50 for organization-specific frameworks so they install after the
built-in ones. The sample plugins in `cfc-testing` use 60 and 65.

---

## Writing rules

### `ComplianceRule`

`com.cloudforgeci.api.core.rules.ComplianceRule` is a record:

```java
public record ComplianceRule(
    String ruleId,
    String description,
    Optional<String> configRuleId,   // related AWS Config rule, if any
    boolean passed,
    Optional<String> errorMessage
)
```

Factory methods:

```java
ComplianceRule.pass(ruleId, description);
ComplianceRule.pass(ruleId, description, configRuleId);
ComplianceRule.fail(ruleId, description, errorMessage);
ComplianceRule.fail(ruleId, description, configRuleId, errorMessage);
```

`toErrorString()` returns an `Optional<String>` that is empty for passing rules.

### Security profile configuration

Inside a validation, read the resolved profile settings from the context:

```java
var config = ctx.securityProfileConfig.get().orElseThrow();
```

Frequently used methods of
[`SecurityProfileConfiguration`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/interfaces/SecurityProfileConfiguration.java):

| Method | Setting |
|--------|---------|
| `isSecurityMonitoringEnabled()` | Security monitoring and alerting |
| `isCloudTrailEnabled()` | CloudTrail audit logging |
| `isGuardDutyEnabled()` | GuardDuty threat detection |
| `isAwsConfigEnabled()` | AWS Config recording |
| `isEbsEncryptionEnabled()` | EBS encryption |
| `isEfsEncryptionAtRestEnabled()` | EFS encryption at rest |
| `isEfsEncryptionInTransitEnabled()` | EFS encryption in transit |
| `isWafEnabled()` | AWS WAF |
| `isFlowLogsEnabled()` | VPC Flow Logs |
| `isMultiAzEnforced()` | Multi-AZ deployment |
| `isAutomatedBackupEnabled()` | Automated backups |
| `isAlbAccessLoggingEnabled()` | ALB access logs |

The interface defines many more settings; see the source for the full list.

### Other context fields

`SystemContext` exposes the deployment inputs as public fields, including `security`
(`SecurityProfile`), `runtime` (`RuntimeType`), `topology` (`TopologyType`), and `cfc`
(`DeploymentContext`).

```java
if (ctx.security != SecurityProfile.PRODUCTION) {
    return;   // enforce only for production deployments
}
```

### Compliance modes

`ctx.cfc.complianceMode()` returns a `ComplianceMode` (`ENFORCE`, `ADVISORY`, or `DISABLED`).
When `complianceMode` is not set, it defaults to `ENFORCE` for `PRODUCTION` and `ADVISORY` for
`DEV` and `STAGING`.

```java
ComplianceMode mode = ctx.cfc.complianceMode();

ctx.getNode().addValidation(() -> {
    List<String> errors = collectErrors(ctx);
    if (mode != ComplianceMode.ENFORCE) {
        errors.forEach(LOG::warning);
        return List.of();   // report without failing synthesis
    }
    return errors;
});
```

[`Iso27001Rules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/Iso27001Rules.java)
shows profile filtering, compliance-mode handling, and AWS Config rule mapping in a complete
framework.

### Guidelines

- Use rule IDs that trace to a specific control, such as `ACME-AC-2.1`.
- Write failure messages that name the setting to change.
- Read configuration inside the validation lambda, not in `install()`, so values set later
  during synthesis are visible.

---

## Testing

Unit tests can start a `SystemContext` on a test stack, set the profile configuration, and
synthesize:

```java
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

@Test
void acmeRulesPassWithGuardDuty() {
    App app = new App();
    Stack stack = new Stack(app, "TestStack");

    Map<String, Object> cfcContext = new HashMap<>();
    cfcContext.put("stackName", "TestStack");
    cfcContext.put("securityProfile", "PRODUCTION");
    cfcContext.put("guardDutyEnabled", true);
    stack.getNode().setContext("cfc", cfcContext);

    DeploymentContext cfc = DeploymentContext.from(stack);
    SystemContext ctx = SystemContext.start(
        stack,
        TopologyType.APPLICATION_SERVICE,
        RuntimeType.FARGATE,
        SecurityProfile.PRODUCTION,
        IAMProfile.MINIMAL,
        cfc);
    ctx.securityProfileConfig.set(new ProductionSecurityProfileConfiguration(cfc));

    new AcmeRules().install(ctx);

    assertDoesNotThrow(() -> Template.fromStack(stack));
}
```

Also test the metadata: that the class carries `@ComplianceFramework` with the expected ID and
priority, and that `ServiceLoader.load(FrameworkRules.class)` finds it. The sample tests under
`cfc-testing/src/test/java/com/cloudforgeci/samples/plugins/compliance/` do this.

---

## Distribution

### Maven dependency

Build against the CloudForge modules with `provided` scope:

```xml
<project>
    <groupId>com.example</groupId>
    <artifactId>cloudforge-acme-plugin</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>com.cloudforgeci</groupId>
            <artifactId>cloudforge-core</artifactId>
            <version>3.2.16</version>
            <scope>provided</scope>
        </dependency>
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
Java 25.

Consumers then add your artifact to the project that runs synthesis:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>cloudforge-acme-plugin</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Local JAR

To use a JAR that is not published to a repository, install it into the local Maven repository:

```bash
mvn install:install-file \
  -Dfile=cloudforge-acme-plugin-1.0.0.jar \
  -DgroupId=com.example \
  -DartifactId=cloudforge-acme-plugin \
  -Dversion=1.0.0 \
  -Dpackaging=jar
```

---

## Troubleshooting

### Framework is not discovered

Confirm the service file is packaged:

```bash
jar tf cloudforge-acme-plugin-1.0.0.jar | grep META-INF/services
# META-INF/services/com.cloudforge.core.interfaces.FrameworkRules
```

`FrameworkLoader` logs each discovered framework at `INFO`
(`Discovered framework via ServiceLoader: ...`). Skipped frameworks are logged at `FINE`:

```java
Logger.getLogger("com.cloudforgeci.api.core.rules").setLevel(Level.FINE);
```

### Framework is discovered but not installed

- `auditManagerEnabled` must be `true`; otherwise no `FrameworkRules` are installed.
- A conditional framework's ID must appear in `complianceFrameworks`, subject to the
  [limitation](#enabling-a-framework) on accepted IDs.
- The class must be annotated with `@ComplianceFramework`; `frameworkId()` throws
  `IllegalStateException` without it.

---

## References

- Sample plugins: [`CustomSecurityPolicyRules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/CustomSecurityPolicyRules.java),
  [`OpenSourceSecurityPolicyRules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/OpenSourceSecurityPolicyRules.java)
- Example framework: [`Iso27001Rules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/Iso27001Rules.java)
- Interface: [`FrameworkRules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/interfaces/FrameworkRules.java)
- Annotation: [`ComplianceFramework`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-core/src/main/java/com/cloudforge/core/annotation/ComplianceFramework.java)
- Loader: [`FrameworkLoader`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/FrameworkLoader.java),
  [`SecurityRules`](https://github.com/CloudForgeCI/cfc-core/blob/develop/cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/SecurityRules.java)

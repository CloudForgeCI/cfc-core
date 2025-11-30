# CloudForge Compliance Framework Plugin Development Guide

## Overview

CloudForge 3.0.0 introduces a plugin architecture for compliance frameworks, enabling external contributors to add new compliance validators without modifying core code. This guide explains how to create, package, and distribute compliance framework plugins.

---

## Quick Start - Create a New Framework in 5 Minutes

### Step 1: Implement `FrameworkRules<SystemContext>`

```java
package com.example.compliance;

import com.cloudforge.core.annotation.ComplianceFramework;
import com.cloudforge.core.interfaces.FrameworkRules;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.rules.ComplianceRule;

import java.util.ArrayList;
import java.util.List;

@ComplianceFramework(
    value = "FEDRAMP",  // Matches config: "complianceFrameworks": "FEDRAMP"
    priority = 50,       // Load order (lower = earlier)
    displayName = "FedRAMP Moderate Baseline",
    description = "Federal Risk and Authorization Management Program compliance"
)
public class FedRampRules implements FrameworkRules<SystemContext> {

    @Override
    public void install(SystemContext ctx) {
        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Add your validation rules
            var config = ctx.securityProfileConfig.get().orElseThrow();

            if (!config.isGuardDutyEnabled()) {
                rules.add(ComplianceRule.fail(
                    "FEDRAMP-AC-2",
                    "GuardDuty required for account management (FedRAMP AC-2)",
                    "Enable AWS GuardDuty"
                ));
            } else {
                rules.add(ComplianceRule.pass(
                    "FEDRAMP-AC-2",
                    "GuardDuty enabled (FedRAMP AC-2)"
                ));
            }

            // Return failed rules
            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(java.util.Optional::stream)
                .toList();
        });
    }
}
```

### Step 2: Package as JAR

```bash
mvn clean package
```

### Step 3: Use Your Framework

Add the JAR to your CloudForge project classpath, then:

```json
{
  "cfc": {
    "complianceFrameworks": "FEDRAMP",
    "auditManagerEnabled": true
  }
}
```

That's it! CloudForge will automatically discover and load your framework.

---

## Architecture Overview

### Version Evolution

| Version | Architecture | External Plugins | Backward Compat |
|---------|-------------|------------------|-----------------|
| **v3.0.0** | Hardcoded static methods | ❌ No | N/A |
| **v3.0.0** | Auto-discovery + static adapters | ✅ Yes (ServiceLoader) | ✅ Full |
| **future versions** | Pure instance-based | ✅ Yes (ServiceLoader) | ⚠️  Static methods deprecated |

### Plugin Loading Process

```
┌─────────────────────────────────────────────────────────┐
│ 1. SecurityRules.install(ctx)                           │
│    - Triggered during CDK synthesis                     │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│ 2. FrameworkLoader.discover()                           │
│    - Scans classpath for @ComplianceFramework          │
│    - Loads via ServiceLoader + built-in registry       │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│ 3. Sort by priority (low → high)                        │
│    - Cross-framework rules: -10 to 0                    │
│    - Core frameworks: 10-20                             │
│    - External frameworks: 50+                           │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│ 4. Install frameworks conditionally                     │
│    - if framework.alwaysLoad() → install                │
│    - if "FRAMEWORK" in complianceFrameworks → install   │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│ 5. CDK Validation executes rules                        │
│    - ENFORCE mode → fails synthesis on violations       │
│    - ADVISORY mode → logs warnings only                 │
└─────────────────────────────────────────────────────────┘
```

---

## Plugin Development Patterns

### Pattern 1: Instance-Based Framework (Recommended for v3.0.0+)

This is the **recommended pattern** for new frameworks. It's the cleanest, most testable, and future-proof approach.

```java
@ComplianceFramework(value = "ISO-27001", priority = 50)
public class Iso27001Rules implements FrameworkRules<SystemContext> {
    private static final Logger LOG = Logger.getLogger(Iso27001Rules.class.getName());

    @Override
    public void install(SystemContext ctx) {
        // Security profile filtering
        if (ctx.security != SecurityProfile.PRODUCTION &&
            ctx.security != SecurityProfile.STAGING) {
            LOG.info("ISO 27001 enforced for PRODUCTION/STAGING only");
            return;
        }

        // Compliance mode handling
        ComplianceMode mode = ComplianceMode.fromString(
            ctx.cfc.complianceMode(),
            ComplianceMode.defaultForProfile(ctx.security)
        );

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            rules.addAll(validateAccessControl(ctx));
            rules.addAll(validateCryptography(ctx));
            rules.addAll(validateLogging(ctx));

            List<String> errors = rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();

            if (!errors.isEmpty() && mode == ComplianceMode.ENFORCE) {
                return errors;  // Block synthesis
            }

            return List.of();  // Pass
        });
    }

    private List<ComplianceRule> validateAccessControl(SystemContext ctx) {
        // ... validation logic
    }
}
```

**Benefits:**
- ✅ Clean instance-based design
- ✅ Easy to test (can mock SystemContext)
- ✅ No static state
- ✅ Future-proof for future versions

---

### Pattern 2: Static Method Framework (v3.0.0 Legacy, Still Supported)

This pattern is automatically wrapped by `FrameworkLoader` for backward compatibility.

```java
public final class HipaaRules {
    private HipaaRules() {}

    public static void install(SystemContext ctx) {
        // ... validation logic
    }
}
```

**FrameworkLoader automatically wraps this as:**
```java
frameworks.add(createStaticAdapter(HipaaRules.class, 10, false));
```

**Note:** This pattern is deprecated for new frameworks but will continue to work through v3.x.

---

## External Plugin Distribution

### Method 1: ServiceLoader (Recommended)

**Step 1:** Create `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`

```
com.example.compliance.FedRampRules
com.example.compliance.Nist80053Rules
```

**Step 2:** Package as JAR

```xml
<!-- pom.xml -->
<project>
    <artifactId>cloudforge-fedramp-plugin</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>com.cloudforgeci</groupId>
            <artifactId>cloudforge-core</artifactId>
            <version>3.0.0</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.cloudforgeci</groupId>
            <artifactId>cloudforge-api</artifactId>
            <version>3.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

**Step 3:** Users add your plugin to their project

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>cloudforge-fedramp-plugin</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Step 4:** Users enable your framework

```json
{
  "cfc": {
    "complianceFrameworks": "FEDRAMP",
    "auditManagerEnabled": true
  }
}
```

---

### Method 2: Direct JAR on Classpath

Users can drop your JAR file directly into their classpath:

```bash
mvn install:install-file \
  -Dfile=cloudforge-fedramp-plugin-1.0.0.jar \
  -DgroupId=com.example \
  -DartifactId=cloudforge-fedramp-plugin \
  -Dversion=1.0.0 \
  -Dpackaging=jar
```

---

## Compliance Rule API

### ComplianceRule Record

```java
public record ComplianceRule(
    String ruleId,                    // "FEDRAMP-AC-2"
    String description,               // Human-readable requirement
    Optional<String> configRuleId,    // AWS Config rule ID (optional)
    boolean passed,
    Optional<String> errorMessage
)
```

### Creating Rules

```java
// Passing rule
ComplianceRule.pass(
    "FEDRAMP-AC-2",
    "GuardDuty enabled (FedRAMP AC-2)",
    "GuardDutyEnabled"  // AWS Config rule ID
);

// Failing rule
ComplianceRule.fail(
    "FEDRAMP-AC-2",
    "GuardDuty required (FedRAMP AC-2)",
    "GuardDutyEnabled",
    "Enable AWS GuardDuty for threat detection"
);
```

---

## Security Profile Configuration API

Access deployment security configuration via:

```java
var config = ctx.securityProfileConfig.get().orElseThrow();
```

### Available Methods

| Method | Description | Applies To |
|--------|-------------|------------|
| `isSecurityMonitoringEnabled()` | Security monitoring & alerting | All profiles |
| `isCloudTrailEnabled()` | AWS CloudTrail audit logging | PRODUCTION, STAGING |
| `isGuardDutyEnabled()` | AWS GuardDuty threat detection | PRODUCTION |
| `isEbsEncryptionEnabled()` | EBS volume encryption | All profiles |
| `isEfsEncryptionAtRestEnabled()` | EFS encryption at rest | PRODUCTION, STAGING |
| `isEfsEncryptionInTransitEnabled()` | EFS encryption in transit (TLS) | PRODUCTION, STAGING |
| `isS3EncryptionEnabled()` | S3 bucket encryption | All profiles |
| `isWafEnabled()` | AWS WAF protection | PRODUCTION |
| `isFlowLogsEnabled()` | VPC Flow Logs | PRODUCTION, STAGING |
| `isMultiAzEnforced()` | Multi-AZ deployment | PRODUCTION |
| `isAutomatedBackupEnabled()` | Automated backups | PRODUCTION, STAGING |

See [SecurityProfileConfiguration.java](../interfaces/SecurityProfileConfiguration.java) for complete API.

---

## Priority System

| Priority Range | Usage | Examples |
|----------------|-------|----------|
| **-10 to -5** | Cross-framework infrastructure | KeyManagement, DatabaseSecurity |
| **-4 to 0** | Cross-framework security | ThreatProtection, IncidentResponse |
| **10-20** | Core compliance frameworks | HIPAA (10), PCI-DSS (12), SOC2 (15), GDPR (18) |
| **50-100** | Extended/contributed frameworks | ISO-27001 (50), FedRAMP (55), NIST 800-53 (60) |
| **100+** | Custom/organizational frameworks | Internal policies |

**Rule:** Lower priority loads first. Use negative priorities for foundation rules that other frameworks depend on.

---

## Testing Your Plugin

### Unit Test Example

```java
@Test
void testFedRampAccessControl() {
    App app = new App();
    Stack stack = new Stack(app, "TestStack");

    // Setup context
    Map<String, Object> cfcContext = new HashMap<>();
    cfcContext.put("stackName", "TestStack");
    cfcContext.put("securityProfile", "PRODUCTION");
    cfcContext.put("complianceFrameworks", "FEDRAMP");
    cfcContext.put("auditManagerEnabled", true);
    stack.getNode().setContext("cfc", cfcContext);

    DeploymentContext cfc = DeploymentContext.from(stack);
    SystemContext ctx = SystemContext.start(
        stack,
        TopologyType.APPLICATION_SERVICE,
        RuntimeType.FARGATE,
        SecurityProfile.PRODUCTION,
        IAMProfile.MINIMAL,
        cfc
    );

    // Install your framework
    new FedRampRules().install(ctx);

    // Verify it doesn't throw (compliance passes)
    assertDoesNotThrow(() -> Template.fromStack(stack));
}
```

---

## Best Practices

### 1. **Use Specific Rule IDs**
```java
// Good: Traceable to specific control
"FEDRAMP-AC-2.1"

// Bad: Generic
"ACCESS_CONTROL_1"
```

### 2. **Map to AWS Config Rules**
```java
ComplianceRule.fail(
    "FEDRAMP-AC-2",
    "GuardDuty required",
    "GuardDutyEnabled",  // ← AWS Config rule ID
    "Enable GuardDuty"
);
```

### 3. **Respect Security Profiles**
```java
// DEV: Advisory warnings only
// STAGING: Balanced security
// PRODUCTION: Full enforcement

if (ctx.security != SecurityProfile.PRODUCTION) {
    LOG.info("Framework enforced for PRODUCTION only");
    return;
}
```

### 4. **Support Compliance Modes**
```java
ComplianceMode mode = ComplianceMode.fromString(
    ctx.cfc.complianceMode(),
    ComplianceMode.defaultForProfile(ctx.security)
);

if (mode == ComplianceMode.ADVISORY) {
    errors.forEach(err -> LOG.warning(err));
    return List.of();  // Don't block
} else {
    return errors;     // Block synthesis
}
```

### 5. **Write Actionable Error Messages**
```java
// Good: Tells user exactly what to do
"Enable AWS GuardDuty for threat detection (FedRAMP AC-2)"

// Bad: Vague
"Security requirement not met"
```

---

## Example: Complete FedRAMP Plugin

See [Iso27001Rules.java](../core/rules/Iso27001Rules.java) for a complete working example demonstrating:
- Instance-based design
- Security profile filtering
- Compliance mode handling
- Structured rule validation
- AWS Config rule mapping

---

## Migration Guide: v3.0.0 → v3.0.0 → future versions

### v3.0.0 (Static Methods Only)
```java
public final class MyRules {
    public static void install(SystemContext ctx) {
        // ...
    }
}
```

### v3.0.0 (Plugin Support, Backward Compatible)
```java
@ComplianceFramework(value = "MY-FRAMEWORK", priority = 50)
public class MyRules implements FrameworkRules<SystemContext> {
    @Override
    public void install(SystemContext ctx) {
        // ...
    }
}
```

### future versions (Pure Instance-Based, No Static Adapters)
Same as v3.0.0, but static methods will be deprecated.

---

## Troubleshooting

### Plugin Not Loading

**Check 1:** Verify ServiceLoader registration
```bash
jar tf cloudforge-fedramp-plugin.jar | grep services
# Should show: META-INF/services/com.cloudforge.core.interfaces.FrameworkRules
```

**Check 2:** Verify annotation
```java
@ComplianceFramework(value = "FEDRAMP", priority = 50)  // ✓ Correct
@ComplianceFramework("FEDRAMP")  // ✗ Missing priority
```

**Check 3:** Enable debug logging
```java
Logger.getLogger("com.cloudforgeci.api.core.rules").setLevel(Level.FINE);
```

### Framework Loads But Doesn't Run

**Check:** Ensure framework ID matches config
```java
@ComplianceFramework(value = "FEDRAMP")  // Must match exactly

// In deployment context:
"complianceFrameworks": "FEDRAMP"  // Case-sensitive!
```

---

## Resources

- **Example Framework:** [Iso27001Rules.java](../core/rules/Iso27001Rules.java)
- **Core API:** [FrameworkRules.java](../../../../cloudforge-core/src/main/java/com/cloudforge/core/interfaces/FrameworkRules.java)
- **Loader Implementation:** [FrameworkLoader.java](../core/rules/FrameworkLoader.java)
- **Annotation:** [ComplianceFramework.java](../../../../cloudforge-core/src/main/java/com/cloudforge/core/annotation/ComplianceFramework.java)

---

## Contributing Your Plugin

We welcome external compliance framework contributions! To add your plugin to the CloudForge ecosystem:

1. Create a GitHub repository for your plugin
2. Add `cloudforge-plugin` topic to your repo
3. Submit a PR to add your plugin to the [Plugin Registry](https://github.com/CloudForgeCI/plugin-registry)

**Plugin Naming Convention:** `cloudforge-{framework}-plugin`

Examples:
- `cloudforge-fedramp-plugin`
- `cloudforge-nist-plugin`
- `cloudforge-iso27001-plugin`

---

*CloudForge Compliance Framework Plugin System - v3.0.0+*

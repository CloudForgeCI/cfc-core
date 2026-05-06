# IAM Profiler Integration with Compliance Framework

## Executive Summary

The IAM Profiler integration will **NOT interfere** with the existing compliance plugin system. The compliance framework is purely a **validation layer** that operates independently from infrastructure configuration.

---

## Compliance Framework Architecture

### Core Components

**FrameworkRules Interface** ([cloudforge-core/src/main/java/com/cloudforge/core/interfaces/FrameworkRules.java](cloudforge-core/src/main/java/com/cloudforge/core/interfaces/FrameworkRules.java))
- Single method: `void install(T ctx)` - registers CDK validations
- Generic type parameter `<T>` (typically `SystemContext`)
- Default methods extract metadata from `@ComplianceFramework` annotation

**ComplianceFramework Annotation** ([cloudforge-core/src/main/java/com/cloudforge/core/annotation/ComplianceFramework.java](cloudforge-core/src/main/java/com/cloudforge/core/annotation/ComplianceFramework.java))
```java
@ComplianceFramework(
    value = "FRAMEWORK_ID",        // Required: Framework identifier
    priority = 100,                // Optional: Load order (lower = first)
    alwaysLoad = false,            // Optional: Load without config
    displayName = "",              // Optional: Human-readable name
    description = ""               // Optional: Documentation
)
```

### How It Works

1. **Discovery**: Java ServiceLoader scans `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`
2. **Loading**: Frameworks loaded in priority order (lower values first)
3. **Filtering**: Only load if `alwaysLoad=true` OR framework ID in `complianceFrameworks` config
4. **Installation**: Each framework's `install(ctx)` method registers CDK validations
5. **Validation**: During CDK synthesis, validations execute and return error strings

### Priority Guidelines

From the annotation documentation:

| Priority Range | Purpose | Examples |
|---|---|---|
| **-10** | Cross-framework infrastructure | KeyManagementRules |
| **-5** | Cross-framework security | DatabaseSecurityRules, AdvancedMonitoringRules |
| **0** | Threat protection | ThreatProtectionRules, IncidentResponseRules |
| **10-20** | Core compliance frameworks | HipaaRules, PciDssRules, Soc2Rules, GdprRules |
| **50+** | Extended/contributed frameworks | FedRampRules, Iso27001Rules, Nist80053Rules |
| **100** | Default priority | Custom contributed frameworks |

---

## IAM Profiler as Compliance Framework

### Implementation Pattern

The IAM Profiler will be implemented as a **compliance framework validator** (Layer 5):

```java
@ComplianceFramework(
    value = "IAMProfiler",
    priority = 35,
    alwaysLoad = false,
    displayName = "IAM Profiler Analysis",
    description = "Analyzes IAM usage patterns to detect over-provisioning and privilege creep"
)
public class IAMProfilerRules implements FrameworkRules<SystemContext> {
    @Override
    public void install(SystemContext ctx) {
        if (!ctx.getConfig().isIamProfilerEnabled()) {
            return;
        }

        ctx.getNode().addValidation(() -> {
            // Analyze IAM permissions
            List<ComplianceRule> rules = analyzeIamPermissions(ctx);

            // Return validation errors
            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();
        });
    }

    private List<ComplianceRule> analyzeIamPermissions(SystemContext ctx) {
        // Get baseline from PermissionMatrix
        List<String> baseline = PermissionMatrix.getRequiredPermissions(
            ctx.getTopologyType(),
            ctx.getRuntimeType(),
            ctx.getIamProfile()
        );

        // Get actual permissions from IAM roles
        List<String> actual = extractActualPermissions(ctx);

        // Analyze with 3rd party library
        return profilerClient.analyze(actual, baseline);
    }
}
```

### Why Priority 35?

- **After infrastructure setup** (-10 to -5): IAM roles are defined
- **After threat protection** (0): Security monitoring is configured
- **After compliance frameworks** (10-20): Core compliance requirements are validated
- **Before topology/governance** (40-50): IAM analysis informs governance decisions
- **Before internal policies** (60+): IAM profiler findings can be referenced by custom policies

This ensures:
1. IAM roles exist before analysis
2. Compliance requirements are known
3. IAM profiler findings available for higher-layer policies

---

## Integration with Existing Systems

### No Interference Because:

1. **Separate Concerns**
   - Compliance frameworks = **validation only**
   - Infrastructure specs = **configuration only**
   - No shared interfaces or dependencies

2. **Independent Loading**
   - IAM Profiler loads via ServiceLoader independently
   - Registered in its own `META-INF/services` entry
   - No modification to existing frameworks required

3. **Generic Context Type**
   - `FrameworkRules<T>` uses generic to avoid coupling
   - IAM Profiler and other frameworks all use `SystemContext`
   - No interface conflicts

4. **Additive Architecture**
   - Adding IAM Profiler doesn't modify existing code
   - Existing frameworks unaware of IAM Profiler
   - CDK validation system handles multiple validators

### Integration Points

**PermissionMatrix Integration** ([cloudforge-api/src/main/java/com/cloudforgeci/api/core/iam/PermissionMatrix.java](cloudforge-api/src/main/java/com/cloudforgeci/api/core/iam/PermissionMatrix.java))
- Use `getRequiredPermissions(topology, runtime, iamProfile)` as baseline
- Leverage `validatePermissions()` for permission checking
- Reuse wildcard matching logic

**IAMProfileMapper Integration** ([cloudforge-core/src/main/java/com/cloudforge/core/iam/IAMProfileMapper.java](cloudforge-core/src/main/java/com/cloudforge/core/iam/IAMProfileMapper.java))
- Respect `isValidCombination(securityProfile, iamProfile)` constraints
- Use `getRecommended(securityProfile)` for suggestions
- Align violation severity with profile restrictions

**SystemContext Access**
- Extract `TopologyType`, `RuntimeType`, `IAMProfile`, `SecurityProfile`
- Access IAM role definitions via CDK constructs
- Read configuration flags (`enableIamProfilerAnalysis`)

---

## Layer 6: IAM Access Analyzer

The IAM Access Analyzer will also be a compliance framework (Layer 6):

```java
@ComplianceFramework(
    value = "IAMAccessAnalyzer",
    priority = 45,
    alwaysLoad = false,
    displayName = "AWS IAM Access Analyzer",
    description = "Continuous IAM monitoring with AWS Config rules and remediation"
)
public class IAMAccessAnalyzerRules implements FrameworkRules<SystemContext> {
    @Override
    public void install(SystemContext ctx) {
        // Deploy AWS IAM Access Analyzer
        // Deploy AWS Config rules
        // Deploy SSM remediation documents
        // Add validations
    }
}
```

**Why Priority 45?**
- After IAM Profiler (35): Static analysis complete
- Before internal policies (60+): Continuous monitoring feeds policy decisions
- Aligns with governance layer (40-50): Runtime monitoring complements topology rules

---

## Complete Layer Architecture

| Layer | Priority | Frameworks | Purpose |
|---|---|---|---|
| **Infrastructure** | -10 to -5 | KeyManagementRules, DatabaseSecurityRules | Cross-framework infrastructure setup |
| **Core Security** | 0-5 | ThreatProtectionRules, IncidentResponseRules | Security monitoring and response |
| **Compliance** | 10-20 | HipaaRules, PciDssRules, Soc2Rules, GdprRules, FedRampRules | Regulatory compliance validation |
| **IAM Analysis** | **35** | **IAMProfilerRules** | **Static IAM permission analysis (3rd party)** |
| **Governance** | 40-50 | TopologyRules, IAMRules, **IAMAccessAnalyzerRules (45)** | Topology governance + continuous IAM monitoring |
| **Internal Policy** | 60+ | CustomSecurityPolicyRules, OpenSourceSecurityPolicyRules | Organization-specific policies |

---

## Registration Pattern

### ServiceLoader Configuration

Add to `cloudforge-api/src/main/resources/META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`:

```
com.cloudforgeci.api.core.rules.IAMProfilerRules
com.cloudforgeci.api.core.rules.IAMAccessAnalyzerRules
```

### Configuration in cdk.json

```json
{
  "complianceFrameworks": "HIPAA,SOC2,IAMProfiler,IAMAccessAnalyzer",
  "iamProfilerConfig": {
    "enabled": true,
    "strictMode": true,
    "allowedWildcards": ["logs:*", "cloudwatch:*"]
  },
  "iamAccessAnalyzerConfig": {
    "enabled": true,
    "enableRemediation": false,
    "requireManualApproval": true
  }
}
```

---

## Example: Existing Compliance Plugins

### CustomSecurityPolicyRules

Location: [cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/CustomSecurityPolicyRules.java](cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/CustomSecurityPolicyRules.java)

```java
@ComplianceFramework(
    value = "CustomSecurity",
    priority = 60,
    alwaysLoad = true,  // Always enforced
    displayName = "ACME Corp Internal Security Policy",
    description = "Organization-specific security requirements"
)
public final class CustomSecurityPolicyRules implements FrameworkRules<SystemContext> {
    @Override
    public void install(SystemContext ctx) {
        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Network security checks
            rules.add(validateVpcFlowLogs(ctx));
            rules.add(validatePrivateSubnets(ctx));

            // Data protection checks
            rules.add(validateEncryptionAtRest(ctx));
            rules.add(validateTlsEnforcement(ctx));

            // Access control checks
            rules.add(validateIamLeastPrivilege(ctx));

            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();
        });
    }
}
```

**Key Points:**
- `alwaysLoad = true` - Enforced on ALL deployments
- Priority 60 - Runs after all standard compliance frameworks
- Validates custom org policies
- **Independent** - Doesn't interfere with other frameworks

### OpenSourceSecurityPolicyRules

Location: [cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/OpenSourceSecurityPolicyRules.java](cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/OpenSourceSecurityPolicyRules.java)

```java
@ComplianceFramework(
    value = "OpenSourceSecurity",
    priority = 65,
    alwaysLoad = false,  // Opt-in
    displayName = "Open Source Security Best Practices",
    description = "Security requirements for open source projects"
)
public final class OpenSourceSecurityPolicyRules implements FrameworkRules<SystemContext> {
    @Override
    public void install(SystemContext ctx) {
        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = new ArrayList<>();

            // Supply chain security
            rules.add(validateSbomAvailable(ctx));
            rules.add(validateImageScanning(ctx));

            // Public infrastructure
            rules.add(validateWafEnabled(ctx));
            rules.add(validateRateLimiting(ctx));

            // Transparency
            rules.add(validateCloudTrailEnabled(ctx));
            rules.add(validateStatusPage(ctx));

            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();
        });
    }
}
```

**Key Points:**
- `alwaysLoad = false` - Only loads when explicitly enabled
- Priority 65 - Runs after custom security policies
- Validates OSS best practices
- **Independent** - Separate from other frameworks

---

## Summary: No Interference

### Why IAM Profiler Won't Interfere:

1. **Compliance frameworks are validators, not infrastructure specs**
   - They don't define IAM roles
   - They validate existing IAM configurations
   - Pure validation layer

2. **ServiceLoader provides isolation**
   - Each framework discovered independently
   - No shared state between frameworks
   - Load order controlled by priority

3. **SystemContext is the only shared dependency**
   - All frameworks use same context type
   - Context provides read-only access to configuration
   - No framework modifies context

4. **Additive architecture**
   - Adding IAM Profiler = add new class + ServiceLoader entry
   - No changes to existing frameworks
   - No changes to core interfaces

5. **Example plugins prove extensibility**
   - CustomSecurityPolicyRules and OpenSourceSecurityPolicyRules show pattern
   - Both coexist peacefully
   - IAM Profiler follows same pattern

### Safe to Proceed

The IAM Profiler epic can proceed without risk of interference with the compliance plugin system. The architecture is designed for extensibility via the `@ComplianceFramework` annotation and ServiceLoader mechanism.

---

## References

- [FrameworkRules.java](cloudforge-core/src/main/java/com/cloudforge/core/interfaces/FrameworkRules.java) - Core compliance interface
- [ComplianceFramework.java](cloudforge-core/src/main/java/com/cloudforge/core/annotation/ComplianceFramework.java) - Discovery annotation
- [IAMProfile.java](cloudforge-core/src/main/java/com/cloudforge/core/enums/IAMProfile.java) - IAM profile enum
- [IAMProfileMapper.java](cloudforge-core/src/main/java/com/cloudforge/core/iam/IAMProfileMapper.java) - Profile mapping
- [PermissionMatrix.java](cloudforge-api/src/main/java/com/cloudforgeci/api/core/iam/PermissionMatrix.java) - Permission baseline
- [CustomSecurityPolicyRules.java](cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/CustomSecurityPolicyRules.java) - Example plugin
- [OpenSourceSecurityPolicyRules.java](cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/OpenSourceSecurityPolicyRules.java) - Example plugin

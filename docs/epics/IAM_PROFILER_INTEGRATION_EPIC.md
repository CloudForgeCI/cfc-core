# Epic: IAM Profiler 3rd Party Library Integration

## Overview

Add a 3rd party IAM profiler library to analyze IAM usage patterns, detect over-provisioning, privilege creep, and excessive permissions. This will be integrated as **Layer 5** in the compliance framework, followed by AWS IAM Access Analyzer as **Layer 6** (similar to AWS Config rules and remediation).

## Current Architecture Context

### IAM Profile System

The codebase has a mature IAM profile system:

**IAMProfile Enum** ([cloudforge-core/src/main/java/com/cloudforge/core/enums/IAMProfile.java](cloudforge-core/src/main/java/com/cloudforge/core/enums/IAMProfile.java)):
- `MINIMAL` - Essential permissions only (production)
- `STANDARD` - Balanced permissions (staging)
- `EXTENDED` - Broad permissions with debugging (development)

**IAMProfileMapper** ([cloudforge-core/src/main/java/com/cloudforge/core/iam/IAMProfileMapper.java](cloudforge-core/src/main/java/com/cloudforge/core/iam/IAMProfileMapper.java)):
- Maps `SecurityProfile` → `IAMProfile`
- `PRODUCTION` → `MINIMAL`
- `STAGING` → `STANDARD`
- `DEV` → `EXTENDED`
- Validates safe combinations (prevents PRODUCTION + EXTENDED)

**PermissionMatrix** ([cloudforge-api/src/main/java/com/cloudforgeci/api/core/iam/PermissionMatrix.java](cloudforge-api/src/main/java/com/cloudforgeci/api/core/iam/PermissionMatrix.java)):
- Defines required permissions per `TopologyType` + `RuntimeType` + `IAMProfile`
- Provides validation: `validatePermissions()` checks for missing/excessive permissions
- Supports wildcards in permission matching
- Returns `ValidationResult` with issues list

### Layer Architecture

Current layers (by priority):

1. **Infrastructure Layer** (Priority -10 to -5): KeyManagementRules, DatabaseSecurityRules
2. **Core Security Layer** (Priority 0-5): ThreatProtectionRules, IncidentResponseRules
3. **Compliance Framework Layer** (Priority 10-20): HIPAA, PCI-DSS, SOC2, GDPR, FedRAMP
4. **Topology/Governance Layer** (Priority 40-50): TopologyRules, IAMRules
5. **Internal Policy Layer** (Priority 60+): CustomSecurityPolicyRules, OpenSourceSecurityPolicyRules

**New Layers:**
- **Layer 5**: IAM Profiler (3rd party analysis) - Priority 35-40
- **Layer 6**: IAM Access Analyzer (AWS Config integration) - Priority 45-50

### Framework Plugin Architecture

Uses Java ServiceLoader pattern:
- Interface: `FrameworkRules<SystemContext>`
- Annotation: `@ComplianceFramework(value, priority, alwaysLoad, displayName, description)`
- Registration: `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`
- Discovery: `FrameworkLoader.discover()`

---

## Epic User Stories

### Story 1: IAM Profiler Library Integration (Layer 5)

**As a** security engineer
**I want** to integrate a 3rd party IAM profiler library
**So that** I can detect over-provisioned IAM roles and privilege creep before deployment

#### Acceptance Criteria

1. **Library Selection & Integration**
   - [ ] Research and select appropriate IAM profiler library (e.g., CloudMapper, Prowler IAM module, Parliament, etc.)
   - [ ] Add dependency to `cloudforge-api/pom.xml`
   - [ ] Ensure library supports programmatic API (not just CLI)
   - [ ] Library can analyze IAM policies and compare against least-privilege baselines

2. **Framework Rules Implementation**
   - [ ] Create `IAMProfilerRules.java` in `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/`
   - [ ] Annotate with `@ComplianceFramework`:
     ```java
     @ComplianceFramework(
         value = "IAMProfiler",
         priority = 35,
         alwaysLoad = false,
         displayName = "IAM Profiler Analysis",
         description = "Analyzes IAM usage patterns to detect over-provisioning and privilege creep"
     )
     ```
   - [ ] Implement `FrameworkRules<SystemContext>` interface
   - [ ] Register in `META-INF/services/com.cloudforge.core.interfaces.FrameworkRules`

3. **Permission Analysis Logic**
   - [ ] Extract actual IAM permissions from `SystemContext`
   - [ ] Get baseline permissions from `PermissionMatrix.getRequiredPermissions(topology, runtime, iamProfile)`
   - [ ] Use profiler library to analyze permission delta (actual vs. baseline)
   - [ ] Detect wildcards (`*`, `s3:*`, etc.) in MINIMAL/STANDARD profiles
   - [ ] Flag permissions not in baseline for current IAMProfile
   - [ ] Analyze cross-service permission patterns (e.g., S3 + Lambda implies data processing)

4. **Compliance Rule Generation**
   - [ ] Create `ComplianceRule` instances for each violation:
     - Rule ID format: `IAM-PROFILER-{category}-{number}` (e.g., `IAM-PROFILER-WILDCARD-001`)
     - Description: Human-readable violation details
     - Error message: Actionable remediation guidance
   - [ ] Categories:
     - `WILDCARD` - Wildcard permissions in restrictive profiles
     - `OVERPERMISSION` - Permissions beyond baseline
     - `PRIVILEGE_CREEP` - Accumulated permissions over time
     - `CROSS_SERVICE` - Unusual cross-service access patterns
     - `ADMIN_ACCESS` - Administrative permissions in non-DEV profiles

5. **Configuration & Control**
   - [ ] Add `enableIamProfilerAnalysis` boolean flag to deployment context
   - [ ] Support profile-specific defaults:
     - MINIMAL profile: Strict analysis (all violations = errors)
     - STANDARD profile: Moderate analysis (wildcards = warnings, major violations = errors)
     - EXTENDED profile: Lenient analysis (only major violations = errors)
   - [ ] Allow exclusion list for known-safe wildcard permissions
   - [ ] Support baseline customization via configuration file

6. **Testing**
   - [ ] Unit tests for each violation category
   - [ ] Integration tests with PermissionMatrix
   - [ ] Test IAMProfileMapper validation alignment
   - [ ] CSV parameterized testing for multiple TopologyType + RuntimeType + IAMProfile combinations
   - [ ] Test with real IAM policies from CloudFormation templates

#### Technical Design

**Key Classes:**

```java
// cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/IAMProfilerRules.java
@ComplianceFramework(value = "IAMProfiler", priority = 35, alwaysLoad = false)
public class IAMProfilerRules implements FrameworkRules<SystemContext> {

    private final IAMProfilerClient profilerClient;

    @Override
    public void install(SystemContext ctx) {
        if (!ctx.getConfig().isIamProfilerEnabled()) {
            return;
        }

        ctx.getNode().addValidation(() -> {
            List<ComplianceRule> rules = analyzeIamPermissions(ctx);
            return rules.stream()
                .filter(r -> !r.passed())
                .map(ComplianceRule::toErrorString)
                .flatMap(Optional::stream)
                .toList();
        });
    }

    private List<ComplianceRule> analyzeIamPermissions(SystemContext ctx) {
        // Extract configuration
        TopologyType topology = ctx.getTopologyType();
        RuntimeType runtime = ctx.getRuntimeType();
        IAMProfile iamProfile = ctx.getIamProfile();
        SecurityProfile securityProfile = ctx.getSecurityProfile();

        // Get baseline permissions from PermissionMatrix
        List<String> baselinePermissions = PermissionMatrix.getRequiredPermissions(
            topology, runtime, iamProfile
        );

        // Get actual permissions from IAM role/policy
        List<String> actualPermissions = extractActualPermissions(ctx);

        // Analyze with profiler library
        IAMAnalysisResult result = profilerClient.analyze(
            actualPermissions,
            baselinePermissions,
            iamProfile,
            securityProfile
        );

        // Convert to ComplianceRule objects
        return convertToComplianceRules(result, iamProfile);
    }

    private List<String> extractActualPermissions(SystemContext ctx) {
        // Extract from IAM role definition in CDK
        // May need to parse PolicyDocument, ManagedPolicyArns, etc.
        return List.of();
    }

    private List<ComplianceRule> convertToComplianceRules(
        IAMAnalysisResult result,
        IAMProfile profile
    ) {
        List<ComplianceRule> rules = new ArrayList<>();

        // Check for wildcard violations
        for (String wildcardPerm : result.getWildcardPermissions()) {
            boolean isViolation = switch (profile) {
                case MINIMAL -> true; // No wildcards allowed
                case STANDARD -> !isAllowedWildcard(wildcardPerm);
                case EXTENDED -> false; // Wildcards OK in DEV
            };

            rules.add(new ComplianceRule(
                "IAM-PROFILER-WILDCARD-" + hash(wildcardPerm),
                "Wildcard permission detected: " + wildcardPerm,
                Optional.empty(),
                !isViolation,
                isViolation ? Optional.of("Remove wildcard or use specific permissions") : Optional.empty()
            ));
        }

        // Check for over-permissions
        for (String excessPerm : result.getExcessPermissions()) {
            rules.add(new ComplianceRule(
                "IAM-PROFILER-EXCESS-" + hash(excessPerm),
                "Excessive permission for " + profile + " profile: " + excessPerm,
                Optional.empty(),
                false,
                Optional.of("Remove permission or use a higher IAM profile")
            ));
        }

        // Check for cross-service patterns
        for (CrossServicePattern pattern : result.getCrossServicePatterns()) {
            boolean requiresReview = pattern.isUnusual() || profile == IAMProfile.MINIMAL;
            rules.add(new ComplianceRule(
                "IAM-PROFILER-CROSSSVC-" + hash(pattern.toString()),
                "Cross-service access detected: " + pattern.getDescription(),
                Optional.empty(),
                !requiresReview,
                requiresReview ? Optional.of("Review if cross-service access is intentional") : Optional.empty()
            ));
        }

        return rules;
    }

    private boolean isAllowedWildcard(String permission) {
        // Check against allowlist (e.g., "logs:*" might be acceptable)
        return ALLOWED_WILDCARDS.contains(permission);
    }

    private static final Set<String> ALLOWED_WILDCARDS = Set.of(
        "logs:*",           // Logging wildcards often acceptable
        "cloudwatch:*"      // Monitoring wildcards in STANDARD profile
    );
}
```

**Integration Points:**

1. **PermissionMatrix Integration**
   - Use `PermissionMatrix.getRequiredPermissions()` as baseline
   - Compare against actual IAM permissions
   - Leverage existing `validatePermissions()` logic

2. **IAMProfileMapper Integration**
   - Respect `isValidCombination(securityProfile, iamProfile)` constraints
   - Use `getRecommended(securityProfile)` to suggest downgrades
   - Align violation severity with profile restrictions

3. **SystemContext Access**
   - Extract `TopologyType`, `RuntimeType`, `IAMProfile`, `SecurityProfile`
   - Access IAM role definitions (may need new getters on SystemContext)
   - Read configuration flags (`enableIamProfilerAnalysis`)

---

### Story 2: IAM Access Analyzer Integration (Layer 6)

**As a** DevOps engineer
**I want** AWS IAM Access Analyzer deployed with Config rules
**So that** I can continuously monitor IAM permissions in runtime and auto-remediate violations

#### Acceptance Criteria

1. **AWS Config Rule Deployment**
   - [ ] Create `IAMAccessAnalyzerRules.java` in `cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/`
   - [ ] Priority 45-50 (after IAM Profiler layer)
   - [ ] Deploy AWS IAM Access Analyzer via CDK
   - [ ] Create Config rules for common IAM violations:
     - Unused IAM roles (90+ days)
     - Over-permissioned roles (compared to usage)
     - External access findings
     - Public access findings
     - Cross-account access without conditions

2. **Remediation Automation**
   - [ ] Extend `ComplianceFactory` with IAM Access Analyzer remediation
   - [ ] SSM Automation documents for:
     - Removing unused IAM roles
     - Attaching SCPs to restrict wildcard permissions
     - Adding condition keys to cross-account policies
     - Revoking public S3 bucket policies
   - [ ] Support manual approval workflow for sensitive changes
   - [ ] Dry-run mode to preview remediation actions

3. **Finding Integration**
   - [ ] Poll IAM Access Analyzer findings via AWS SDK
   - [ ] Convert findings to `ComplianceRule` format
   - [ ] Link to IAM Profiler violations (Layer 5) for correlation
   - [ ] Archive resolved findings
   - [ ] Export findings to Security Hub

4. **Config Rule Examples**
   - [ ] `iam-access-analyzer-enabled` - Analyzer must be active
   - [ ] `iam-unused-roles` - Flag roles unused for 90+ days
   - [ ] `iam-external-access` - Flag external access without conditions
   - [ ] `iam-public-access` - Flag public S3/SQS/SNS policies
   - [ ] `iam-wildcard-permissions` - Flag `*:*` or service-level wildcards

5. **Continuous Monitoring**
   - [ ] EventBridge integration for real-time finding notifications
   - [ ] SNS topic for critical IAM findings
   - [ ] CloudWatch dashboard for IAM posture metrics
   - [ ] Integration with existing `ComplianceFactory.enableSecurityHubRemediation()`

6. **Testing**
   - [ ] Deploy test IAM roles with known violations
   - [ ] Verify Config rules trigger on violations
   - [ ] Test SSM remediation documents in sandbox
   - [ ] Verify findings appear in Security Hub
   - [ ] Test correlation with Layer 5 IAM Profiler findings

#### Technical Design

**Key Classes:**

```java
// cloudforge-api/src/main/java/com/cloudforgeci/api/core/rules/IAMAccessAnalyzerRules.java
@ComplianceFramework(value = "IAMAccessAnalyzer", priority = 45, alwaysLoad = false)
public class IAMAccessAnalyzerRules implements FrameworkRules<SystemContext> {

    @Override
    public void install(SystemContext ctx) {
        if (!ctx.getConfig().isIamAccessAnalyzerEnabled()) {
            return;
        }

        // Deploy IAM Access Analyzer
        AccessAnalyzer analyzer = AccessAnalyzer.Builder.create(ctx, "IAMAnalyzer")
            .analyzerName(ctx.getStackName() + "-iam-analyzer")
            .type(AnalyzerType.ACCOUNT)
            .build();

        // Deploy Config rules
        deployConfigRules(ctx, analyzer);

        // Add validation for pre-deployment checks
        ctx.getNode().addValidation(() -> {
            return validateIamAnalyzerFindings(ctx);
        });
    }

    private void deployConfigRules(SystemContext ctx, AccessAnalyzer analyzer) {
        // Similar to ComplianceFactory pattern

        // Rule: IAM Access Analyzer must be enabled
        new ManagedRule(ctx, "IAMAnalyzerEnabled", ManagedRuleProps.builder()
            .identifier(ManagedRuleIdentifiers.ACCESS_KEYS_ROTATED)
            .configRuleName("iam-access-analyzer-enabled")
            .description("Ensures IAM Access Analyzer is enabled")
            .build());

        // Rule: No unused IAM roles
        new ManagedRule(ctx, "IAMUnusedRoles", ManagedRuleProps.builder()
            .identifier("IAM_ROLE_NOT_USED")
            .configRuleName("iam-unused-roles")
            .inputParameters(Map.of("maxCredentialUsageAge", "90"))
            .ruleScope(RuleScope.fromResources(List.of(ResourceType.IAM_ROLE)))
            .build());

        // Add SSM remediation (if enabled)
        if (ctx.getConfig().isIamAccessAnalyzerRemediationEnabled()) {
            deployRemediation(ctx);
        }
    }

    private void deployRemediation(SystemContext ctx) {
        // SSM Automation document for removing unused roles
        CfnDocument remediationDoc = CfnDocument.Builder.create(ctx, "RemoveUnusedRoles")
            .documentType("Automation")
            .content(Map.of(
                "schemaVersion", "0.3",
                "description", "Removes IAM roles unused for 90+ days",
                "parameters", Map.of(
                    "RoleArn", Map.of("type", "String")
                ),
                "mainSteps", List.of(
                    Map.of(
                        "name", "DeleteRole",
                        "action", "aws:executeAwsApi",
                        "inputs", Map.of(
                            "Service", "iam",
                            "Api", "DeleteRole",
                            "RoleName", "{{ RoleArn }}"
                        )
                    )
                )
            ))
            .build();

        // Link Config rule to remediation
        new CfnRemediationConfiguration(ctx, "UnusedRoleRemediation",
            CfnRemediationConfigurationProps.builder()
                .configRuleName("iam-unused-roles")
                .targetType("SSM_DOCUMENT")
                .targetIdentifier(remediationDoc.getRef())
                .automatic(false) // Require manual approval
                .build());
    }

    private List<String> validateIamAnalyzerFindings(SystemContext ctx) {
        // Query IAM Access Analyzer findings at deployment time
        // (This would require AWS SDK call during synthesis - may need to be runtime check)
        return List.of();
    }
}
```

**ComplianceFactory Extension:**

```java
// Add to cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java

public static void enableIamAccessAnalyzerRemediation(
    Construct scope,
    String id,
    boolean enabled,
    Map<String, Object> config
) {
    if (!enabled) return;

    // Deploy EventBridge rule for Access Analyzer findings
    Rule.Builder.create(scope, id + "-AccessAnalyzerFindings")
        .eventPattern(EventPattern.builder()
            .source(List.of("aws.access-analyzer"))
            .detailType(List.of("Access Analyzer Finding"))
            .build())
        .targets(List.of(
            new SnsTopic(Topic.fromTopicArn(scope, "SecurityTopic",
                (String) config.get("securityTopicArn")))
        ))
        .build();

    // Add to Security Hub
    if (config.containsKey("enableSecurityHub") && (Boolean) config.get("enableSecurityHub")) {
        // Security Hub automatically ingests Access Analyzer findings
    }
}
```

---

## Dependencies

### Library Options (Story 1)

Research required to select IAM profiler library:

1. **Parliament** (open source by Duo Security)
   - Analyzes IAM policies for security issues
   - Detects wildcards, missing conditions, privilege escalation
   - Python-based (may need JNI wrapper or REST API)

2. **CloudMapper** (open source by Duo Security)
   - Network security visualization
   - Has IAM analysis capabilities
   - Python-based

3. **Prowler** (open source)
   - AWS security assessment tool
   - IAM checks included
   - Python-based

4. **AWS IAM Policy Simulator API**
   - Native AWS service
   - Can simulate policy evaluation
   - Java SDK available

5. **Custom Implementation**
   - Build on top of AWS IAM Policy Grammar
   - Use existing PermissionMatrix as baseline
   - No external dependencies

**Recommendation**: Start with AWS IAM Policy Simulator API (native, Java SDK) or custom implementation leveraging PermissionMatrix.

### CDK Dependencies (Story 2)

- AWS CDK IAM Access Analyzer construct
- AWS Config constructs
- SSM Automation documents
- EventBridge integration

---

## Testing Strategy

### Unit Tests

- [ ] Test IAMProfilerRules with mock SystemContext
- [ ] Test permission delta calculation
- [ ] Test ComplianceRule generation for each violation type
- [ ] Test wildcard detection across profiles
- [ ] Test PermissionMatrix integration

### Integration Tests

- [ ] Deploy test stack with IAMProfilerRules
- [ ] Verify validation failures on over-permissioned roles
- [ ] Test with all TopologyType + RuntimeType + IAMProfile combinations
- [ ] Verify IAMAccessAnalyzerRules deploys Config rules
- [ ] Test remediation SSM documents in sandbox

### CSV Parameterized Tests

Following pattern in `docs/compliance/CSV_PARAMETERIZED_TESTING.md`:

```csv
topology,runtime,iamProfile,securityProfile,testPermissions,expectedViolations
JENKINS_SERVICE,EC2,MINIMAL,PRODUCTION,"s3:*",IAM-PROFILER-WILDCARD-001
JENKINS_SERVICE,EC2,MINIMAL,PRODUCTION,"logs:CreateLogGroup,ec2:*",IAM-PROFILER-WILDCARD-002
JENKINS_SERVICE,FARGATE,STANDARD,STAGING,"ecr:*,cloudwatch:*",PASS
JENKINS_SERVICE,EC2,EXTENDED,DEV,"*:*",PASS
```

---

## Documentation

- [ ] Update `docs/guides/IAM_RULES.md` with IAM Profiler usage
- [ ] Create `docs/compliance/IAM_PROFILER_RULES.md` with violation catalog
- [ ] Add IAM Access Analyzer setup guide
- [ ] Document remediation workflows and approval process
- [ ] Add examples to `cloudforge-api/src/test/java/com/cloudforgeci/api/examples/IAMExampleTest.java`

---

## Rollout Plan

### Phase 1: IAM Profiler (Layer 5)
1. Library selection and POC
2. Implement IAMProfilerRules
3. Integration with PermissionMatrix
4. Unit and integration tests
5. Documentation

### Phase 2: IAM Access Analyzer (Layer 6)
1. Deploy Access Analyzer via CDK
2. Create Config rules
3. Implement SSM remediation documents
4. EventBridge + Security Hub integration
5. Testing and documentation

### Phase 3: Production Rollout
1. Enable in DEV environment (alwaysLoad=true for testing)
2. Enable in STAGING (monitor findings)
3. Enable in PRODUCTION (strict validation)
4. Monitor remediation effectiveness
5. Iterate on violation detection rules

---

## Success Metrics

- [ ] Zero wildcard permissions in MINIMAL profile deployments
- [ ] &lt;5% false positive rate on violation detection
- [ ] 100% coverage of TopologyType + RuntimeType + IAMProfile combinations in tests
- [ ] &lt;24 hour remediation time for critical IAM findings
- [ ] IAM Access Analyzer findings integrated into Security Hub

---

## References

- [IAMProfile.java](cloudforge-core/src/main/java/com/cloudforge/core/enums/IAMProfile.java)
- [IAMProfileMapper.java](cloudforge-core/src/main/java/com/cloudforge/core/iam/IAMProfileMapper.java)
- [PermissionMatrix.java](cloudforge-api/src/main/java/com/cloudforgeci/api/core/iam/PermissionMatrix.java)
- [FrameworkRules.java](cloudforge-core/src/main/java/com/cloudforge/core/interfaces/FrameworkRules.java)
- [ComplianceFactory.java](cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java)
- [IAM_RULES.md](docs/guides/IAM_RULES.md)
- [CustomSecurityPolicyRules.java](cfc-testing/src/main/java/com/cloudforgeci/samples/plugins/compliance/CustomSecurityPolicyRules.java)

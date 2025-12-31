# Comprehensive Test Plan - Edge Cases & Validation

## Current Test Coverage

The `comprehensive-resource-validator.sh` currently tests:
- **Runtimes**: EC2, FARGATE (2)
- **Topologies**: JENKINS_SERVICE, APPLICATION_SERVICE (2)
- **Security Profiles**: DEV, STAGING, PRODUCTION (3)
- **Domain Configs**: with-domain, no-domain (2)
- **SSL Configs**: ssl-enabled, ssl-disabled (2)
- **Subdomain Configs**: with-subdomain, no-subdomain (2)

**Total Combinations**: 2 × 2 × 3 × 2 × 2 × 2 = 96 (many invalid, ~60 valid)

## Missing Edge Cases - High Priority

### 1. Network Modes (CRITICAL for Security)
**Current**: Only tests `public-no-nat`

**Missing**:
- `private-with-nat` - Most secure for production workloads
- `public-with-nat` - Public subnet with NAT gateway
- `private-no-nat` - Fully isolated (requires VPC endpoints)

**Why Important**:
- Different network modes affect security group rules, routing, and compliance
- HIPAA/PCI-DSS often require private networks
- Different cfn-guard rules apply to public vs private resources

**Test Matrix Addition**: +3 network modes = 180 additional test cases

---

### 2. Authentication Modes (CRITICAL for Compliance)
**Current**: Only tests `none` (DEV) and `alb-oidc` (STAGING/PRODUCTION)

**Missing**:
- `jenkins-oidc` - Jenkins-native OIDC integration
- `application-oidc` - Application-level OIDC
- `aws-iam` - IAM-based authentication
- `saml` - SAML federation

**Why Important**:
- SOC2 requires authentication for customer-facing systems
- Different auth modes create different Cognito/IAM resources
- Each mode has different cfn-guard validation requirements

**Test Matrix Addition**: +5 auth modes = 300 additional test cases

---

### 3. Regional Variations (CRITICAL for GDPR)
**Current**: Only tests `us-east-1`

**Missing**:
- `eu-west-1` (Ireland) - GDPR primary region
- `eu-central-1` (Frankfurt) - GDPR secondary region
- `us-west-2` (Oregon) - US backup region
- `ap-southeast-1` (Singapore) - APAC region

**Why Important**:
- GDPR requires EU data residency or approved transfer mechanisms
- Different regions have different service availability
- Cross-region compliance validation

**Test Scenarios**:
```bash
# GDPR Compliance Tests
- PRODUCTION + GDPR + us-east-1 + gdprDataTransferApproved=true → PASS
- PRODUCTION + GDPR + us-east-1 + gdprDataTransferApproved=false → FAIL (expected)
- PRODUCTION + GDPR + eu-west-1 → PASS (EU region, no transfer needed)
```

---

### 4. Log Retention Periods (CRITICAL for Compliance)
**Current**: Fixed at `7 days`

**Missing**:
- `30 days` - Minimum for basic compliance
- `90 days` - GDPR recommended minimum
- `365 days` - SOC2/HIPAA requirement
- `1825 days` (5 years) - Financial/healthcare long-term retention

**Why Important**:
- SOC2 requires 365+ days retention for audit trails
- GDPR requires "adequate" retention (90+ days recommended)
- PCI-DSS requires 90 days minimum, 1 year recommended
- Different retention = different S3 lifecycle policies, costs

**Test Scenarios**:
```bash
# SOC2 Compliance - Log Retention
- PRODUCTION + SOC2 + logRetentionDays=7 → FAIL (too short)
- PRODUCTION + SOC2 + logRetentionDays=365 → PASS
- PRODUCTION + SOC2 + logRetentionDays=90 → WARN (meets GDPR, not SOC2)
```

---

### 5. Individual Compliance Framework Testing
**Current**: Tests all frameworks together (`SOC2,HIPAA,PCI-DSS,GDPR`)

**Missing**: Individual framework validation
- `SOC2` only - Verify SOC2-specific controls
- `HIPAA` only - Verify HIPAA-specific controls (BAA, audit logging)
- `PCI-DSS` only - Verify PCI-DSS network segmentation, encryption
- `GDPR` only - Verify data residency, right to erasure
- `FedRAMP` - Government cloud requirements

**Why Important**:
- Isolates framework-specific failures
- Some frameworks have conflicting requirements
- Customers may only need specific certifications
- Validates compliance matrix correctly handles single frameworks

**Test Matrix**: +5 framework variations per security profile

---

### 6. Negative Test Cases (CRITICAL for Validation)
**Current**: Only tests valid configurations

**Missing**: Intentionally invalid configurations to verify validation works

**Examples**:
```bash
# These should FAIL during validation (not synthesis)
- ssl-enabled + no-domain → INVALID (SSL requires domain)
- subdomain + no-domain → INVALID (subdomain requires domain)
- authMode=alb-oidc + no-ssl → INVALID (OIDC requires HTTPS)
- PRODUCTION + wafEnabled=false → WARN (PRODUCTION should have WAF)
- STAGING + complianceFrameworks=HIPAA + logRetentionDays=7 → FAIL (insufficient retention)
- PRODUCTION + networkMode=public-no-nat + PCI-DSS → FAIL (PCI requires private networks)
- GDPR + us-east-1 + gdprDataTransferApproved=false → FAIL (data residency violation)
```

**Test Strategy**: Create `negative-tests.sh` script with ~50 invalid configurations

---

### 7. Encryption Configurations
**Current**: Always `enableEncryption=true`

**Missing**:
- `enableEncryption=false` - Should FAIL for STAGING/PRODUCTION
- Custom KMS keys vs AWS-managed keys
- Different encryption for different resources (EBS, S3, RDS, etc.)

**Test Scenarios**:
```bash
# Encryption Requirements
- DEV + enableEncryption=false → PASS (DEV allows unencrypted)
- STAGING + enableEncryption=false → FAIL (compliance requires encryption)
- PRODUCTION + kmsKeyType=aws-managed → WARN (should use customer-managed)
- PRODUCTION + kmsKeyType=customer-managed → PASS
```

---

### 8. Bastion Host Scenarios
**Current**: No bastion host testing

**Missing**:
- Bastion host for private network access
- Bastion + private-with-nat network mode
- Bastion CIDR restrictions
- Session Manager vs SSH access

**Why Important**:
- Required for accessing private resources
- Security best practice for production environments
- Affects security group rules and compliance

---

### 9. Auto-Scaling Edge Cases
**Current**: Fixed `minInstanceCapacity=1, maxInstanceCapacity=3`

**Missing**:
- `min=max` (no auto-scaling)
- `min=0` (scale to zero)
- Large scale: `min=10, max=100`
- Different CPU/memory targets

---

### 10. Multi-Stack Scenarios
**Current**: Single stack per test

**Missing**:
- Multiple stacks in same account (resource isolation)
- Multiple stacks with shared VPC
- Multiple stacks with different security profiles
- Stack dependencies (App stack depends on DB stack)

---

## Test Prioritization

### Tier 1 - Must Have (Immediate)
1. ✅ **Compliance frameworks on DEV** (completed)
2. **Network modes** - `private-with-nat`, `public-with-nat`
3. **Negative test cases** - Invalid configurations
4. **Log retention variations** - 30, 90, 365 days
5. **Individual compliance frameworks** - Test each separately

### Tier 2 - Should Have (Next Sprint)
6. **Regional variations** - EU regions for GDPR
7. **Authentication modes** - jenkins-oidc, application-oidc
8. **Encryption variations** - Test with encryption disabled
9. **Bastion host scenarios** - Private network access

### Tier 3 - Nice to Have (Future)
10. **Auto-scaling edge cases** - Various capacity configurations
11. **Multi-stack scenarios** - Resource sharing and isolation
12. **Instance type variations** - Different sizes
13. **Custom KMS keys** - Customer-managed encryption

---

## Proposed Test Structure

```bash
tests/
├── comprehensive/           # Current comprehensive tests
│   └── comprehensive-resource-validator.sh
├── edge-cases/
│   ├── network-modes-test.sh          # Tier 1
│   ├── negative-validation-test.sh    # Tier 1
│   ├── log-retention-test.sh          # Tier 1
│   ├── individual-frameworks-test.sh  # Tier 1
│   ├── regional-compliance-test.sh    # Tier 2
│   ├── auth-modes-test.sh             # Tier 2
│   ├── encryption-test.sh             # Tier 2
│   └── bastion-host-test.sh           # Tier 2
├── integration/
│   ├── actual-deployment-test.sh      # Deploy to real AWS
│   ├── post-deployment-validation.sh  # Verify resources created
│   └── cleanup-test.sh                # Verify clean deletion
└── performance/
    ├── synthesis-benchmark.sh         # Measure synth time
    └── large-scale-test.sh            # 100+ resources

```

---

## Implementation Recommendations

### Option 1: Extend Existing Script (Quick)
Add new configuration dimensions to `comprehensive-resource-validator.sh`:
- Pros: Single test suite, easier to maintain
- Cons: Test matrix explodes (96 → 1000+ combinations), very long runtime

### Option 2: Separate Test Suites (Recommended)
Create focused test scripts for each dimension:
- Pros: Faster targeted testing, easier to debug, parallel execution
- Cons: More files to maintain, need orchestration script

### Option 3: Parameterized Testing (Advanced)
CSV-driven test matrix with configurable dimensions:
- Pros: Highly flexible, easy to add new cases, data-driven
- Cons: More complex setup, requires parsing infrastructure

---

## Recommended Approach

**Phase 1** (This Sprint):
1. ✅ Update DEV to test compliance frameworks (completed)
2. Create `negative-validation-test.sh` with ~30 invalid configurations
3. Create `log-retention-test.sh` with 4 retention periods × 3 profiles
4. Create `individual-frameworks-test.sh` to test each framework separately

**Phase 2** (Next Sprint):
5. Create `network-modes-test.sh` for private-with-nat, public-with-nat
6. Create `regional-compliance-test.sh` for GDPR EU regions
7. Create `auth-modes-test.sh` for different authentication methods

**Phase 3** (Future):
8. Integration tests with actual AWS deployment
9. Performance benchmarks
10. Multi-stack scenarios

---

## Success Metrics

- **Coverage**: 90%+ of valid configuration combinations
- **Negative Tests**: 50+ invalid configurations correctly rejected
- **Runtime**: Full suite completes in &lt;30 minutes
- **Compliance**: All frameworks validated at all 3 layers (cdk-nag, FrameworkRules, cfn-guard)
- **Regression**: No previously passing tests fail after changes

---

## Next Steps

1. Review and approve test plan
2. Prioritize which Tier 1 tests to implement first
3. Create test implementation tickets
4. Set up CI/CD pipeline to run tests automatically
5. Create baseline results for regression detection

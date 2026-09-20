# CloudForge CI Compliance Posture and Test Coverage

## Executive Summary

CloudForge CI provides **infrastructure-level** compliance controls: security profile defaults, synthesis-time validators, AWS Config rules, and remediation actions. These support SOC 2, HIPAA, PCI DSS, and GDPR programs, but CloudForge **does not provide compliance certification**.

This document keeps three things separate:

1. **Compliance controls** - what CloudForge configures in AWS (encryption, logging, network segmentation, password policy, retention).
2. **Validation coverage** - what CloudForge checks, and how those checks are tested.
3. **Certification** - an independent auditor's opinion or assessment, which requires organizational controls and third-party testing that CloudForge does not provide.

### Framework Status

| Framework | `complianceFrameworks` value | Validator | Status |
|-----------|------------------------------|-----------|--------|
| SOC 2 | `soc2` | `Soc2Rules` | Supported |
| HIPAA | `hipaa` | `HipaaRules` | Supported (technical safeguards) |
| PCI DSS v4.0.1 | `pci-dss` | `PciDssRules` | Supported; WAF required in `production` |
| GDPR | `gdpr` | `GdprRules` | Supported (technical measures) |
| ISO/IEC 27001 | not accepted | `Iso27001Rules` | Validator and cfn-guard rules exist; not selectable |
| FedRAMP Moderate / High | not accepted | `FedRampRules`, `FedRampHighRules` | In development; see [FedRAMP Controls Mapping](compliance/FEDRAMP_CONTROLS_MAPPING.md) |

SAML federation is also an incomplete feature: SAML factories exist, but no `authMode` value selects them.

### Validation Layers

| Layer | Description | Enabled by |
|-------|-------------|------------|
| **cdk-nag** | Construct checks (`HIPAASecurityChecks`, `PCIDSS321Checks`, `AwsSolutionsChecks`) | `production` profile with frameworks selected |
| **CloudForge validators** | `FrameworkRules` implementations | `auditManagerEnabled: true` |
| **cfn-guard** | Template policies in `cloudforge-api/src/main/resources/cfn-guard/frameworks/` | Interactive Deployer in `enforce` mode; test suite |
| **AWS Config** | Rules, conformance packs, and remediation | `awsConfigEnabled: true` |

See [Validation Architecture](compliance/VALIDATION_ARCHITECTURE.md).

### Test Coverage

- `cloudforge-api/src/test/resources/compliance-test-matrix.csv` and the split files in `compliance-matrices/` drive `TruthTableValidationTest`, including negative (`FAIL`) rows for PCI DSS WAF, flow logs, log retention, and multi-framework combinations. See [CSV Parameterized Testing](compliance/CSV_PARAMETERIZED_TESTING.md).
- Unit tests cover `ComplianceMatrix` and the individual rule classes in `cloudforge-api/src/test/java/com/cloudforgeci/api/core/rules/`.

### Known Gaps in Validation

- `ConfigurationValidationRules`, `HipaaOrganizationalRules`, and `GdprOrganizationalRules` are defined but are not installed during synthesis (not registered, or their IDs cannot be selected).
- In `disabled` compliance mode, the PCI DSS, HIPAA, SOC 2, and GDPR validators still block on failures.
- The IAM account password policy for PCI DSS alone uses an 8-character minimum, below PCI DSS v4.0.1 Req 8.3.6.

**What "COMPLIANT" means**: an AWS Config rule or CloudForge validator reported that the evaluated resource satisfied that rule. It does not mean a deployment is SOC 2, HIPAA, PCI DSS, or GDPR certified.

**Related Documentation:**
- **[Security Best Practices](guides/SECURITY_RULES_README.md)** - Security rules, service enablement by profile, IAM policies
- **[AUDITOR_COMPLIANCE_MAPPING.md](AUDITOR_COMPLIANCE_MAPPING.md)** - Control mappings and evidence collection for external audits
- **[Multi-Framework Compliance Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md)** - How to configure multiple frameworks

---

## Blockers for Regulated Workloads

**Before deploying regulated workloads (PHI, PCI, PII), you MUST address these gaps:**

| Blocker | Framework | Impact | Action Required |
|---------|-----------|--------|-----------------|
| **GuardDuty response not configured** | PCI-DSS, HIPAA | Findings are not routed to responders | ✅ Enable GuardDuty, verify findings, configure alert routing |
| **No PHI in production** | HIPAA | Infrastructure tested without actual ePHI | ⚠️ HIPAA compliance requires risk analysis with actual ePHI data |
| **Cardholder data handling** | PCI-DSS | Infrastructure encrypts, but app must mask PAN | ❌ Application-level controls required (see Req 3-4) |
| **No ASV/Pen test** | PCI-DSS | External vulnerability testing required | ❌ Engage an Approved Scanning Vendor and penetration testers |
| **No organizational policies** | SOC2, All | Most SOC 2 criteria are organizational | ❌ Document policies, training, incident response |
| **No DSR workflow** | GDPR | Cannot fulfill data subject rights requests | ❌ Implement DSR intake, verification, fulfillment process |

**Legend:**
- ✅ **Technical configuration**
- ⚠️ **Requires process implementation**
- ❌ **Requires external engagement or application work**

---

## Infrastructure vs. Organizational Compliance

### What CloudForge CI Provides: Infrastructure-Level Technical Controls

CloudForge CI automates **technical infrastructure controls** that form the foundation of compliance frameworks. These are the AWS resource configurations, security policies, and monitoring capabilities that can be automated through code.

**✅ What We Automate:**
- IAM password policies and MFA enforcement
- Encryption at rest (EBS, RDS, S3)
- Network security (VPC, security groups, NACLs)
- Audit logging (CloudTrail, VPC Flow Logs, ALB logs)
- Access controls (IAM policies, S3 bucket policies)
- Monitoring and alerting (CloudWatch, Config rules)
- Data retention and lifecycle management
- Infrastructure as Code (IaC) compliance

Coverage varies by framework, workload, and audit scope. The controls listed here represent only the infrastructure portion of a compliance program.

---

### ❌ What CloudForge CI CANNOT Provide: Organizational Compliance

Compliance frameworks require **organizational policies, procedures, and human processes** that cannot be automated through infrastructure code. These require business decisions, legal review, employee training, and third-party audits.

#### SOC2 Compliance - Full Audit Requirements

**✅ Infrastructure Controls We Provide:**
- CC6.1: Logical access controls (IAM, MFA, encryption at rest)
- CC6.6: Boundary protection (VPC, security groups, WAF)
- CC6.7: Transmission protection (TLS)
- CC7.2: System monitoring and logging

**❌ Organizational Requirements You Must Implement:**
- **CC1.1**: Control environment and tone at the top
  - *Cannot automate*: Board oversight, management philosophy, organizational structure
  - *You need*: Written policies, board meeting minutes, organizational charts

- **CC1.2**: Management commitment to competence
  - *Cannot automate*: Job descriptions, training programs, performance evaluations
  - *You need*: HR policies, training records, competency frameworks

- **CC1.4**: Compliance accountability
  - *Cannot automate*: Assignment of responsibility and authority
  - *You need*: Responsibility matrices, escalation procedures

- **CC3.1/CC3.2**: Risk assessment process
  - *Cannot automate*: Business risk identification and assessment
  - *You need*: Risk register, risk assessment methodology, risk treatment plans

- **CC5.3**: Policies and procedures
  - *Cannot automate*: Documented security policies, acceptable use policies
  - *You need*: Security policy manual, employee handbook, signed acknowledgments

- **CC9.1**: Vendor management
  - *Cannot automate*: Third-party risk assessments, vendor contracts
  - *You need*: Vendor due diligence, SLAs, security questionnaires

- **A1.1**: Availability commitments (if applicable)
  - *Cannot automate*: SLA definitions, incident response plans
  - *You need*: Disaster recovery plan, business continuity plan, tested runbooks

**SOC2 Type 2 Audit Requirements:**
- 6-12 months of operational evidence
- Third-party auditor engagement (CPA firm)
- Management assertion letter
- System description document
- Auditor testing of controls
- **Cost**: $15,000 - $50,000+ for audit

---

#### HIPAA Compliance - Beyond Technical Safeguards

**✅ Infrastructure Controls We Provide:**
- Technical safeguards (45 CFR § 164.312)
  - Access controls, audit controls, encryption

**❌ Organizational Requirements You Must Implement:**
- **Administrative Safeguards (45 CFR § 164.308):**
  - Security management process
  - Workforce training and management
  - Information access management
  - Security awareness training program
  - Contingency planning and disaster recovery

- **Physical Safeguards (45 CFR § 164.310):**
  - Facility access controls
  - Workstation security policies
  - Device and media controls

- **Documentation Requirements:**
  - Written policies and procedures
  - Business Associate Agreements (BAAs)
  - Breach notification procedures
  - HIPAA Privacy Rule compliance
  - Risk analysis documentation

- **Ongoing Obligations:**
  - Annual HIPAA training for all workforce members
  - Regular risk assessments
  - Breach notification within 60 days
  - Compliance officer designation
  - Patient rights fulfillment (access, amendment, accounting)

**HIPAA Compliance Cost:**
- Initial risk assessment: $10,000 - $30,000
- Gap remediation: $20,000 - $100,000+
- Annual compliance program: $15,000 - $50,000/year
- BAA legal review: $2,000 - $5,000 each

---

#### PCI-DSS Compliance - QSA Requirements

**✅ Infrastructure Controls We Provide:**
- Network segmentation and firewalls
- Encryption in transit and at rest
- Access controls and MFA
- Logging and monitoring
- WAF protection

**❌ Organizational Requirements You Must Implement:**
- **Requirement 1-2**: Network architecture documentation
  - *Cannot automate*: Network diagrams, data flow diagrams, firewall rulesets review
  - *You need*: Quarterly network diagram updates, change control procedures

- **Requirement 3**: Cardholder data protection
  - *Cannot automate*: Data retention policies, secure disposal procedures
  - *You need*: Data inventory, data classification, secure deletion procedures

- **Requirement 4**: Transmission security
  - *Cannot automate*: Certificate management policies, trusted key management
  - *You need*: Crypto key management procedures, certificate lifecycle management

- **Requirement 6**: Secure development
  - *Cannot automate*: Secure SDLC, code review procedures, vulnerability patching
  - *You need*: Development standards, change control board, patch management policy

- **Requirement 8**: Access management
  - *Cannot automate*: User provisioning workflows, termination procedures
  - *You need*: Access request forms, approval workflows, quarterly access reviews

- **Requirement 9**: Physical access
  - *Cannot automate*: Data center security, visitor logs, badge management
  - *You need*: Physical security policy, video surveillance, access logs

- **Requirement 10**: Logging and monitoring
  - *Cannot automate*: Log review procedures, security incident response
  - *You need*: Daily log reviews, incident response plan, forensic readiness

- **Requirement 11**: Security testing
  - *Cannot automate*: Quarterly ASV scans, annual penetration testing
  - *You need*: ASV vendor contract ($2,000-5,000/year), pen test ($10,000-30,000/year)

- **Requirement 12**: Information security policy
  - *Cannot automate*: Security policies, acceptable use policy, incident response
  - *You need*: Complete security policy manual, annual security awareness training

**PCI-DSS Compliance Costs:**
- Level 1 (6M+ transactions/year): $50,000 - $500,000/year
  - Requires annual on-site QSA audit
  - Report on Compliance (ROC) required
  - Quarterly network scans ($2,000-5,000/quarter)
  - Annual penetration testing ($10,000-30,000)

- Level 2-4 (fewer transactions): $10,000 - $50,000/year
  - Self-Assessment Questionnaire (SAQ) may be acceptable
  - Quarterly scans still required
  - Annual penetration testing recommended

**QSA (Qualified Security Assessor) Requirements:**
- Must be engaged from PCI SSC approved list
- Cannot assess if involved in implementation
- Requires complete documentation package
- On-site interviews with staff
- Technical testing of all 12 requirements
- ROC or AOC issuance

---

#### GDPR Compliance - Legal and Operational Obligations

**✅ Infrastructure Controls We Provide:**
- Encryption (Article 32)
- Access controls (Article 32)
- Audit logging (Article 30)
- Data retention management (Article 5)

**❌ Legal and Operational Requirements You Must Implement:**
- **Article 13-14**: Transparency and information
  - *Cannot automate*: Privacy notices, data collection disclosures
  - *You need*: Privacy policy, cookie consent, data collection notices

- **Article 15-22**: Data subject rights
  - *Cannot automate*: Access requests, rectification, erasure, portability
  - *You need*: DSR workflow, 30-day response process, verification procedures

- **Article 30**: Records of processing activities
  - *Cannot automate*: Data inventory, processing purposes, legal basis
  - *You need*: ROPA (Record of Processing Activities), data mapping

- **Article 33-34**: Breach notification
  - *Cannot automate*: 72-hour notification to DPA, user notification
  - *You need*: Breach response plan, DPA contacts, notification templates

- **Article 35**: Data Protection Impact Assessment (DPIA)
  - *Cannot automate*: Privacy risk assessment for high-risk processing
  - *You need*: DPIA template, risk assessment methodology

- **Article 37**: Data Protection Officer (DPO)
  - *Cannot automate*: DPO appointment for public authorities or large-scale processing
  - *You need*: Designated DPO, independence, resources, reporting line to top management

- **Article 28**: Data Processing Agreements (DPA)
  - *Cannot automate*: Contracts with all data processors and sub-processors
  - *You need*: Legal counsel, DPA templates, vendor due diligence

**GDPR Compliance Costs:**
- Initial gap assessment: $15,000 - $50,000
- DPIA for high-risk processing: $5,000 - $20,000 each
- DPO (if required): $50,000 - $150,000/year (full-time) or $10,000-30,000/year (part-time consultant)
- Legal counsel: $15,000 - $100,000/year
- DSR automation tooling: $5,000 - $50,000/year
- Staff training: $2,000 - $10,000/year

**Supervisory Authority Requirements:**
- DPA registration in some jurisdictions
- Cooperation with audits and investigations
- Demonstration of compliance through documentation
- Fines up to €20M or 4% of global revenue

---

### The Compliance Pyramid

```
                        ┌─────────────────────────┐
                        │   External Audits       │
                        │  (SOC2, HIPAA, PCI-DSS) │
                        └─────────────────────────┘
                                    │
              ┌─────────────────────┴─────────────────────┐
              │     Organizational Processes              │
              │  (Policies, Training, Incident Response)  │
              └─────────────────────┬─────────────────────┘
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        │         People & Culture                              │
        │  (Security awareness, competence, accountability)     │
        └───────────────────────────┬───────────────────────────┘
                                    │
    ┌───────────────────────────────┴───────────────────────────────┐
    │              Infrastructure Controls                          │
    │         (AWS Config, IAM, Encryption, Logging)                │
    │          Controls configured by CloudForge CI                 │
    └───────────────────────────────────────────────────────────────┘
```

- CloudForge CI provides the **foundation** (bottom layer)
- You must build the **organizational layer** (policies, procedures, training)
- You must engage **external auditors** for certification (top layer)

AWS charges for the compliance services (AWS Config, CloudTrail, GuardDuty, VPC Flow Logs, S3 storage) depend on region, data volume, resource count, and current pricing. Use the [AWS Pricing Calculator](https://calculator.aws.amazon.com/) for an estimate.

---

### What You Still Need to Achieve Certification

#### For SOC2 Type 2:
1. ✅ Deploy CloudForge CI with SOC2 profile
2. ❌ Document all security policies and procedures
3. ❌ Implement employee security awareness training
4. ❌ Conduct risk assessment and document findings
5. ❌ Establish vendor management program
6. ❌ Create incident response plan and test it
7. ❌ Implement change management process
8. ❌ Engage SOC2 auditor (CPA firm)
9. ❌ Maintain 6-12 months of evidence
10. ❌ Complete audit testing and receive report

**Timeline**: 6-12 months minimum
**Cost**: $30,000-$80,000 (first year)

#### For HIPAA:
1. ✅ Deploy CloudForge CI with HIPAA profile
2. ❌ Conduct comprehensive risk analysis
3. ❌ Document all administrative safeguards
4. ❌ Implement physical safeguards
5. ❌ Create breach notification procedures
6. ❌ Establish BAA with all business associates
7. ❌ Implement HIPAA training program
8. ❌ Designate Privacy and Security Officers
9. ❌ Implement patient rights fulfillment process
10. ❌ (Optional) Engage third-party HIPAA assessment

**Timeline**: 6-12 months minimum
**Cost**: $40,000-$150,000 (first year)

#### For PCI-DSS:
1. ✅ Deploy CloudForge CI with PCI-DSS profile
2. ❌ Document cardholder data environment (CDE)
3. ❌ Create network segmentation diagrams
4. ❌ Implement secure SDLC
5. ❌ Establish quarterly ASV scanning
6. ❌ Conduct annual penetration testing
7. ❌ Implement physical security controls
8. ❌ Create complete security policy manual
9. ❌ Engage QSA for audit (Level 1) or complete SAQ (Level 2-4)
10. ❌ Submit ROC or SAQ to acquiring bank

**Timeline**: 6-18 months (depends on merchant level)
**Cost**: $60,000-$500,000 (Level 1), $20,000-$80,000 (Level 2-4)

---

### Disclaimer

**CloudForge CI is NOT a complete compliance solution.**

We provide:
- ✅ Infrastructure-level technical controls
- ✅ AWS Config rules for continuous monitoring
- ✅ Automated remediation where possible
- ✅ Audit log collection and retention
- Infrastructure evidence sources for supported controls

We do NOT provide:
- ❌ Legal advice or compliance consulting
- ❌ Organizational policies and procedures
- ❌ Employee training programs
- ❌ Third-party audit services
- ❌ Compliance certification or attestation
- ❌ Physical security implementation
- ❌ Vendor management programs
- ❌ Business Associate Agreements (BAAs)
- ❌ Data Protection Agreements (DPAs)
- ❌ Incident response consulting
- ❌ Penetration testing services
- ❌ Security awareness training

**Recommendation**:
Engage qualified compliance and legal professionals as appropriate for your scope. The amount of audit coverage supplied by these infrastructure controls depends on the framework, workload, implementation, and auditor.

---

## SOC 2 Infrastructure-Control Validation

### AWS Config Rules for SOC 2

With `awsConfigEnabled: true` and `soc2` selected, `ComplianceFactory` deploys:

- **Base rules** for every selection: EBS encryption by default, S3 server-side encryption, S3 public read prohibited, S3 versioning, and the IAM password policy
- **Production rules**: `CLOUD_TRAIL_ENABLED`
- **SOC 2 rules** under the `EnableSoc2Rules` condition, including Security Hub, Inspector, and Macie checks; database rules also require `provisionDatabase`
- **Collected rules** registered by other factories for resources that are created
- **A conformance pack** for the framework

The full rule list, with Trust Services Criteria mappings, is in the [SOC 2 Controls Gap Analysis](compliance/SOC2_CONTROLS_GAP_ANALYSIS.md).

### Remediation

| Remediation | Mode | Condition |
|-------------|------|-----------|
| IAM account password policy | Automatic | Stack creates the Config recorder |
| S3 bucket versioning | Automatic | `enableS3VersioningRemediation` and the stack creates the recorder |
| CloudTrail bucket policy | Automatic | `enableCloudTrailBucketAccessRemediation`, `production` profile |
| RDS deletion protection, RDS minor version upgrades | Automatic | `enableRdsDeletionProtectionRemediation`, `enableRdsAutoMinorVersionUpgradeRemediation` |
| Enable Security Hub, Inspector, Macie (SOC 2) and GuardDuty (PCI DSS) | Automatic, account-level | `production` stacks that create the recorder |
| All other rules | Detection only | - |

See [Retained Resources](compliance/RETAINED_RESOURCES.md#aws-config-auto-remediation) for details, including how to remove remediations.

### Test Results

Synthesis of SOC 2 configurations is covered by the `soc2_*` matrices in `TruthTableValidationTest`. Deployed evaluation results depend on the target account and are not part of the automated test suite.

---

## Partial Implementation: Other Frameworks

### HIPAA Compliance

**Status**: Config rules functional but not fully tested

**Implemented:**
- ✅ 6-year log retention (S3 lifecycle policies)
- ✅ 14-character password policy
- ✅ Encryption at rest (EBS, RDS, S3)
- ✅ Audit logging (CloudTrail with 6-year retention)
- ✅ Access controls (IAM policies, MFA)

**Not Fully Tested:**
- ⚠️ Breach notification procedures
- ⚠️ Business Associate Agreement (BAA) tracking
- ⚠️ HIPAA training program validation
- ⚠️ Emergency access procedures
- ⚠️ Automatic logoff enforcement

**Recommendation**: Full HIPAA testing required before production use with PHI

### PCI-DSS Compliance

**Status**: Config rules functional but not fully tested

**Implemented:**
- ✅ 1-year log retention (90 days immediately available)
- ✅ Network segmentation (VPC, security groups)
- ✅ Encryption (in transit and at rest)
- ✅ Access logging (ALB, CloudTrail)
- ✅ WAF protection (Application Load Balancer)

**Not Fully Tested:**
- ⚠️ Quarterly vulnerability scans
- ⚠️ Penetration testing procedures
- ⚠️ Cardholder data environment (CDE) isolation
- ⚠️ Network diagram documentation
- ⚠️ Compensating controls documentation

**Recommendation**: PCI-DSS ASV scans and formal attestation required

### GDPR Compliance

**Status**: Config rules functional but not fully tested

**Implemented:**
- ✅ Encryption (data protection by design)
- ✅ Access controls (right to access)
- ✅ Audit logging (accountability)
- ✅ Data retention policies (storage limitation)
- ✅ S3 versioning (availability and recovery; erasure requests must also remove prior versions)
- ✅ Data residency validation for the deployment region (`gdprDataTransferApproved` for approved transfers)

**Not Fully Tested:**
- ⚠️ Data subject rights automation
- ⚠️ Consent management
- ⚠️ 72-hour breach notification process
- ⚠️ Data processing agreements (DPA)
- ⚠️ Privacy impact assessments (DPIA)

**Recommendation**: GDPR legal review and DPA templates required

---

## GuardDuty Status

### Current Implementation

- `GuardDutyFactory` creates a detector (15-minute finding publishing frequency) when `guardDutyEnabled` and `createGuardDutyDetector` are `true`; the `production` profile enables GuardDuty by default.
- With PCI DSS selected, `ComplianceFactory` deploys the `GUARDDUTY_ENABLED_CENTRALIZED` Config rule and, for `production`, an automatic remediation that enables GuardDuty.
- Finding routing and automated response are not configured.

### Known Gaps

1. **No automated response** - Findings are not routed to SSM Automation, Lambda, or EventBridge targets
2. **No finding notifications** - Security team alerts for findings are not configured
3. **No finding aggregation** - Multi-region findings are not centralized
4. **No severity filtering** - All findings are treated equally

### Recommendation

For production security posture:
1. Enable GuardDuty in all regions
2. Route HIGH and CRITICAL findings to SNS with EventBridge rules
3. Implement automated response for common threats
4. Aggregate findings in a delegated administrator account

---

## Compliance Posture by Security Profile

### Profile Defaults

These are the security profile defaults when no framework requires otherwise. A framework that marks a control REQUIRED in `ComplianceMatrix` turns it on (unless `complianceMode` is `disabled`), and explicit deployment context values override the remaining defaults.

| Setting | DEV | STAGING | PRODUCTION |
|---------|-----|---------|------------|
| CloudTrail | Not created | Created | Created |
| CloudWatch Logs retention | 1 week | 3 months | 6 years |
| VPC Flow Logs | Off | On | On |
| ALB access logging | Off | On | On |
| WAF | Off | On | Off (required by PCI DSS, SOC 2, GDPR) |
| GuardDuty | Off | Off | On |
| Security monitoring alarms | Off | On | On |
| `complianceMode` default | advisory | advisory | enforce |
| cdk-nag packs | No | No | Yes, when frameworks are selected |
| AWS Config rules | `awsConfigEnabled` | `awsConfigEnabled` | `awsConfigEnabled` |
| Audit Manager and validators | `auditManagerEnabled` | `auditManagerEnabled` | `auditManagerEnabled` |
| IAM password minimum (no framework) | 12 | 12 | 14 |

---

## AWS Config Rule Implementation

### How Config Rules Work

```
┌─────────────────────────────────────────────────────────────┐
│                CloudForge Compliance Engine                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
            ┌─────────────────────────────────┐
            │   ComplianceMatrix.java          │
            │  - Reads complianceFrameworks    │
            │  - Marks controls REQUIRED or    │
            │    ADVISORY per framework        │
            └─────────────────────────────────┘
                              │
                              ▼
            ┌─────────────────────────────────┐
            │   ComplianceFactory.java         │
            │  - Creates AWS::Config::ConfigRule│
            │  - Adds remediation actions      │
            │  - Sets evaluation frequency     │
            └─────────────────────────────────┘
                              │
                              ▼
            ┌─────────────────────────────────┐
            │   AWS CloudFormation             │
            │  - Deploys Config rules          │
            │  - Creates SSM documents         │
            │  - Configures remediation        │
            └─────────────────────────────────┘
                              │
                              ▼
            ┌─────────────────────────────────┐
            │   AWS Config Service             │
            │  - Evaluates resources           │
            │  - Triggers remediation          │
            │  - Records compliance status     │
            └─────────────────────────────────┘
```

### Rule Evaluation

**Configuration Changes**: Rules evaluate immediately when resources change
**Periodic Evaluation**: All rules re-evaluate every 24 hours
**Manual Trigger**: Can force evaluation via API/Console

### Remediation Actions

**Automatic**: SSM Automation documents execute immediately
**Manual**: Config marks non-compliant, admin must fix
**Retry**: 5 attempts at 60-second intervals (password policy, S3 versioning) or 3 attempts at 120-second intervals (other remediations)

---

## Testing Strategy

### Current Test Coverage

1. **Unit tests** - `ComplianceMatrix`, rule classes, and cfn-guard rule files (`*GuardTest`)
2. **Truth table tests** - `TruthTableValidationTest` synthesizes stacks from CSV matrices and checks cdk-nag, validator, cfn-guard, and Config rule results
3. **Synthesis scripts** - `cfc-testing/scripts/` contains synthesis, dry-run, and LocalStack deployment scripts
4. **LocalStack workflow** - `.github/workflows/localstack-compliance-verification.yml` deploys matrix configurations to LocalStack

### Recommended Additional Testing

For full compliance posture validation:

1. **HIPAA Testing**
   - Deploy with PHI-like test data
   - Validate 6-year retention
   - Test breach notification procedures
   - Verify BAA compliance tracking

2. **PCI-DSS Testing**
   - Run ASV vulnerability scans
   - Test cardholder data encryption
   - Validate network segmentation
   - Verify quarterly scan automation

3. **GDPR Testing**
   - Test data subject rights (access, erasure)
   - Validate consent workflows
   - Test 72-hour breach notification
   - Verify data processing agreements

4. **GuardDuty Testing**
   - Generate simulated threats
   - Validate finding detection
   - Test automated response
   - Verify SNS notifications

5. **Multi-Framework Testing**
   - Deploy HIPAA+PCI-DSS+SOC2 simultaneously
   - Verify strictest rules applied
   - Test conflicting requirements
   - Validate cost optimization

---

## Compliance Gaps and Recommendations

### High Priority

1. **GuardDuty response**
   - **Gap**: Findings are not routed or acted on
   - **Risk**: Missed threat detection
2. **HIPAA testing with representative workloads**
   - **Gap**: Infrastructure checks are not tested with PHI workloads
   - **Risk**: Non-compliance if used for healthcare without further assessment
3. **Validator registration**
   - **Gap**: `ConfigurationValidationRules` and the organizational validators are not installed
   - **Risk**: Documented checks do not run

### Medium Priority

4. **PCI DSS ASV scans**
   - **Gap**: No external vulnerability scanning
   - **Risk**: Required for PCI DSS
5. **GDPR data subject rights**
   - **Gap**: Manual processes for GDPR requests
   - **Risk**: Response deadlines at scale
6. **Audit Manager coverage**
   - **Gap**: Framework resolution depends on AWS CLI name matching at synthesis time
   - **Risk**: Assessments silently skipped

### Low Priority

7. **Multi-region GuardDuty aggregation**
   - **Gap**: Findings not centralized
8. **Custom Config rules for business logic**
   - **Gap**: No business-specific compliance rules

---

## Deployment Context Configuration

### Minimal SOC 2 Configuration

```json
{
  "securityProfile": "production",
  "complianceFrameworks": "soc2",
  "awsConfigEnabled": true,
  "albAccessLogging": true,
  "enableEncryption": true,
  "logRetentionDays": "731"
}
```

### Multi-Framework

```json
{
  "securityProfile": "production",
  "complianceFrameworks": "soc2,hipaa,pci-dss",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableEncryption": true,
  "logRetentionDays": "3653"
}
```

`logRetentionDays` must be one of the CloudWatch Logs retention values (for example `365`, `731`, `1827`, `3653`). HIPAA validation requires at least 2190 days, so use `3653` with HIPAA or leave it unset to use the `production` default of 6 years.

---

## Cost Implications

The compliance services add AWS charges for AWS Config, CloudTrail, S3 log storage, CloudWatch, and, when enabled, GuardDuty, WAF, and Audit Manager. Charges scale with resource count, log volume, and retention. Use the [AWS Pricing Calculator](https://calculator.aws.amazon.com/) for an estimate.

---

## Verification Commands

### Check Config Rule Compliance

```bash
# List all Config rules
aws configservice describe-config-rules \
  --query 'ConfigRules[*].ConfigRuleName' \
  --output table

# Check compliance status
aws configservice describe-compliance-by-config-rule \
  --query 'ComplianceByConfigRules[*].[ConfigRuleName,Compliance.ComplianceType]' \
  --output table

# Get detailed compliance
aws configservice get-compliance-details-by-config-rule \
  --config-rule-name <rule-name> \
  --compliance-types NON_COMPLIANT
```

### Check GuardDuty Status

```bash
# Check if enabled
aws guardduty list-detectors

# Get findings (if enabled)
aws guardduty list-findings \
  --detector-id <DETECTOR_ID> \
  --max-results 50
```

### Check CloudTrail

```bash
# Verify trail is logging
aws cloudtrail get-trail-status \
  --name <TRAIL_NAME>

# List recent events
aws cloudtrail lookup-events \
  --max-results 10
```

---

## Summary

- CloudForge configures infrastructure controls for SOC 2, HIPAA, PCI DSS, and GDPR, validated by cdk-nag, CloudForge validators, cfn-guard, and AWS Config.
- The compliance test matrix covers single and combined framework selections, including negative cases.
- ISO 27001 and FedRAMP validators exist but cannot be selected; FedRAMP is in development.
- Certification requires organizational controls and an independent audit.

**Before production use:**
- Review the applicable test evidence and known gaps for each selected framework
- Test simultaneous framework selections against the target workload
- Confirm deployed AWS Config evaluations and remediation behavior
- Document and assess organizational, application, and third-party controls separately

---


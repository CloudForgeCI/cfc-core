# CloudForge CI - Auditor Compliance Mapping

## Purpose

This document maps CloudForge CI's automated controls to compliance framework requirements. It is designed for:
- **External Auditors** conducting SOC2, HIPAA, PCI-DSS, or GDPR assessments
- **Internal Audit Teams** validating control implementation
- **Compliance Officers** documenting control effectiveness
- **Risk Management** assessing control coverage

**Document Classification**: Public (Audit Support Documentation)
**Last Updated**: 2025-11-20 | **Version**: 2.0
**Audience**: External auditors, compliance assessors, security reviewers

---

## Executive Summary for Auditors

### What This System Provides

CloudForge CI is an Infrastructure-as-Code (IaC) solution that automatically deploys and enforces technical security controls on Amazon Web Services (AWS). The system uses AWS Config for continuous compliance monitoring with automatic remediation.

**Key Audit Evidence:**
- ✅ **Automated Control Deployment**: All controls deployed via CloudFormation (immutable infrastructure)
- ✅ **Continuous Monitoring**: AWS Config evaluates controls 24/7
- ✅ **Audit Trail**: All changes logged to CloudTrail with 6-year retention (HIPAA profile)
- ✅ **Remediation Tracking**: SSM Automation execution history provides evidence of control effectiveness
- ✅ **Configuration Baseline**: Git repository serves as configuration management database (CMDB)

**Scope of Controls:**
- Technical infrastructure controls only (approx. 30-40% of total framework requirements)
- Does NOT include organizational policies, procedures, or training
- Does NOT replace need for external audit or certification

**Control Deployment Count:**

| Framework Configuration | Base Controls | Framework-Specific Controls | Total AWS Config Rules |
|------------------------|---------------|----------------------------|----------------------|
| **SOC2 only** | 9 rules | + 7 SOC2-specific | = **16 rules** |
| **HIPAA only** | 9 rules | + 8 HIPAA-specific | = **17 rules** |
| **PCI-DSS only** | 9 rules | + 8 PCI-DSS-specific | = **17 rules** |
| **GDPR only** | 9 rules | + 8 GDPR-specific | = **17 rules** |
| **Multi-framework (all 4)** | 9 base | + 31 framework-specific | = **40 rules total** |

*Note: Base controls (encryption, IAM, S3, CloudTrail, VPC Flow Logs) are always deployed. Framework-specific controls only deploy when enabled via `complianceFrameworks` configuration property.*

**Testing Status:**
- ✅ **SOC2 (16 rules)**: Fully tested, all rules return COMPLIANT status
- ⚠️ **HIPAA (17 rules)**: Not Tested
- ⚠️ **PCI-DSS (17 rules)**: No Tested
- ⚠️ **GDPR (17 rules)**: Not Tested

---

## Control Mapping Matrix

### 3.1 SOC2 Trust Service Criteria

| TSC ID | Control Name | CloudForge Implementation | AWS Service | Evidence Location | Test Status |
|--------|--------------|---------------------------|-------------|-------------------|-------------|
| **CC6.1** | **Logical and Physical Access Controls** | | | | |
| CC6.1.1 | Restrict logical access | IAM policies, security groups, NACLs | IAM, VPC | CloudFormation templates | ✅ Tested |
| CC6.1.2 | Identify and authenticate users | IAM password policy, MFA enforcement | IAM | AWS Config: iam-password-policy, iam-user-mfa-enabled | ✅ Tested |
| CC6.1.3 | Remove access when no longer required | Access key rotation (90 days) | IAM | AWS Config: access-keys-rotated | ✅ Tested |
| CC6.1.4 | Restrict access to data | S3 bucket policies, encryption | S3, KMS | AWS Config: s3-bucket-public-read-prohibited | ✅ Tested |
| **CC6.6** | **Encryption** | | | | |
| CC6.6.1 | Encryption at rest | EBS, RDS, S3 encryption | EC2, RDS, S3 | AWS Config: encrypted-volumes, rds-storage-encrypted | ✅ Tested |
| CC6.6.2 | Encryption in transit | HTTPS ALB listeners, TLS 1.2+ | ELB | ALB configuration in CloudFormation | ✅ Tested |
| CC6.6.3 | Key management | KMS key rotation | KMS | AWS Config: kms-key-rotation-enabled | ✅ Tested |
| **CC6.7** | **System Monitoring** | | | | |
| CC6.7.1 | Logging of security events | CloudTrail, VPC Flow Logs, ALB logs | CloudTrail, VPC, ELB | S3 buckets with lifecycle policies | ✅ Tested |
| CC6.7.2 | Log retention | 2-year retention for SOC2 | S3 | S3 lifecycle policies | ✅ Tested |
| CC6.7.3 | Log integrity | S3 versioning, CloudTrail validation | S3, CloudTrail | AWS Config: s3-bucket-versioning-enabled | ✅ Tested |
| **CC7.2** | **System Operations - Monitoring** | | | | |
| CC7.2.1 | System availability monitoring | CloudWatch metrics, alarms | CloudWatch | CloudWatch Logs and dashboards | ✅ Tested |
| CC7.2.2 | Incident detection | AWS Config compliance status | AWS Config | Config dashboard | ✅ Tested |
| CC7.2.3 | Incident response | Config remediation actions | SSM | SSM Automation execution history | ✅ Tested |

**Additional SOC2 Controls Not Automated:**
- CC1.x: Control Environment (requires organizational structure, governance)
- CC2.x: Risk Assessment (requires business risk analysis)
- CC3.x: Control Activities (requires documented policies and procedures)
- CC9.x: Vendor Management (requires third-party assessments)

**SOC2 Coverage Summary:**
- **Controls Automated**: 13 out of ~75 TSC criteria (~17%)
- **Infrastructure Coverage**: Primarily CC6 (Logical Access), CC7 (System Operations)
- **Manual Controls Required**: ~60 TSC criteria (83%) including governance, HR, policies

---

### 3.2 HIPAA Security Rule Mapping (45 CFR Part 164 Subpart C)

| HIPAA Reference | Standard Name | CloudForge Implementation | AWS Service | Evidence Location | Test Status |
|----------------|---------------|---------------------------|-------------|-------------------|-------------|
| **§ 164.308(a)(1)** | **Security Management Process** | | | | |
| (i)(A) | Risk Analysis | Not automated - requires organizational risk analysis | Manual | Risk assessment documentation | ❌ Manual |
| (i)(B) | Risk Management | Config rules + remediation reduce technical risks | AWS Config | Config compliance reports | ⚠️ Partial |
| (i)(C) | Sanction Policy | Not automated - requires HR policy | Manual | Employee handbook | ❌ Manual |
| (i)(D) | Information System Activity Review | CloudTrail logs, Config compliance | CloudTrail, Config | CloudWatch Log Insights | ✅ Tested |
| **§ 164.308(a)(3)** | **Workforce Security** | | | | |
| (i)(A) | Authorization and/or Supervision | IAM policies, least privilege | IAM | IAM policy documents | ✅ Tested |
| (i)(B) | Workforce Clearance | Not automated - requires HR screening | Manual | Background check records | ❌ Manual |
| (i)(C) | Termination Procedures | Access key rotation detects unused keys | IAM | AWS Config: access-keys-rotated | ⚠️ Partial |
| **§ 164.308(a)(4)** | **Information Access Management** | | | | |
| (i)(A) | Isolating Healthcare Clearinghouse | VPC isolation, security groups | VPC | Network ACLs, security groups | ✅ Tested |
| (i)(B) | Access Authorization | IAM roles, S3 bucket policies | IAM, S3 | CloudFormation templates | ✅ Tested |
| (i)(C) | Access Establishment and Modification | Not automated - requires access request process | Manual | Access request tickets | ❌ Manual |
| **§ 164.308(a)(5)** | **Security Awareness and Training** | | | | |
| (i)(A) | Security Reminders | Not automated - requires training program | Manual | Training records | ❌ Manual |
| (i)(B) | Protection from Malicious Software | **FARGATE + GuardDuty**: Immutable containers + runtime protection | GuardDuty, ECS | ThreatProtectionRules:115-126 | ✅ Tested (SOC2) |
| (i)(C) | Log-in Monitoring | CloudTrail console sign-in events | CloudTrail | CloudTrail event history | ✅ Tested |
| (i)(D) | Password Management | IAM password policy (14 chars, 90-day rotation) | IAM | AWS Config: iam-password-policy | ✅ Tested |
| **§ 164.308(a)(6)** | **Security Incident Procedures** | | | | |
| (i) | Response and Reporting | Not automated - requires incident response plan | Manual | IR playbooks | ❌ Manual |
| **§ 164.308(a)(7)** | **Contingency Plan** | | | | |
| (i)(A) | Data Backup Plan | EBS snapshots, RDS automated backups | EC2, RDS | Backup retention policies | ⚠️ Partial |
| (i)(B) | Disaster Recovery Plan | Not automated - requires DR procedures | Manual | DR plan document | ❌ Manual |
| (i)(C) | Emergency Mode Operation | Not automated - requires emergency procedures | Manual | Emergency operations plan | ❌ Manual |
| (i)(D) | Testing and Revision | Not automated - requires annual testing | Manual | DR test reports | ❌ Manual |
| (i)(E) | Applications and Data Criticality Analysis | Not automated - requires BIA | Manual | Business impact analysis | ❌ Manual |
| **§ 164.312(a)(1)** | **Access Control (Technical)** | | | | |
| (i) | Unique User Identification | IAM users (no shared credentials) | IAM | IAM user list | ✅ Tested |
| (ii) | Emergency Access Procedure | Not automated - requires break-glass procedures | Manual | Emergency access policy | ❌ Manual |
| (iii) | Automatic Logoff | Not automated - requires session timeout config | Application | Application configuration | ❌ Manual |
| (iv) | Encryption and Decryption | KMS encryption for data at rest | KMS | AWS Config: encrypted-volumes | ✅ Tested |
| **§ 164.312(b)** | **Audit Controls** | | | | |
| (i) | Hardware/Software Audit Controls | CloudTrail, VPC Flow Logs, Config | CloudTrail, Config | S3 audit log buckets | ✅ Tested |
| **§ 164.312(c)(1)** | **Integrity Controls** | | | | |
| (i) | Mechanism to Authenticate ePHI | S3 versioning, CloudTrail log validation | S3, CloudTrail | AWS Config: s3-bucket-versioning-enabled | ✅ Tested |
| **§ 164.312(d)** | **Person or Entity Authentication** | | | | |
| (i) | Authentication | IAM MFA enforcement | IAM | AWS Config: iam-user-mfa-enabled, root-account-mfa-enabled | ✅ Tested |
| **§ 164.312(e)(1)** | **Transmission Security** | | | | |
| (i) | Integrity Controls | TLS 1.2+ for ALB listeners | ELB | ALB listener configuration | ✅ Tested |
| (ii) | Encryption | HTTPS encryption for data in transit | ELB | ALB SSL/TLS configuration | ✅ Tested |

**HIPAA Compliance Summary:**
- ✅ Technical Safeguards (§ 164.312): 70% automated (~10 out of 14 implementation specs)
- ⚠️ Administrative Safeguards (§ 164.308): 20% automated (~5 out of 25 implementation specs)
- ❌ Physical Safeguards (§ 164.310): 0% automated (AWS responsibility - requires physical data center controls)
- **Total Coverage**: ~15 out of 39 HIPAA implementation specifications (~38%)

---

### 3.3 PCI-DSS v4.0 Requirements

| Requirement | Description | CloudForge Implementation | AWS Service | Evidence Location | Test Status |
|-------------|-------------|---------------------------|-------------|-------------------|-------------|
| **1** | **Install and Maintain Network Security Controls** | | | | |
| 1.1.1 | Documented network security controls | VPC, security groups, NACLs | VPC | CloudFormation templates, network diagrams | ⚠️ Requires documentation |
| 1.2.1 | Restrict inbound/outbound traffic | Security group rules (no 0.0.0.0/0 ingress) | VPC | AWS Config: restricted-ssh, restricted-common-ports | ✅ Tested |
| 1.2.5 | Segmentation of CDE | VPC subnets, private/public tier | VPC | VPC subnet configuration | ⚠️ Requires CDE definition |
| 1.4.1 | NSC change control | Infrastructure as Code (Git) | Git | Commit history, PR approvals | ✅ Tested |
| **2** | **Apply Secure Configurations** | | | | |
| 2.1 | Change vendor defaults | **PRODUCTION profile**: Auto-approved (operational control) | IAM | PciDssRules:521-540 | ✅ Tested |
| 2.2 | Configuration standards | **PRODUCTION profile**: Auto-approved (hardening assumed) | Multiple | PciDssRules:542-557 | ✅ Tested |
| 2.2.2 | Enable only necessary services | **PRODUCTION profile**: Enforced via security groups | VPC | PciDssRules:559-574 | ✅ Tested |
| 2.2.5 | Remove unnecessary functionality | **PRODUCTION profile**: Minimal images assumed | EC2/ECS | PciDssRules:576-591 | ✅ Tested |
| 2.3 | Encrypt admin access | HTTPS/TLS for all admin access | ALB | PciDssRules:595-607 | ✅ Tested |
| 2.4 | Maintain inventory | AWS Config provides continuous inventory | AWS Config | PciDssRules:609-625 | ✅ Tested |
| **3** | **Protect Stored Account Data** | | | | |
| 3.3.1 | Mask PAN when displayed | **Not automated - application responsibility** | Application | Application code review | ❌ Application-level |
| 3.5.1 | Cryptographic keys securely stored | KMS key management | KMS | KMS key policies, rotation | ✅ Tested |
| 3.6.1 | Procedures for cryptographic keys | KMS key rotation enabled | KMS | AWS Config: kms-key-rotation-enabled | ✅ Tested |
| **4** | **Protect Cardholder Data with Strong Cryptography** | | | | |
| 4.1.1 | PAN transmission encryption | HTTPS ALB listeners with TLS 1.2+ | ELB | ALB listener configuration | ✅ Tested |
| 4.2.1 | PAN never sent via unencrypted technologies | **Not automated - application responsibility** | Application | Application code review | ❌ Application-level |

**⚠️ Important Note on Requirements 3-4:** PCI-DSS Requirements 3 and 4 cannot be fully automated at the infrastructure level. These requirements govern **how applications handle cardholder data** (PAN masking, data flow, storage restrictions), which is application-specific. CloudForge provides encryption and key management infrastructure, but application-level controls must be implemented in your Jenkins pipelines and workloads.
| **5** | **Protect Systems and Networks from Malicious Software** | | | | |
| 5.1 | Deploy anti-malware | **FARGATE + GuardDuty**: Immutable containers + runtime protection | GuardDuty, ECS | ThreatProtectionRules:115-126 | 🚧 Implemented |
| 5.2 | Anti-malware kept current | AWS-managed (GuardDuty auto-updates) | GuardDuty | ThreatProtectionRules:114-132 | 🚧 Implemented |
| 5.3 | Anti-malware protection mechanisms | Immutable infrastructure prevents malware persistence | ECS | ThreatProtectionRules:114-132 | 🚧 Implemented |
| **6** | **Develop and Maintain Secure Systems and Software** | | | | |
| 6.2.1 | Bespoke software developed securely | Not automated - requires secure SDLC | Manual | SDLC documentation | ❌ Manual |
| 6.3.2 | Inventory of software components | Not automated - requires SBOM | Manual | Software inventory | ❌ Manual |
| **8** | **Identify Users and Authenticate Access** | | | | |
| 8.2.1 | Unique user IDs | IAM users (no shared credentials) | IAM | IAM user list | ✅ Tested |
| 8.3.1 | MFA for admin access | IAM MFA enforcement | IAM | AWS Config: iam-user-mfa-enabled | ✅ Tested |
| 8.3.6 | Strong authentication minimum 8 characters | IAM password policy (8 chars minimum for PCI) | IAM | AWS Config: iam-password-policy | ✅ Tested |
| 8.3.9 | Password reuse prevention | IAM password reuse prevention (4 passwords for PCI) | IAM | IAM account password policy | ✅ Tested |
| 8.3.11 | Password rotation every 90 days | IAM password max age 90 days | IAM | IAM account password policy | ✅ Tested |
| **10** | **Log and Monitor All Access** | | | | |
| 10.2.1 | Audit logs capture required events | CloudTrail, ALB logs, VPC Flow Logs | CloudTrail, VPC, ELB | S3 audit log buckets | ✅ Tested |
| 10.2.2 | Audit logs capture user actions | CloudTrail records API calls | CloudTrail | CloudTrail event history | ✅ Tested |
| 10.3.1 | Audit log entries include required details | CloudTrail provides user, timestamp, action | CloudTrail | CloudTrail log format | ✅ Tested |
| 10.3.4 | Audit logs tamper-resistant | S3 versioning, CloudTrail log validation | S3, CloudTrail | AWS Config: s3-bucket-versioning-enabled | ✅ Tested |
| 10.4.1 | Logs retained for at least 12 months | S3 lifecycle policies (1-year retention for PCI) | S3 | S3 lifecycle configuration | ✅ Tested |
| 10.7.2 | Logs reviewed at least daily | Not automated - requires log review process | Manual | Log review records | ❌ Manual |
| **11** | **Test Security of Systems and Networks Regularly** | | | | |
| 11.3.1 | External vulnerability scans quarterly | Not automated - requires ASV | Third-party | ASV scan reports | ❌ Requires ASV vendor |
| 11.3.2 | Internal vulnerability scans quarterly | Not automated - requires scanning tool | Third-party | Internal scan reports | ❌ Requires scanning tool |
| 11.4 | Intrusion detection/prevention | GuardDuty threat detection | GuardDuty | GuardDuty findings | ✅ Tested (SOC2) |
| 11.4.1 | Penetration testing annually | Not automated - requires pen testers | Third-party | Pen test reports | ❌ Requires pen testers |
| 11.5 | File integrity monitoring | **FARGATE**: Immutable infrastructure = file integrity by design | ECS | ThreatProtectionRules:331-350 | 🚧 Implemented |
| 11.5 | Infrastructure change detection | AWS Config tracks infrastructure changes | AWS Config | ThreatProtectionRules:354-371 | ✅ Tested (SOC2) |
| **12** | **Support Information Security with Organizational Policies** | | | | |
| 12.1.1 | Information security policy | Not automated - requires documented policy | Manual | Security policy manual | ❌ Manual |
| 12.2.1 | Acceptable use policy | Not automated - requires documented policy | Manual | Acceptable use policy | ❌ Manual |
| 12.10.1 | Incident response plan | Not automated - requires IR procedures | Manual | Incident response plan | ❌ Manual |

**PCI-DSS Compliance Summary:**
- ✅ Network Security (Req 1): ~70% automated (VPC segmentation, security groups, IaC change control)
- ✅ Secure Configurations (Req 2): **PRODUCTION profile auto-approves** (vendor defaults, hardening, minimal services, inventory via AWS Config)
- ⚠️ Data Protection (Req 3-4): 30% automated (encryption provided; cardholder data handling is application responsibility)
- ✅ Malware Protection (Req 5): **FARGATE + GuardDuty** (immutable containers + runtime threat detection)
- ⚠️ Secure Development (Req 6): 20% automated (requires SDLC, SBOM, vulnerability scanning tools)
- ✅ Access Control (Req 8): 80% automated (IAM users, MFA, password policy, key rotation)
- ✅ Logging and Monitoring (Req 10): 70% automated (CloudTrail, VPC Flow Logs, log retention, tamper-resistance)
- ✅ Testing (Req 11): **File integrity via immutable infrastructure** (FARGATE + AWS Config); GuardDuty threat detection
- ❌ Policies (Req 12): 0% automated (requires organizational documentation)
- **Total Coverage**: ~40 out of 60 PCI-DSS v4.0 requirements (~67% of technical controls)
- **Key Innovation**: PRODUCTION profile + FARGATE eliminates operational attestation requirements for Req 2, 5, and 11.5

---

### 3.4 GDPR Articles Mapping

| GDPR Article | Requirement | CloudForge Implementation | AWS Service | Evidence Location | Test Status |
|--------------|-------------|---------------------------|-------------|-------------------|-------------|
| **Article 5** | **Principles relating to processing** | | | | |
| 5(1)(a) | Lawfulness, fairness, transparency | Not automated - requires privacy notice | Manual | Privacy policy | ❌ Manual |
| 5(1)(b) | Purpose limitation | Not automated - requires data inventory | Manual | Data processing register | ❌ Manual |
| 5(1)(c) | Data minimisation | Not automated - application design | Application | Data flow diagrams | ❌ Application-level |
| 5(1)(d) | Accuracy | Not automated - application functionality | Application | Data quality procedures | ❌ Application-level |
| 5(1)(e) | Storage limitation | S3 lifecycle policies per framework | S3 | S3 lifecycle configuration | ✅ Tested |
| 5(1)(f) | Integrity and confidentiality | Encryption at rest and in transit | KMS, ELB | AWS Config: encrypted-volumes | ✅ Tested |
| **Article 24** | **Responsibility of the controller** | | | | |
| 24(1) | Technical and organizational measures | Infrastructure controls provided | Multiple | CloudFormation stack | ⚠️ Partial (infrastructure only) |
| 24(2) | Demonstrate compliance | Audit logs, Config compliance reports | CloudTrail, Config | S3 audit logs | ✅ Tested |
| **Article 25** | **Data protection by design and by default** | | | | |
| 25(1) | Data protection by design | Encryption enabled by default | KMS | AWS Config: encrypted-volumes | ✅ Tested |
| 25(2) | Data protection by default | Least privilege IAM policies | IAM | IAM policy documents | ✅ Tested |
| **Article 30** | **Records of processing activities** | | | | |
| 30(1) | Maintain processing records | Not automated - requires ROPA | Manual | Record of processing activities | ❌ Manual |
| **Article 32** | **Security of processing** | | | | |
| 32(1)(a) | Pseudonymisation and encryption | Encryption at rest (EBS, RDS, S3) | KMS | AWS Config: encrypted-volumes | ✅ Tested |
| 32(1)(b) | Confidentiality, integrity, availability | IAM access controls, backups | IAM, EC2, RDS | Backup policies | ✅ Tested |
| 32(1)(c) | Restore availability after incident | Not automated - requires DR plan | Manual | Disaster recovery plan | ❌ Manual |
| 32(1)(d) | Testing and evaluation of measures | AWS Config continuous evaluation | AWS Config | Config compliance dashboard | ✅ Tested |
| 32(2) | Assess appropriate level of security | Not automated - requires risk assessment | Manual | Data protection risk assessment | ❌ Manual |
| **Article 33** | **Breach notification to authority** | | | | |
| 33(1) | 72-hour notification | Not automated - requires incident response | Manual | Breach notification procedures | ❌ Manual |
| **Article 34** | **Breach notification to data subject** | | | | |
| 34(1) | Notification without undue delay | Not automated - requires communication plan | Manual | Breach communication templates | ❌ Manual |
| **Article 35** | **Data Protection Impact Assessment** | | | | |
| 35(1) | DPIA for high-risk processing | Not automated - requires privacy assessment | Manual | DPIA documentation | ❌ Manual |
| **Article 37** | **Designation of DPO** | | | | |
| 37(1) | Appoint DPO where required | Not automated - organizational decision | Manual | DPO appointment letter | ❌ Manual |

**GDPR Compliance Summary:**
- ✅ Technical Measures (Art 32): 70% automated (~7 out of 10 technical safeguards)
- ⚠️ Accountability (Art 24-25): 30% automated (requires risk assessment and ROPA)
- ❌ Transparency (Art 13-14): 0% automated (requires privacy notices)
- ❌ Data Subject Rights (Art 15-22): 0% automated (requires DSR workflow)
- ❌ Breach Notification (Art 33-34): 0% automated (requires incident response procedures)
- **Total Coverage**: ~7 out of 99 GDPR Articles (~7% - GDPR is predominantly organizational)

**⚠️ Important Note on GDPR Technical Measures:**
CloudForge provides **Article 32 technical safeguards** (encryption, access control, logging). However, GDPR is predominantly an organizational/legal framework. **Data Subject Rights (DSR)** (Art 15-22: access requests, erasure, portability) and **breach notification** (Art 33-34) require business processes supported by audit logs but cannot be fully automated. CloudForge provides the technical foundation - your organization must implement the legal/operational processes.

---

## AWS Audit Manager Integration

### Supported Frameworks

CloudForge CI integrates with AWS Audit Manager for automated evidence collection. The following frameworks are available:

| Framework | Audit Manager Framework ID | Control Set Count | Evidence Collection | Test Status |
|-----------|---------------------------|-------------------|---------------------|-------------|
| SOC2 | aws/standard/SOC2 | 5 control sets | ✅ Automated | ✅ Tested |
| HIPAA | aws/standard/HIPAA | 8 control sets | ✅ Automated | ⚠️ Not fully tested |
| PCI-DSS v3.2.1 | aws/standard/PCI-DSS-v3.2.1 | 12 control sets | ✅ Automated | ⚠️ Not fully tested |
| GDPR | aws/standard/GDPR | 7 control sets | ✅ Automated | ⚠️ Not fully tested |

**Note**: Only SOC2 framework has been fully tested and validated. Other frameworks are functional but require additional testing.

### Evidence Collection

Audit Manager automatically collects evidence from:
- AWS Config rule compliance evaluations
- CloudTrail API activity logs
- IAM policy documents
- S3 bucket configurations
- VPC network ACLs and security groups
- Encryption key configurations
- Backup retention policies

### Assessment Reports

To generate an assessment report for auditors:

```bash
# Create assessment
aws auditmanager create-assessment \
  --name "SOC2-Assessment-2025" \
  --description "SOC2 Type 2 Assessment" \
  --assessment-reports-destination s3://your-audit-bucket \
  --scope "accountIds=123456789012,awsServices=S3,EC2,IAM,Config" \
  --roles assessmentReportDestination="arn:aws:iam::123456789012:role/AuditManager" \
  --framework-id "aws/standard/SOC2"

# Generate report
aws auditmanager get-assessment-report \
  --assessment-id <ASSESSMENT_ID> \
  --assessment-report-destination s3://your-audit-bucket
```

**Report Contents:**
- Control implementation status
- Evidence collected per control
- Non-compliant findings
- Remediation status
- Timestamps and responsible parties

---

## Detailed AWS Config Rules Breakdown

### Base Rules (Always Deployed - 9 Rules)

These rules are deployed for ALL security profiles and frameworks:

| Rule Name | AWS Managed Rule ID | Purpose | Applies To |
|-----------|-------------------|---------|------------|
| EBS Encryption | EC2_EBS_ENCRYPTION_BY_DEFAULT | Ensures EBS volumes are encrypted | All frameworks |
| S3 Bucket Encryption | S3_BUCKET_SERVER_SIDE_ENCRYPTION_ENABLED | Ensures S3 buckets have encryption enabled | All frameworks |
| S3 Public Access Block | S3_BUCKET_PUBLIC_READ_PROHIBITED | Prevents public S3 bucket access | All frameworks |
| S3 Versioning | S3_BUCKET_VERSIONING_ENABLED | Enables S3 versioning for audit trail | All frameworks |
| IAM Password Policy | IAM_PASSWORD_POLICY | Enforces strong password requirements | All frameworks |
| IAM Root Access Keys | IAM_ROOT_ACCESS_KEY_CHECK | Ensures no root access keys exist | All frameworks |
| CloudTrail Enabled | CLOUD_TRAIL_ENABLED | Ensures CloudTrail is logging | All frameworks |
| CloudTrail Log Validation | CLOUD_TRAIL_LOG_FILE_VALIDATION_ENABLED | Ensures log file validation is enabled | All frameworks |
| VPC Flow Logs | VPC_FLOW_LOGS_ENABLED | Ensures VPC Flow Logs are enabled | All frameworks |

### SOC2-Specific Rules (7 Rules)

Deploy only when `complianceFrameworks` includes "SOC2":

| Rule Name | AWS Managed Rule ID | SOC2 TSC Mapping | Purpose |
|-----------|-------------------|-----------------|---------|
| IAM User No Policies | IAM_USER_NO_POLICIES_CHECK | CC6.1 | Enforces role-based access control |
| Restricted SSH | INCOMING_SSH_DISABLED | CC6.6 | Blocks SSH from 0.0.0.0/0 |
| ALB HTTPS Redirection | ALB_HTTP_TO_HTTPS_REDIRECTION_CHECK | CC6.7 | Enforces HTTPS |
| Security Hub Enabled | SECURITYHUB_ENABLED | CC7.2 | Monitors security posture |
| CloudTrail S3 Data Events | CLOUDTRAIL_S3_DATAEVENTS_ENABLED | CC8.1 | Tracks S3 data access |
| RDS Multi-AZ | RDS_MULTI_AZ_SUPPORT | A1.2 | Ensures high availability |
| ELB Deletion Protection | ELB_DELETION_PROTECTION_ENABLED | A1.2 | Prevents accidental deletion |

### HIPAA-Specific Rules (8 Rules)

Deploy only when `complianceFrameworks` includes "HIPAA":

| Rule Name | AWS Managed Rule ID | HIPAA CFR Mapping | Purpose |
|-----------|-------------------|------------------|---------|
| CloudTrail to CloudWatch | CLOUD_TRAIL_CLOUD_WATCH_LOGS_ENABLED | §164.308(a)(1)(ii)(D) | System activity review |
| IAM Group Membership | IAM_USER_GROUP_MEMBERSHIP_CHECK | §164.308(a)(3) | Workforce clearance |
| DynamoDB Point-in-Time Recovery | DYNAMODB_PITR_ENABLED | §164.310(d)(2)(iv) | Backup ePHI |
| RDS Snapshot Encrypted | RDS_SNAPSHOT_ENCRYPTED | §164.312(a)(2)(iv) | Encrypt ePHI backups |
| Root Account MFA | ROOT_ACCOUNT_MFA_ENABLED | §164.312(a)(2)(i) | Unique user ID |
| ALB WAF Enabled | ALB_WAF_ENABLED | §164.312(b) | Record system activity |
| CloudTrail Encryption | CLOUD_TRAIL_ENCRYPTION_ENABLED | §164.312(c)(2) | Authenticate integrity |
| ELB ACM Certificate | ELB_ACM_CERTIFICATE_REQUIRED | §164.312(e)(2)(ii) | Encrypt in transit |

### PCI-DSS-Specific Rules (8 Rules)

Deploy only when `complianceFrameworks` includes "PCI-DSS":

| Rule Name | AWS Managed Rule ID | PCI-DSS Req Mapping | Purpose |
|-----------|-------------------|-------------------|---------|
| VPC Default SG Closed | VPC_DEFAULT_SECURITY_GROUP_CLOSED | Req 1.3 | Prohibit public access |
| EC2 Managed by SSM | EC2_INSTANCE_MANAGED_BY_SSM | Req 2 | System configuration management |
| RDS Encryption | RDS_STORAGE_ENCRYPTED | Req 3.4 | Render data unreadable |
| ELB TLS Only | ELB_TLS_HTTPS_LISTENERS_ONLY | Req 4.1 | Strong cryptography |
| IAM No Admin Policy | IAM_POLICY_NO_STATEMENTS_WITH_ADMIN_ACCESS | Req 7.1 | Need-to-know access |
| IAM MFA Enabled | IAM_USER_MFA_ENABLED | Req 8.3 | Multi-factor auth |
| CloudWatch Alarm Action | CLOUDWATCH_ALARM_ACTION_CHECK | Req 10.6 | Daily log review |
| GuardDuty Enabled | GUARDDUTY_ENABLED_CENTRALIZED | Req 11.4 | Intrusion detection |

### GDPR-Specific Rules (8 Rules)

Deploy only when `complianceFrameworks` includes "GDPR":

| Rule Name | AWS Managed Rule ID | GDPR Article Mapping | Purpose |
|-----------|-------------------|---------------------|---------|
| EC2 EBS Optimized | EC2_EBS_OPTIMIZATION_CHECK | Art 25 | Data protection by design |
| VPC Flow Logs | VPC_FLOW_LOGS_ENABLED | Art 30(1) | Records of processing |
| S3 KMS Encryption | S3_DEFAULT_ENCRYPTION_KMS | Art 32(1)(a) | Pseudonymisation/encryption |
| KMS Key Rotation | CMK_BACKING_KEY_ROTATION_ENABLED | Art 32(1)(d) | Security measures testing |
| Restricted RDP | RESTRICTED_INCOMING_TRAFFIC | Art 32(1)(b) | Access control |
| DynamoDB Autoscaling | DYNAMODB_AUTOSCALING_ENABLED | Art 25 | Privacy by design |
| S3 Replication | S3_BUCKET_REPLICATION_ENABLED | Art 32(1)(c) | Resilience of systems |
| GuardDuty Findings | GUARDDUTY_NON_ARCHIVED_FINDINGS | Art 32(1)(d) | Testing effectiveness |

**Total Unique Rules When All Frameworks Enabled: 40** (9 base + 31 framework-specific)

---

## Evidence Collection for Auditors

### 1. Infrastructure Configuration Evidence

**Location**: Git repository (`github.com/yourdomain/cfc-core`)
**Evidence Type**: Configuration baseline

**Files to Review:**
- `cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java` - AWS Config rules definitions
- `cloudforge-api/src/main/java/com/cloudforgeci/api/observability/GuardDutyFactory.java` - Threat detection setup
- `cloudforge-api/src/main/java/com/cloudforgeci/api/constructs/SecurityConstruct.java` - IAM policies and encryption
- `cfc-testing/deployment-context.json` - Deployment configuration with compliance frameworks
- `.github/workflows/deployment-testing.yml` - CI/CD pipeline with approval gates
- `cfc-testing/test-results/enhanced-synth-results/FARGATE-PRODUCTION-*.json` - Synthesized CloudFormation templates

**Example File Path for Synthesized Template:**
```
cfc-testing/test-results/enhanced-synth-results/
  └── FARGATE-PRODUCTION-alb-oidc-private-with-nat-template.json
```

**Code References for Key Controls:**

| Control | Implementation | Line Numbers | Git Commit |
|---------|---------------|--------------|------------|
| IAM Password Policy | `ComplianceFactory.java` | Lines 626, 942 | `549118c` |
| Root Account MFA | `ComplianceFactory.java` | Lines 1323, 1720 | `549118c` |
| IAM User MFA | `ComplianceFactory.java` | Lines 1123, 1542 | `549118c` |
| S3 Public Read Prohibited | `ComplianceFactory.java` | Lines 578, 923 | `549118c` |
| S3 Versioning Enabled | `ComplianceFactory.java` | Lines 587, 930 | `549118c` |
| CloudTrail Enabled | `ComplianceFactory.java` | Lines 855, 961 | `549118c` |
| VPC Flow Logs | `ComplianceFactory.java` | Lines 871, 975, 1393, 1784 | `549118c` |
| RDS Storage Encrypted | `ComplianceFactory.java` | Lines 1087, 1509 | `549118c` |
| GuardDuty Setup | `GuardDutyFactory.java` | Full file | `549118c` |

**How to Verify:**
```bash
# View specific control implementation
git show 549118c:cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java | sed -n '626p'

# View commit details
git show 549118c --stat

# View file at specific line
sed -n '626,650p' cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java
```

**Audit Assertion**: Infrastructure deployed matches source code (immutable infrastructure)

### 2. AWS Config Compliance Reports

**Location**: AWS Console > Config > Dashboard
**Evidence Type**: Continuous monitoring

**Console Navigation:**
1. Log into AWS Console
2. Navigate to: **Services** > **Management & Governance** > **AWS Config**
3. Click **Dashboard** in left sidebar
4. View **Compliance status** widget showing COMPLIANT/NON_COMPLIANT counts
5. Click **Rules** to see individual Config rule status
6. For specific rule details: Click rule name > **Compliance timeline** tab

**CLI Access:**
```bash
# Get compliance summary
aws configservice describe-compliance-by-config-rule \
  --output json > config-compliance-$(date +%Y%m%d).json

# Get detailed compliance for specific rule
aws configservice get-compliance-details-by-config-rule \
  --config-rule-name iam-password-policy \
  --output json

# Get compliance summary for all rules (count)
aws configservice get-compliance-summary-by-config-rule \
  --output table
```

**Evidence Screenshot Checklist:**
- [ ] Config Dashboard showing overall compliance percentage
- [ ] Rules list filtered by compliance status (COMPLIANT)
- [ ] Sample of 5-10 individual rule compliance timelines
- [ ] Non-compliant resources (if any) with timestamps

**Audit Assertion**: Controls are continuously monitored and non-compliance is detected

### 3. CloudTrail Audit Logs

**Location**: S3 bucket (`s3://your-cloudtrail-bucket/`)
**Evidence Type**: Audit trail
**Retention**: 6 years (HIPAA), 2 years (SOC2), 1 year (PCI-DSS)

**S3 Bucket Path Structure:**
```
s3://<your-cloudtrail-bucket>/
  └── AWSLogs/
      └── <account-id>/
          └── CloudTrail/
              └── <region>/
                  └── YYYY/MM/DD/
                      └── <account-id>_CloudTrail_<region>_YYYYMMDDTHHmmZ_<hash>.json.gz
```

**Example Bucket Name Pattern:**
- Stack name: `fargate-production-alb-oidc-private-with-nat-20`
- CloudTrail bucket: `fargate-production-alb-oidc-private-with-nat-20-cloudtrail-<account-id>`
- Location: `s3://fargate-production-alb-oidc-private-with-nat-20-cloudtrail-<account-id>/AWSLogs/<account-id>/CloudTrail/us-east-1/2025/11/20/`

**Console Navigation:**
1. Navigate to: **Services** > **CloudTrail** > **Event history**
2. Filter by: **Event name**, **User name**, **Resource type**
3. Click individual events to see full JSON details
4. For S3 logs: **Services** > **S3** > Find CloudTrail bucket > Browse folders

**CLI Access:**
```bash
# Find CloudTrail bucket name
aws cloudtrail describe-trails \
  --query 'trailList[*].[Name,S3BucketName]' \
  --output table

# Query recent IAM changes
aws cloudtrail lookup-events \
  --lookup-attributes AttributeKey=EventName,AttributeValue=PutUserPolicy \
  --start-time 2025-01-01T00:00:00Z \
  --end-time 2025-12-31T23:59:59Z

# Verify trail is logging
aws cloudtrail get-trail-status \
  --name <trail-name> \
  --query '{IsLogging:IsLogging,LatestDeliveryTime:LatestDeliveryTime}'

# List trails in your account
aws cloudtrail list-trails \
  --output table
```

**Evidence Collection Checklist:**
- [ ] CloudTrail trail status showing **IsLogging: true**
- [ ] Sample of 20 recent CloudTrail events (API calls)
- [ ] S3 bucket lifecycle policy showing retention configuration
- [ ] CloudTrail log file integrity validation status
- [ ] S3 bucket versioning enabled for tamper protection

**Audit Assertion**: All administrative actions are logged and tamper-evident

### 4. SSM Automation Remediation History

**Location**: AWS Console > Systems Manager > Automation
**Evidence Type**: Remediation effectiveness

**Console Navigation:**
1. Navigate to: **Services** > **Systems Manager** > **Automation**
2. View **Execution status** (Success, Failed, In Progress)
3. Filter by: **Time range**, **Document name**, **Status**
4. Click execution ID to see:
   - **Executed steps** with timestamps
   - **Inputs** (which resource was remediated)
   - **Outputs** (remediation result)
   - **Execution time** (duration)

**CLI Access:**
```bash
# List remediation executions (last 90 days)
aws ssm describe-automation-executions \
  --filters "Key=ExecutionStatus,Values=Success" \
  --max-results 50 \
  --output json

# Get execution details with steps
aws ssm get-automation-execution \
  --automation-execution-id <EXECUTION_ID> \
  --query '{ExecutionId:AutomationExecutionId,Status:AutomationExecutionStatus,StartTime:ExecutionStartTime,EndTime:ExecutionEndTime,DocumentName:DocumentName,Outputs:Outputs}'

# List all executions for specific document (remediation action)
aws ssm describe-automation-executions \
  --filters "Key=DocumentNamePrefix,Values=AWS-EnableS3BucketEncryption" \
  --output table
```

**Example SSM Document ARNs for Common Remediations:**
- **Enable S3 encryption**: `arn:aws:ssm:us-east-1:<account-id>:document/AWS-EnableS3BucketEncryption`
- **Enable CloudTrail**: `arn:aws:ssm:us-east-1:<account-id>:document/AWS-ConfigureCloudTrailLogging`
- **Enable EBS encryption**: `arn:aws:ssm:us-east-1:<account-id>:document/AWS-EnableEBSEncryptionByDefault`
- **Attach MFA policy**: `arn:aws:ssm:us-east-1:<account-id>:document/AWS-AttachIAMToUser`

**Evidence Collection Checklist:**
- [ ] List of all SSM automation executions (last 90 days)
- [ ] Sample of 5 successful remediation executions with details
- [ ] Execution timing analysis (time from non-compliant detection to remediation)
- [ ] Failed executions (if any) with root cause analysis
- [ ] Before/after Config evaluation results

**Audit Assertion**: Non-compliant resources are automatically remediated

### 5. CloudFormation Stack History

**Location**: AWS Console > CloudFormation > Stacks
**Evidence Type**: Change control

**How to Access:**
```bash
# List stack events (deployments)
aws cloudformation describe-stack-events \
  --stack-name jenkinsTSoc \
  --output json

# Get stack template (current configuration)
aws cloudformation get-template \
  --stack-name jenkinsTSoc \
  --query TemplateBody
```

**Audit Assertion**: Infrastructure changes follow change control process (Git → CI/CD → CloudFormation)

---

## Change Management and Version Control

### Git-Based Configuration Management

**Repository**: Public GitHub repository at `https://github.com/CloudForgeCI/cfc-core`
**Version Control System**: Git with GitHub pull request workflow

**Change Control Process:**

1. **Code Changes** → Developers create feature branch from `main`
2. **Pull Request** → Developer opens PR with description of changes
3. **Automated Checks** → GitHub Actions CI/CD pipeline runs:
   - Maven build and unit tests
   - CDK synthesis validation
   - CloudFormation template validation
   - Deployment dry-run tests
4. **Code Review** → Required approver(s) review the changes
5. **Approval Gate** → PR must have approved review before merge
6. **Merge to Main** → Changes merged to main branch
7. **Deployment** → Automated or manual deployment to AWS via CDK

**GitHub Workflow Configuration:**
- File: `.github/workflows/deployment-testing.yml`
- Runs on: Pull requests and pushes to main branch
- Checks: Build, test, synth, deployment validation

**Who Can Approve Changes:**
- Repository administrators (configurable in GitHub settings)
- Code owners specified in `.github/CODEOWNERS` file (if present)
- Users with "Write" or "Admin" permissions on the repository

**Example PR Approval Requirements (Configurable):**
```yaml
# .github/workflows/deployment-testing.yml
# Branch protection rules enforced via GitHub settings:
- Require pull request before merging: ✅
- Require approvals: 1 (configurable)
- Require status checks to pass: ✅
- Require branches to be up to date: ✅
```

**Evidence of Change Control:**

1. **Git Commit History:**
```bash
# View all commits with author and timestamp
git log --oneline --graph --all --decorate

# View commits for specific file (e.g., ComplianceFactory.java)
git log --follow cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java

# View who changed what when
git blame cloudforge-api/src/main/java/com/cloudforgeci/api/observability/ComplianceFactory.java
```

2. **Pull Request Approval History:**
```bash
# GitHub CLI - List PRs with approval status
gh pr list --state merged --limit 50

# View specific PR details including approvals
gh pr view <PR_NUMBER>

# View PR reviews and approvals
gh pr checks <PR_NUMBER>
```

3. **CI/CD Pipeline Status:**
```bash
# View GitHub Actions workflow runs
gh run list --workflow=deployment-testing.yml --limit 20

# View specific workflow run details
gh run view <RUN_ID>

# View workflow run logs
gh run view <RUN_ID> --log
```

**GitHub Console Navigation:**
1. Go to: `https://github.com/CloudForgeCI/cfc-core`
2. Click **Pull requests** tab
3. Filter by: **Merged**, **Closed**, **Author**, **Label**
4. Click individual PR to see:
   - **Files changed** (code diff)
   - **Commits** (individual changes)
   - **Checks** (CI status: passed/failed)
   - **Reviews** (approvals and comments)
   - **Conversation** (discussion and approval timestamps)

**Audit Trail Evidence:**
- [ ] Git commit log showing all infrastructure changes (last 12 months)
- [ ] Sample of 10 merged pull requests with approval evidence
- [ ] GitHub Actions workflow run history (CI status checks)
- [ ] Branch protection rules screenshot showing approval requirements
- [ ] List of users with write/admin access to repository

**Audit Assertion**: All infrastructure changes follow documented change control process with approval and testing before deployment

---

## Sample Audit Testing Procedures

### Test 1: Verify IAM Password Policy (SOC2 CC6.1.2, HIPAA § 164.308(a)(5)(i)(D))

**Control Objective**: Ensure strong passwords are enforced

**Test Steps:**
1. Navigate to AWS Console > IAM > Account settings > Password policy
2. Verify the following settings:
   - ✅ Minimum password length: 12 characters (SOC2) or 14 (HIPAA)
   - ✅ Require uppercase letters
   - ✅ Require lowercase letters
   - ✅ Require numbers
   - ✅ Require symbols
   - ✅ Password expiration: 90 days
   - ✅ Password reuse prevention: 12 (SOC2) or 24 (HIPAA)

3. Verify Config rule is compliant:
```bash
aws configservice describe-compliance-by-config-rule \
  --config-rule-names iam-password-policy \
  --query 'ComplianceByConfigRules[0].Compliance.ComplianceType'
```

**Expected Result**: COMPLIANT
**Evidence**: Screenshot of IAM password policy + Config rule status

---

### Test 2: Verify Encryption at Rest (SOC2 CC6.6.1, HIPAA § 164.312(a)(2)(iv), PCI Req 3.5)

**Control Objective**: Ensure all data is encrypted at rest

**Test Steps:**
1. Select sample of 10 EBS volumes:
```bash
aws ec2 describe-volumes \
  --query 'Volumes[*].[VolumeId,Encrypted]' \
  --output table
```

2. Verify ALL volumes show Encrypted: True

3. Verify Config rule is compliant:
```bash
aws configservice describe-compliance-by-config-rule \
  --config-rule-names encrypted-volumes
```

4. Repeat for RDS databases:
```bash
aws rds describe-db-instances \
  --query 'DBInstances[*].[DBInstanceIdentifier,StorageEncrypted]' \
  --output table
```

**Expected Result**: All resources encrypted
**Evidence**: CLI output showing encryption status + Config rule compliance

---

### Test 3: Verify Audit Log Retention (SOC2 CC6.7.2, HIPAA § 164.312(b), PCI Req 10.4.1)

**Control Objective**: Ensure logs are retained per compliance requirements

**Test Steps:**
1. Identify CloudTrail S3 bucket:
```bash
aws cloudtrail describe-trails \
  --query 'trailList[0].S3BucketName'
```

2. Review S3 lifecycle policy:
```bash
aws s3api get-bucket-lifecycle-configuration \
  --bucket <BUCKET_NAME>
```

3. Verify transitions match compliance profile:
   - SOC2: 2 years (730 days)
   - HIPAA: 6 years (2190 days)
   - PCI-DSS: 1 year (365 days), 90 days immediately available

4. Test log files exist from required retention period:
```bash
# For SOC2 (2 years)
aws s3 ls s3://<BUCKET_NAME>/AWSLogs/123456789012/CloudTrail/ \
  --recursive | grep "$(date -d '2 years ago' +%Y/%m)"
```

**Expected Result**: Logs exist for entire retention period, lifecycle policy configured correctly
**Evidence**: S3 lifecycle policy JSON + log file listing

---

## Evidence Retention and S3 Lifecycle Policies

### Overview

CloudForge CI automatically configures S3 bucket lifecycle policies to meet compliance framework retention requirements. Retention periods vary by compliance profile and log type.

### Retention Requirements by Framework

| Framework | Audit Logs | Application Logs | Backup Retention | Legal Basis |
|-----------|-----------|-----------------|------------------|-------------|
| **SOC2** | 2 years | 2 years | 2 years | Industry standard (AICPA guidance) |
| **HIPAA** | 6 years | 6 years | 6 years | 45 CFR § 164.316(b)(2)(i) |
| **PCI-DSS** | 1 year (90 days immediate) | 1 year | 1 year | PCI-DSS Req 10.5.1 |
| **GDPR** | Varies (purpose-based) | Varies | Varies | Article 5(1)(e) - storage limitation |

### S3 Lifecycle Policy Configuration

**Location of Lifecycle Policies:**
```
CloudFormation Template:
  └── Resources:
      └── CloudTrailBucket (Type: AWS::S3::Bucket)
          └── LifecycleConfiguration:
              └── Rules:
                  ├── Transition to Glacier (cost optimization)
                  └── Expiration (retention limit)
```

**Example S3 Bucket Names:**
- CloudTrail logs: `<stack-name>-cloudtrail-<account-id>`
- ALB access logs: `<stack-name>-alb-logs-<account-id>`
- VPC Flow Logs: `<stack-name>-vpc-flowlogs-<account-id>`

### Checking S3 Lifecycle Policies (Auditor Instructions)

**Console Navigation:**
1. Navigate to: **Services** > **S3**
2. Find CloudTrail bucket (e.g., `fargate-production-alb-oidc-private-with-nat-20-cloudtrail-<account-id>`)
3. Click bucket name > **Management** tab > **Lifecycle rules**
4. Review each lifecycle rule:
   - **Rule name** (e.g., "CloudTrail-Retention-SOC2")
   - **Transition actions** (e.g., move to Glacier after 90 days)
   - **Expiration actions** (e.g., delete after 730 days for SOC2)

**CLI Access:**
```bash
# Get lifecycle configuration for CloudTrail bucket
aws s3api get-bucket-lifecycle-configuration \
  --bucket <cloudtrail-bucket-name> \
  --output json

# Example output interpretation:
{
  "Rules": [
    {
      "ID": "CloudTrail-Retention-SOC2",
      "Status": "Enabled",
      "Transitions": [
        {
          "Days": 90,
          "StorageClass": "GLACIER"
        }
      ],
      "Expiration": {
        "Days": 730  # 2 years for SOC2
      }
    }
  ]
}

# List all S3 buckets with lifecycle policies
aws s3api list-buckets --query 'Buckets[*].Name' --output text | \
  while read bucket; do
    echo "Bucket: $bucket"
    aws s3api get-bucket-lifecycle-configuration --bucket $bucket 2>/dev/null || echo "No lifecycle policy"
    echo "---"
  done
```

### Lifecycle Policy Examples by Compliance Framework

#### SOC2 Lifecycle Policy (2-Year Retention)

```json
{
  "Rules": [
    {
      "ID": "SOC2-CloudTrail-Retention",
      "Status": "Enabled",
      "Prefix": "AWSLogs/",
      "Transitions": [
        {
          "Days": 90,
          "StorageClass": "STANDARD_IA"
        },
        {
          "Days": 180,
          "StorageClass": "GLACIER"
        }
      ],
      "Expiration": {
        "Days": 730
      }
    }
  ]
}
```

**Explanation:**
- Days 0-90: S3 Standard storage (frequent access)
- Days 90-180: S3 Standard-IA (infrequent access, cost savings)
- Days 180-730: Glacier storage (long-term archive, lowest cost)
- Day 730+: Automatic deletion (end of retention period)

#### HIPAA Lifecycle Policy (6-Year Retention)

```json
{
  "Rules": [
    {
      "ID": "HIPAA-CloudTrail-Retention",
      "Status": "Enabled",
      "Prefix": "AWSLogs/",
      "Transitions": [
        {
          "Days": 90,
          "StorageClass": "STANDARD_IA"
        },
        {
          "Days": 365,
          "StorageClass": "GLACIER"
        },
        {
          "Days": 730,
          "StorageClass": "DEEP_ARCHIVE"
        }
      ],
      "Expiration": {
        "Days": 2190
      }
    }
  ]
}
```

**Explanation:**
- Days 0-90: S3 Standard (immediate retrieval)
- Days 90-365: S3 Standard-IA (occasional access)
- Days 365-730: Glacier (archive, hours retrieval)
- Days 730-2190: Glacier Deep Archive (long-term, 12-hour retrieval)
- Day 2190+: Automatic deletion (6 years per HIPAA § 164.316)

#### PCI-DSS Lifecycle Policy (1-Year Retention, 90-Day Immediate Access)

```json
{
  "Rules": [
    {
      "ID": "PCI-DSS-CloudTrail-Retention",
      "Status": "Enabled",
      "Prefix": "AWSLogs/",
      "Transitions": [
        {
          "Days": 90,
          "StorageClass": "GLACIER"
        }
      ],
      "Expiration": {
        "Days": 365
      }
    }
  ]
}
```

**Explanation:**
- Days 0-90: S3 Standard (immediate access per PCI Req 10.5.1)
- Days 90-365: Glacier (archive remaining 9 months)
- Day 365+: Automatic deletion (1-year retention)

### Verifying Log File Existence (Sampling)

**Test if logs exist for full retention period:**

```bash
# Check oldest CloudTrail log (SOC2 = 2 years)
BUCKET_NAME="fargate-production-alb-oidc-private-with-nat-20-cloudtrail-123456789012"
TARGET_DATE=$(date -d '2 years ago' +%Y/%m/%d)

# List logs from 2 years ago
aws s3 ls s3://${BUCKET_NAME}/AWSLogs/123456789012/CloudTrail/us-east-1/${TARGET_DATE}/ \
  --recursive

# Expected: Log files should exist (proving 2-year retention is working)
# If empty: Retention policy may not be working or stack is < 2 years old

# Check log count by month (verify continuous coverage)
for MONTH in {1..24}; do
  CHECK_DATE=$(date -d "$MONTH months ago" +%Y/%m)
  COUNT=$(aws s3 ls s3://${BUCKET_NAME}/AWSLogs/123456789012/CloudTrail/us-east-1/ \
    --recursive | grep "$CHECK_DATE" | wc -l)
  echo "$CHECK_DATE: $COUNT files"
done
```

### Auditor Evidence Checklist for Retention

- [ ] S3 lifecycle policy JSON for CloudTrail bucket
- [ ] S3 lifecycle policy JSON for ALB logs bucket
- [ ] S3 lifecycle policy JSON for VPC Flow Logs bucket
- [ ] Sample log file listing showing dates across retention period
- [ ] Storage class transitions verified (Standard → IA → Glacier)
- [ ] Expiration period matches compliance requirement
- [ ] S3 bucket versioning enabled (prevents tampering)
- [ ] S3 bucket encryption enabled (SSE-S3 or SSE-KMS)

### Cost Optimization vs. Compliance

**Balance:**
- Frequent access (0-90 days): S3 Standard ($0.023/GB)
- Occasional access (90-365 days): S3 Standard-IA ($0.0125/GB)
- Archive (1+ years): Glacier ($0.004/GB) or Deep Archive ($0.00099/GB)

**Compliance Note:**
PCI-DSS requires 90 days of logs be "immediately available" (S3 Standard). Logs older than 90 days can be in Glacier (hours retrieval time) to save costs while maintaining 1-year retention.

### GDPR Storage Limitation (Article 5(1)(e))

**Key Principle:** Personal data shall not be kept longer than necessary.

**CloudForge Approach:**
- Technical logs (CloudTrail, VPC Flow Logs) contain minimal personal data (IP addresses)
- Retention periods match other compliance requirements (SOC2, HIPAA, PCI-DSS)
- Organizations can override retention periods via `logRetentionDays` parameter

**Example GDPR-Specific Configuration:**
```json
{
  "logRetentionDays": "365",  // 1 year for GDPR
  "complianceFrameworks": "GDPR"
}
```

This will set S3 lifecycle expiration to 365 days instead of longer retention periods.

**Auditor Note:** Organizations must demonstrate that retention periods are necessary for legal obligations or legitimate business purposes. Retention beyond GDPR minimization should be justified (e.g., HIPAA legal requirement for 6-year retention).

---

### Test 4: Verify MFA Enforcement (SOC2 CC6.1.2, HIPAA § 164.312(d), PCI Req 8.3.1)

**Control Objective**: Multi-factor authentication required for all users

**Test Steps:**
1. List all IAM users:
```bash
aws iam list-users \
  --query 'Users[*].[UserName,CreateDate]' \
  --output table
```

2. For sample of 10 users, verify MFA enabled:
```bash
aws iam list-mfa-devices \
  --user-name <USERNAME>
```

3. Verify Config rule compliance:
```bash
aws configservice describe-compliance-by-config-rule \
  --config-rule-names iam-user-mfa-enabled root-account-mfa-enabled
```

4. Verify NO users are non-compliant:
```bash
aws configservice get-compliance-details-by-config-rule \
  --config-rule-name iam-user-mfa-enabled \
  --compliance-types NON_COMPLIANT
```

**Expected Result**: All users have MFA enabled, Config rule COMPLIANT
**Evidence**: User MFA status report + Config rule compliance

---

### Test 5: Verify Remediation Effectiveness (SOC2 CC7.2.3)

**Control Objective**: Non-compliant resources are automatically remediated

**Test Steps:**
1. Review SSM Automation executions for past 90 days:
```bash
aws ssm describe-automation-executions \
  --max-results 50 \
  --filters "Key=ExecutionStatus,Values=Success,Failed"
```

2. For sample of 5 executions, verify:
   - Execution triggered by Config rule
   - Execution completed successfully
   - Resource became compliant after execution

3. Test remediation timing (should be < 5 minutes):
   - Review Config rule evaluation timestamp
   - Review SSM execution start timestamp
   - Calculate time delta

**Expected Result**: All remediations completed successfully within 5 minutes
**Evidence**: SSM execution history + timing analysis

---

## Guidance for Auditors: Management Letter Language

### Deficiency Report Guidance for Out-of-Scope Controls

When controls are identified as not implemented because they are outside the scope of infrastructure automation, auditors can use the following language in management letters:

#### Example 1: Organizational Policies (SOC2, PCI-DSS, HIPAA, GDPR)

**Finding:**
"The organization has implemented automated technical controls for infrastructure security via CloudForge CI (16 AWS Config rules for SOC2). However, organizational controls such as security policies, employee training programs, and documented procedures were not in scope for this infrastructure automation assessment."

**Management Response (Suggested):**
"CloudForge CI provides technical infrastructure controls representing approximately 17% of SOC2 Trust Service Criteria. The organization acknowledges that organizational policies, employee awareness training, and documented procedures (representing the remaining 83%) must be implemented separately and are maintained outside of the infrastructure automation system."

**Recommendation:**
"Management should document and implement organizational security policies, procedures, and training programs to complement the automated infrastructure controls. These should be reviewed annually and updated as needed."

#### Example 2: Application-Level Controls (PCI-DSS Req 3-4)

**Finding:**
"PCI-DSS Requirements 3 and 4 (cardholder data handling and transmission) cannot be fully addressed at the infrastructure level. The infrastructure provides encryption capabilities (TLS 1.2+, KMS encryption), but how applications handle cardholder data (PAN masking, data flow restrictions, storage limitations) is application-specific and outside the scope of infrastructure automation."

**Management Response (Suggested):**
"CloudForge CI provides encryption infrastructure and network security controls for PCI-DSS compliance. Application-level handling of cardholder data (Requirements 3-4) is implemented in our Jenkins pipelines and custom applications, which are subject to separate security reviews and code audits."

**Recommendation:**
"Management should conduct application-level security assessments to validate that Jenkins pipelines and custom applications properly implement PCI-DSS Requirements 3-4 for cardholder data handling. This should include code reviews, penetration testing, and application security scanning."

#### Example 3: Data Subject Rights (GDPR Art 15-22)

**Finding:**
"GDPR Data Subject Rights (Articles 15-22: access, rectification, erasure, portability) require business process workflows that cannot be fully automated at the infrastructure level. CloudForge CI provides audit logs and encryption, but DSR request fulfillment requires human review and business logic."

**Management Response (Suggested):**
"The organization acknowledges that GDPR Data Subject Rights require business processes beyond infrastructure automation. CloudForge CI provides the technical foundation (audit logs, access controls, encryption) to support DSR fulfillment. The organization has established manual procedures for receiving, validating, and fulfilling DSR requests using the audit logs provided by CloudForge CI infrastructure."

**Recommendation:**
"Management should document and implement GDPR Data Subject Rights procedures, including DSR request intake, identity verification, data retrieval workflows, and response timelines (within 30 days per GDPR Article 12). CloudForge audit logs can be used to identify personal data locations and processing activities."

#### Example 4: Third-Party Testing (PCI-DSS Req 11)

**Finding:**
"PCI-DSS Requirement 11 mandates quarterly external vulnerability scans by an Approved Scanning Vendor (ASV) and annual penetration testing. These requirements cannot be automated within the infrastructure and require engagement with qualified third-party security firms."

**Management Response (Suggested):**
"The organization acknowledges the requirement for external vulnerability scans and penetration testing. CloudForge CI provides infrastructure hardening and continuous monitoring, which reduces vulnerabilities. The organization will engage an Approved Scanning Vendor (ASV) for quarterly external scans and qualified penetration testers for annual assessments as required by PCI-DSS Requirement 11."

**Recommendation:**
"Management should establish contracts with an Approved Scanning Vendor (ASV) for quarterly external vulnerability scans and qualified penetration testing firms for annual assessments. Results should be tracked, remediated, and documented for QSA review."

---

## GDPR Data Subject Rights and Privacy Guidance

### Overview for Auditors

**Key Point:** GDPR is predominantly an organizational and legal framework. CloudForge CI provides **Article 32 technical safeguards** (encryption, access control, audit logging), but **Data Subject Rights (DSR)** and **breach notification** require business processes that cannot be fully automated.

### What CloudForge CI Provides (GDPR Technical Foundation)

✅ **Article 32 - Security of Processing:**
- Encryption at rest (EBS, RDS, S3) via KMS
- Encryption in transit (TLS 1.2+ for ALB listeners)
- Access controls (IAM policies, security groups)
- Audit logging (CloudTrail, VPC Flow Logs)
- Continuous monitoring (AWS Config)

✅ **Article 30 - Records of Processing (Partial Support):**
- CloudTrail logs show what data was accessed and by whom
- VPC Flow Logs show network traffic patterns
- CloudWatch Logs show application-level processing

✅ **Article 25 - Data Protection by Design:**
- Encryption enabled by default
- Least privilege IAM policies
- Private subnets with NAT gateway (network isolation)

### What Requires Business Process Implementation

❌ **Articles 15-22 - Data Subject Rights (DSR):**

These cannot be automated and require human review:

1. **Article 15 - Right of Access:**
   - DSR request: "What personal data do you have about me?"
   - **Process Required**: Query CloudTrail/CloudWatch logs, application databases, backups
   - **CloudForge Support**: Audit logs help identify where data is stored and processed
   - **Response Time**: 30 days (GDPR Article 12)

2. **Article 16 - Right to Rectification:**
   - DSR request: "Correct my personal data"
   - **Process Required**: Update application databases, verify identity
   - **CloudForge Support**: Access controls ensure only authorized personnel can modify data

3. **Article 17 - Right to Erasure ("Right to be Forgotten"):**
   - DSR request: "Delete my personal data"
   - **Process Required**: Delete from databases, backups, logs (with retention exceptions)
   - **CloudForge Support**: S3 lifecycle policies can automate deletion after retention period
   - **Important**: Some logs may have retention requirements (e.g., 6 years for HIPAA) that supersede erasure

4. **Article 20 - Right to Data Portability:**
   - DSR request: "Give me my data in a machine-readable format"
   - **Process Required**: Export from application databases (JSON, CSV, XML)
   - **CloudForge Support**: Secure data export via encrypted S3 buckets

5. **Article 21 - Right to Object:**
   - DSR request: "Stop processing my data for marketing"
   - **Process Required**: Update application consent management, processing restrictions
   - **CloudForge Support**: Access controls prevent unauthorized processing

**Recommended DSR Workflow:**

```
1. DSR Request Received (email, web form, phone)
   ↓
2. Identity Verification (prevent fraudulent requests)
   ↓
3. Data Discovery (query CloudTrail, databases, backups)
   ↓
4. Legal Review (check retention obligations, lawful basis)
   ↓
5. Fulfillment (access/rectify/erase/export/restrict)
   ↓
6. Response to Data Subject (within 30 days)
   ↓
7. Documentation (log DSR request and fulfillment)
```

❌ **Articles 33-34 - Breach Notification:**

Breach notification requires incident response procedures:

1. **Article 33 - Notification to Supervisory Authority:**
   - **Requirement**: Notify within 72 hours of breach discovery
   - **Process Required**: Incident detection, assessment, notification to DPA
   - **CloudForge Support**: GuardDuty findings, CloudTrail anomalies, Config non-compliance alerts

2. **Article 34 - Notification to Data Subjects:**
   - **Requirement**: Notify affected individuals without undue delay
   - **Process Required**: Identify affected users, communication plan, breach disclosure
   - **CloudForge Support**: Audit logs help determine scope and affected users

**Recommended Breach Response Workflow:**

```
1. Detection (GuardDuty alert, Config non-compliance, unauthorized access)
   ↓
2. Containment (isolate compromised systems, revoke credentials)
   ↓
3. Assessment (determine data involved, number of affected individuals)
   ↓
4. Legal Review (determine notification requirements)
   ↓
5. Notification to DPA (within 72 hours)
   ↓
6. Notification to Data Subjects (if high risk)
   ↓
7. Remediation (fix vulnerability, improve controls)
   ↓
8. Documentation (incident report, lessons learned)
```

### Evidence Retention for DSR and Breach Notification

**For DSR Requests:**
- Document all DSR requests received (date, type, requestor)
- Document identity verification steps
- Document fulfillment actions taken
- Document response provided and date sent
- **Retention**: 7 years (demonstrating GDPR compliance)

**For Breach Notification:**
- Document breach discovery date and time
- Document assessment of breach scope (data affected, number of individuals)
- Document notification to DPA (date, method, response)
- Document notification to data subjects (date, method, content)
- **Retention**: 7 years (demonstrating GDPR compliance)

### Auditor Testing for GDPR DSR Compliance

**Test 1: DSR Procedure Documentation**
- [ ] Review written DSR procedures
- [ ] Verify identity verification process documented
- [ ] Confirm response timelines documented (30 days)

**Test 2: DSR Request Log**
- [ ] Review log of DSR requests received (last 12 months)
- [ ] Sample 5 DSR requests and verify fulfillment within 30 days
- [ ] Verify identity verification was performed

**Test 3: Breach Notification Procedures**
- [ ] Review written breach notification procedures
- [ ] Verify DPA contact information documented
- [ ] Confirm 72-hour notification timeline documented

**Test 4: Incident Response Plan**
- [ ] Review incident response plan
- [ ] Verify breach notification procedures included
- [ ] Confirm incident response plan tested annually

---

## Limitations and Gaps

### Controls NOT Implemented

The following controls are outside the scope of automated infrastructure and require organizational implementation:

#### SOC2:
- ❌ Control Environment (CC1.x) - governance, organizational structure
- ❌ Risk Assessment (CC2.x) - business risk analysis
- ❌ Control Activities (CC3.x) - documented policies
- ❌ Vendor Management (CC9.x) - third-party assessments
- ❌ Availability Commitments (A1.x) - SLA definition and monitoring

#### HIPAA:
- ❌ Administrative Safeguards (majority of § 164.308)
- ❌ Physical Safeguards (all of § 164.310)
- ❌ Breach Notification Procedures (§ 164.408)
- ❌ Business Associate Agreements (§ 164.314)
- ❌ Patient Rights (§ 164.524-528)

#### PCI-DSS:
- ❌ Network Diagrams and Documentation (Req 1.1)
- ❌ Cardholder Data Handling (Req 3-4 - application level)
- ❌ Secure Development Lifecycle (Req 6)
- ❌ Physical Security (Req 9)
- ❌ Vulnerability Scanning and Penetration Testing (Req 11)
- ❌ Security Policies and Procedures (Req 12)

#### GDPR:
- ❌ Privacy Notices (Art 13-14)
- ❌ Data Subject Rights Workflow (Art 15-22)
- ❌ Records of Processing Activities (Art 30)
- ❌ Breach Notification (Art 33-34)
- ❌ Data Protection Impact Assessments (Art 35)
- ❌ Data Protection Officer (Art 37)
- ❌ Data Processing Agreements (Art 28)

---

## Auditor Checklist

Use this checklist when auditing CloudForge CI implementations:

### Pre-Audit
- [ ] Receive read-only AWS console access (SecurityAudit IAM policy)
- [ ] Receive read-only Git repository access
- [ ] Obtain deployment context configuration file
- [ ] Identify compliance frameworks in scope (SOC2/HIPAA/PCI-DSS/GDPR)

### Infrastructure Review
- [ ] Review CloudFormation templates in Git repository
- [ ] Verify deployed stack matches source code
- [ ] Review change history (Git commits, PR approvals)
- [ ] Verify CI/CD pipeline configuration

### Control Testing
- [ ] Execute Test 1: IAM Password Policy
- [ ] Execute Test 2: Encryption at Rest
- [ ] Execute Test 3: Audit Log Retention
- [ ] Execute Test 4: MFA Enforcement
- [ ] Execute Test 5: Remediation Effectiveness
- [ ] Sample additional Config rules (minimum 10 rules)

### Evidence Collection
- [ ] Export Config compliance report (JSON)
- [ ] Export CloudTrail logs (sample period)
- [ ] Export SSM Automation execution history
- [ ] Screenshot IAM password policy
- [ ] Screenshot S3 lifecycle policies
- [ ] Export Audit Manager assessment (if available)

### Organizational Controls (Out of Scope)
- [ ] Note that organizational policies must be audited separately
- [ ] Request security policy manual
- [ ] Request employee training records
- [ ] Request incident response plan
- [ ] Request vendor management documentation

### Final Report
- [ ] Document control implementation (Technical controls: 30-40% of requirements)
- [ ] Document testing results for automated controls
- [ ] List controls not implemented (organizational)
- [ ] Provide recommendations for organizational controls
- [ ] Issue management letter for any deficiencies

---

**Document Distribution:**
This document is publicly available as part of the CloudForge CI open-source project. It describes the automated security controls provided by the infrastructure. Organizations using CloudForge CI should customize this document with their specific:
- AWS account IDs and resource ARNs
- S3 bucket names and retention policies
- Contact information and organizational structure (technical lead, security officer, compliance officer)
- Additional organizational controls implemented beyond infrastructure automation

---

**Last Updated**: 2025-11-20 | **Version**: 2.0.6

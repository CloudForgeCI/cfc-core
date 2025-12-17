# SOC2 CC5 - Control Activities & Operational Procedures

**Control**: CC5 - Control Activities
**Status**: Fully Documented
**Last Updated**: 2025-12-16
**Owner**: Infrastructure Administrator

---

## Overview

This document defines operational procedures for security controls deployed by CloudForge CI. These procedures complement the automated technical controls to satisfy SOC2 CC5 (Control Activities) requirements.

---

## Automated Control Activities

CloudForge CI automates the following control activities:

| Control Category | Automated Implementation | SOC2 Mapping |
|-----------------|-------------------------|--------------|
| **Access Control** | IAM roles, security groups, private subnets | CC5.1, CC6.1 |
| **Encryption** | KMS encryption, TLS 1.2+, EFS/RDS/S3 encryption | CC5.1, CC6.7 |
| **Network Security** | VPC isolation, WAF, security groups | CC5.1, CC6.6 |
| **Change Management** | CloudFormation IaC, git versioning | CC5.2, CC8.1 |
| **Backup & Recovery** | AWS Backup, S3 versioning, RDS snapshots | CC5.1, A1.3 |
| **Monitoring** | CloudTrail, GuardDuty, Config, CloudWatch | CC5.3, CC7.2 |

---

## Operational Procedures

### 1. Access Management Procedures

#### 1.1 User Provisioning

**Trigger**: New employee or contractor requires system access

**Procedure**:
1. Submit access request via ticketing system
2. Review against least-privilege principle
3. If approved, create IAM user/role or Cognito account
4. Enable MFA (required for all users)
5. Document in access management log
6. Notify user with secure credential delivery

**CloudForge CI Implementation**:
```java
// CognitoAuthenticationFactory enforces:
.mfa(mfaRequired ? Mfa.REQUIRED : Mfa.OPTIONAL)
.passwordPolicy(PasswordPolicy.builder()
    .minLength(securityProfileConfig.getMinimumPasswordLength())  // 14 chars
    .requireUppercase(true)
    .requireLowercase(true)
    .requireDigits(true)
    .requireSymbols(true)
    .build())
```

#### 1.2 Access Revocation

**Trigger**: Employee termination, role change, or access review finding

**Procedure**:
1. Receive notification of termination/change
2. Disable account within:
   - Immediate: Involuntary termination
   - 24 hours: Voluntary termination
   - 7 days: Role change
3. Remove from all IAM groups/roles
4. Rotate any shared credentials
5. Document in access management log

#### 1.3 Quarterly Access Review

**Frequency**: Quarterly (January, April, July, October)

**Procedure**:
1. Generate access report from IAM/Cognito
2. Review and certify each user's access
3. Remove any unauthorized access
4. Document review completion

---

### 2. Change Management Procedures

#### 2.1 Infrastructure Changes

**All infrastructure changes must follow GitOps workflow**:

```
Developer creates feature branch
         ↓
Implement changes in CDK/CloudFormation
         ↓
Run local tests (cdk synth, unit tests)
         ↓
Create Pull Request
         ↓
Automated CI checks:
  - CFN Guard validation
  - Compliance rule checks
  - Unit tests
         ↓
Peer review & approval (minimum 1 reviewer)
         ↓
Merge to main branch
         ↓
Automated deployment to staging
         ↓
Staging validation
         ↓
Production deployment (with approval)
         ↓
Post-deployment verification
```

**CloudForge CI Implementation**:
- All changes tracked in git (CC8.1-IaC)
- CloudFormation drift detection (CC8.1-Config)
- Automated compliance validation pre-deployment

#### 2.2 Emergency Changes

**Definition**: Changes required to restore service or prevent imminent security breach

**Procedure**:
1. Identify emergency condition
2. Notify stakeholders as appropriate
3. Implement minimal fix to restore service
4. Document emergency change with justification
5. Create follow-up ticket for proper review
6. Conduct post-incident review within 48 hours

**Documentation Required**:
- Incident ticket number
- Justification for emergency
- Changes made
- Approver name
- Post-incident review date

---

### 3. Encryption Key Management

#### 3.1 Key Rotation

**Automatic Rotation** (CloudForge CI managed):
- KMS keys: Annual automatic rotation enabled
- Secrets Manager: 30-day rotation (when configured)
- TLS certificates: ACM automatic renewal

**CloudForge CI Implementation**:
```java
// RdsFactory creates KMS key with rotation
Key.Builder.create(scope, instanceId + "EncryptionKey")
    .enableKeyRotation(true)  // Annual automatic rotation
    .build();
```

#### 3.2 Key Access

- KMS keys restricted to specific IAM roles
- Key policies follow least-privilege
- Key usage logged in CloudTrail
- No direct key material access

---

### 4. Backup & Recovery Procedures

#### 4.1 Backup Schedule

| Resource | Frequency | Retention | Cross-Region |
|----------|-----------|-----------|--------------|
| RDS | Daily | 30 days (prod) | Yes (prod) |
| EFS | Daily | 90 days (prod) | Yes (prod) |
| S3 (logs) | Versioned | 6 years | Optional |
| Secrets | Versioned | 30 days | N/A |

**CloudForge CI Implementation**:
```java
// BackupFactory creates automated backups
BackupPlanRule.Builder.create()
    .ruleName("DailyBackup")
    .scheduleExpression(Schedule.cron(hour("3"), minute("0")))
    .deleteAfter(Duration.days(retentionDays))
    .build();
```

#### 4.2 Recovery Testing

**Frequency**: Quarterly

**Procedure**:
1. Select test recovery scenario
2. Restore backup to isolated environment
3. Validate data integrity
4. Document recovery time and success
5. Update recovery runbooks if needed

**Documentation**: Recovery test results stored in `[INTERNAL]/disaster-recovery/tests/`

---

### 5. Security Patch Management

#### 5.1 Container Image Patching (ECS/Fargate)

**Automated**:
- ECR image scanning on push (vulnerabilities detected automatically)
- Container images rebuilt with latest base via CI/CD
- Automated deployment rolls out patched images

**Process**:
1. Base image updated in Dockerfile
2. CI/CD rebuilds and pushes to ECR
3. ECR scan detects vulnerabilities
4. ECS service updated with new task definition
5. Rolling deployment replaces containers

#### 5.2 Database Patching (RDS)

**Automated** (for PRODUCTION):
- Minor version upgrades applied automatically during maintenance window
- Security patches included in minor versions

**CloudForge CI Implementation**:
```java
// RdsFactory enables automatic patching for production
.autoMinorVersionUpgrade(security == SecurityProfile.PRODUCTION)
```

#### 5.3 Application Dependencies

**Procedure**:
1. Dependabot/Snyk scans identify vulnerabilities
2. Triage findings by severity
3. Critical/High: Patch within 7 days
4. Medium: Patch within 30 days
5. Low: Patch in next release cycle

---

### 6. Incident Response Procedures

#### 6.1 Incident Classification

| Severity | Definition | Response Time |
|----------|------------|---------------|
| **SEV-1** | Service down, data breach, active attack | 15 minutes |
| **SEV-2** | Degraded service, potential breach | 1 hour |
| **SEV-3** | Minor impact, no data at risk | 4 hours |
| **SEV-4** | Informational, no immediate impact | 24 hours |

#### 6.2 Incident Response Flow

```
Detection (automated or manual)
         ↓
    Triage & Classification
         ↓
    Containment
         ↓
    Eradication
         ↓
    Recovery
         ↓
    Post-Incident Review
         ↓
    Documentation & Lessons Learned
```

**Reference**: Full incident response plan in `docs/compliance/INCIDENT_RESPONSE_PLAN.md`

---

## Procedure Review Schedule

| Procedure | Review Frequency |
|-----------|-----------------|
| Access Management | Quarterly |
| Change Management | Annually |
| Key Management | Annually |
| Backup & Recovery | Semi-annually |
| Patch Management | Quarterly |
| Incident Response | Annually |

---

## Evidence Collection

### Automated Evidence

- Git commit history (change management)
- CloudFormation stack events (deployments)
- AWS Config compliance history
- CloudTrail API logs

### Manual Evidence

| Evidence Type | Location | Retention |
|---------------|----------|-----------|
| Access review certifications | Ticketing system | 7 years |
| Change request tickets | Ticketing system | 7 years |
| Incident tickets | Ticketing system | 7 years |
| Recovery test reports | SharePoint/Confluence | 7 years |
| Procedure review records | SharePoint/Confluence | 7 years |

---

**Document Control**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-12-16 | CloudForge CI | Initial release |

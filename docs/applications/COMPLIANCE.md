# ApplicationSpec Compliance Requirements

This document outlines compliance considerations for CloudForge ApplicationSpecs across various regulatory frameworks.

## Compliance Framework Overview

CloudForge supports the following compliance frameworks:
- **SOC 2** - Service Organization Control 2 (Trust Services Criteria)
- **PCI-DSS** - Payment Card Industry Data Security Standard
- **HIPAA** - Health Insurance Portability and Accountability Act
- **GDPR** - General Data Protection Regulation
- **FERPA** - Family Educational Rights and Privacy Act

## Applications with Compliance Requirements

### Databases (HIGH COMPLIANCE RISK)

#### PostgreSQL ⚠️ **HIGH RISK**
**Compliance Frameworks**: ALL (SOC2, PCI-DSS, HIPAA, GDPR, FERPA)

**Why**: Databases store sensitive data including:
- Personal Identifiable Information (PII)
- Protected Health Information (PHI) - HIPAA
- Payment Card Data - PCI-DSS
- Customer data - SOC2, GDPR
- Student records - FERPA

**Required Controls**:
- ✅ **Encryption at Rest**: EFS/EBS encryption (automatic in CloudForge)
- ✅ **Encryption in Transit**: SSL/TLS connections required
- ⚠️ **Access Control**: Strong passwords, no defaults
- ⚠️ **Audit Logging**: Enable PostgreSQL audit extension (pgAudit)
- ⚠️ **Backup/Retention**: Automated backups with retention policies
- ⚠️ **Data Masking**: Required for non-production environments
- ⚠️ **Network Isolation**: Private subnets only for production

**PCI-DSS Specific** (Storing cardholder data):
- Req 3.4: Encryption of cardholder data
- Req 3.5.1: Restrict access to cryptographic keys
- Req 8.2: Multi-factor authentication for administrators
- Req 10.2: Audit trail for all access to cardholder data

**HIPAA Specific** (Storing PHI):
- §164.312(a)(2)(iv): Encryption and decryption
- §164.312(b): Audit controls
- §164.312(c)(1): Integrity controls
- §164.312(d): Person or entity authentication

**Implementation**:
```java
// CloudForge automatically handles:
// - EFS encryption at rest ✓
// - Security groups for network isolation ✓
// - CloudWatch audit logging ✓

// User must configure:
// - Strong passwords (use AWS Secrets Manager)
// - SSL/TLS connections
// - Database-level audit logging (pgAudit)
```

---

#### Redis ⚠️ **HIGH RISK**
**Compliance Frameworks**: ALL (when storing sensitive data)

**Why**: Often used for:
- Session storage (PII, authentication tokens)
- Cache for sensitive data
- Message queues with customer data

**Required Controls**:
- ✅ **Encryption at Rest**: EFS/EBS encryption
- ✅ **Authentication**: Password required (default: changeme - MUST change!)
- ⚠️ **Encryption in Transit**: Enable TLS mode
- ⚠️ **Key Expiration**: Set TTLs for sensitive data
- ⚠️ **Network Isolation**: Private subnets only

**PCI-DSS Considerations**:
- Session tokens are considered "sensitive authentication data"
- Redis must NOT store unencrypted cardholder data
- If used for caching, implement key expiration

**HIPAA Considerations**:
- PHI in cache must be encrypted and expire quickly
- Access logs required for audit trails

---

### Version Control Systems (MEDIUM-HIGH COMPLIANCE RISK)

#### GitLab ⚠️ **MEDIUM-HIGH RISK**
**Compliance Frameworks**: SOC2, GDPR, FERPA (HIPAA/PCI-DSS if storing regulated code)

**Why**: Source code repositories may contain:
- Secrets/credentials accidentally committed
- Personally identifiable information (PII) in code/comments
- Customer data in test fixtures
- Proprietary algorithms and business logic

**Required Controls**:
- ✅ **Encryption at Rest**: EFS/EBS encryption
- ⚠️ **Access Control**: Strong authentication (SSO/MFA recommended)
- ⚠️ **Audit Logging**: Enable GitLab audit events
- ⚠️ **Secret Scanning**: Enable secret detection
- ⚠️ **Branch Protection**: Protect main branches
- ⚠️ **Code Review**: Required for compliance-sensitive repos

**SOC2 CC6.1** (Logical Access):
- Implement MFA for all users
- Regular access reviews
- Disable inactive accounts

**GDPR Article 32** (Security of Processing):
- Data breach notification capability
- Access logs for user data
- Right to erasure (delete user accounts/data)

---

#### Gitea ⚠️ **MEDIUM RISK**
**Compliance Frameworks**: SOC2, GDPR, FERPA

**Similar requirements to GitLab** but lighter weight:
- Encryption at rest ✓
- Strong authentication required
- Enable built-in access logging
- Regular security updates

---

### CI/CD Systems (MEDIUM COMPLIANCE RISK)

#### Jenkins ⚠️ **MEDIUM RISK**
**Compliance Frameworks**: SOC2, PCI-DSS (if building payment systems), HIPAA (if deploying healthcare apps)

**Why**: CI/CD systems have access to:
- Source code repositories
- Cloud credentials (AWS, GCP, Azure)
- Deployment secrets
- Build artifacts
- Production environments

**Required Controls**:
- ✅ **Encryption at Rest**: EFS/EBS encryption
- ⚠️ **Credential Management**: Use Credentials Plugin with encryption
- ⚠️ **Access Control**: Role-based access (Matrix Authorization)
- ⚠️ **Audit Logging**: Enable Audit Trail plugin
- ⚠️ **Build Isolation**: Separate agents for prod/non-prod
- ⚠️ **Artifact Scanning**: Security scanning before deployment

**PCI-DSS Req 6.3.2**:
- Secure code review before production
- Separation of dev/test from production
- Code change tracking and approval

**SOC2 CC8.1** (Change Management):
- Approval required for production deployments
- Rollback capability
- Audit trail of all changes

---

#### GitLab CI/CD (included in GitLab)
**Same compliance requirements as GitLab VCS** plus:
- Runner isolation
- Secret masking in logs
- Protected variables for production

---

#### Drone ⚠️ **MEDIUM RISK**
**Compliance Frameworks**: SOC2

**Similar to Jenkins**:
- Secret management required
- Build isolation
- Audit logging

---

### Monitoring Systems (LOW-MEDIUM COMPLIANCE RISK)

#### Grafana ⚠️ **LOW-MEDIUM RISK**
**Compliance Frameworks**: SOC2, HIPAA (if displaying PHI metrics)

**Why**: Dashboards may display:
- System metrics with customer identifiers
- Application logs with PII
- Security monitoring data

**Required Controls**:
- ✅ **Encryption at Rest**: EFS/EBS encryption
- ⚠️ **Access Control**: Enable authentication (not anonymous)
- ⚠️ **Data Minimization**: Don't display PII/PHI in dashboards
- ⚠️ **Audit Logging**: Track dashboard access

**SOC2 A1.2** (Monitoring):
- Grafana is a monitoring control itself
- Access to monitoring data must be restricted
- Alerts for security events

---

#### Prometheus ⚠️ **LOW-MEDIUM RISK**
**Compliance Frameworks**: SOC2

**Why**: Metrics collection for monitoring

**Required Controls**:
- ✅ **Encryption at Rest**: EFS/EBS encryption
- ⚠️ **Network Isolation**: Not publicly accessible
- ⚠️ **Data Retention**: Define retention policies
- ⚠️ **Label Security**: Don't include PII in metric labels

---

## Compliance Matrix

| Application | SOC2 | PCI-DSS | HIPAA | GDPR | FERPA | Risk Level |
|-------------|------|---------|-------|------|-------|------------|
| **PostgreSQL** | ✅ YES | ✅ YES | ✅ YES | ✅ YES | ✅ YES | 🔴 HIGH |
| **Redis** | ✅ YES | ✅ YES | ✅ YES | ✅ YES | ✅ YES | 🔴 HIGH |
| **GitLab** | ✅ YES | ⚠️ MAYBE | ⚠️ MAYBE | ✅ YES | ✅ YES | 🟡 MED-HIGH |
| **Gitea** | ✅ YES | ⚠️ MAYBE | ⚠️ MAYBE | ✅ YES | ✅ YES | 🟡 MEDIUM |
| **Jenkins** | ✅ YES | ⚠️ MAYBE | ⚠️ MAYBE | ⚠️ MAYBE | ⚠️ MAYBE | 🟡 MEDIUM |
| **Drone** | ✅ YES | ⚠️ MAYBE | ⚠️ MAYBE | ⚠️ MAYBE | ⚠️ MAYBE | 🟡 MEDIUM |
| **Grafana** | ✅ YES | ❌ NO | ⚠️ MAYBE | ⚠️ MAYBE | ❌ NO | 🟢 LOW-MED |
| **Prometheus** | ✅ YES | ❌ NO | ⚠️ MAYBE | ⚠️ MAYBE | ❌ NO | 🟢 LOW-MED |

**Legend**:
- ✅ YES: Likely to be in scope for this framework
- ⚠️ MAYBE: Depends on use case and data processed
- ❌ NO: Unlikely to be in scope
- 🔴 HIGH: Stores sensitive data directly
- 🟡 MEDIUM: Access to sensitive data or critical systems
- 🟢 LOW: Monitoring/observability tools

## CloudForge Security Profile Mapping

### PRODUCTION Profile
Automatically enforces compliance controls:
- ✅ Encryption at rest (EFS, EBS, S3)
- ✅ Private subnets with NAT gateways
- ✅ CloudWatch audit logging (2+ years retention)
- ✅ GuardDuty intrusion detection
- ✅ AWS Config compliance monitoring
- ✅ VPC Flow Logs
- ✅ WAF protection for web applications
- ✅ IAM least privilege roles
- ✅ MFA enforcement (when using Cognito)

### Additional User Responsibilities

**All Databases**:
- Change default passwords immediately
- Use AWS Secrets Manager for credential storage
- Enable application-level audit logging
- Configure backup retention policies
- Implement data classification

**Source Control & CI/CD**:
- Enable MFA for all users
- Implement code review processes
- Use secret scanning tools
- Separate prod/non-prod environments
- Regular security updates

**Monitoring Tools**:
- Restrict access with authentication
- Avoid displaying PII/PHI in dashboards
- Configure data retention policies

## Compliance Validation

CloudForge provides built-in compliance validation rules:

```java
// Example: PCI-DSS Rules for databases
if (topology == TopologyType.DATABASE_SERVICE &&
    complianceFrameworks.contains("PCI-DSS")) {
    // Enforce:
    // - Encryption at rest ✓ (automatic)
    // - Private subnets only ✓ (enforced)
    // - Audit logging enabled ✓ (automatic)
    // - Strong password policy ⚠️ (user configures)
    // - Network isolation ✓ (security groups)
}
```

## Recommendations by Use Case

### Storing Payment Card Data (PCI-DSS)
**Required Applications**: PostgreSQL with strict controls
- Use PRODUCTION security profile
- Private subnets only (no public IPs)
- Enable pgAudit extension
- Quarterly vulnerability scans
- Penetration testing annually
- Consider: AWS RDS with PCI-compliant configuration instead

### Storing Protected Health Information (HIPAA)
**Required Applications**: PostgreSQL, possibly Redis
- Use PRODUCTION security profile
- Sign AWS Business Associate Agreement (BAA)
- Enable encryption in transit (SSL/TLS)
- Implement audit logging (pgAudit)
- Data backup and disaster recovery plan
- Consider: AWS RDS/ElastiCache with HIPAA eligibility

### General Business Data (SOC2)
**Most Applications**: Normal deployment
- Use PRODUCTION or STAGING profile
- Enable CloudWatch logging
- Implement access controls
- Regular security updates

## References

- [PCI-DSS Requirements](https://www.pcisecuritystandards.org/)
- [HIPAA Security Rule](https://www.hhs.gov/hipaa/for-professionals/security/)
- [SOC 2 Trust Services Criteria](https://us.aicpa.org/interestareas/frc/assuranceadvisoryservices/aicpasoc2report)
- [GDPR Official Text](https://gdpr-info.eu/)
- [CloudForge Security Profiles](../core/security/)

---

**⚠️ IMPORTANT DISCLAIMER**: This document provides general guidance only. Compliance requirements vary by organization, jurisdiction, and specific use cases. Consult with your legal and compliance teams for specific requirements. CloudForge provides security controls but cannot guarantee compliance - proper configuration and operational procedures are the user's responsibility.

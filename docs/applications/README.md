# CloudForge ApplicationSpec Catalog

This directory contains ApplicationSpec implementations for deploying various containerized applications using CloudForge 3.0.0.

## Overview

Each ApplicationSpec defines:
- **Application Identity**: Unique ID, Docker image, ports
- **Container Configuration**: Data paths, user/group IDs, permissions
- **EC2 Configuration**: EBS device, log paths, UserData scripts
- **Storage Strategy**: Support for both EFS (shared) and EBS (single-instance)

## Available Applications

### CI/CD (Continuous Integration/Continuous Deployment)

| Application | Description | Default Port | Container Image |
|-------------|-------------|--------------|-----------------|
| **Jenkins** | Open-source automation server | 8080 | `jenkins/jenkins:lts` |
| **GitLab** | Complete DevOps platform with Git + CI/CD | 80, 22 (SSH) | `gitlab/gitlab-ce:latest` |
| **Drone** | Container-native CI platform | 80 | `drone/drone:2` |

**Package**: `com.cloudforgeci.api.application.cicd`

### Version Control Systems

| Application | Description | Default Port | Container Image |
|-------------|-------------|--------------|-----------------|
| **Gitea** | Lightweight Git hosting in Go | 3000, 22 (SSH) | `gitea/gitea:latest` |

**Package**: `com.cloudforgeci.api.application.vcs`

### Monitoring & Observability

| Application | Description | Default Port | Container Image |
|-------------|-------------|--------------|-----------------|
| **Grafana** | Metrics visualization and dashboards | 3000 | `grafana/grafana:latest` |
| **Prometheus** | Systems monitoring and alerting | 9090 | `prom/prometheus:latest` |

**Package**: `com.cloudforgeci.api.application.monitoring`

### Databases & Caching

| Application | Description | Default Port | Container Image |
|-------------|-------------|--------------|-----------------|
| **PostgreSQL** | Object-relational database | 5432 | `postgres:15` |
| **Redis** | In-memory data store and cache | 6379 | `redis:7-alpine` |

**Package**: `com.cloudforgeci.api.application.database`

## Architecture

### Storage Strategies

CloudForge applications support two storage backends:

1. **EFS (Elastic File System)** - Shared storage for multi-instance deployments
   - Auto-scaling support
   - High availability
   - NFSv4 protocol
   - IAM-based access control via Access Points

2. **EBS (Elastic Block Store)** - Block storage for single-instance deployments
   - Higher performance
   - Lower cost
   - Encrypted at rest
   - Automated snapshots

The ApplicationSpec interface abstracts these details - the infrastructure automatically selects the appropriate storage based on deployment configuration.

### Standard User/Group IDs

Common UIDs/GIDs used by applications:

| Application | UID:GID | User | Notes |
|-------------|---------|------|-------|
| Jenkins | 1000:1000 | jenkins | Standard Linux user |
| GitLab | 998:998 | git | Git operations user |
| Gitea | 1000:1000 | git | Standard user |
| Grafana | 472:472 | grafana | Official Grafana UID |
| Prometheus | 65534:65534 | nobody | Nobody/nogroup |
| PostgreSQL | 999:999 | postgres | PostgreSQL user |
| Redis | 999:999 | redis | Redis user |

### Security Considerations

All ApplicationSpecs follow CloudForge security best practices:

- ✅ **Encryption at Rest**: EFS and EBS volumes encrypted
- ✅ **Least Privilege**: IAM roles with minimal permissions
- ✅ **CloudWatch Integration**: Centralized logging
- ✅ **Security Groups**: Network isolation with explicit rules
- ✅ **POSIX Permissions**: Proper file ownership and permissions
- ⚠️ **Default Passwords**: Change immediately in production!
- ⚠️ **Secrets Management**: Use AWS Secrets Manager for sensitive data

## Compliance Requirements by Application

### 🔴 CRITICAL RISK - All Compliance Frameworks Required

#### PostgreSQL & Redis (Databases)
**Frameworks**: SOC2, PCI-DSS, HIPAA, GDPR, FERPA

**Why Critical**:
- Store sensitive data (PII, PHI, payment card data, education records)
- Direct data access point for applications
- Backup and recovery critical for compliance
- Audit logging required for all data access

**Required Controls**:

**SOC2 (CC6.1, CC6.6, CC6.7)**:
- ✅ Encryption at rest (EBS/EFS encryption)
- ✅ Encryption in transit (TLS connections)
- ✅ Access logging via CloudWatch
- ✅ Network isolation (Security Groups)
- ⚠️ **ACTION REQUIRED**: Enable query logging for audit trail
- ⚠️ **ACTION REQUIRED**: Implement backup retention (7-90 days depending on framework)

**PCI-DSS (Req 3.4, 8.2, 10.2)**:
- ✅ Strong encryption for cardholder data at rest
- ✅ Unique user IDs (IAM integration)
- ⚠️ **ACTION REQUIRED**: Log all access to cardholder data
- ⚠️ **ACTION REQUIRED**: Implement key rotation every 90 days
- ⚠️ **ACTION REQUIRED**: Quarterly vulnerability scans

**HIPAA (§164.312(a)(2)(iv), §164.312(e)(2)(ii))**:
- ✅ Automatic logoff (container restarts)
- ✅ Encryption at rest and in transit
- ⚠️ **ACTION REQUIRED**: Audit logs retained for 6 years
- ⚠️ **ACTION REQUIRED**: Implement BAA with AWS
- ⚠️ **ACTION REQUIRED**: PHI access controls and audit trails

**GDPR (Art. 32, Art. 25)**:
- ✅ Encryption of personal data
- ✅ Data-at-rest protection
- ⚠️ **ACTION REQUIRED**: Data retention policies (right to erasure)
- ⚠️ **ACTION REQUIRED**: Data export capability (data portability)
- ⚠️ **ACTION REQUIRED**: Breach notification procedures

**FERPA**:
- ⚠️ **ACTION REQUIRED**: Education record access logging
- ⚠️ **ACTION REQUIRED**: Role-based access controls
- ⚠️ **ACTION REQUIRED**: Audit trail for all data access

**PostgreSQL-Specific**:
```bash
# Enable audit logging
ALTER SYSTEM SET log_statement = 'all';
ALTER SYSTEM SET log_connections = 'on';
ALTER SYSTEM SET log_disconnections = 'on';

# Enable SSL/TLS
ALTER SYSTEM SET ssl = 'on';

# Automated backups
# Configure in CloudForge: enableBackups = true, retentionDays = 90
```

**Redis-Specific**:
```bash
# Enable AOF persistence for compliance
appendonly yes
appendfsync everysec

# Require authentication
requirepass <strong-password-from-secrets-manager>

# TLS encryption
tls-port 6379
tls-cert-file /path/to/redis.crt
tls-key-file /path/to/redis.key
```

---

### 🟠 HIGH RISK - SOC2, GDPR, FERPA Required

#### GitLab (Complete DevOps Platform)
**Frameworks**: SOC2, GDPR, FERPA, (PCI-DSS if processing payments), (HIPAA if handling PHI)

**Why High Risk**:
- Source code repository (intellectual property, trade secrets)
- May contain secrets, credentials, API keys in code
- User PII in profiles, commit history
- CI/CD pipeline access to production systems
- Container registry may store sensitive images

**Required Controls**:

**SOC2 (CC6.1, CC6.2, CC8.1)**:
- ✅ OIDC authentication (via OmniAuth)
- ✅ Encryption at rest (EBS/EFS)
- ⚠️ **ACTION REQUIRED**: Enable audit logging
- ⚠️ **ACTION REQUIRED**: Secret scanning in repositories
- ⚠️ **ACTION REQUIRED**: Branch protection rules
- ⚠️ **ACTION REQUIRED**: Code review requirements
- ⚠️ **ACTION REQUIRED**: Signed commits

**GDPR (Art. 32)**:
- ✅ OIDC authentication
- ⚠️ **ACTION REQUIRED**: User consent for profile data
- ⚠️ **ACTION REQUIRED**: Data export capability (user profiles, commit history)
- ⚠️ **ACTION REQUIRED**: Right to erasure procedures
- ⚠️ **ACTION REQUIRED**: Privacy policy in instance

**FERPA** (if storing education records in repos):
- ⚠️ **ACTION REQUIRED**: Access controls for education record repositories
- ⚠️ **ACTION REQUIRED**: Audit logging for all repository access

**GitLab-Specific Configuration**:
```ruby
# In /etc/gitlab/gitlab.rb

# Audit logging
gitlab_rails['audit_events_enabled'] = true

# Secret detection
gitlab_rails['secret_detection_enabled'] = true

# Require 2FA for all users (SOC2, PCI-DSS)
gitlab_rails['require_two_factor_authentication'] = true

# Session timeout (HIPAA, SOC2)
gitlab_rails['session_expire_delay'] = 10800  # 3 hours

# Password complexity (PCI-DSS Req 8.2.3)
gitlab_rails['password_authentication_enabled_for_web'] = true
gitlab_rails['password_minimum_length'] = 12

# IP whitelisting for admin access
gitlab_rails['monitoring_whitelist'] = ['10.0.0.0/8']
```

**Compliance Checklist for GitLab**:
- [ ] Enable audit logging
- [ ] Configure secret scanning
- [ ] Enable branch protection on all production branches
- [ ] Require code reviews (minimum 1 approver)
- [ ] Enable signed commits
- [ ] Configure session timeouts
- [ ] Implement backup retention (SOC2: 30 days, HIPAA: 6 years)
- [ ] Enable 2FA for all users

---

#### Gitea (Git Hosting)
**Frameworks**: SOC2, GDPR, (FERPA if education records)

**Why High Risk**:
- Source code repository
- May contain secrets and credentials
- User PII in profiles

**Required Controls**:

**SOC2 (CC6.1, CC8.1)**:
- ✅ OIDC authentication (supports OpenID Connect)
- ⚠️ **ACTION REQUIRED**: Enable audit logging in `app.ini`
- ⚠️ **ACTION REQUIRED**: Protected branches
- ⚠️ **ACTION REQUIRED**: Require signed commits

**GDPR (Art. 32)**:
- ✅ OIDC authentication
- ⚠️ **ACTION REQUIRED**: Data export capability
- ⚠️ **ACTION REQUIRED**: User data deletion procedures

**Gitea-Specific Configuration**:
```ini
[security]
INSTALL_LOCK = true
SECRET_KEY = <generate-strong-secret>
MIN_PASSWORD_LENGTH = 12
PASSWORD_COMPLEXITY = lower,upper,digit,spec

[service]
REQUIRE_SIGNIN_VIEW = true
ENABLE_REVERSE_PROXY_AUTHENTICATION = false
ENABLE_REVERSE_PROXY_AUTO_REGISTRATION = false

[log]
MODE = file
LEVEL = Info
ROOT_PATH = /var/log/gitea

[session]
PROVIDER = file
COOKIE_SECURE = true
COOKIE_HTTP_ONLY = true
SESSION_LIFE_TIME = 10800  # 3 hours
```

---

### 🟡 MEDIUM-HIGH RISK - SOC2, PCI-DSS (if deploying payment systems)

#### Jenkins (CI/CD Automation)
**Frameworks**: SOC2, (PCI-DSS if deploying to payment systems), (HIPAA if deploying to healthcare systems)

**Why Medium-High Risk**:
- Access to cloud credentials and deployment secrets
- Can deploy to production systems
- Pipeline logs may contain sensitive data
- Build artifacts may contain PII/PHI

**Required Controls**:

**SOC2 (CC8.1 - Change Management)**:
- ✅ OIDC authentication (via Jenkins OIDC plugin)
- ⚠️ **ACTION REQUIRED**: Audit logging for all build executions
- ⚠️ **ACTION REQUIRED**: Approval gates for production deployments
- ⚠️ **ACTION REQUIRED**: Secrets management (HashiCorp Vault, AWS Secrets Manager)
- ⚠️ **ACTION REQUIRED**: Build artifact retention policy

**PCI-DSS (Req 6.3.2 - Secure Deployment)**:
- ⚠️ **ACTION REQUIRED**: Separate development/test/production pipelines
- ⚠️ **ACTION REQUIRED**: Code review before production deployment
- ⚠️ **ACTION REQUIRED**: Automated security testing in pipeline
- ⚠️ **ACTION REQUIRED**: Change approval workflow

**HIPAA** (if deploying healthcare applications):
- ⚠️ **ACTION REQUIRED**: Audit trail for all deployments
- ⚠️ **ACTION REQUIRED**: Access controls for PHI-related pipelines
- ⚠️ **ACTION REQUIRED**: Encryption of build artifacts

**Jenkins-Specific Configuration**:
```groovy
// In Jenkins Configuration as Code (JCasC)

jenkins:
  securityRealm:
    oic:
      // OIDC configuration (auto-configured by CloudForge)

  authorizationStrategy:
    globalMatrix:
      permissions:
        - "Overall/Administer:authenticated"
        - "Job/Build:developers"
        - "Job/Read:developers"

  // Audit logging
  auditTrail:
    loggers:
      - logFile:
          log: "/var/log/jenkins/audit.log"
          limit: 100

  // Session timeout (SOC2, HIPAA)
  securityOptions:
    sessionTimeout: 10800  # 3 hours
```

**Compliance Checklist for Jenkins**:
- [ ] Enable audit logging for all builds
- [ ] Implement approval gates for production
- [ ] Use Credentials Plugin for secrets (never hardcode)
- [ ] Configure build artifact retention (30-90 days)
- [ ] Enable OIDC/SSO authentication
- [ ] Implement role-based access control
- [ ] Separate pipelines for dev/test/prod

---

#### Drone (Container-native CI)
**Frameworks**: SOC2, (PCI-DSS if deploying payment systems)

**Why Medium-High Risk**:
- CI/CD pipeline with deployment access
- Container image building (supply chain risk)

**Required Controls**:

**SOC2 (CC8.1)**:
- ⚠️ **ACTION REQUIRED**: Audit logging for pipeline executions
- ⚠️ **ACTION REQUIRED**: Secrets management (Drone secrets)
- ⚠️ **ACTION REQUIRED**: Pipeline approval workflows

**PCI-DSS (Req 6.3.2)**:
- ⚠️ **ACTION REQUIRED**: Separate deployment environments
- ⚠️ **ACTION REQUIRED**: Security scanning in pipeline

---

### 🟢 LOW-MEDIUM RISK - SOC2 (Monitoring/Observability)

#### Grafana (Metrics Visualization)
**Frameworks**: SOC2, (GDPR if displaying user metrics)

**Why Low-Medium Risk**:
- May display sensitive metrics (user behavior, financial data)
- User authentication and access controls important
- Dashboard sharing may expose sensitive data

**Required Controls**:

**SOC2 (A1.2 - Monitoring)**:
- ✅ OIDC authentication (via generic_oauth)
- ✅ Role-based dashboards
- ⚠️ **ACTION REQUIRED**: Audit logging for dashboard access
- ⚠️ **ACTION REQUIRED**: Data source access controls
- ⚠️ **ACTION REQUIRED**: Anonymous access disabled

**GDPR** (if displaying user PII):
- ⚠️ **ACTION REQUIRED**: Data retention policies for metrics
- ⚠️ **ACTION REQUIRED**: User consent for behavior tracking

**Grafana-Specific Configuration**:
```ini
[auth]
disable_login_form = false
oauth_auto_login = false

[auth.generic_oauth]
enabled = true
# OIDC configuration (auto-configured by CloudForge)

[security]
admin_user = admin
admin_password = <strong-password>
secret_key = <generate-strong-secret>
disable_gravatar = true

[users]
allow_sign_up = false
allow_org_create = false
auto_assign_org = true
auto_assign_org_role = Editor

[log]
mode = console file
level = info

[session]
session_life_time = 10800  # 3 hours
```

**Compliance Checklist for Grafana**:
- [ ] Enable OIDC authentication
- [ ] Disable anonymous access
- [ ] Configure session timeouts
- [ ] Implement dashboard access controls
- [ ] Enable audit logging (Grafana Enterprise)

---

#### Prometheus (Time-Series Database)
**Frameworks**: SOC2 (for monitoring compliance)

**Why Low-Medium Risk**:
- Collects system metrics (may include sensitive performance data)
- No authentication by default (use reverse proxy)

**Required Controls**:

**SOC2 (A1.2)**:
- ⚠️ **ACTION REQUIRED**: Use reverse proxy with authentication (oauth2-proxy)
- ⚠️ **ACTION REQUIRED**: Network isolation (Security Groups)
- ⚠️ **ACTION REQUIRED**: Data retention policies

**Prometheus-Specific Configuration**:
```yaml
# prometheus.yml

global:
  scrape_interval: 15s
  evaluation_interval: 15s

  # External labels for compliance
  external_labels:
    environment: 'production'
    compliance: 'soc2'

# Alerting for compliance monitoring
alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']

# Data retention (SOC2 requirement)
storage:
  tsdb:
    retention.time: 30d  # Adjust based on compliance needs
```

**Compliance Checklist for Prometheus**:
- [ ] Deploy behind oauth2-proxy for authentication
- [ ] Configure data retention (30-90 days)
- [ ] Network isolation via Security Groups
- [ ] Alert on compliance-related metrics

---

## Compliance Summary Matrix

| Application | SOC2 | PCI-DSS | HIPAA | GDPR | FERPA | Risk Level |
|-------------|------|---------|-------|------|-------|------------|
| **PostgreSQL** | ✅ Required | ✅ Required | ✅ Required | ✅ Required | ✅ Required | 🔴 CRITICAL |
| **Redis** | ✅ Required | ✅ Required | ✅ Required | ✅ Required | ✅ Required | 🔴 CRITICAL |
| **GitLab** | ✅ Required | ⚠️ If payments | ⚠️ If PHI | ✅ Required | ⚠️ If edu records | 🟠 HIGH |
| **Gitea** | ✅ Required | ❌ N/A | ❌ N/A | ✅ Required | ⚠️ If edu records | 🟠 HIGH |
| **Jenkins** | ✅ Required | ⚠️ If deploying payments | ⚠️ If deploying PHI | ⚠️ If user data | ❌ N/A | 🟡 MEDIUM-HIGH |
| **Drone** | ✅ Required | ⚠️ If deploying payments | ❌ N/A | ❌ N/A | ❌ N/A | 🟡 MEDIUM-HIGH |
| **Grafana** | ✅ Required | ❌ N/A | ❌ N/A | ⚠️ If user metrics | ❌ N/A | 🟢 LOW-MEDIUM |
| **Prometheus** | ✅ Required | ❌ N/A | ❌ N/A | ❌ N/A | ❌ N/A | 🟢 LOW-MEDIUM |

**Legend**:
- ✅ **Required**: Compliance framework applies, controls must be implemented
- ⚠️ **Conditional**: Applies only if application handles specific data types
- ❌ **N/A**: Framework does not apply to this application

---

## CloudForge Automatic Compliance Controls

When deploying with **SecurityProfile = PRODUCTION**, CloudForge automatically enables:

✅ **Encryption at Rest** (all applications)
- EBS volumes encrypted with AWS KMS
- EFS filesystems encrypted with AWS KMS

✅ **Encryption in Transit** (all applications)
- TLS 1.2+ for all connections
- SSL certificates via ACM

✅ **Network Isolation** (all applications)
- Security Groups with least-privilege rules
- VPC isolation
- Optional: Private subnets with NAT Gateway

✅ **Logging & Monitoring** (all applications)
- CloudWatch Logs integration
- VPC Flow Logs (if enabled)
- ALB access logging (if enabled)

✅ **Access Control** (all applications)
- IAM roles with least privilege
- OIDC authentication (if supported)
- Session timeouts

✅ **Compliance Monitoring** (if AWS Config enabled)
- Automated compliance checks
- Remediation for non-compliant resources
- Audit trail via CloudTrail

---

## User Responsibilities by Application

### Databases (PostgreSQL, Redis)
- [ ] Change default passwords immediately
- [ ] Enable query/access logging
- [ ] Configure backup retention based on compliance needs
- [ ] Implement key rotation (PCI-DSS: 90 days)
- [ ] Store passwords in AWS Secrets Manager
- [ ] Enable TLS/SSL for connections
- [ ] Implement data retention and deletion policies

### Source Control (GitLab, Gitea)
- [ ] Enable audit logging
- [ ] Configure secret scanning
- [ ] Implement branch protection
- [ ] Require code reviews
- [ ] Enable signed commits
- [ ] Configure 2FA/MFA for all users
- [ ] Implement backup retention
- [ ] Create data export procedures

### CI/CD (Jenkins, Drone)
- [ ] Enable audit logging for all builds
- [ ] Implement approval gates for production
- [ ] Use secrets management (never hardcode)
- [ ] Configure artifact retention policies
- [ ] Separate dev/test/prod pipelines
- [ ] Implement security scanning in pipelines
- [ ] Configure role-based access control

### Monitoring (Grafana, Prometheus)
- [ ] Enable authentication (OIDC or reverse proxy)
- [ ] Disable anonymous access
- [ ] Configure session timeouts
- [ ] Implement dashboard access controls
- [ ] Configure data retention policies
- [ ] Enable audit logging (if available)

---

## Compliance Validation

CloudForge provides automated compliance validation when `auditManagerEnabled = true`:

**Frameworks Validated**:
- SOC 2 Type II
- PCI-DSS v4.0
- HIPAA Security Rule
- GDPR
- FERPA

**Validation Checks**:
1. Encryption at rest enabled
2. Encryption in transit enabled
3. Audit logging configured
4. Access controls implemented
5. Network isolation verified
6. Backup retention configured
7. Password policies enforced

**Reports Available**:
- Compliance posture dashboard
- Evidence collection for audits
- Gap analysis reports
- Remediation recommendations

## Usage Example

```java
// Create Jenkins deployment
ApplicationSpec jenkinsSpec = new JenkinsApplicationSpec();

// Access properties
String image = jenkinsSpec.defaultContainerImage(); // "jenkins/jenkins:lts"
int port = jenkinsSpec.applicationPort(); // 8080
String volumeName = jenkinsSpec.volumeName(); // "jenkinsHome"

// EC2 UserData is automatically configured
UserDataBuilder builder = ...;
Ec2Context context = ...;
jenkinsSpec.configureUserData(builder, context);
```

## Adding New Applications

To add a new ApplicationSpec:

1. Create a new class implementing `ApplicationSpec`
2. Place it in the appropriate category package
3. Implement all required methods
4. Document ports, requirements, and security notes
5. Add to this README

### Template

```java
package com.cloudforgeci.api.application.{category};

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;
import java.util.List;

public class MyAppApplicationSpec implements ApplicationSpec {

    private static final String APPLICATION_ID = "myapp";
    private static final String DEFAULT_IMAGE = "myapp/myapp:latest";
    private static final int APPLICATION_PORT = 8080;
    private static final String CONTAINER_DATA_PATH = "/data";
    private static final String EFS_DATA_PATH = "/myapp";
    private static final String VOLUME_NAME = "myappData";
    private static final String CONTAINER_USER = "1000:1000";
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/var/lib/myapp";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/log/myapp/myapp.log",
        "/var/log/userdata.log"
    );

    // Implement all ApplicationSpec methods...
}
```

## Future Applications

The following applications are planned for future releases:

### CI/CD
- TeamCity, Bamboo, Concourse CI, CircleCI Runner, Buildkite Agent, Woodpecker CI

### Version Control
- Gogs, Forgejo, Phabricator

### Monitoring
- Jaeger, Netdata, Uptime Kuma, Zabbix

### Databases
- MySQL, MongoDB, MariaDB, CockroachDB

### Project Management
- Jira, Redmine, Taiga, YouTrack

### Artifact Repositories
- Nexus, Artifactory, Harbor, GitLab Container Registry

### Code Quality & Security
- SonarQube, Snyk, Checkmarx, Trivy

## References

- [ApplicationSpec Interface](../../../cloudforge-core/src/main/java/com/cloudforge/core/interfaces/ApplicationSpec.java)
- [DatabaseSpec Interface](../../../cloudforge-core/src/main/java/com/cloudforge/core/interfaces/DatabaseSpec.java)
- [Interactive Deployer](../../../cfc-testing/src/main/java/com/cloudforgeci/samples/app/InteractiveDeployer.java)
- [Plugin System Guide](../plugins/PLUGIN-SYSTEM.md)

## Support

For questions or issues with ApplicationSpecs:
- GitHub Issues: https://github.com/CloudForgeCI/cfc-core/issues
- [Documentation Index](../README.md)

---

**CloudForge 3.0.0** - Universal Application Deployment Platform
*Making cloud infrastructure deployment painless*

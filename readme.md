# CloudForge 3.0.0 — Compliance-Ready AWS Infrastructure Framework

**Open-source infrastructure-as-code framework for deploying secure, auditable application workloads on AWS**

[![Maven Central](https://img.shields.io/maven-central/v/com.cloudforgeci/cloudforge-api)](https://central.sonatype.com/artifact/com.cloudforgeci/cloudforge-api)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

Deploy secure, compliance-ready application infrastructure on AWS in minutes. **10+ applications across 8 categories**: CI/CD (Jenkins, GitLab, Drone), Version Control (Gitea), Monitoring (Grafana, Prometheus), Databases (PostgreSQL, Redis), Secrets Management (Vault), Artifact Registry (Nexus, Harbor), Collaboration (Mattermost), and Analytics (Metabase, Superset). Built-in OIDC authentication (AWS Cognito, IAM Identity Center) and automated compliance validation for SOC2, HIPAA, PCI-DSS, GDPR, and ISO 27001.

**📈 [Live Test Reports Dashboard](https://cloudforgeci.github.io/cfc-core/)** — Coverage, validation, compliance truth tables & drift detection

> **⚠️ IMPORTANT:** This software is provided "AS IS" under the Apache License 2.0. It is **compliance-ready**, not compliance-certified. While CloudForge provides tools, controls, and configurations designed to support compliance efforts, it does NOT guarantee compliance with any regulatory framework. Organizations are solely responsible for conducting their own compliance assessments, engaging qualified auditors and legal counsel, and meeting all applicable regulatory requirements. See [LICENSE](LICENSE) for full terms.

---

## 📚 Documentation Hub

### 🚀 Getting Started
- **[Quick Start Guide](docs/compliance/QUICK_START_GUIDE.md)** - Get running in 10 minutes
- **[Sample Project](https://github.com/CloudForgeCI/cloudforge-sample)** - Complete working example
- **[Interactive Deployer](docs/guides/INTERACTIVE_DEPLOYER.md)** - User-friendly CLI deployment tool

### 🔌 Plugin System
- **[Plugin Ecosystem Overview](docs/plugins/PLUGIN-ECOSYSTEM.md)** - 14 built-in applications + custom plugins
- **[Plugin System Guide](docs/plugins/PLUGIN-SYSTEM.md)** - Architecture and development
- **[Application Plugin Guide](docs/plugins/APPLICATION-PLUGIN-GUIDE.md)** - Build custom application plugins
- **[Compliance Plugin Guide](docs/plugins/COMPLIANCE-PLUGIN-GUIDE.md)** - Build compliance framework plugins

### 🔐 Security & Authentication
- **[OIDC Integration Guide](docs/applications/OIDC.md)** - Application-level OIDC (Grafana, GitLab, Jenkins)
- **[Identity Center Setup](docs/setup/AWS_IDENTITY_CENTER_SETUP.md)** - Enterprise SSO with ALB-OIDC (Okta, Auth0)
- **[Cognito MFA Setup](docs/setup/COGNITO_MFA_COMPLIANCE_SETUP.md)** - AWS Cognito user pools with MFA for compliance
- **[IAM Best Practices](docs/guides/IAM_RULES.md)** - IAM security rules and policies
- **[Security Hardening](SECURITY.md)** - Security best practices

### 💾 Database (RDS) Integration
- **[Database Deployment Guide](docs/databases/DATABASE-DEPLOYMENT-GUIDE.md)** - RDS provisioning, compliance, and automated remediation

### ✅ Compliance
- **[Compliance Overview](docs/compliance/README.md)** - Complete compliance documentation hub
- **[Compliance Posture & Testing](docs/COMPLIANCE_POSTURE.md)** - Infrastructure control status & coverage analysis
- **[Auditor Compliance Mapping](docs/AUDITOR_COMPLIANCE_MAPPING.md)** - Control mappings for external audits
- **[Automated Compliance](docs/compliance/AUTOMATED_COMPLIANCE.md)** - Auto-remediation features
- **[Multi-Framework Guide](docs/compliance/MULTI_FRAMEWORK_COMPLIANCE.md)** - SOC2, HIPAA, PCI-DSS, GDPR
- **[S3 Versioning Remediation](docs/compliance/S3_VERSIONING_REMEDIATION.md)** - Automatic versioning
- **[PCI-DSS Compliance](docs/compliance/PCI_DSS_COMPLIANCE.md)** - Payment card security
- **[Security Rules](docs/guides/SECURITY_RULES_README.md)** - Comprehensive guidelines

### 📖 Advanced Topics
- **[Deployment Guide](docs/compliance/DEPLOYMENT_GUIDE.md)** - Production strategies
- **[AWS Config Multi-Stack](docs/compliance/AWS_CONFIG_MULTI_STACK.md)** - Multi-account setup
- **[Extended Testing](docs/guides/EXTENDED-TESTING.md)** - Comprehensive testing (deployment configurations)
- **[Compliance Truth Tables](docs/testing/COMPLIANCE_TRUTH_TABLES.md)** - Systematic compliance rules testing
- **[AWS Audit Manager](docs/AUDIT_MANAGER.md)** - Continuous auditing

### 📊 Reports & Testing
- **[📈 Test Reports Dashboard](https://cloudforgeci.github.io/cfc-core/)** - Live coverage, validation, compliance & drift reports

### 📑 Indexes
- **[Documentation Index](docs/README.md)** - All documentation
- **[Compliance README](docs/compliance/README.md)** - All compliance docs

---

## 🎯 Quick Start

### Option 1: Use the Sample Project (Recommended)

```bash
git clone https://github.com/CloudForgeCI/cloudforge-sample.git
cd cloudforge-sample
vi deployment-context.json  # Edit with your settings
mvn clean package
cdk deploy
```

Includes example configurations for all scenarios: OIDC/Cognito auth, SOC2/HIPAA/PCI-DSS/GDPR compliance, EC2 and Fargate runtimes.

### Option 2: Add to Your Existing Project

```xml
<properties>
  <cloudforge.version>2.0.6</cloudforge.version>
</properties>

<dependencies>
  <dependency>
    <groupId>com.cloudforgeci</groupId>
    <artifactId>cloudforge-api</artifactId>
    <version>${cloudforge.version}</version>
  </dependency>
</dependencies>
```

Check [Maven Central](https://central.sonatype.com/artifact/com.cloudforgeci/cloudforge-api) for the latest version.

### Option 3: Local Development (Contributors)

```bash
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core
./mvnw -T1C -DskipTests install  # Fast build (skip tests)
./mvnw clean verify               # Full build with tests
```

---

## ⚙️ Configuration Reference

CloudForge uses `deployment-context.json` to configure deployments. **All properties are optional** unless marked **[required]**.

### Core Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `runtime` | string | `"fargate"` | **Compute platform:** `"ec2"` or `"fargate"` |
| `topology` | string | `"jenkins-service"` | **Architecture:** `"jenkins-service"` (HA), `"jenkins-single-node"`, or `"s3-website"` |
| `securityProfile` | string | `"dev"` | **Security level:** `"dev"`, `"staging"`, or `"production"` |
| `region` | string | `"us-east-1"` | AWS region to deploy to |
| `stackName` | string | auto | CloudFormation stack name |
| `env` | string | `"dev"` | Environment: `"dev"`, `"stage"`, or `"prod"` |

### DNS & SSL

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `domain` | string | - | Your domain (e.g., `"example.com"`) |
| `subdomain` | string | - | Subdomain (e.g., `"jenkins"` → jenkins.example.com) |
| `fqdn` | string | - | Full domain (overrides domain+subdomain): `"jenkins.example.com"` |
| `enableSsl` | boolean | `false` | Enable HTTPS with ACM certificate |
| `createZone` | boolean | `false` | Create Route53 hosted zone |

### Network & Security

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `networkMode` | string | `"public-no-nat"` | `"public-no-nat"` or `"private-with-nat"` |
| `wafEnabled` | boolean | `false` | Enable AWS WAF (web application firewall) |
| `albAccessLogging` | boolean | `false` | Enable ALB access logs to S3 |
| `bastionCidr` | string | `"10.0.1.0/24"` | CIDR for SSH access (production only) |
| `guardDutyEnabled` | boolean | `false` | Enable threat detection (PCI-DSS Req 11.4) |
| `enableFlowlogs` | boolean | `false` | Enable VPC Flow Logs |

### Authentication

| Property | Type | Default | Description                                     |
|----------|------|---------|-------------------------------------------------|
| `authMode` | string | `"none"` | `"none"`, `"alb-oidc"`, or `"application-oidc"` |

> **⚠️ Note:** SAML authentication and Keycloak integration are in active development and may have breaking changes.

#### Cognito Configuration (Simplest Authentication)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cognitoAutoProvision` | boolean | `false` | Automatically create Cognito User Pool |
| `cognitoDomainPrefix` | string | - | **[required if auto-provisioning]** Unique domain prefix |
| `cognitoMfaEnabled` | boolean | `false` | Enable multi-factor authentication |
| `cognitoAdminGroupName` | string | `"Jenkins-Admins"` | Admin group name |
| `cognitoInitialAdminEmail` | string | - | Email for initial admin user |

[See full Cognito config options →](#cognito-configuration-full)

#### OIDC Configuration (Enterprise SSO)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `oidcIssuer` | string | - | OIDC issuer URL (from your IdP) |
| `oidcClientId` | string | - | OIDC client ID (from your IdP) |
| `oidcClientSecretName` | string | - | AWS Secrets Manager secret name |
| `ssoInstanceArn` | string | - | IAM Identity Center instance ARN |
| `ssoGroupId` | string | - | Identity Center group UUID |

[See full OIDC config options →](#oidc-configuration-full)

### Compute & Scaling

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `instanceType` | string | `"t3.micro"` | EC2 instance type (EC2 runtime only) |
| `cpu` | integer | `1024` | Fargate vCPU units (Fargate runtime only) |
| `memory` | integer | `2048` | Fargate memory MiB (Fargate runtime only) |
| `minInstanceCapacity` | integer | `1` | Minimum instances |
| `maxInstanceCapacity` | integer | `1` | Maximum instances |
| `cpuTargetUtilization` | integer | `60` | CPU target % for auto-scaling |

### Storage

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `artifactsBucket` | string | - | S3 bucket for build artifacts |
| `retainStorage` | boolean | `false` | Keep EFS/EBS on stack deletion |
| `existingFileSystemId` | string | - | Reuse existing EFS (disaster recovery) |

### Database (RDS)

CloudForge 3.0+ automatically provisions RDS databases for applications with database requirements.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `provisionDatabase` | boolean | auto | **Optional DB apps only** (Metabase, Grafana). `true` = RDS PostgreSQL, `false` = embedded DB (H2/SQLite) |
| `enableRdsDeletionProtectionRemediation` | boolean | `false` | Auto-enable RDS deletion protection (HIPAA, SOC2, GDPR) |
| `enableRdsAutoMinorVersionUpgradeRemediation` | boolean | `false` | Auto-enable RDS security patches (PCI-DSS, SOC2, HIPAA, GDPR) |

**Applications with database requirements:**
- **REQUIRED:** GitLab, Mattermost, Harbor, Superset (always provision RDS)
- **OPTIONAL:** Metabase, Grafana (choose RDS or embedded)
- See [DATABASE-DEPLOYMENT-GUIDE.md](docs/databases/DATABASE-DEPLOYMENT-GUIDE.md) for full details

### Monitoring & Compliance

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enableMonitoring` | boolean | `true` | CloudWatch monitoring |
| `logRetentionDays` | integer | `7` | CloudWatch log retention days |
| `awsConfigEnabled` | boolean | `false` | Enable AWS Config compliance |
| `createConfigInfrastructure` | boolean | `false` | Create Config Recorder (account-level) |
| `complianceFrameworks` | string | - | `"SOC2"`, `"HIPAA"`, `"PCI-DSS"`, `"GDPR"` (comma-separated) |
| `auditManagerEnabled` | boolean | `false` | Enable AWS Audit Manager |
| `enableS3VersioningRemediation` | boolean | `false` | Auto-enable S3 versioning (SOC2, GDPR) |
| `enableCloudTrailBucketAccessRemediation` | boolean | `false` | Auto-enable CloudTrail bucket logging (PCI-DSS, HIPAA) |

### Compliance Remediation

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enableS3VersioningRemediation` | boolean | `false` | Auto-enable S3 versioning on non-compliant buckets |
| `scopeConfigRulesToDeployment` | boolean | `false` | Scope Config rules to stack resources (vs account-wide) |

### AWS Backup (NEW in 3.0)

Automated backup for EFS and RDS with security profile-based retention.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `automatedBackupEnabled` | boolean | profile | Enable AWS Backup (DEV: false, STAGING/PROD: true) |
| `backupRetentionDays` | integer | profile | Backup retention (DEV: 0, STAGING: 14, PROD: 90) |
| `crossRegionBackupEnabled` | boolean | profile | Enable cross-region backup copy (PROD only) |

**Security Profile Defaults:**
- **DEV:** Backups disabled (cost savings)
- **STAGING:** 14-day retention, no cross-region
- **PRODUCTION:** 90-day retention, cross-region copy, vault lock (prevents deletion)

### Optional Application Ports

Enable additional ports for applications that support them.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enableAgents` | boolean | `false` | JNLP build agents (Jenkins: 50000) |
| `enableSsh` | boolean | `false` | Git SSH (GitLab: 22, Gitea: 2222) |
| `enableSmtp` | boolean | `false` | SMTP email (Mattermost: 587) |
| `enableSmtps` | boolean | `false` | SMTP TLS (Mattermost: 465) |
| `enableClustering` | boolean | `false` | HA clustering (Mattermost: 8074-8075, Vault: 8201) |
| `enableDockerRegistry` | boolean | `false` | Container registry (GitLab: 5050, Nexus: 5000-5002) |
| `enableMetrics` | boolean | `false` | Prometheus metrics (GitLab: 9090) |
| `enableNotary` | boolean | `false` | Notary content trust (Harbor: 4443) |
| `enableTrivy` | boolean | `false` | Trivy scanner (Harbor: 8080) |
| `enableSentinel` | boolean | `false` | Redis Sentinel (Redis: 26379) |
| `enableCluster` | boolean | `false` | Redis Cluster bus (Redis: 16379) |

---

## 📋 Example Configurations

### Minimal Dev Setup (No Domain)

```json
{
  "runtime": "fargate",
  "topology": "jenkins-service",
  "securityProfile": "dev"
}
```

**What you get:**
- ✅ Jenkins on Fargate
- ✅ No domain (uses ALB DNS name)
- ✅ HTTP only (no SSL)
- ✅ Perfect for testing

### Production with SSL & Authentication

```json
{
  "runtime": "ec2",
  "topology": "jenkins-service",
  "securityProfile": "production",
  "domain": "example.com",
  "subdomain": "jenkins",
  "enableSsl": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "my-jenkins-auth",
  "cognitoMfaEnabled": true,
  "cognitoInitialAdminEmail": "admin@example.com",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4
}
```

EC2 with auto-scaling, SSL, Cognito MFA, and custom domain.

---

## 🔌 Application-Specific Configurations

CloudForge supports 14 applications. Set `applicationId` to deploy any application.

### GitLab (CI/CD + Version Control)

```json
{
  "applicationId": "gitlab",
  "runtime": "ec2",
  "securityProfile": "production",
  "domain": "example.com",
  "subdomain": "gitlab",
  "enableSsl": true,
  "instanceType": "t3.large",
  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "gitlab-auth",
  "enableDockerRegistry": true,
  "enableSsh": true,
  "enableMetrics": true
}
```

**Includes:** Container registry (port 5050), Git SSH (port 22), Prometheus metrics, OIDC SSO.

### Mattermost (Team Collaboration)

```json
{
  "applicationId": "mattermost",
  "runtime": "fargate",
  "securityProfile": "production",
  "domain": "example.com",
  "subdomain": "chat",
  "enableSsl": true,
  "cpu": 2048,
  "memory": 4096,
  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "mattermost-auth",
  "enableSmtp": true,
  "enableClustering": true
}
```

**Includes:** PostgreSQL RDS (required), SMTP email, high-availability clustering, OIDC/SAML SSO.

### Grafana (Monitoring Dashboard)

```json
{
  "applicationId": "grafana",
  "runtime": "fargate",
  "securityProfile": "staging",
  "domain": "example.com",
  "subdomain": "monitoring",
  "enableSsl": true,
  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "grafana-auth",
  "provisionDatabase": false
}
```

**Options:** `provisionDatabase: true` for PostgreSQL (production), `false` for embedded SQLite (dev).

### Harbor (Container Registry)

```json
{
  "applicationId": "harbor",
  "runtime": "ec2",
  "securityProfile": "production",
  "domain": "example.com",
  "subdomain": "registry",
  "enableSsl": true,
  "instanceType": "t3.medium",
  "enableDockerRegistry": true,
  "enableNotary": true,
  "enableTrivy": true
}
```

**Includes:** PostgreSQL + Redis (required), Docker registry, Notary content trust, Trivy vulnerability scanning.

### Vault (Secrets Management)

```json
{
  "applicationId": "vault",
  "runtime": "ec2",
  "securityProfile": "production",
  "domain": "example.com",
  "subdomain": "vault",
  "enableSsl": true,
  "instanceType": "t3.small",
  "networkMode": "private-with-nat",
  "enableClustering": true
}
```

**Note:** Use private network for production secrets management.

### Metabase (Analytics)

```json
{
  "applicationId": "metabase",
  "runtime": "fargate",
  "securityProfile": "staging",
  "domain": "example.com",
  "subdomain": "analytics",
  "enableSsl": true,
  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "metabase-auth",
  "provisionDatabase": true
}
```

**Options:** `provisionDatabase: true` for PostgreSQL (production), `false` for embedded H2 (dev).

---

## 🏆 Compliance Framework Configurations

**Testing Status:**
- ✅ **SOC2** - Fully tested in production
- ⚠️ **HIPAA, PCI-DSS, GDPR** - Configuration provided, not yet tested in production

### SOC 2 Compliance ✅ Tested

Access controls, monitoring, 2-year log retention.

```json
{
  "runtime": "fargate",
  "topology": "jenkins-service",
  "securityProfile": "production",
  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "scopeConfigRulesToDeployment": true,
  "enableS3VersioningRemediation": true,
  "enableMonitoring": true,
  "logRetentionDays": 730,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-soc2",
  "cognitoMfaEnabled": true
}
```

Enables IAM password policy remediation, S3 versioning remediation, MFA, and continuous monitoring scoped to your deployment. Cost: ~$50-100/month.

---

### HIPAA Compliance ⚠️ Not Yet Tested

Encryption, access controls, audit trails, 6-year retention.

```json
{
  "runtime": "ec2",
  "topology": "jenkins-service",
  "securityProfile": "production",
  "complianceFrameworks": "HIPAA",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "networkMode": "private-with-nat",
  "enableEncryption": true,
  "logRetentionDays": 2190,
  "retainStorage": true,
  "bastionCidr": "10.0.1.0/24",
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-hipaa",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "both"
}
```

14-char passwords, private network, 6-year logs, encrypted storage, MFA (TOTP+SMS), retained storage. Cost: ~$150-250/month.

---

### PCI-DSS Compliance ⚠️ Not Yet Tested

Network segmentation, WAF, threat detection, 1-year retention.

```json
{
  "runtime": "fargate",
  "topology": "jenkins-service",
  "securityProfile": "production",
  "complianceFrameworks": "PCI-DSS",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "guardDutyEnabled": true,
  "guardDutyAlertsConfigured": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "certificateExpirationMonitoring": true,
  "logRetentionDays": 365,
  "networkMode": "private-with-nat",
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-pcidss",
  "cognitoMfaEnabled": true
}
```

WAF (Req 6.6), GuardDuty (Req 11.4), ALB logging (Req 10.2), certificate monitoring (Req 4.1), 1-year logs (Req 10.7). Cost: ~$200-300/month.

---

### GDPR Compliance ⚠️ Not Yet Tested

Encryption, access controls, audit trails, 2-year retention.

```json
{
  "runtime": "fargate",
  "topology": "jenkins-service",
  "securityProfile": "production",
  "region": "eu-west-1",
  "complianceFrameworks": "GDPR",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "enableEncryption": true,
  "logRetentionDays": 730,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-gdpr",
  "cognitoMfaEnabled": true,
  "enableS3VersioningRemediation": true
}
```

EU region deployment, encryption at rest/transit, MFA, S3 versioning, CloudTrail audit. Cost: ~$50-100/month.

---

### Multi-Framework Compliance ⚠️ Not Yet Tested

Combine multiple frameworks - strictest requirements win.

```json
{
  "runtime": "ec2",
  "topology": "jenkins-service",
  "securityProfile": "production",
  "complianceFrameworks": "SOC2,HIPAA,PCI-DSS",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "enableS3VersioningRemediation": true,
  "guardDutyEnabled": true,
  "guardDutyAlertsConfigured": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "certificateExpirationMonitoring": true,
  "networkMode": "private-with-nat",
  "enableEncryption": true,
  "logRetentionDays": 2190,
  "retainStorage": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-compliant",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "both",
  "bastionCidr": "10.0.1.0/24"
}
```

Combines all security controls: 14-char passwords, 6-year retention, WAF, GuardDuty, encrypted storage. Cost: ~$250-400/month.

---

## 🎓 Framework Comparison

| Requirement | SOC2 | HIPAA | PCI-DSS | GDPR |
|-------------|------|-------|---------|------|
| **Min Password Length** | 12 | 14 | 8 | 12 |
| **Password Rotation** | 90 days | 90 days | 90 days | 90 days |
| **MFA Required** | ✅ | ✅ | ✅ | ✅ |
| **Log Retention** | 2 years | 6 years | 1 year | 2 years |
| **Encryption** | ✅ | ✅ | ✅ | ✅ |
| **WAF** | Recommended | Recommended | Required | Recommended |
| **Threat Detection** | Recommended | Recommended | Required | Recommended |
| **Private Network** | Recommended | Required | Required | Recommended |
| **Storage Retention** | Optional | Required | Optional | Optional |

---

## 🧰 Full Configuration Reference

<details>
<summary id="cognito-configuration-full"><b>Cognito Configuration (Full Options)</b></summary>

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cognitoAutoProvision` | boolean | `false` | Auto-create Cognito User Pool |
| `cognitoDomainPrefix` | string | - | **[required]** Globally unique domain prefix |
| `cognitoUserPoolName` | string | - | User Pool display name |
| `cognitoMfaEnabled` | boolean | `false` | Enable multi-factor authentication |
| `cognitoMfaMethod` | string | `"both"` | MFA method: `"totp"`, `"sms"`, or `"both"` |
| `cognitoCreateGroups` | boolean | `true` | Create admin and user groups |
| `cognitoAdminGroupName` | string | `"Jenkins-Admins"` | Admin group name |
| `cognitoUserGroupName` | string | `"Jenkins-Users"` | User group name |
| `cognitoUserPoolId` | string | - | Existing User Pool ID (reuse existing) |
| `cognitoAppClientId` | string | - | Existing App Client ID (reuse existing) |
| `cognitoInitialAdminEmail` | string | - | Initial admin user email |
| `cognitoInitialAdminPhone` | string | - | Phone in E.164 format: `"+12025551234"` |

</details>

<details>
<summary id="oidc-configuration-full"><b>OIDC Configuration (Full Options)</b></summary>

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `oidcIssuer` | string | - | OIDC issuer URL |
| `oidcAuthorizationEndpoint` | string | - | Authorization endpoint URL |
| `oidcTokenEndpoint` | string | - | Token endpoint URL |
| `oidcUserInfoEndpoint` | string | - | UserInfo endpoint URL |
| `oidcClientId` | string | - | OIDC application client ID |
| `oidcClientSecretName` | string | `"jenkins/oidc/client-secret"` | Secrets Manager secret name |

**Legacy Identity Center:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ssoInstanceArn` | string | - | IAM Identity Center instance ARN |
| `ssoGroupId` | string | - | Identity Center group UUID |
| `ssoTargetAccountId` | string | - | 12-digit AWS account ID |
| `autoProvisionIdentityCenter` | boolean | `false` | Auto-provision Identity Center |
| `identityCenterGroupName` | string | `"Jenkins-Users"` | Group name for auto-provisioning |

</details>

<details>
<summary><b>Health Check Configuration</b></summary>

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `healthCheckGracePeriod` | integer | `300` | Grace period (seconds) |
| `healthCheckInterval` | integer | `30` | Check interval (seconds) |
| `healthCheckTimeout` | integer | `5` | Timeout (seconds) |
| `healthyThreshold` | integer | `2` | Healthy count threshold |
| `unhealthyThreshold` | integer | `3` | Unhealthy count threshold |

</details>

<details>
<summary><b>Advanced Monitoring & Threat Detection</b></summary>

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `guardDutyEnabled` | boolean | `false` | Enable GuardDuty threat detection |
| `guardDutyAlertsConfigured` | boolean | `false` | Configure GuardDuty alerts (EventBridge) |
| `certificateExpirationMonitoring` | boolean | `false` | Certificate expiration CloudWatch alarms |

</details>

---

## 🧪 Testing & Validation

### Quick Syntax Test

```bash
cd cfc-testing
cdk synth
```

### Full Test Suite

```bash
cd cfc-testing
./test-synth.sh
```

### Performance Benchmarking

```bash
cd cfc-testing
./benchmark-synth.sh
```

See **[Extended Testing Guide](docs/guides/EXTENDED-TESTING.md)** for comprehensive testing documentation.

---

## 🔐 Security & SBOM

### Generate Software Bill of Materials

```bash
mvn clean package -DskipTests
cat target/cfc-core-sbom.json
```

### Scan for Vulnerabilities

```bash
mvn dependency-check:check
open target/dependency-check-report.html
```

### Automated Security

Security scanning runs automatically on:
- ✅ Every push to main/develop
- ✅ All pull requests
- ✅ Weekly scheduled scans

See **[SECURITY.md](SECURITY.md)** for details.

---

## 🏗️ Repository Structure

```
cfc-core/
├── cloudforge-api/          # Core API: configuration, interfaces
├── cfc-testing/             # Testing framework & sample app
├── docs/                    # Complete documentation
│   ├── compliance/          # Compliance guides (SOC2, HIPAA, PCI-DSS, GDPR)
│   ├── setup/               # Setup guides (OIDC, Cognito, Identity Center)
│   └── guides/              # Advanced guides (testing, IAM, security)
├── .github/workflows/       # CI/CD automation
├── README.md               # This file
└── SECURITY.md             # Security policy
```

---

## 🤝 Contributing

We welcome contributions! See **[CONTRIBUTING.md](CONTRIBUTING.md)** for guidelines.

### Prerequisites

- **Java 21+**
- **Maven 3.9+**
- **Node.js 18+**
- **AWS CDK CLI**

### Quick Commands

```bash
# Fast build (skip tests)
./mvnw -T1C -DskipTests install

# Full build
./mvnw clean verify

# Single module
./mvnw -pl cloudforge-api -am package
```

---

## 📈 Changelog

See **[CHANGELOG.md](CHANGELOG.md)** for release history.

---

## 🆘 Support

- **GitHub Issues:** [Report bugs or request features](https://github.com/CloudForgeCI/cfc-core/issues)
- **Sample Project:** [Complete example](https://github.com/CloudForgeCI/cloudforge-sample)
- **Documentation:** [Full docs](docs/README.md)

---

## 💖 Sponsors

If CloudForge CI saved you time and money, consider **[supporting development](SPONSORS.md)**!

---

## 📄 License

Apache License 2.0 — see **[LICENSE](LICENSE)**

---

## 🔗 Related Projects

- **[cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample)** - Complete working example
- **[cloudforge-community](https://github.com/CloudForgeCI/cloudforge-community)** - Community wrappers

---

**Built with ❤️ by the CloudForge CI community**

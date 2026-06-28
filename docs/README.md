# CloudForge CI Documentation

Complete documentation for deploying and managing secure, compliant Jenkins infrastructure on AWS.

---

## 📖 Table of Contents

- [Quick Start](#quick-start)
- [Applications & Plugins](#applications--plugins)
- [Setup & Configuration](#setup--configuration)
- [Compliance & Security](#compliance--security)
- [Advanced Topics](#advanced-topics)
- [Reference](#reference)

---

## 🚀 Quick Start

**New to CloudForge?** Start here:

1. **[Quick Start Guide](compliance/QUICK_START_GUIDE.md)** - Get running in 10 minutes
2. **[Sample Project](https://github.com/CloudForgeCI/cloudforge-sample)** - Clone and deploy
3. **[Interactive Deployer](guides/INTERACTIVE_DEPLOYER.md)** - User-friendly CLI tool

**Example deployment:**
```bash
git clone https://github.com/CloudForgeCI/cloudforge-sample.git
cd cloudforge-sample
mvn clean package
cdk deploy --context cfc=@deployment-context.json
```

---

## 🔌 Applications & Plugins

### Application Guides

Comprehensive guides for each application with deployment-context examples:

| Application | Status | Guide |
|-------------|--------|-------|
| **Jenkins** | Verified | [Jenkins Guide](guides/applications/jenkins.md) |
| **Mattermost** | Verified | [Mattermost Guide](guides/applications/mattermost.md) |
| **Metabase** | Verified | [Metabase Guide](guides/applications/metabase.md) |
| **GitLab** | Available | [GitLab Guide](guides/applications/gitlab.md) |
| **Grafana** | Available | [Grafana Guide](guides/applications/grafana.md) |
| **Harbor** | Available | [Harbor Guide](guides/applications/harbor.md) |
| **Nexus** | Available | [Nexus Guide](guides/applications/nexus.md) |
| **SonarQube** | Plugin | [SonarQube Guide](guides/applications/sonarqube.md) |

**[All Application Guides](guides/applications/)** | **[Deployment Context Examples](examples/applications/)**

### Application Catalog

| Document | Description |
|----------|-------------|
| **[Application Catalog](applications/README.md)** | Complete catalog of 33 built-in applications |
| **[Application Compliance](applications/COMPLIANCE.md)** | Compliance requirements for each application |
| **[OIDC Integration](applications/OIDC.md)** | Application-level OIDC authentication (Grafana, GitLab, Jenkins) |

### Plugin System

| Document | Description |
|----------|-------------|
| **[Plugin Ecosystem](plugins/PLUGIN-ECOSYSTEM.md)** | Overview of built-in applications and plugin architecture |
| **[Plugin System Guide](plugins/PLUGIN-SYSTEM.md)** | Core architecture and development patterns |
| **[Application Plugin Guide](plugins/APPLICATION-PLUGIN-GUIDE.md)** | Build custom application plugins |
| **[Compliance Plugin Guide](plugins/COMPLIANCE-PLUGIN-GUIDE.md)** | Build custom compliance framework validators |

---

## ⚙️ Setup & Configuration

### Authentication

| Document | Description | Best For |
|----------|-------------|----------|
| **[Identity Center Setup](setup/AWS_IDENTITY_CENTER_SETUP.md)** | AWS IAM Identity Center + ALB-OIDC (Okta, Auth0) | Enterprise SSO |
| **[Cognito MFA Compliance](setup/COGNITO_MFA_COMPLIANCE_SETUP.md)** | AWS Cognito user pools with MFA | HIPAA, PCI-DSS, Quick setup |

### Configuration Files

| Document | Description |
|----------|-------------|
| **[deployment-context.json Reference](https://github.com/CloudForgeCI/cfc-core/blob/main/readme.md#configuration-reference)** | All configuration properties |
| **[Compliance Configurations](https://github.com/CloudForgeCI/cfc-core/blob/main/readme.md#compliance-framework-configurations)** | Framework-specific settings |

---

## 🔐 Compliance & Security

### Compliance Frameworks

| Framework | Document | Key Features |
|-----------|----------|--------------|
| **SOC 2** | [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md) | Access controls, monitoring, 2-year logs |
| **HIPAA** | [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md) | Encryption, 6-year logs, private network |
| **PCI-DSS** | [PCI-DSS Guide](compliance/PCI_DSS_COMPLIANCE.md) | WAF, threat detection, 1-year logs |
| **GDPR** | [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md) | Encryption, data protection, EU regions |

**[Framework Comparison Table](https://github.com/CloudForgeCI/cfc-core/blob/main/readme.md#framework-comparison)** - See requirements side-by-side

### Automated Compliance

| Document | Description |
|----------|-------------|
| **[Automated Compliance](compliance/AUTOMATED_COMPLIANCE.md)** | Auto-remediation features overview |
| **[S3 Versioning Remediation](compliance/S3_VERSIONING_REMEDIATION.md)** | Automatic S3 versioning enforcement |
| **[Multi-Framework Compliance](compliance/MULTI_FRAMEWORK_COMPLIANCE.md)** | Deploy multiple frameworks simultaneously |
| **[PCI-DSS Application Security](compliance/PCI_DSS_APPLICATION_SECURITY.md)** | Application-level PCI compliance |
| **[AWS Config Multi-Stack](compliance/AWS_CONFIG_MULTI_STACK.md)** | Multi-account AWS Config setup |
| **[Deployment Guide](compliance/DEPLOYMENT_GUIDE.md)** | Production deployment strategies |

### Security

| Document | Description |
|----------|-------------|
| **[Security Hardening](https://github.com/CloudForgeCI/cfc-core/blob/main/SECURITY.md)** | Security best practices and policies |
| **[Security Rules](guides/SECURITY_RULES_README.md)** | Comprehensive security guidelines |
| **[IAM Rules](guides/IAM_RULES.md)** | IAM best practices and policies |

---

## 📚 Advanced Topics

### Audit & Monitoring

| Document | Description |
|----------|-------------|
| **[AWS Audit Manager](AUDIT_MANAGER.md)** | Continuous audit automation setup |
| **[Auditor Compliance Mapping](AUDITOR_COMPLIANCE_MAPPING.md)** | Control mappings for external audits |

### Testing & Validation

| Document | Description |
|----------|-------------|
| **[Extended Testing](guides/EXTENDED-TESTING.md)** | Comprehensive testing guide |
| **[Compliance Truth Tables](testing/COMPLIANCE_TRUTH_TABLES.md)** | Systematic compliance rules testing |

### Developer Resources

| Document | Description |
|----------|-------------|
| **[IAM Rules](guides/IAM_RULES.md)** | IAM best practices and policies |
| **[Security Rules](guides/SECURITY_RULES_README.md)** | Comprehensive security guidelines |

---

## 📑 Reference

### Indexes & Catalogs

- **[Compliance Overview](compliance/README.md)** - All compliance documentation
- **[Configuration Reference](https://github.com/CloudForgeCI/cfc-core/blob/main/readme.md#configuration-reference)** - All deployment-context.json properties

### Quick Links

- **[Main README](https://github.com/CloudForgeCI/cfc-core/blob/main/readme.md)** - Project overview and quick start
- **[CHANGELOG](https://github.com/CloudForgeCI/cfc-core/blob/main/CHANGELOG.md)** - Release history
- **[CONTRIBUTING](https://github.com/CloudForgeCI/cfc-core/blob/main/CONTRIBUTING.md)** - How to contribute
- **[LICENSE](../LICENSE)** - Apache 2.0 License

---

## 🎯 Documentation by Use Case

### "I want to deploy an application"
1. [Application Guides](guides/applications/) - Comprehensive guides for each app
2. [Deployment Context Examples](docs/examples/) - Ready-to-use JSON configs
3. [Interactive Deployer](guides/INTERACTIVE_DEPLOYER.md) - CLI deployment tool

### "I want to deploy Jenkins quickly"
1. [Jenkins Guide](guides/applications/jenkins.md)
2. [Jenkins Dev Example](docs/examples/jenkins-dev.json)
3. [Quick Start Guide](compliance/QUICK_START_GUIDE.md)

### "I need SOC2 compliance"
1. [SOC2 Configuration Example](https://github.com/CloudForgeCI/cfc-core/blob/main/readme.md#soc-2-compliance-simplest)
2. [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md)
3. [Automated Compliance](compliance/AUTOMATED_COMPLIANCE.md)

### "I need HIPAA compliance"
1. [HIPAA Configuration Example](https://github.com/CloudForgeCI/cfc-core/blob/main/readme.md#hipaa-compliance-healthcare)
2. [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md)
3. [Security Hardening](https://github.com/CloudForgeCI/cfc-core/blob/main/SECURITY.md)

### "I need PCI-DSS compliance"
1. [PCI-DSS Configuration Example](https://github.com/CloudForgeCI/cfc-core/blob/main/readme.md#pci-dss-compliance-payment-processing)
2. [PCI-DSS Guide](compliance/PCI_DSS_COMPLIANCE.md)
3. [PCI-DSS Application Security](compliance/PCI_DSS_APPLICATION_SECURITY.md)

### "I want to set up authentication"
1. [Identity Center Setup](setup/AWS_IDENTITY_CENTER_SETUP.md) (enterprise SSO)
2. [Cognito MFA Setup](setup/COGNITO_MFA_COMPLIANCE_SETUP.md) (AWS-native with MFA)

### "I'm deploying to production"
1. [Deployment Guide](compliance/DEPLOYMENT_GUIDE.md)
2. [Security Rules](guides/SECURITY_RULES_README.md)
3. [Extended Testing](guides/EXTENDED-TESTING.md)

---

## 📞 Getting Help

- **Issues:** [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
- **Examples:** [cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample)
- **Discussions:** [GitHub Discussions](https://github.com/CloudForgeCI/cfc-core/discussions)

---

## 🤝 Contributing

Found a documentation error or want to improve something?

1. Read [CONTRIBUTING.md](https://github.com/CloudForgeCI/cfc-core/blob/main/CONTRIBUTING.md)
2. Submit a pull request
3. Help others by sharing your knowledge

---

**Last Updated:** June 2026
**Documentation Version:** 3.1.0

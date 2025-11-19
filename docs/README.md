# CloudForge CI Documentation

Complete documentation for deploying and managing secure, compliant Jenkins infrastructure on AWS.

---

## 📖 Table of Contents

- [Quick Start](#quick-start)
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

## ⚙️ Setup & Configuration

### Authentication

| Document | Description | Best For |
|----------|-------------|----------|
| **[OIDC Setup Guide](setup/OIDC_SETUP_GUIDE.md)** | ALB-OIDC with Identity Center, Okta, Auth0 | Enterprise SSO |
| **[Cognito Setup](setup/COGNITO_SETUP_COMPLETE.md)** | AWS Cognito user pools with MFA | Quick setup, AWS-native |
| **[Cognito MFA Compliance](setup/COGNITO_MFA_COMPLIANCE_SETUP.md)** | MFA configuration for compliance | HIPAA, PCI-DSS |
| **[Identity Center Setup](setup/AWS_IDENTITY_CENTER_SETUP.md)** | AWS IAM Identity Center integration | Enterprise organizations |
| **[OIDC Integration Summary](OIDC_INTEGRATION_SUMMARY.md)** | OIDC implementation overview | Developers |

### Configuration Files

| Document | Description |
|----------|-------------|
| **[deployment-context.json Reference](../README.md#configuration-reference)** | All configuration properties |
| **[Compliance Configurations](../README.md#compliance-framework-configurations)** | Framework-specific settings |

---

## 🔐 Compliance & Security

### Compliance Frameworks

| Framework | Document | Key Features |
|-----------|----------|--------------|
| **SOC 2** | [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md) | Access controls, monitoring, 2-year logs |
| **HIPAA** | [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md) | Encryption, 6-year logs, private network |
| **PCI-DSS** | [PCI-DSS Guide](compliance/PCI_DSS_README.md) | WAF, threat detection, 1-year logs |
| **GDPR** | [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md) | Encryption, data protection, EU regions |

**[Framework Comparison Table](../README.md#framework-comparison)** - See requirements side-by-side

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
| **[Security Hardening](../SECURITY.md)** | Security best practices and policies |
| **[Security Rules](guides/SECURITY_RULES_README.md)** | Comprehensive security guidelines |
| **[IAM Rules](guides/IAM_RULES.md)** | IAM best practices and policies |

---

## 📚 Advanced Topics

### Audit & Monitoring

| Document | Description |
|----------|-------------|
| **[AWS Audit Manager](AUDIT_MANAGER.md)** | Continuous audit automation setup |
| **[Config to Audit Manager Linkage](CONFIG_TO_AUDIT_MANAGER_LINKAGE.md)** | Integrate Config with Audit Manager |
| **[Removal Policy Audit](REMOVAL_POLICY_AUDIT.md)** | Data retention policy analysis |

### Testing & Validation

| Document | Description |
|----------|-------------|
| **[Extended Testing](guides/EXTENDED-TESTING.md)** | Comprehensive testing guide |
| **[Testing Recommendations](guides/TESTING_RECOMMENDATIONS.md)** | Best practices for testing |

### Developer Resources

| Document | Description |
|----------|-------------|
| **[Cognito Implementation](COGNITO_IMPLEMENTATION_SUMMARY.md)** | Cognito integration details |
| **[OIDC Authentication](OIDC_AUTHENTICATION.md)** | OIDC implementation guide |

---

## 📑 Reference

### Indexes & Catalogs

- **[Compliance Analysis Index](compliance/COMPLIANCE_ANALYSIS_INDEX.md)** - All compliance documentation
- **[Configuration Reference](../README.md#configuration-reference)** - All deployment-context.json properties

### Quick Links

- **[Main README](../README.md)** - Project overview and quick start
- **[CHANGELOG](../CHANGELOG.md)** - Release history
- **[CONTRIBUTING](../CONTRIBUTING.md)** - How to contribute
- **[LICENSE](../LICENSE)** - Apache 2.0 License

---

## 🎯 Documentation by Use Case

### "I want to deploy Jenkins quickly"
1. [Quick Start Guide](compliance/QUICK_START_GUIDE.md)
2. [Sample Project](https://github.com/CloudForgeCI/cloudforge-sample)
3. [Interactive Deployer](guides/INTERACTIVE_DEPLOYER.md)

### "I need SOC2 compliance"
1. [SOC2 Configuration Example](../README.md#soc-2-compliance-simplest)
2. [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md)
3. [Automated Compliance](compliance/AUTOMATED_COMPLIANCE.md)

### "I need HIPAA compliance"
1. [HIPAA Configuration Example](../README.md#hipaa-compliance-healthcare)
2. [Multi-Framework Guide](compliance/MULTI_FRAMEWORK_COMPLIANCE.md)
3. [Security Hardening](../SECURITY.md)

### "I need PCI-DSS compliance"
1. [PCI-DSS Configuration Example](../README.md#pci-dss-compliance-payment-processing)
2. [PCI-DSS Guide](compliance/PCI_DSS_README.md)
3. [PCI-DSS Application Security](compliance/PCI_DSS_APPLICATION_SECURITY.md)

### "I want to set up authentication"
1. [OIDC Setup Guide](setup/OIDC_SETUP_GUIDE.md) (recommended)
2. [Cognito Setup](setup/COGNITO_SETUP_COMPLETE.md) (easiest)
3. [Identity Center Setup](setup/AWS_IDENTITY_CENTER_SETUP.md) (enterprise)

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

1. Read [CONTRIBUTING.md](../CONTRIBUTING.md)
2. Submit a pull request
3. Help others by sharing your knowledge

---

**Last Updated:** 2025-11-18
**Documentation Version:** 2.0.6

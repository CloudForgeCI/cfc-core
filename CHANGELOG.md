# Changelog

All notable changes to CloudForge Community Core are documented here.

## [3.0.0] - Current Release

### Added

**AWS Backup Infrastructure (NEW)**
- BackupFactory for EFS and RDS automated backups
- Daily backup schedules with configurable retention (DEV: disabled, STAGING: 14 days, PRODUCTION: 90 days)
- Backup vault lock for PRODUCTION (prevents manual deletion - PCI-DSS compliance)
- Cross-region backup copy support for disaster recovery
- AwsRegion enum with DR region pairs and geographic areas for data residency

**SAML Authentication (NEW) - ⚠️ IN DEVELOPMENT**
- SAML 2.0 integration framework for enterprise IdPs
- MattermostSamlIntegration for AD/LDAP group sync
- MetabaseSamlIntegration with group mapping
- ApplicationSamlFactory for application-level SAML configuration
- CognitoSamlFactory for Cognito + Keycloak SAML bridge
- IdentityCenterSamlFactory for AWS IAM Identity Center SAML
- KeycloakFactory for Keycloak IdP deployment

> **Note:** SAML authentication and Keycloak integration are still in active development.
> These features may have breaking changes in future releases.

**PCI-DSS Compliance Enhancements**
- TLS 1.2+ SSL policy on ALB HTTPS listeners (SslPolicy.RECOMMENDED_TLS)
- RDS IAM database authentication for STAGING/PRODUCTION profiles
- ECS Container Insights enabled for STAGING/PRODUCTION profiles
- Backup vault lock to prevent recovery point deletion

**Configuration Introspection (NEW)**
- @ConfigField annotation for field metadata
- ConfigurationIntrospector for automatic field discovery
- VisibilityExpressionEvaluator for conditional field visibility
- Support for field tags (DESTRUCTIVE, BILLING_IMPACT, IMMUTABLE)
- Category-based field organization

### Changed

- VpcFactory now reads flowlogs dynamically from context (fixes flow logs not being created)
- ComplianceFactory S3 buckets use appropriate removal policies for compliance buckets
- Security profile configurations now support deployment context overrides

### Fixed

- VPC Flow Logs not being created due to annotation injection timing
- S3 auto-delete permission errors on compliance buckets
- AWS Backup vault/plan name validation (2-50 chars, alphanumeric with hyphens/underscores)


**Plugin System (NEW)**
- Universal ApplicationSpec plugin system for custom applications
- Compliance framework plugin system for custom validators
- Java ServiceLoader-based automatic plugin discovery
- 14 built-in applications across 8 categories
- 12 built-in compliance frameworks (5 always-load, 7 conditional)
- Plugin ecosystem documentation and developer guides

**Applications (NEW)**
- Jenkins, GitLab, Drone (CI/CD)
- Gitea (Version Control)
- Grafana, Prometheus (Monitoring)
- Metabase, Apache Superset (Analytics)
- PostgreSQL, Redis (Databases)
- Nexus, Harbor (Artifact Registries)
- HashiCorp Vault (Secrets Management)
- Mattermost (Collaboration)

**OIDC Integration (NEW)**
- Application-level OIDC integration framework
- Grafana OIDC integration (implemented)
- GitLab OIDC integration (implemented)
- Jenkins OIDC integration (implemented)
- Gitea OIDC integration (in progress)
- Cognito and IAM Identity Center OIDC configurations
- PKCE support for enhanced security
- Secrets Manager integration for client secrets

**Authentication & Security**
- AWS IAM Identity Center integration for enterprise SSO
- OIDC authentication factory for ALB-based auth (alb-oidc mode)
- Application-level OIDC integration (application-oidc mode)
- Cognito authentication with auto-provisioning
- Multi-framework compliance support (PCI-DSS, HIPAA, SOC 2, GDPR, ISO 27001)
- Security profile configurations (DEV, STAGING, PRODUCTION)
- Comprehensive security rules engine with compliance mappings

**Infrastructure**
- WAF factory for web application firewall
- Compliance factory for automated audit evidence
- AWS Audit Manager integration
- Enhanced monitoring and logging capabilities
- VPC Flow Logs support
- CloudWatch alarms for security events

**Testing & Validation**
- Comprehensive CDK synthesis test suite (100% success rate)
- Automated validation workflow in GitHub Actions
- Performance benchmarking tool for synthesis operations
- Truth table generator for configuration combinations
- Drift detection and resource validation

**Documentation**
- OIDC setup guides for IAM Identity Center and Cognito
- Security compliance documentation
- PCI-DSS application security guide
- Multi-framework compliance mapping
- Interactive deployer documentation
- Testing recommendations and validation guides

**Developer Experience**
- Interactive deployment script with guided setup
- SystemContext orchestration layer
- Strategy pattern for extensible deployments
- Improved error messages and validation
- Context injection framework
- Slot-based configuration system
- ApplicationFactory for universal application deployment
- FrameworkLoader for automatic compliance discovery
- Ec2Context and UserDataBuilder for simplified EC2 configuration

**Supply Chain Security**
- Software Bill of Materials (SBOM) generation in CycloneDX format
- OWASP Dependency-Check integration
- Automated vulnerability scanning in CI/CD
- GitHub Security integration with SARIF reports
- Weekly scheduled security scans

### Changed

**Refactoring**
- Renamed `IConfiguration` to `BaseConfiguration`
- Renamed `ISlot` to `BaseSlot`
- Cleaned up excessive logging in factories
- Moved SSL management logic to appropriate locations
- Improved DNS record creation (slot-based approach)
- Enhanced HTTP listener routing for Fargate

**Configuration**
- DNS record creation now prevents duplicates
- HTTP listener properly routes to Fargate services in SSL mode
- Improved target group configuration for ALB
- Better hosted zone lookup (prevents duplicate zones)

**Testing**
- Expanded test coverage across all modules
- Added unit tests for BaseFactory (100% coverage)
- Comprehensive validation for CDK constructs
- Fixed all synthesis test failures (10/10 passing)

### Fixed

**Infrastructure Issues**
- DNS record duplication using slot-based approach
- HTTP listener routing for "Jenkins is starting up..." page
- ALB target group creation and listener configuration
- Hosted zone creation (now uses lookup when createZone=false)
- Fargate SSL mode routing issues

**Validation & Testing**
- GitHub Pages 404 errors in workflow
- Validation workflow failures
- Vulnerability report HTML page generation
- EC2 + Node topology synthesis (architectural incompatibility resolved)
- Compilation errors in test files

**Code Quality**
- Removed verbose LOG.info debug statements
- Cleaned up excessive logging throughout codebase
- Fixed unused imports
- Improved code readability

## [2.0.0] - Previous Release

### Foundation
- AWS CDK v2 constructs for Jenkins deployment
- EC2 and Fargate runtime support
- Service and single-node topology options
- VPC with public/private subnet configurations
- Application Load Balancer with SSL/TLS support
- Route 53 DNS management
- EFS storage for Jenkins home
- CloudWatch monitoring and logging
- Security groups with least privilege
- IAM roles and policies

---

## Version Scheme

We follow [Semantic Versioning](https://semver.org/):
- **MAJOR**: Breaking API changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

## Getting Updates

Watch this repository:
- **Releases only**: Get notified of new versions
- **Security advisories**: Critical security updates

Check before upgrading:
```bash
# View the full changelog
cat CHANGELOG.md

# Check current version
git describe --tags --abbrev=0
```

## Links

- [Security Policy](SECURITY.md)
- [Contributing Guidelines](CONTRIBUTING.md) (if exists)
- [GitHub Releases](https://github.com/CloudForgeCI/cfc-core/releases)

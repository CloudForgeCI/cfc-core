# CloudForge CI Documentation

Documentation for defining, deploying, and validating application infrastructure on AWS with
CloudForge. For a short introduction and quick start, see the
[project README](https://github.com/CloudForgeCI/cfc-core/blob/develop/readme.md).

- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Applications and plugins](#applications-and-plugins)
- [Authentication](#authentication)
- [Local emulators](#local-emulators)
- [Compliance and security](#compliance-and-security)
- [Testing and validation](#testing-and-validation)
- [Project and release](#project-and-release)

## Getting started

| Document | Description |
|---|---|
| [Onboarding Quick Start](ONBOARDING_QUICK_START.md) | Deploy an example configuration to AWS, from development to production profiles |
| [Local Emulator Quick Start](guides/LOCAL_EMULATOR_QUICK_START.md) | Build, start MiniStack or LocalStack, and deploy without an AWS account |
| [cloudforge-cli](https://github.com/CloudForgeCI/cloudforge-cli) | Command-line tool for deploying and managing local emulators |
| [Compliance Quick Start](compliance/QUICK_START_GUIDE.md) | Configure compliance validation |
| [Sample project](https://github.com/CloudForgeCI/cloudforge-sample) | Standalone project that consumes the published artifacts |
| [Sample project BOM template](architecture/cloudforge-sample-bom.template.md) | POM and layout for your own project |

## Configuration

| Document | Description |
|---|---|
| [Advanced Guide](ADVANCED.md) | Full `deployment-context.json` reference, security profile defaults, example configurations, and command-line reference |
| [Example configurations](examples/README.md) | Deployment contexts by profile and framework |
| [Application examples](examples/applications/README.md) | Deployment contexts per application |
| [Security Profiles](guides/SECURITY_RULES_README.md) | `dev`, `staging`, and `production` defaults |
| [IAM Rules](guides/IAM_RULES.md) | IAM profiles and generated permissions |
| [Database Deployment Guide](databases/DATABASE-DEPLOYMENT-GUIDE.md) | RDS provisioning and remediation |
| [SSM Parameter Scoping](SSM_PARAMETER_SCOPING.md) | SSM parameter naming and access scope |

## Applications and plugins

| Document | Description |
|---|---|
| [Application guides](guides/applications/README.md) | Jenkins, GitLab, Grafana, Harbor, Mattermost, Metabase, Nexus, SonarQube, and others |
| [CMS guides](guides/cms/README.md) | WordPress, WooCommerce, Drupal, Magento, PrestaShop, Moodle, and the `cms-service` topology |
| [Application catalog](applications/README.md) | Built-in application specifications |
| [Application compliance](applications/COMPLIANCE.md) | Compliance considerations per application |
| [CMS applications](applications/CMS.md) | CMS application reference |
| [Plugin Ecosystem](plugins/PLUGIN-ECOSYSTEM.md) | Built-in applications and plugin architecture |
| [Plugin System Guide](plugins/PLUGIN-SYSTEM.md) | Plugin architecture and development patterns |
| [Application Plugin Guide](plugins/APPLICATION-PLUGIN-GUIDE.md) | Build an application plugin |
| [Compliance Plugin Guide](plugins/COMPLIANCE-PLUGIN-GUIDE.md) | Build a compliance framework plugin |

## Authentication

| Document | Description |
|---|---|
| [OIDC Integration](applications/OIDC.md) | ALB-level and application-level OIDC |
| [Cognito MFA Setup](setup/COGNITO_MFA_COMPLIANCE_SETUP.md) | Cognito user pools with MFA |
| [IAM Identity Center Setup](setup/AWS_IDENTITY_CENTER_SETUP.md) | IAM Identity Center and external identity providers |

## Local emulators

| Document | Description |
|---|---|
| [Local Emulator Quick Start](guides/LOCAL_EMULATOR_QUICK_START.md) | Build, start, deploy |
| [Application compatibility](guides/LOCAL_EMULATOR_APP_CATALOG.md) | Which applications run on MiniStack and LocalStack |
| [Local host names](guides/LOCAL_EMULATOR_HOSTS.md) | `*.cloudforge.localhost` host entries |
| [Emulator edge](guides/LOCAL_EMULATOR_EDGE.md) | nginx routing by `Host` header |
| [MiniStack](ministack/README.md) | Setup, deployment, verification, troubleshooting |
| [LocalStack](localstack/README.md) | Token, adapter behavior, deployable applications |
| [Docker Quick Start](guides/DOCKER_QUICK_START.md) and [Docker local development](guides/DOCKER_LOCAL_DEV_README.md) | Run applications with Docker Compose |

## Compliance and security

| Document | Description |
|---|---|
| [Compliance overview](compliance/README.md) | Index of compliance documentation |
| [Multi-Framework Compliance](compliance/MULTI_FRAMEWORK_COMPLIANCE.md) | SOC 2, PCI DSS, HIPAA, and GDPR controls |
| [Automated Compliance](compliance/AUTOMATED_COMPLIANCE.md) | AWS Config rules and remediation |
| [Compliance Deployment Guide](compliance/DEPLOYMENT_GUIDE.md) | Production deployment considerations |
| [PCI DSS Compliance](compliance/PCI_DSS_COMPLIANCE.md) and [PCI DSS Application Security](compliance/PCI_DSS_APPLICATION_SECURITY.md) | PCI DSS controls |
| [S3 Versioning Remediation](compliance/S3_VERSIONING_REMEDIATION.md) | S3 versioning remediation |
| [AWS Config Multi-Stack](compliance/AWS_CONFIG_MULTI_STACK.md) | Sharing AWS Config infrastructure across stacks |
| [CloudTrail Auto-Remediation](CLOUDTRAIL_AUTO_REMEDIATION.md) | CloudTrail remediation |
| [Compliance Posture](COMPLIANCE_POSTURE.md) | Control status and test coverage |
| [Compliance Severity Levels](COMPLIANCE_SEVERITY_LEVELS.md) | Severity classification |
| [Auditor Compliance Mapping](AUDITOR_COMPLIANCE_MAPPING.md) | Control mappings for external audits |
| [Audit Readiness Guide](AUDIT_READINESS_GUIDE.md) | Preparing evidence for an audit |
| [AWS Audit Manager](AUDIT_MANAGER.md) | Audit Manager integration |
| [Evidence generation example](EVIDENCE_GENERATION_OUTPUT_EXAMPLE.md) | Sample evidence output |
| [Security policy](https://github.com/CloudForgeCI/cfc-core/blob/develop/SECURITY.md) | Supported versions and vulnerability reporting |

## Testing and validation

| Document | Description |
|---|---|
| [Extended Testing](guides/EXTENDED-TESTING.md) | Synthesis, validation, and benchmark scripts |
| [Compliance Truth Tables](testing/COMPLIANCE_TRUTH_TABLES.md) | Framework and profile combination tests |
| [Integration Tests](testing/INTEGRATION_TESTS.md) | AWS integration tests |
| [Test reports](https://cloudforgeci.github.io/cfc-core/) | Published coverage, validation, compliance, and drift reports |

## Project and release

| Document | Description |
|---|---|
| [Contributing](CONTRIBUTING.md) | Development setup, tests, and pull requests |
| [Maven Release Process](MAVEN_RELEASE_PROCESS.md) | How artifacts are published |
| [Documentation Maintenance](DOCUMENTATION_SETUP.md) | Maintaining this documentation site |
| [Changelog](https://github.com/CloudForgeCI/cfc-core/blob/develop/CHANGELOG.md) | Release history |
| [License](https://github.com/CloudForgeCI/cfc-core/blob/develop/LICENSE) | Apache License 2.0 |

## Getting help

- [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
- [GitHub Discussions](https://github.com/CloudForgeCI/cfc-core/discussions)

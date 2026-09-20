# CloudForge Application Guides

These guides describe how CloudForge deploys each built-in application: its image, ports, storage, supported authentication modes, and example deployment contexts.

## Available Applications

### CI/CD & Automation

| Application | Application ID | Status | Guide |
|-------------|----------------|--------|-------|
| **Jenkins** | `jenkins` | Verified | [Jenkins Guide](jenkins.md) |
| **GitLab** | `gitlab` | Available | [GitLab Guide](gitlab.md) |
| **Drone** | `drone` | Available | [Drone Guide](drone.md) |

### Team Collaboration

| Application | Application ID | Status | Guide |
|-------------|----------------|--------|-------|
| **Mattermost Team** | `mattermost-team` | Verified | [Mattermost Guide](mattermost.md) |
| **Mattermost Enterprise** | `mattermost-enterprise` | Verified | [Mattermost Guide](mattermost.md) |

> **Mattermost editions:** `mattermost-team` needs no license and signs users in through Mattermost's GitLab OAuth provider, without single logout. `mattermost-enterprise` uses Mattermost's native OpenID Connect with single logout and requires a Mattermost license. See the [Mattermost Guide](mattermost.md).

### Analytics & Business Intelligence

| Application | Application ID | Status | Guide |
|-------------|----------------|--------|-------|
| **Metabase** | `metabase` | Verified | [Metabase Guide](metabase.md) |
| **Superset** | `superset` | Available | [Superset Guide](superset.md) |

### Monitoring & Observability

| Application | Application ID | Status | Guide |
|-------------|----------------|--------|-------|
| **Grafana** | `grafana` | Available | [Grafana Guide](grafana.md) |
| **Prometheus** | `prometheus` | Available | [Prometheus Guide](prometheus.md) |

### Artifact Registries

| Application | Application ID | Status | Guide |
|-------------|----------------|--------|-------|
| **Harbor** | `harbor` | Available | [Harbor Guide](harbor.md) |
| **Nexus** | `nexus` | Available | [Nexus Guide](nexus.md) |

### Version Control

| Application | Application ID | Status | Guide |
|-------------|----------------|--------|-------|
| **Gitea** | `gitea` | Available | [Gitea Guide](gitea.md) |

### Code Quality

| Application | Application ID | Status | Guide |
|-------------|----------------|--------|-------|
| **SonarQube** | `sonarqube` | Available | [SonarQube Guide](sonarqube.md) |

### Databases

| Application | Application ID | Status | Guide |
|-------------|----------------|--------|-------|
| **PostgreSQL** | `postgresql` | Available | [PostgreSQL Guide](postgresql.md) |
| **Redis** | `redis` | Available | [Redis Guide](redis.md) |

### Secrets Management

| Application | Application ID | Status | Guide |
|-------------|----------------|--------|-------|
| **Vault** | `vault` | Available | [Vault Guide](vault.md) |

### CMS, E-commerce, Forums, and Other Web Applications

CloudForge also includes PHP-based applications that use the `cms-service` topology: WordPress, WooCommerce, Drupal, Joomla, TYPO3, Concrete CMS, October CMS, Magento, PrestaShop, OpenCart, Sylius, Bagisto, phpBB, Flarum, MyBB, SuiteCRM, MediaWiki, Moodle, and Dolphin (UNA). They do not have dedicated guides yet. The full list of built-in application specs is registered in `cloudforge-api/src/main/resources/META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`.

**Status legend:**
- **Verified**: deployed and exercised end to end by maintainers.
- **Available**: built in and covered by unit tests, but not yet verified end to end.

## Quick Start

### 1. Choose an Application

Use the guides above to choose an application and review its requirements.

### 2. Copy a Deployment Context

The CDK app in `cfc-testing` synthesizes `cfc-testing/deployment-context.json`. Start from an example:

```bash
cd cfc-testing
cp ../docs/examples/applications/jenkins-dev.json deployment-context.json

# Edit the required fields
vim deployment-context.json

# Deploy
cdk deploy
```

You can also run the Interactive Deployer, which prompts for these values and writes `deployment-context.json` for you. See the [root README](https://github.com/CloudForgeCI/cfc-core/blob/develop/readme.md).

### 3. Customize for Your Environment

At minimum, update these fields:
- `stackName`: a unique CloudFormation stack name
- `domain` / `subdomain`: your DNS configuration (when using TLS or a custom domain)
- `cognitoDomainPrefix`: a globally unique Cognito domain prefix (when using OIDC)
- `region`: the target AWS region

## Guide Structure

Each application guide covers the relevant subset of:

1. **Quick Reference**: application ID, image, port, resource defaults, and supported auth modes
2. **Optional Ports**: additional ports and the deployment-context flags that open them
3. **Database Requirements**: engine, defaults, and how CloudForge connects the application
4. **Authentication**: supported auth modes and OIDC integration details
5. **Environment Variables**: variables CloudForge sets in the container
6. **Storage Configuration**: container and EC2 data paths
7. **Deployment Context Examples**: example JSON configurations
8. **Compliance Considerations**: infrastructure controls CloudForge configures and controls you configure yourself
9. **Post-Deployment Tasks** and **Troubleshooting**

## Deployment Context Examples

The `docs/examples/applications/` directory contains application-specific examples, including:

```
docs/examples/applications/
├── jenkins-dev.json           # Jenkins development
├── jenkins-production.json    # Jenkins production with SOC 2 rules
├── mattermost-dev.json        # Mattermost development
├── mattermost-production.json # Mattermost production with PostgreSQL
├── metabase-dev.json          # Metabase development
├── metabase-production.json   # Metabase production with PostgreSQL
├── gitlab-production.json     # GitLab production with PostgreSQL
├── grafana-production.json    # Grafana production with PostgreSQL
├── harbor-production.json
├── sonarqube-production.json
└── compliance-*.json          # Framework-focused examples
```

## Authentication Modes

CloudForge supports three authentication modes (`authMode`):

| Mode | Description |
|------|-------------|
| `none` | No CloudForge-managed authentication. Not recommended for production. |
| `alb-oidc` | The Application Load Balancer authenticates users with OIDC before forwarding requests. |
| `application-oidc` | The application itself signs users in through OIDC (or SAML, for Metabase). |

Each application declares which modes it supports. The interactive deployer and `CloudForgeDeployment` replace an unsupported mode with the application's recommended mode and print a warning.

| Supported modes | Applications |
|-----------------|--------------|
| `application-oidc`, `alb-oidc`, `none` | Jenkins, GitLab, Grafana, Mattermost Team, Mattermost Enterprise, Metabase (SAML), WordPress, WooCommerce, Drupal, Joomla, Magento, PrestaShop, Moodle |
| `alb-oidc`, `none` | Other CMS, e-commerce, forum, and wiki applications |
| `none` | Drone, Gitea, SonarQube, Superset, Prometheus, Harbor, Nexus, Vault, PostgreSQL, Redis |

**Recommendation:**
- **Development**: `none`, or `alb-oidc` where supported.
- **Production**: `application-oidc` where supported, so the application has per-user identities.

## Runtime Options

| Runtime | Characteristics |
|---------|-----------------|
| **Fargate** (`fargate`) | No instances to manage; data on EFS. Billed per task. |
| **EC2** (`ec2`) | Instances you can access through SSM Session Manager; data on EBS or EFS depending on the application. Requires instance patching and management. |

## Related Documentation

- [Deployment Context Reference](../../examples/README.md)
- [Configuration Reference and Advanced Commands](../../ADVANCED.md)
- [Plugin System](../../plugins/PLUGIN-SYSTEM.md): create custom applications
- [Compliance Guide](../../compliance/README.md)
- [OIDC Integration](../../applications/OIDC.md)

## Support

- **Issues**: [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
- **Examples**: [cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample)

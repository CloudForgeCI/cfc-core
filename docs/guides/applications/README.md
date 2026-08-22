# CloudForge Application Guides

Guides for deploying applications with CloudForge include configuration options and deployment-context examples.

## Available Applications

### CI/CD & Automation

| Application | Status | Guide |
|-------------|--------|-------|
| **Jenkins** | Verified | [Jenkins Guide](jenkins.md) |
| **GitLab** | Available | [GitLab Guide](gitlab.md) |
| **Drone** | Available | [Drone Guide](drone.md) |

### Team Collaboration

| Application | Status | Guide |
|-------------|--------|-------|
| **Mattermost Team** | Verified | [Mattermost Guide](mattermost.md) |
| **Mattermost Enterprise** | Verified | [Mattermost Guide](mattermost.md) |

> **Mattermost Editions:** Team Edition is free (uses GitLab OAuth, no single logout). Enterprise Edition requires a license for full features (native OIDC with single logout). See the [Mattermost Guide](mattermost.md) for details.

### Analytics & Business Intelligence

| Application | Status | Guide |
|-------------|--------|-------|
| **Metabase** | Verified | [Metabase Guide](metabase.md) |
| **Superset** | Available | [Superset Guide](superset.md) |

### Monitoring & Observability

| Application | Status | Guide |
|-------------|--------|-------|
| **Grafana** | Available | [Grafana Guide](grafana.md) |
| **Prometheus** | Available | [Prometheus Guide](prometheus.md) |

### Artifact Registries

| Application | Status | Guide |
|-------------|--------|-------|
| **Harbor** | Available | [Harbor Guide](harbor.md) |
| **Nexus** | Available | [Nexus Guide](nexus.md) |

### Version Control

| Application | Status | Guide |
|-------------|--------|-------|
| **Gitea** | Available | [Gitea Guide](gitea.md) |

### Databases

| Application | Status | Guide |
|-------------|--------|-------|
| **PostgreSQL** | Available | [PostgreSQL Guide](postgresql.md) |
| **Redis** | Available | [Redis Guide](redis.md) |

### Secrets Management

| Application | Status | Guide |
|-------------|--------|-------|
| **Vault** | Available | [Vault Guide](vault.md) |

### Operations

| Application | Status | Guide |
|-------------|--------|-------|
| **CloudForge Manager** | Available | [CloudForge Manager Guide](cloudforge-manager.md) |

### Code Quality (Plugin Example)

| Application | Status | Guide |
|-------------|--------|-------|
| **SonarQube** | Plugin | [SonarQube Guide](sonarqube.md) |

**Status Legend:**
- **Verified**: Fully tested and production-ready
- **Available**: Built-in, functional, awaiting verification
- **Plugin**: Community plugin example

## Quick Start

### 1. Choose Your Application

Browse the guides above to find detailed documentation for each application.

### 2. Copy a Deployment Context

Each guide includes ready-to-use `deployment-context.json` examples that you can copy directly:

```bash
# Copy an example from the docs/examples directory
cp docs/examples/applications/jenkins-dev.json deployment-context.json

# Customize required fields
vim deployment-context.json

# Deploy
cdk deploy
```

### 3. Customize for Your Environment

At minimum, update these fields:
- `stackName`: Unique name for your CloudFormation stack
- `domain` / `subdomain`: Your DNS configuration (production)
- `cognitoDomainPrefix`: Globally unique Cognito domain (if using OIDC)
- `region`: Target AWS region

## Guide Structure

Each application guide includes:

1. **Overview** - What the application does and key features
2. **Quick Reference** - Ports, images, resource requirements at a glance
3. **Configuration Options** - All available settings
4. **Optional Ports** - Additional services you can enable
5. **Authentication** - OIDC/SAML integration details
6. **Deployment Context Examples** - Ready-to-use JSON configurations
7. **Environment Variables** - Application-specific variables
8. **Health Checks** - Monitoring configuration
9. **Compliance Considerations** - Security and compliance notes

## Deployment Context Examples

The `docs/examples/applications/` directory contains application-specific examples:

```
docs/examples/applications/
├── jenkins-dev.json           # Jenkins development
├── jenkins-production.json    # Jenkins production with SOC2
├── mattermost-dev.json        # Mattermost development
├── mattermost-production.json # Mattermost production with database
├── metabase-dev.json          # Metabase development
├── metabase-production.json   # Metabase production
├── gitlab-production.json     # GitLab with registry
├── grafana-production.json    # Grafana with database
├── cloudforge-manager-dev.json
├── cloudforge-manager-production.json  # optional Postgres
└── ... more examples
```

## Authentication Modes

CloudForge supports three authentication modes:

| Mode | Description | Applications |
|------|-------------|--------------|
| `none` | No authentication | All (not recommended for production) |
| `alb-oidc` | ALB-level authentication | All applications |
| `application-oidc` | Native app authentication | Jenkins, GitLab, Grafana, Mattermost |

**Recommendation:**
- **Development**: `none` or `alb-oidc` for quick setup
- **Production**: `application-oidc` where available for best user experience

## Runtime Options

| Runtime | Best For | Pros | Cons |
|---------|----------|------|------|
| **Fargate** | Dev/Staging, Auto-scaling | No EC2 management, Pay-per-use | Higher cost at scale |
| **EC2** | Production, Cost-sensitive | Lower cost, More control | Requires management |

## Related Documentation

- [Deployment Context Reference](../../examples/README.md) - Complete configuration options
- [Plugin System](../../plugins/PLUGIN-SYSTEM.md) - Create custom applications
- [Compliance Guide](../../compliance/README.md) - Security frameworks
- [OIDC Integration](../../applications/OIDC.md) - Authentication details

## Support

- **Issues**: [GitHub Issues](https://github.com/CloudForgeCI/cfc-core/issues)
- **Examples**: [cloudforge-sample](https://github.com/CloudForgeCI/cloudforge-sample)

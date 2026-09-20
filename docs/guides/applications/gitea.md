# Gitea Application Guide

Gitea is a self-hosted Git service written in Go.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `gitea` |
| **Category** | Version Control |
| **Default Image** | `gitea/gitea:latest` |
| **Application Port** | `3000` |
| **SSH Port** | `2222` (optional, see below) |
| **Default CPU** | 512 (Fargate) |
| **Default Memory** | 1024 MB (Fargate) |
| **Default Instance** | t3.micro (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` |
| **Database Required** | No (embedded SQLite) |

---

## Upstream Features

- Git repository hosting, pull requests, issues, and wikis
- Organizations, teams, and webhooks
- Git LFS and repository mirroring
- Package registry and Gitea Actions

---

## Optional Ports

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 2222 | TCP | Inbound | `enableSsh` | Git SSH |

On EC2, the container's SSH port 22 is published on host port 2222 (`GITEA__server__SSH_PORT=2222`) to avoid a conflict with the host's SSH daemon.

**Example enabling SSH:**
```json
{
  "enableSsh": true
}
```

---

## Authentication

| Mode | Description |
|------|-------------|
| `none` | Gitea local accounts |

Gitea declares only the `none` auth mode. When a context is prepared for a deployment target (the interactive deployer or `CloudForgeDeployment`), an unsupported `authMode` such as `alb-oidc` is replaced with `none` and a warning is printed. Compliance frameworks that require CloudForge-managed authentication, such as the SOC 2 CC6.2 rule, report a failure when `authMode` is `none`.

Gitea supports OpenID Connect natively, but CloudForge does not configure it.

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/data` |
| EFS Path | `/gitea` |
| Volume Name | `giteaData` |
| Container User | `1000:1000` |
| EFS Permissions | `755` |

On EC2, data is stored under `/var/lib/gitea`.

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Gitea-Dev",
  "applicationId": "gitea",
  "applicationName": "Gitea Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 512,
  "memory": 1024,

  "enableSsh": true,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Production

```json
{
  "stackName": "Gitea-Production",
  "applicationId": "gitea",
  "applicationName": "Gitea",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "git",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.small",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 2,

  "enableSsh": true,

  "awsConfigEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

---

## Post-Deployment Tasks

1. Open the Gitea URL.
2. Complete the initial setup wizard and create the administrator account.
3. Configure SSH clone URLs if `enableSsh` is set.
4. Create organizations and repositories.

---

## Related Documentation

- [Gitea Documentation](https://docs.gitea.com/)

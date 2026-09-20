# Drone Application Guide

Drone is a container-native continuous integration platform that defines pipelines in a YAML file stored with the repository.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `drone` |
| **Category** | CI/CD |
| **Default Image** | `drone/drone:2` |
| **Application Port** | `80` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` (Drone authenticates users through its source-control provider) |
| **Database Required** | No (embedded SQLite) |

---

## Upstream Features

- Pipelines defined in `.drone.yml`
- Container-based build steps
- GitHub, GitLab, Gitea, and Bitbucket integration
- Secrets, cron schedules, parallel steps, and matrix builds

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/data` |
| EFS Path | `/drone` |
| Volume Name | `droneData` |
| Container User | `1000:1000` |
| EFS Permissions | `755` |

On EC2, data is stored under `/var/lib/drone`.

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Drone-Dev",
  "applicationId": "drone",
  "applicationName": "Drone CI",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Production

```json
{
  "stackName": "Drone-Production",
  "applicationId": "drone",
  "applicationName": "Drone CI",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "ci",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.small",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 2,

  "awsConfigEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

---

## Authentication

Drone declares only the `none` auth mode. When a context is prepared for a deployment target (the interactive deployer or `CloudForgeDeployment`), an unsupported `authMode` such as `alb-oidc` is replaced with `none` and a warning is printed.

Drone signs users in through its source-control provider (GitHub, GitLab, Gitea, or Bitbucket OAuth). Compliance frameworks that require CloudForge-managed authentication, such as the SOC 2 CC6.2 rule, report a failure when `authMode` is `none`.

---

## Post-Deployment Tasks

CloudForge does not set Drone's source-control provider variables (for example `DRONE_GITHUB_CLIENT_ID` and `DRONE_GITHUB_CLIENT_SECRET`). On EC2 the server is started with `DRONE_SERVER_PROTO=http` and the instance's public hostname.

1. Create an OAuth application in your source-control provider.
2. Supply the provider's client ID and secret to the Drone server.
3. Activate repositories in the Drone UI.
4. Add a `.drone.yml` file to each repository.

---

## Related Documentation

- [Drone Documentation](https://docs.drone.io/)

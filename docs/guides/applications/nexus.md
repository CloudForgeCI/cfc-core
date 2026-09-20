# Nexus Repository Application Guide

Sonatype Nexus Repository is an artifact repository manager for Maven, npm, Docker, PyPI, and other formats.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `nexus` |
| **Category** | Artifact Registry |
| **Default Image** | `sonatype/nexus3:latest` |
| **Application Port** | `8081` |
| **Default CPU** | 2048 (Fargate) |
| **Default Memory** | 4096 MB (Fargate) |
| **Default Instance** | t3.medium (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` |
| **Database Required** | No (embedded database) |

---

## Upstream Features

- Maven, npm, NuGet, PyPI, RubyGems, and Docker formats
- Proxy, hosted, and group repositories
- File and S3 blob stores
- REST API

---

## Optional Ports

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 5000 | TCP | Inbound | `enableDockerRegistry` | Docker Registry (group) |
| 5001 | TCP | Inbound | `enableDockerRegistry` | Docker Registry (hosted) |
| 5002 | TCP | Inbound | `enableDockerRegistry` | Docker Registry (proxy) |

**Example enabling Docker registry:**
```json
{
  "enableDockerRegistry": true
}
```

---

## Authentication

| Mode | Description |
|------|-------------|
| `none` | Nexus local accounts |

Nexus declares only the `none` auth mode. When a context is prepared for a deployment target (the interactive deployer or `CloudForgeDeployment`), an unsupported `authMode` such as `alb-oidc` is replaced with `none` and a warning is printed. Compliance frameworks that require CloudForge-managed authentication, such as the SOC 2 CC6.2 rule, report a failure when `authMode` is `none`.

Native SAML in Nexus requires a Nexus Pro license; CloudForge does not configure it.

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `INSTALL4J_ADD_VM_PARAMS` | JVM heap settings. Set only by the EC2 user data (`-Xms2703m -Xmx2703m -XX:MaxDirectMemorySize=2703m`). |

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/nexus-data` |
| EFS Path | `/nexus` |
| Volume Name | `nexusData` |
| Container User | `200:200` |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/opt/nexus-data` |
| Log Paths | `/opt/nexus-data/log/nexus.log`, `/opt/nexus-data/log/audit/audit.log` |

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Nexus-Dev",
  "applicationId": "nexus",
  "applicationName": "Nexus Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 2048,
  "memory": 4096,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Production with Docker Registry Ports

```json
{
  "stackName": "Nexus-Production",
  "applicationId": "nexus",
  "applicationName": "Nexus Repository",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "nexus",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.large",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 2,

  "enableDockerRegistry": true,

  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

---

## Compliance Use Cases

A private artifact repository can support controls such as tracking which build artifacts were deployed and restricting where dependencies are fetched from. Deploying Nexus does not by itself satisfy any framework's requirements.

---

## Post-Deployment Tasks

1. **Get Admin Password**:
   ```bash
   # Fargate
   aws ecs execute-command --cluster CLUSTER --task TASK --container nexus \
     --command "cat /nexus-data/admin.password"

   # EC2
   ssh ec2-user@instance 'cat /opt/nexus-data/admin.password'
   ```
2. **Change the admin password** when prompted at first sign-in.
3. **Create repositories** for the formats you use. Docker repositories must be bound to ports 5000-5002 to match the optional security-group rules.
4. **Configure blob stores**, for example an S3 blob store.
5. **Set up cleanup policies** to manage storage growth.

---

## Related Documentation

- [Nexus Documentation](https://help.sonatype.com/repomanager3)
- [Compliance Guide](../../compliance/README.md)

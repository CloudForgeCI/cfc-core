# Harbor Application Guide

Harbor is an open-source container registry with role-based access control, vulnerability scanning, and image signing.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `harbor` |
| **Category** | Artifact Registry |
| **Default Image** | `goharbor/harbor-core:v2.9.0` |
| **Application Port** | `80` |
| **Default CPU** | 2048 (Fargate) |
| **Default Memory** | 4096 MB (Fargate) |
| **Default Instance** | t3.medium (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` |
| **Database Required** | Yes (PostgreSQL) |

> **Limitation:** CloudForge runs only the `goharbor/harbor-core` image. A complete Harbor installation also needs the portal, registry, job service, and Redis components, which CloudForge does not deploy.

---

## Upstream Features

- OCI image and artifact registry with projects and role-based access control
- Vulnerability scanning (Trivy) and image signing
- Replication, garbage collection, and audit logging

---

## Optional Ports

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 4443 | TCP | Inbound | `enableNotary` | Content Trust (Notary) |
| 8080 | TCP | Inbound | `enableTrivy` | Trivy Scanner |

These flags only open security-group ports; CloudForge does not deploy Notary or Trivy containers.

**Example enabling security features:**
```json
{
  "enableNotary": true,
  "enableTrivy": true
}
```

---

## Database Requirements

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 13 or later |
| Instance Class | `db.t3.medium` (default) |
| Storage | 50 GB (default) |
| Database Name | `registry` |
| Backup Retention | 30 days |

**Database parameters:** `max_connections=250`, `shared_buffers={DBInstanceClassMemory/4096}`, `work_mem=16MB`, `maintenance_work_mem=256MB`, `log_statement=ddl`.

---

## Authentication

| Mode | Description |
|------|-------------|
| `none` | Harbor local accounts |

Harbor declares only the `none` auth mode. When a context is prepared for a deployment target (the interactive deployer or `CloudForgeDeployment`), an unsupported `authMode` such as `alb-oidc` is replaced with `none` and a warning is printed. Compliance frameworks that require CloudForge-managed authentication, such as the SOC 2 CC6.2 rule, report a failure when `authMode` is `none`.

Harbor supports OpenID Connect natively, but CloudForge does not configure it.

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `HARBOR_HOSTNAME` | Deployment FQDN (set when a domain is configured) |
| `HARBOR_EXTERNAL_URL` | `https://<fqdn>` or `http://<fqdn>` depending on `enableSsl` |
| `POSTGRESQL_HOST`, `POSTGRESQL_PORT`, `POSTGRESQL_DATABASE`, `POSTGRESQL_USERNAME` | RDS connection |
| `POSTGRESQL_SSLMODE` | `require` |
| `POSTGRESQL_PASSWORD` | Injected from the database secret in Secrets Manager |

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/data` |
| EFS Path | `/harbor` |
| Volume Name | `harborData` |
| Container User | `10000:10000` |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/data/harbor` |

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Harbor-Dev",
  "applicationId": "harbor",
  "applicationName": "Harbor Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 2048,
  "memory": 4096,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.small",
  "databaseAllocatedStorageGB": 50,
  "databaseName": "registry",

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Production

```json
{
  "stackName": "Harbor-Production",
  "applicationId": "harbor",
  "applicationName": "Harbor Registry",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "registry",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.large",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseName": "registry",
  "databaseBackupRetentionDays": 30,

  "enableNotary": true,
  "enableTrivy": true,

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

A private registry with scanning and audit logs can support controls around image provenance and vulnerability management. Deploying Harbor does not by itself satisfy any framework's requirements.

---

## Post-Deployment Tasks

1. **Sign in** as `admin`. Harbor's upstream default password is `Harbor12345` unless overridden.
2. **Change the admin password** immediately.
3. **Create projects** to organize images.
4. **Configure scanning policies** if a scanner is available.
5. **Set up replication** to or from other registries if needed.

---

## Related Documentation

- [Harbor Documentation](https://goharbor.io/docs/)
- [Compliance Guide](../../compliance/README.md)

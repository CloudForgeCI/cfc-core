# Superset Application Guide

Apache Superset is a data exploration and visualization platform for building charts and dashboards.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `superset` |
| **Category** | Analytics |
| **Default Image** | `apache/superset:latest` |
| **Application Port** | `8088` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` |
| **Database Required** | Yes (PostgreSQL) |

---

## Upstream Features

- SQL Lab for ad hoc queries
- Chart builder and dashboards
- Role-based access control
- Connectors for many SQL databases
- Alerts and scheduled reports (require additional worker and cache configuration)

---

## Database Requirements

Superset requires a PostgreSQL database for metadata storage. CloudForge provisions Amazon RDS for PostgreSQL when `provisionDatabase` is set.

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 13 or later |
| Instance Class | `db.t3.small` (default) |
| Storage | 20 GB (default) |
| Database Name | `superset` |
| Backup Retention | 14 days |

---

## Authentication

| Mode | Description |
|------|-------------|
| `none` | Superset local accounts |

Superset declares only the `none` auth mode. When a context is prepared for a deployment target (the interactive deployer or `CloudForgeDeployment`), an unsupported `authMode` such as `alb-oidc` is replaced with `none` and a warning is printed. Compliance frameworks that require CloudForge-managed authentication, such as the SOC 2 CC6.2 rule, report a failure when `authMode` is `none`.

Native OIDC in Superset requires a custom `superset_config.py`, which CloudForge does not generate.

---

## Environment Variables

| Variable | Value |
|----------|-------|
| `SUPERSET_SECRET_KEY` | Fixed placeholder value. Replace it with a long random string before storing real data. |
| `ENABLE_PROXY_FIX` | `True` |
| `PROXY_FIX_X_FOR`, `PROXY_FIX_X_PROTO`, `PROXY_FIX_X_HOST`, `PROXY_FIX_X_PORT`, `PROXY_FIX_X_PREFIX` | `1` |
| `DATABASE_DIALECT` | `postgresql` |
| `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_DB`, `DATABASE_USER` | RDS connection |
| `SUPERSET_DATABASE_PASSWORD` | Injected from the database secret in Secrets Manager |
| `SQLALCHEMY_DATABASE_URI` | `postgresql://<user>:${SUPERSET_DATABASE_PASSWORD}@<host>:<port>/<db>` |

ECS does not expand `${...}` references inside environment variable values, so `SQLALCHEMY_DATABASE_URI` contains the literal text `${SUPERSET_DATABASE_PASSWORD}` unless the container's entrypoint expands it.

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/app/superset_home` |
| EFS Path | `/superset` |
| Volume Name | `supersetData` |
| Container User | `0:0` (root) |
| EFS Permissions | `755` |

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Superset-Dev",
  "applicationId": "superset",
  "applicationName": "Superset Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.micro",
  "databaseAllocatedStorageGB": 20,
  "databaseName": "superset",

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Production

```json
{
  "stackName": "Superset-Production",
  "applicationId": "superset",
  "applicationName": "Superset Analytics",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "data",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.medium",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseMultiAz": true,
  "databaseName": "superset",
  "databaseBackupRetentionDays": 30,

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

## Post-Deployment Tasks

CloudForge does not run Superset's initialization commands. Run them in the container after the first deployment:

1. **Initialize Database:**
   ```bash
   superset db upgrade
   ```
2. **Create Admin User:**
   ```bash
   superset fab create-admin
   ```
3. **Load Examples (optional):**
   ```bash
   superset load_examples
   ```
4. **Initialize Superset:**
   ```bash
   superset init
   ```
5. **Connect data sources** in the UI.

---

## Compliance Use Cases

Superset can present dashboards used as evidence for monitoring controls (for example security-event metrics or transaction monitoring). Deploying Superset does not by itself satisfy any framework's requirements.

---

## Related Documentation

- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [Superset Documentation](https://superset.apache.org/docs/intro)

# PostgreSQL Application Guide

This guide covers running PostgreSQL as a containerized application. To give another application a managed database, use its `provisionDatabase` settings instead (see the [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)).

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `postgresql` |
| **Category** | Database |
| **Default Image** | `postgres:15` |
| **Application Port** | `5432` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` |
| **Database Required** | N/A (is a database) |

---

## When to Use

A containerized PostgreSQL instance is suited to development, testing, and prototyping.

For production data, consider Amazon RDS for PostgreSQL, which provides automated backups, Multi-AZ deployment, read replicas, and managed patching.

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/var/lib/postgresql/data` |
| EFS Path | `/postgresql` |
| Volume Name | `postgresData` |
| Container User | `999:999` |
| EFS Permissions | `700` |

On EC2, data is stored under `/var/lib/postgresql/data`.

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "PostgreSQL-Dev",
  "applicationId": "postgresql",
  "applicationName": "PostgreSQL Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

---

## Environment Variables

The EC2 user data starts the container with these variables. The Fargate task definition does not set them; the `postgres` image requires `POSTGRES_PASSWORD` (or an explicit trust setting) to initialize.

| Variable | Value (EC2) |
|----------|-------------|
| `POSTGRES_PASSWORD` | Read from the Secrets Manager secret `<stack name>/password` if it exists; otherwise generated with `openssl rand` |
| `POSTGRES_DB` | `cloudforge` |
| `POSTGRES_USER` | `cloudforge` |

When the password is generated, it is also written to `/var/log/userdata.log` on the instance. Rotate it after first sign-in.

---

## Compliance Considerations

Databases often hold regulated data (PII, PHI, payment data). Compliance frameworks typically expect encryption at rest and in transit, access audit logging, and backup retention that matches the framework's requirements.

For regulated workloads, Amazon RDS offers Multi-AZ deployment, encryption, Enhanced Monitoring, Performance Insights, and automated backups.

---

## Related Documentation

- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

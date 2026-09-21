# Redis Application Guide

Redis is an in-memory data store used as a cache, database, and message broker.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `redis` |
| **Category** | Database |
| **Default Image** | `redis:7-alpine` |
| **Application Port** | `6379` |
| **Default CPU** | 512 (Fargate) |
| **Default Memory** | 1024 MB (Fargate) |
| **Default Instance** | t3.micro (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` |
| **Database Required** | N/A |

---

## When to Use

A containerized Redis instance is suited to development, testing, session storage, and caching.

For production, consider Amazon ElastiCache, which provides automatic failover, Multi-AZ deployment, read replicas, and managed patching.

---

## Optional Ports

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 16379 | TCP | Inbound | `enableCluster` | Cluster Bus |
| 26379 | TCP | Inbound | `enableSentinel` | Sentinel |

These flags only open security-group ports. CloudForge does not configure Redis Cluster or Sentinel.

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/data` |
| EFS Path | `/redis` |
| Volume Name | `redisData` |
| Container User | `999:999` |
| EFS Permissions | `755` |

On EC2, data is stored under `/var/lib/redis`, and the server runs with `--appendonly yes --requirepass <password>`. The password is read from the Secrets Manager secret `<stack name>/redis-password`; create that secret before deploying, because the user data otherwise falls back to a fixed placeholder value. The Fargate task definition does not set a password.

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Redis-Dev",
  "applicationId": "redis",
  "applicationName": "Redis Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 512,
  "memory": 1024,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

---

## Related Documentation

- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [Redis Documentation](https://redis.io/docs/)

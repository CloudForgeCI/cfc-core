# Prometheus Application Guide

Prometheus is an open-source systems monitoring and alerting toolkit.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `prometheus` |
| **Category** | Monitoring |
| **Default Image** | `prom/prometheus:latest` |
| **Application Port** | `9090` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` |
| **Database Required** | No (embedded TSDB) |

---

## Upstream Features

- Multi-dimensional time-series data model and PromQL
- Pull-based metrics collection and service discovery
- Recording and alerting rules (alert delivery uses a separate Alertmanager)
- Remote storage integration

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/prometheus` |
| EFS Path | `/prometheus` |
| Volume Name | `prometheusData` |
| Container User | `65534:65534` (nobody) |
| EFS Permissions | `755` |

On EC2, data is stored under `/var/lib/prometheus`.

---

## Authentication

Prometheus declares only the `none` auth mode. When a context is prepared for a deployment target (the interactive deployer or `CloudForgeDeployment`), an unsupported `authMode` such as `alb-oidc` is replaced with `none` and a warning is printed. Compliance frameworks that require CloudForge-managed authentication, such as the SOC 2 CC6.2 rule, report a failure when `authMode` is `none`.

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Prometheus-Dev",
  "applicationId": "prometheus",
  "applicationName": "Prometheus Dev",
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

### Production

```json
{
  "stackName": "Prometheus-Production",
  "applicationId": "prometheus",
  "applicationName": "Prometheus",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "prometheus",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.medium",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 2,

  "awsConfigEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365",
  "retainStorage": true
}
```

---

## Configuration

On EC2, CloudForge writes the following `prometheus.yml` to `/var/lib/prometheus/config/prometheus.yml` and mounts it into the container. On Fargate, the image's built-in configuration is used.

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
```

---

## Post-Deployment Tasks

1. Add scrape targets to `prometheus.yml`.
2. Deploy exporters (for example Node Exporter or CloudWatch Exporter) as needed.
3. Configure an Alertmanager if you need alert delivery.
4. Add Prometheus as a Grafana data source.

---

## Related Documentation

- [Grafana Guide](grafana.md)
- [Prometheus Documentation](https://prometheus.io/docs/)

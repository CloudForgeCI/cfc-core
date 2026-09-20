# Vault Application Guide

HashiCorp Vault stores and controls access to secrets, and provides encryption as a service.

**Status**: Available (not yet verified end to end)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `vault` |
| **Category** | Secrets Management |
| **Default Image** | `hashicorp/vault:latest` |
| **Application Port** | `8200` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `none` |
| **Database Required** | No |

---

## Upstream Features

- Static and dynamic secrets
- Encryption as a service and PKI
- Database and AWS IAM credential engines
- Audit devices and access policies

---

## Authentication

Vault declares only the `none` auth mode. When a context is prepared for a deployment target (the interactive deployer or `CloudForgeDeployment`), an unsupported `authMode` such as `alb-oidc` is replaced with `none` and a warning is printed. Compliance frameworks that require CloudForge-managed authentication, such as the SOC 2 CC6.2 rule, report a failure when `authMode` is `none`. Vault's own authentication methods (tokens, OIDC, AWS IAM, and others) are configured inside Vault after initialization.

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/vault/file` |
| EFS Path | `/vault` |
| Volume Name | `vaultData` |
| Container User | `100:1000` |
| EFS Permissions | `750` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/opt/vault/data` |
| Log Paths | `/var/log/vault/vault.log`, `/var/log/vault/audit.log` |

### Runtime Configuration

- **EC2:** the user data writes `/etc/vault.d/vault.hcl` (mounted at `/vault/config`) with the `file` storage backend and a TLS listener that expects `/vault/config/tls/cert.pem` and `key.pem`, and it also passes `VAULT_LOCAL_CONFIG` with a TLS-disabled listener on the same address. CloudForge does not provision the TLS certificate files, and `api_addr` in `vault.hcl` is set to a placeholder host. Review and edit the configuration before relying on this deployment.
- **Fargate:** CloudForge sets no Vault command or configuration, so the image's default command applies. For the official image that is a development-mode server with in-memory storage, which does not persist data to the EFS volume.

---

## Deployment Context Examples

### Development

```json
{
  "stackName": "Vault-Dev",
  "applicationId": "vault",
  "applicationName": "Vault Dev",
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
  "stackName": "Vault-Production",
  "applicationId": "vault",
  "applicationName": "Vault",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "vault",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "none",

  "instanceType": "t3.small",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 1,

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

## Important Notes

### Initialization and Unsealing

CloudForge does not initialize or unseal Vault, and it does not configure KMS auto-unseal. After the first deployment:

1. **Initialize Vault:**
   ```bash
   vault operator init
   ```
   Store the unseal keys and root token in separate, secure locations.

2. **Unseal Vault:**
   ```bash
   vault operator unseal <key1>
   vault operator unseal <key2>
   vault operator unseal <key3>
   ```

   Unsealing is required after every restart.

3. **Enable an audit device:**
   ```bash
   vault audit enable file file_path=/vault/logs/audit.log
   ```

### Production Recommendations

- Configure [AWS KMS auto-unseal](https://developer.hashicorp.com/vault/docs/configuration/seal/awskms) to avoid manual unsealing.
- Prefer EC2 for any persistent deployment (see Runtime Configuration).
- Keep a single instance while the `file` storage backend is in use. The `file` backend does not support high availability; running several Vault instances requires Integrated Storage (Raft) or another HA-capable backend, which CloudForge does not configure.
- Enable an audit device.

---

## Compliance Use Cases

Vault can support compliance programs by centralizing secrets and producing audit logs, for example storing payment-gateway API keys (PCI DSS), managing encryption keys for PHI (HIPAA), or rotating data-encryption keys (GDPR). Deploying Vault does not by itself satisfy any framework's requirements.

---

## Related Documentation

- [Compliance Guide](../../compliance/README.md)
- [Vault Documentation](https://developer.hashicorp.com/vault/docs)

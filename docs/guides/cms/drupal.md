# Drupal Application Guide

Drupal is a CMS used in government, higher education, and large organizations. It supports structured content models, multilingual sites, and configurable access controls.

**Status**: Available

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `drupal` |
| **Category** | CMS |
| **Default Image** | `drupal:10-php8.2-fpm-alpine` |
| **PHP Version** | 8.2 |
| **Application Port** | `80` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Health Check Path** | `/user/login` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Authentication** | ALB-OIDC (Cognito) |
| **Database Required** | Yes (PostgreSQL 16 or MySQL 8.0) |

---

## Capabilities

- Structured content with custom entity types
- Multilingual content and interface
- Layout Builder for visual page composition
- Views for dynamic content listings
- Paragraphs module for component-based editing
- Media Library with S3 integration
- JSON:API and GraphQL for headless architectures
- Drush CLI for administration and deployments
- Native OpenID Connect module (no third-party plugin)
- Configuration management (config sync)

---

## Auto-Provisioned Infrastructure

| Resource | Provisioned | Purpose |
|----------|-------------|---------|
| S3 bucket | Yes | Media files via S3FS or S3 File System module |
| ElastiCache Redis | Yes | Page cache, dynamic page cache, object cache |
| CloudFront CDN | Yes | Asset and media delivery |
| EFS | Yes | `/var/www/html/sites/default/files` (public files) |
| Route53 records | When domain configured | A + AAAA records to ALB |

---

## Authentication

Drupal is protected at the ALB level by Cognito. For admin-only sites, all traffic is gated. For public-facing sites with authenticated editors, configure `publicPaths`.

| Mode | Status | Description |
|------|--------|-------------|
| `alb-oidc` | **Recommended** | Cognito at ALB — zero config inside Drupal |
| `none` | Dev only | No authentication |

The Drupal login page (`/user/login`) remains accessible for local admin access if needed.

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `DRUPAL_OIDC_CLIENT_ID` | Cognito client ID |
| `DRUPAL_OIDC_AUTHORIZATION_ENDPOINT` | Cognito authorization endpoint |
| `DRUPAL_OIDC_TOKEN_ENDPOINT` | Cognito token endpoint |
| `DRUPAL_OIDC_USERINFO_ENDPOINT` | Cognito userinfo endpoint |
| `DRUPAL_OIDC_SCOPES` | `openid email profile` |
| `REDIS_HOST` | ElastiCache endpoint |
| `REDIS_PORT` | `6379` |
| `DRUPAL_DB_HOST` | RDS endpoint |
| `DRUPAL_DB_NAME` | Database name |
| `DRUPAL_DB_USER` | Database user |
| `DRUPAL_DB_PASSWORD` | From Secrets Manager |

---

## Storage Configuration

### Container (Fargate)

| Property | Value |
|----------|-------|
| Data Path | `/var/www/html` |
| EFS Path | `/drupal` |
| Volume Name | `drupalData` |
| Container User | `33:33` (www-data) |
| EFS Permissions | `755` |

### EC2

| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/var/www/html` |
| Log Paths | `/var/log/nginx/error.log`, `/var/log/php-fpm/error.log`, `/var/log/drupal/drupal.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development - Minimal

```json
{
  "stackName": "Drupal-Dev",
  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "cms-service",
  "applicationId": "drupal",

  "networkMode": "public-no-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "16",
  "databaseInstanceClass": "db.t3.micro",
  "databaseAllocatedStorageGB": 20,
  "databaseName": "drupal",

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

**Cost estimate:** ~$45/month

### Staging - With Auth and Redis

```json
{
  "stackName": "Drupal-Staging",
  "runtime": "fargate",
  "securityProfile": "staging",
  "topology": "cms-service",
  "applicationId": "drupal",

  "domain": "staging.example.com",
  "subdomain": "cms",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "drupal-staging-yourcompany",

  "cpu": 2048,
  "memory": 4096,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "16",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseName": "drupal",

  "enableMonitoring": true,
  "logRetentionDays": "90"
}
```

**Cost estimate:** ~$180/month

### Production - Government / Enterprise

Drupal is commonly used in US federal and state government contexts. This configuration targets production readiness with SOC2 controls:

```json
{
  "stackName": "Drupal-Production",
  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "cms-service",
  "applicationId": "drupal",

  "domain": "example.gov",
  "subdomain": "www",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "drupal-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "t3.large",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 6,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 60,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "16",
  "databaseInstanceClass": "db.r6g.large",
  "databaseAllocatedStorageGB": 200,
  "databaseMultiAz": true,
  "databaseBackupRetentionDays": 30,

  "complianceFrameworks": "SOC2,HIPAA",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

**Cost estimate:** ~$550-750/month

---

## Post-Deployment Tasks

### 1. Complete Drupal Installation

1. Navigate to `https://your-domain.com`
2. Select **Standard** installation profile
3. Configure database connection (pre-populated from env vars in most cases)
4. Set site name, admin email, and admin password

### 2. Install Recommended Modules

For production Drupal on AWS:

- **S3 File System** (`s3fs`) — Mount the CloudForge S3 bucket as the public files filesystem
- **Redis** (`redis`) — Connect to ElastiCache using `PhpRedis` backend
- **Metatag** — SEO metadata management
- **Pathauto** — Automatic URL aliases
- **Config Split** — Per-environment configuration management

Install via Drush (EC2 access via SSM):

```bash
aws ssm start-session --target <instance-id>
cd /var/www/html
drush composer require drupal/s3fs drupal/redis
drush en s3fs redis -y
drush cr
```

### 3. Configure Config Sync

For team-based development, export configuration to the repository:

```bash
drush cex -y
```

On deployment, import configuration:

```bash
drush cim -y
drush cr
```

### 4. Run Database Updates

After any module update:

```bash
drush updb -y
drush cr
```

---

## Compliance Considerations

### SOC2

- [ ] Enable Drupal's database logging module (`dblog`) or syslog
- [ ] Configure session timeouts (`/admin/config/people/accounts`)
- [ ] Enable password policies (Password Policy module)
- [ ] Restrict admin role assignments
- [ ] Enable revision tracking on all content types

### HIPAA

- [ ] Enable field-level access controls for PHI content types
- [ ] Configure content access logging
- [ ] Restrict file download access with private file system

---

## Troubleshooting

### Permission denied errors on `sites/default/files`

EFS is mounted at the document root. The container runs as `www-data` (UID 33). Verify EFS access point permissions:

```bash
# Check EFS mount
aws efs describe-access-points --file-system-id <efs-id>
```

The access point must have `posixUser: {uid: 33, gid: 33}` and `rootDirectory.creationInfo.permissions: "755"`.

### Drush commands not found

Drush is installed via Composer at `/var/www/html/vendor/bin/drush`. Use the full path or add to `PATH`:

```bash
/var/www/html/vendor/bin/drush status
```

---

## Related Documentation

- [CMS Guides Index](README.md)
- [CMS Topology Reference](../../applications/CMS.md)
- [OIDC Integration](../../applications/OIDC.md)

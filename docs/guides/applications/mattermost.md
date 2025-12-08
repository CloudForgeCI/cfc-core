# Mattermost Application Guide

Mattermost is an open-source, self-hosted team collaboration platform providing secure messaging, file sharing, and integrations for enterprise teams.

**Status**: Verified

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `mattermost` |
| **Category** | Collaboration |
| **Default Image** | `mattermost/mattermost-enterprise-edition:latest` |
| **Application Port** | `8065` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **OIDC Support** | Yes (Verified) |
| **Database Required** | Yes (PostgreSQL) |

---

## Capabilities

- Real-time team messaging
- Direct messages and group channels
- File sharing with preview
- Audio/video calls (with plugins)
- Webhooks and bot integrations
- Mobile apps (iOS, Android)
- Desktop apps (Windows, Mac, Linux)
- LDAP/AD integration
- Custom emojis and branding
- Message search and archiving
- Compliance exports

**Note:** The Enterprise Edition image runs in "Team Edition" mode without a license, providing core features. Enterprise features require a license.

---

## Optional Ports

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 587 | TCP | Outbound | `enableSmtp` | SMTP Email (STARTTLS) |
| 465 | TCP | Outbound | `enableSmtps` | SMTP Email (TLS) |
| 8074 | TCP | Inbound | `enableClustering` | Cluster Gossip |
| 8075 | TCP | Inbound | `enableClustering` | Cluster Gossip |

**Example enabling SMTP:**
```json
{
  "enableSmtp": true
}
```

**Example enabling clustering (High Availability):**
```json
{
  "enableClustering": true
}
```

---

## Database Requirements

Mattermost **requires** a PostgreSQL database.

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 14+ |
| Instance Class | db.t3.small (default) |
| Storage | 30 GB (default) |
| Database Name | `mattermost` |
| Backup Retention | 14 days |

**Database Parameters:**
- `max_connections`: 200
- `shared_buffers`: Optimized for instance class
- `work_mem`: 16MB

When deploying Mattermost, CloudForge automatically provisions RDS PostgreSQL.

---

## Authentication

### Supported Auth Modes

| Mode | Status | Description |
|------|--------|-------------|
| `application-oidc` | **Verified** | Native OIDC via GitLab OAuth provider |
| `alb-oidc` | Verified | ALB-level authentication |
| `none` | Available | No SSO (local accounts only) |

### OIDC Integration Details

Mattermost uses the **GitLab OAuth provider** (MM_GITLABSETTINGS_*) for OIDC, which is compatible with any OAuth 2.0 / OpenID Connect provider including Cognito.

**Features:**
- Auto-create users on first login
- Email-based account creation
- Customizable login button text and color
- OAuth 2.0 / OpenID Connect standard flow

**Callback Path:** `/signup/gitlab/complete`

**Limitations:**
- No automatic group synchronization (manual team membership)
- No AD/LDAP sync in OIDC mode

**Note:** SAML support exists but OIDC is the verified and recommended approach.

---

## Environment Variables

CloudForge automatically configures these environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `MM_SERVICESETTINGS_SITEURL` | External URL (critical for OAuth) | `https://chat.example.com` |
| `MM_SERVICESETTINGS_TRUSTEDPROXYIPHEADER` | Trust ALB headers | `X-Forwarded-For,X-Real-IP` |
| `MM_SERVICESETTINGS_FORWARD80TO443` | Disable (ALB handles) | `false` |
| `MM_SQLSETTINGS_DRIVERNAME` | Database driver | `postgres` |
| `MM_SQLSETTINGS_DATASOURCE` | Database connection | Injected via SSM |

**OIDC-specific variables (when enabled):**
| Variable | Description |
|----------|-------------|
| `MM_GITLABSETTINGS_ENABLE` | Enable GitLab OAuth |
| `MM_GITLABSETTINGS_ID` | OAuth client ID |
| `MM_GITLABSETTINGS_SECRET` | OAuth client secret |
| `MM_GITLABSETTINGS_AUTHENDPOINT` | Authorization endpoint |
| `MM_GITLABSETTINGS_TOKENENDPOINT` | Token endpoint |
| `MM_GITLABSETTINGS_USERAPIENDPOINT` | UserInfo endpoint |

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/mattermost/data` |
| EFS Path | `/mattermost` |
| Volume Name | `mattermostData` |
| Container User | `2000:2000` |
| EFS Permissions | `755` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/opt/mattermost/data` |
| Log Paths | `/opt/mattermost/logs/mattermost.log`, `/var/log/userdata.log` |

---

## Deployment Context Examples

### Development - Minimal Setup

Quick Mattermost for testing (uses embedded database - not for production).

```json
{
  "stackName": "Mattermost-Dev",
  "applicationId": "mattermost",
  "applicationName": "Mattermost Dev",
  "description": "Mattermost development environment",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public-no-nat",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

**Note:** Without RDS, Mattermost uses H2 embedded database (single instance only).

**Cost estimate:** ~$40/month

### Development - With Database

Team development with PostgreSQL database.

```json
{
  "stackName": "Mattermost-Dev-DB",
  "applicationId": "mattermost",
  "applicationName": "Mattermost Dev",
  "description": "Mattermost with PostgreSQL",
  "environment": "development",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "domain": "dev.example.com",
  "subdomain": "chat",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "mattermost-dev-yourcompany",
  "cognitoCreateGroups": true,

  "cpu": 1024,
  "memory": 2048,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.micro",
  "databaseAllocatedStorageGB": 20,
  "databaseName": "mattermost",

  "enableMonitoring": true,
  "logRetentionDays": "30"
}
```

**Cost estimate:** ~$80/month

### Staging - With Email

Pre-production with SMTP for email notifications.

```json
{
  "stackName": "Mattermost-Staging",
  "applicationId": "mattermost",
  "applicationName": "Mattermost Staging",
  "description": "Mattermost staging with email",
  "environment": "staging",

  "runtime": "fargate",
  "securityProfile": "staging",
  "topology": "application-service",

  "domain": "staging.example.com",
  "subdomain": "chat",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "mattermost-staging-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,

  "cpu": 1024,
  "memory": 2048,
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 2,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.small",
  "databaseAllocatedStorageGB": 30,
  "databaseName": "mattermost",
  "databaseBackupRetentionDays": 7,

  "enableSmtp": true,

  "complianceFrameworks": "SOC2",
  "scopeConfigRulesToDeployment": true,
  "awsConfigEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365"
}
```

**Cost estimate:** ~$180/month

### Production - SOC2 Compliance

Full production deployment for enterprise teams.

```json
{
  "stackName": "Mattermost-Production",
  "applicationId": "mattermost",
  "applicationName": "Mattermost",
  "description": "Production Mattermost with SOC2 compliance",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "chat",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "mattermost-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "MattermostAdmins",
  "cognitoUserGroupName": "MattermostUsers",

  "instanceType": "t3.medium",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 60,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 50,
  "databaseMultiAz": true,
  "databaseName": "mattermost",
  "databaseBackupRetentionDays": 30,

  "enableSmtp": true,

  "complianceFrameworks": "SOC2",
  "scopeConfigRulesToDeployment": false,
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
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

**Cost estimate:** ~$450/month

### Production - HIPAA (Healthcare)

For healthcare teams communicating about PHI.

```json
{
  "stackName": "Mattermost-HIPAA",
  "applicationId": "mattermost",
  "applicationName": "Mattermost Secure",
  "description": "HIPAA-compliant team messaging",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "secure.healthcare.com",
  "subdomain": "chat",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "mattermost-hipaa-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,

  "instanceType": "t3.medium",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 6,
  "enableAutoScaling": true,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.medium",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseName": "mattermost",
  "databaseBackupRetentionDays": 90,

  "enableSmtp": true,

  "complianceFrameworks": "HIPAA,SOC2",
  "scopeConfigRulesToDeployment": false,
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "guardDutyEnabled": true,
  "auditManagerEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "2190",
  "retainStorage": true
}
```

**Cost estimate:** ~$550/month

### High Availability - Clustering

For large organizations requiring high availability.

```json
{
  "stackName": "Mattermost-HA",
  "applicationId": "mattermost",
  "applicationName": "Mattermost HA",
  "description": "High availability Mattermost cluster",
  "environment": "production",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "enterprise.example.com",
  "subdomain": "chat",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "mattermost-ha-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",

  "instanceType": "t3.large",
  "minInstanceCapacity": 3,
  "maxInstanceCapacity": 10,
  "enableAutoScaling": true,
  "cpuTargetUtilization": 50,

  "provisionDatabase": true,
  "databaseEngine": "aurora-postgresql",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.r5.large",
  "databaseAllocatedStorageGB": 100,
  "databaseMultiAz": true,
  "databaseName": "mattermost",
  "databaseBackupRetentionDays": 30,

  "enableSmtp": true,
  "enableClustering": true,

  "complianceFrameworks": "SOC2",
  "scopeConfigRulesToDeployment": false,
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,
  "albAccessLogging": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "730",
  "retainStorage": true
}
```

**Cost estimate:** ~$800+/month

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/` | Health check endpoint |
| Grace Period | 300 seconds | Time before health checks start |
| Interval | 30 seconds | Time between checks |
| Timeout | 5 seconds | Response timeout |
| Healthy Threshold | 2 | Consecutive successes |
| Unhealthy Threshold | 3 | Consecutive failures |

---

## Compliance Considerations

### SOC2

**Automatic Controls:**
- Encryption at rest (EBS/EFS/RDS)
- Encryption in transit (TLS)
- Network isolation (Security Groups)
- CloudWatch logging
- Database backup retention

**Use Cases:**
- Audit logs for team communications
- Access control via OIDC

**User Responsibilities:**
- [ ] Configure message retention policies
- [ ] Enable compliance exports (Enterprise)
- [ ] Set up audit logging
- [ ] Configure data retention

### HIPAA

**Automatic Controls:**
- All SOC2 controls
- 6-year log retention
- Enhanced encryption

**User Responsibilities:**
- [ ] Enable compliance exports
- [ ] Configure DLP policies (Enterprise)
- [ ] Disable public channels for PHI
- [ ] Train users on PHI handling

### GDPR

**User Responsibilities:**
- [ ] Configure data retention policies
- [ ] Enable user data export
- [ ] Implement right to erasure procedures
- [ ] Privacy policy in instance

---

## Post-Deployment Tasks

### 1. Initial Login

After deployment with `authMode: "application-oidc"`:

1. Navigate to `https://chat.your-domain.com`
2. Click "Sign in with GitLab" (Cognito via GitLab OAuth)
3. Authenticate with Cognito
4. First user becomes system admin

### 2. Create Teams and Channels

1. Go to **System Console** > **Teams**
2. Create initial teams
3. Create public and private channels
4. Invite users

### 3. Configure Email (if enabled)

When `enableSmtp: true`, configure in System Console:

1. **System Console** > **Email** > **SMTP**
2. Enter SMTP server details (e.g., Amazon SES)
3. Test email delivery

### 4. Configure Integrations

1. Enable incoming/outgoing webhooks
2. Install plugins (Jira, GitHub, etc.)
3. Configure slash commands

---

## Troubleshooting

### Mattermost won't start

**Check logs:**
```bash
# Fargate
aws logs tail /aws/ecs/mattermost --follow

# EC2
ssh ec2-user@instance 'tail -f /opt/mattermost/logs/mattermost.log'
```

### Database connection fails

1. Verify security group allows port 5432
2. Check RDS endpoint in SSM parameters
3. Verify database credentials in Secrets Manager

### OIDC login fails

1. Verify Cognito domain prefix is globally unique
2. Check callback URL is registered
3. Ensure `MM_SERVICESETTINGS_SITEURL` matches actual URL

### WebSocket errors

1. Check ALB is configured for sticky sessions
2. Verify security groups allow WebSocket upgrade
3. Check WAF rules aren't blocking WebSocket

---

## Related Documentation

- [OIDC Integration](../../applications/OIDC.md)
- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [Compliance Guide](../compliance/README.md)
- [Deployment Context Reference](../../../deployment-contexts/README.md)

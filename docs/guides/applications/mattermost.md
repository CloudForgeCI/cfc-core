# Mattermost Application Guide

Mattermost is an open-source, self-hosted team messaging platform with file sharing and integrations.

**Status**: Verified (deployed and exercised end to end by maintainers)

---

## Editions Overview

CloudForge provides two Mattermost application IDs:

| Edition | Application ID | License | OIDC Method | Single Logout |
|---------|---------------|---------|-------------|---------------|
| **Team (Free)** | `mattermost-team` | None required | GitLab OAuth provider | No |
| **Enterprise** | `mattermost-enterprise` | Required for enterprise features | Native OpenID Connect | Yes |

### Which Edition Should I Use?

**Use `mattermost-team` if** you do not have a Mattermost license and do not need single logout, AD/LDAP group sync, or compliance exports.

**Use `mattermost-enterprise` if** you need single logout (signing out of Mattermost also ends the Cognito session), SAML, AD/LDAP group synchronization, compliance exports, or clustering, and you have or plan to buy a Mattermost license.

Both IDs use the `mattermost/mattermost-enterprise-edition` image. Without a license it runs with Team Edition features; `mattermost-team` signs users in through Mattermost's GitLab OAuth provider pointed at the OIDC provider. Enterprise features are enabled by uploading a license.

---

## Quick Reference

### Mattermost Team (Free)

| Property | Value |
|----------|-------|
| **Application ID** | `mattermost-team` |
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
| **Supported Auth Modes** | `application-oidc` (default), `alb-oidc`, `none` |
| **Database Required** | Yes (PostgreSQL) |

### Mattermost Enterprise

| Property | Value |
|----------|-------|
| **Application ID** | `mattermost-enterprise` |
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
| **Supported Auth Modes** | `application-oidc` (default), `alb-oidc`, `none` |
| **Database Required** | Yes (PostgreSQL) |

---

## Upstream Features

- Channels, direct messages, and file sharing
- Webhooks, slash commands, bots, and plugins
- Desktop and mobile clients
- LDAP/AD integration and compliance exports (licensed features)

---

## Optional Ports

### Mattermost Team (Free)

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 587 | TCP | Outbound | `enableSmtp` | SMTP Email (STARTTLS) |
| 465 | TCP | Outbound | `enableSmtps` | SMTP Email (TLS) |

Clustering is not available without a license.

### Mattermost Enterprise

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

The SMTP entries describe outbound traffic. CloudForge adds security-group rules only for inbound optional ports, so outbound SMTP depends on the security group's egress configuration.

---

## Database Requirements

Mattermost requires a PostgreSQL database. The interactive deployer and `CloudForgeDeployment` enable `provisionDatabase` automatically when it is not set; set it explicitly in hand-written contexts.

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 14 or later |
| Instance Class | `db.t3.small` (default) |
| Storage | 30 GB (default) |
| Database Name | `mattermost` |
| Backup Retention | 14 days |

**Database parameters:** `max_connections=200`, `shared_buffers={DBInstanceClassMemory/4096}`, `work_mem=8MB`, `maintenance_work_mem=128MB`, `log_statement=ddl`.

---

## Authentication

### Supported Auth Modes

| Mode | Team Edition | Enterprise Edition | Description |
|------|--------------|-------------------|-------------|
| `application-oidc` | GitLab OAuth provider | Native OIDC | Mattermost handles sign-in (default) |
| `alb-oidc` | Yes | Yes | The load balancer authenticates users |
| `none` | Yes | Yes | Mattermost local accounts only |

### OIDC Integration Details

#### Mattermost Team (Free) - GitLab OAuth

`mattermost-team` configures Mattermost's GitLab OAuth provider (`MM_GITLABSETTINGS_*`) with the OIDC provider's endpoints, which lets the free edition sign users in through Cognito.

- Users are created on first sign-in.
- The login button text and color are configurable.

**Callback Path:** `/signup/gitlab/complete`

**Limitations:**
- No single logout: signing out of Mattermost does not end the Cognito session.
- No group synchronization; team membership is managed in Mattermost.
- Endpoints are configured individually; the discovery document is not used.

#### Mattermost Enterprise - Native OpenID Connect

`mattermost-enterprise` configures Mattermost's OpenID Connect provider (`MM_OPENIDSETTINGS_*`).

- Users are created on first sign-in.
- Endpoints come from the provider's discovery document.
- Single logout uses the provider's `end_session_endpoint`.
- The login button text and color are configurable.

**Callback Path:** `/signup/openid/complete`

**Limitations:**
- Mattermost's OpenID Connect provider requires a Professional or Enterprise license.
- No group synchronization; team membership is managed in Mattermost.

A SAML integration class (`MattermostSamlIntegration`) exists but is not the default and is incomplete.

---

## Environment Variables

CloudForge sets these environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `MM_SERVICESETTINGS_SITEURL` | External URL, required for OAuth redirects (set when an FQDN is configured) | `https://chat.example.com` |
| `MM_SERVICESETTINGS_TRUSTEDPROXYIPHEADER` | Headers trusted from the load balancer | `X-Forwarded-For,X-Real-IP` |
| `MM_SERVICESETTINGS_FORWARD80TO443` | Disabled; TLS terminates at the load balancer | `false` |
| `MM_SERVICESETTINGS_WEBSOCKETURL` | Empty, so the site URL is used | `""` |
| `MM_SQLSETTINGS_DRIVERNAME` | Database driver | `postgres` |
| `MM_SQLSETTINGS_DATASOURCE` | Complete connection URL, injected from an SSM parameter | |

### OIDC Variables - Team Edition (GitLab OAuth)

| Variable | Description |
|----------|-------------|
| `MM_GITLABSETTINGS_ENABLE` | Enable GitLab OAuth |
| `MM_GITLABSETTINGS_ID` | OAuth client ID |
| `MM_GITLABSETTINGS_SECRET` | OAuth client secret, injected from Secrets Manager |
| `MM_GITLABSETTINGS_AUTHENDPOINT` | Authorization endpoint |
| `MM_GITLABSETTINGS_TOKENENDPOINT` | Token endpoint |
| `MM_GITLABSETTINGS_USERAPIENDPOINT` | UserInfo endpoint |
| `MM_GITLABSETTINGS_SCOPE` | OAuth scopes (`openid profile email`) |
| `MM_GITLABSETTINGS_BUTTONTEXT` | Login button text |
| `MM_GITLABSETTINGS_BUTTONCOLOR` | Login button color |

### OIDC Variables - Enterprise Edition (Native OIDC)

| Variable | Description |
|----------|-------------|
| `MM_OPENIDSETTINGS_ENABLE` | Enable native OpenID Connect |
| `MM_OPENIDSETTINGS_ID` | OIDC client ID |
| `MM_OPENIDSETTINGS_SECRET` | OIDC client secret, injected from Secrets Manager |
| `MM_OPENIDSETTINGS_DISCOVERYENDPOINT` | OIDC discovery endpoint |
| `MM_OPENIDSETTINGS_SCOPE` | OIDC scopes (`openid profile email`) |
| `MM_OPENIDSETTINGS_BUTTONTEXT` | Login button text |
| `MM_OPENIDSETTINGS_BUTTONCOLOR` | Login button color |

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

### Development (Team Edition)

A minimal configuration with the required PostgreSQL database.

```json
{
  "stackName": "Mattermost-Dev",
  "applicationId": "mattermost-team",
  "applicationName": "Mattermost Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "provisionDatabase": true,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```



### Development with OIDC (Team Edition)

```json
{
  "stackName": "Mattermost-Dev-DB",
  "applicationId": "mattermost-team",
  "applicationName": "Mattermost Dev",
  "environment": "dev",

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

### Staging with Email (Enterprise Edition)

```json
{
  "stackName": "Mattermost-Staging",
  "applicationId": "mattermost-enterprise",
  "applicationName": "Mattermost Staging",
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
  "maxInstanceCapacity": 1,

  "provisionDatabase": true,
  "databaseEngine": "postgres",
  "databaseVersion": "15",
  "databaseInstanceClass": "db.t3.small",
  "databaseAllocatedStorageGB": 30,
  "databaseName": "mattermost",
  "databaseBackupRetentionDays": 7,

  "enableSmtp": true,

  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "wafEnabled": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365"
}
```

### Production with SOC 2 Controls (Enterprise Edition)

```json
{
  "stackName": "Mattermost-Production",
  "applicationId": "mattermost-enterprise",
  "applicationName": "Mattermost",
  "environment": "prod",

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
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 1,

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

### Production with HIPAA Controls (Enterprise Edition)

For teams whose messages may contain PHI.

```json
{
  "stackName": "Mattermost-HIPAA",
  "applicationId": "mattermost-enterprise",
  "applicationName": "Mattermost Secure",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "secure.example.com",
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
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 1,

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

### High Availability

Running more than one Mattermost instance requires Mattermost's clustering feature, which needs an Enterprise license and `ClusterSettings` configuration. CloudForge does not set `MM_CLUSTERSETTINGS_*` variables, so the examples above use a single instance. The `enableClustering` flag only opens the gossip ports (8074 and 8075).

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

Setting `complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for those frameworks. It does not certify the deployment.

### SOC 2

**Infrastructure controls CloudForge can configure:**
- Encryption at rest (EBS, EFS, RDS) and in transit (TLS)
- Network isolation with security groups
- CloudWatch logging
- Database backup retention

**Controls you configure in Mattermost:**
- Message and data retention policies
- Compliance exports (a licensed feature)
- Audit logging

### HIPAA

The HIPAA example sets `logRetentionDays` to 2190 (six years). Controls you configure in Mattermost and your processes:
- Compliance exports
- Data loss prevention policies (a licensed feature)
- Restrictions on public channels for PHI
- User training on PHI handling

### GDPR

Controls you configure in Mattermost and your processes:
- Data retention policies
- User data export
- Erasure procedures
- A privacy policy for the instance

---

## Post-Deployment Tasks

### 1. Initial Login

After deployment with `authMode: "application-oidc"`:

1. Open `https://chat.example.com` (your configured FQDN).
2. Choose the Cognito sign-in button.
   - `mattermost-team` uses the GitLab OAuth provider (callback `/signup/gitlab/complete`).
   - `mattermost-enterprise` uses OpenID Connect (callback `/signup/openid/complete`).
3. Authenticate with Cognito.
4. The first user to sign in becomes the system administrator.

With `mattermost-team`, signing out of Mattermost does not end the Cognito session; it remains active until it expires.

### 2. Create Teams and Channels

1. Create the initial teams.
2. Create public and private channels.
3. Invite users.

### 3. Configure Email (if enabled)

CloudForge does not configure SMTP settings in Mattermost. In the System Console:

1. Go to **Environment** > **SMTP**.
2. Enter the SMTP server details (for example Amazon SES).
3. Test email delivery.

### 4. Configure Integrations

1. Enable incoming and outgoing webhooks.
2. Install plugins as needed.
3. Configure slash commands.

---

## Troubleshooting

### Mattermost won't start

**Check logs:**
```bash
# Fargate (log group name when storage is not retained; otherwise find the
# stack's log group in the CloudWatch console)
aws logs tail /aws/ecs/<stack-name>/fargate/<security-profile> --follow

# EC2 (via SSM Session Manager)
aws ssm start-session --target <instance-id>
# then: tail -f /opt/mattermost/logs/mattermost.log
```

### Database connection fails

1. Verify the security group allows port 5432 from the application.
2. Check the datasource SSM parameter.
3. Verify the database credentials in Secrets Manager.

### OIDC login fails

1. Verify the Cognito domain prefix is globally unique.
2. Check that the callback URL is registered.
3. Ensure `MM_SERVICESETTINGS_SITEURL` matches the URL users open.

### WebSocket errors

1. Check whether the target group uses sticky sessions.
2. Verify that WebSocket upgrade requests reach the target.
3. Check that WAF rules are not blocking WebSocket requests.

---

## Related Documentation

- [OIDC Integration](../../applications/OIDC.md)
- [Database Deployment Guide](../../databases/DATABASE-DEPLOYMENT-GUIDE.md)
- [Compliance Guide](../../compliance/README.md)
- [Deployment Context Reference](../../examples/README.md)

# Jenkins Application Guide

Jenkins is an open-source automation server for building, testing, and deploying software through CI/CD pipelines.

**Status**: Verified (deployed and exercised end to end by maintainers)

---

## Quick Reference

| Property | Value |
|----------|-------|
| **Application ID** | `jenkins` |
| **Category** | CI/CD |
| **Default Image** | `jenkins/jenkins:lts` |
| **Application Port** | `8080` |
| **Default CPU** | 1024 (Fargate) |
| **Default Memory** | 2048 MB (Fargate) |
| **Default Instance** | t3.small (EC2) |
| **Health Check Path** | `/login` |
| **Health Check Grace** | 300 seconds |
| **Supports Fargate** | Yes |
| **Supports EC2** | Yes |
| **Supported Auth Modes** | `application-oidc` (default), `alb-oidc`, `none` |
| **Database Required** | No |

---

## Upstream Features

- Pipelines defined in a `Jenkinsfile`
- Distributed builds with agents
- Plugin ecosystem, including Configuration as Code (JCasC)
- Integration with Git, Docker, and Kubernetes

---

## Optional Ports

| Port | Protocol | Direction | Feature Flag | Description |
|------|----------|-----------|--------------|-------------|
| 50000 | TCP | Inbound | `enableAgents` | JNLP Build Agents |

**Example enabling agents:**
```json
{
  "enableAgents": true
}
```

When enabled, inbound agents connect over TCP port 50000.

---

## Authentication

### Supported Auth Modes

| Mode | Description |
|------|-------------|
| `application-oidc` | Jenkins signs users in through the OpenID Connect Authentication plugin (default) |
| `alb-oidc` | The load balancer authenticates users before requests reach Jenkins |
| `none` | No CloudForge-managed authentication; use for development only |

### OIDC Integration Details

With `application-oidc`, CloudForge configures the OpenID Connect Authentication plugin (`oic-auth`) through Jenkins Configuration as Code (JCasC):

- Groups are read from the OIDC groups claim (`cognito:groups` for Cognito).
- Authorization uses a project matrix keyed on those groups.
- Sign-out also ends the Cognito session and returns to the Jenkins URL.
- The plugin's escape-hatch (local fallback) login is disabled on EC2.
- The setup wizard is skipped (`-Djenkins.install.runSetupWizard=false`).

**Callback Path:** `/securityRealm/finishLogin`

**Group-Based Authorization:**
- Admin group: `Overall/Administer`
- Developer group: `Overall/Read` plus `Job/Build`, `Job/Configure`, `Job/Create`, `Job/Read`, and `Job/Workspace`
- Viewer group: `Overall/Read` and `Job/Read`

---

## Environment Variables

CloudForge sets these container environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `JAVA_OPTS` | JVM system properties | `-Djenkins.model.Jenkins.rootUrl=https://jenkins.example.com/` |
| `JENKINS_OPTS` | Jenkins launcher options | `--httpListenAddress=0.0.0.0 --httpsPort=-1` |
| `JENKINS_URL` | External URL (set when an FQDN is configured) | `https://jenkins.example.com` |

`JAVA_OPTS` includes the root URL and inbound-agent host name (when an FQDN is configured), a form-content size limit, a relaxed `DirectoryBrowserSupport.CSP`, and, for `application-oidc`, the setup-wizard skip. HTTPS is disabled on Jenkins itself because TLS terminates at the load balancer.

---

## Storage Configuration

### Container (Fargate)
| Property | Value |
|----------|-------|
| Data Path | `/var/jenkins_home` |
| EFS Path | `/jenkins` |
| Volume Name | `jenkinsHome` |
| Container User | `1000:1000` |
| EFS Permissions | `750` |

### EC2
| Property | Value |
|----------|-------|
| EBS Device | `/dev/xvdh` |
| Data Path | `/var/lib/jenkins` |
| Log Paths | `/var/log/jenkins/jenkins.log`, `/var/log/userdata.log`, `/var/log/messages` |

---

## Deployment Context Examples

A Jenkins controller keeps its state in `JENKINS_HOME` and does not support running several controllers against the same data, so the examples below use a single instance. Scale build capacity with agents instead.

### Development

A minimal configuration without authentication.

```json
{
  "stackName": "Jenkins-Dev",
  "applicationId": "jenkins",
  "applicationName": "Jenkins Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "networkMode": "public",
  "region": "us-east-1",

  "authMode": "none",

  "cpu": 1024,
  "memory": 2048,

  "enableMonitoring": true,
  "logRetentionDays": "7"
}
```

### Development with Authentication

Jenkins with Cognito-backed OIDC sign-in.

```json
{
  "stackName": "Jenkins-Dev-Auth",
  "applicationId": "jenkins",
  "applicationName": "Jenkins Dev",
  "environment": "dev",

  "runtime": "fargate",
  "securityProfile": "dev",
  "topology": "application-service",

  "domain": "dev.example.com",
  "subdomain": "jenkins",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-dev-yourcompany",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "JenkinsAdmins",
  "cognitoUserGroupName": "JenkinsDevelopers",

  "cpu": 1024,
  "memory": 2048,

  "enableMonitoring": true,
  "logRetentionDays": "30"
}
```

### Staging with SOC 2 Controls

A pre-production environment with the SOC 2 framework rules enabled.

```json
{
  "stackName": "Jenkins-Staging",
  "applicationId": "jenkins",
  "applicationName": "Jenkins Staging",
  "environment": "staging",

  "runtime": "fargate",
  "securityProfile": "staging",
  "topology": "application-service",

  "domain": "staging.example.com",
  "subdomain": "jenkins",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-staging-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "JenkinsAdmins",
  "cognitoUserGroupName": "JenkinsDevelopers",

  "cpu": 2048,
  "memory": 4096,
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 1,

  "complianceFrameworks": "SOC2",
  "awsConfigEnabled": true,
  "guardDutyEnabled": true,
  "wafEnabled": true,
  "enableFlowlogs": true,

  "enableMonitoring": true,
  "enableEncryption": true,
  "logRetentionDays": "365"
}
```

### Production with SOC 2 Controls and Build Agents

An EC2 deployment with inbound agents enabled.

```json
{
  "stackName": "Jenkins-Production",
  "applicationId": "jenkins",
  "applicationName": "Jenkins CI",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "example.com",
  "subdomain": "jenkins",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-prod-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "JenkinsAdmins",
  "cognitoUserGroupName": "JenkinsDevelopers",

  "instanceType": "t3.medium",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 1,

  "enableAgents": true,

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

### Production with PCI DSS Controls

For pipelines that deploy payment-processing applications.

```json
{
  "stackName": "Jenkins-PCI",
  "applicationId": "jenkins",
  "applicationName": "Jenkins PCI",
  "environment": "prod",

  "runtime": "ec2",
  "securityProfile": "production",
  "topology": "application-service",

  "domain": "secure.example.com",
  "subdomain": "jenkins",
  "enableSsl": true,

  "networkMode": "private-with-nat",
  "region": "us-east-1",

  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "jenkins-pci-yourcompany",
  "cognitoMfaEnabled": true,
  "cognitoMfaMethod": "totp",
  "cognitoCreateGroups": true,
  "cognitoAdminGroupName": "JenkinsAdmins",
  "cognitoUserGroupName": "JenkinsDevelopers",

  "instanceType": "t3.large",
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 1,

  "enableAgents": true,

  "complianceFrameworks": "PCI-DSS,SOC2",
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

---

## Health Check Configuration

| Property | Default | Description |
|----------|---------|-------------|
| Path | `/login` | Health check endpoint |
| Grace Period | 300 seconds | Time before health checks start |
| Interval | 30 seconds | Time between checks |
| Timeout | 5 seconds | Response timeout |
| Healthy Threshold | 2 | Consecutive successes |
| Unhealthy Threshold | 3 | Consecutive failures |

**Custom configuration:**
```json
{
  "healthCheckGracePeriod": 300,
  "healthCheckInterval": 30,
  "healthCheckTimeout": 5,
  "healthyThreshold": 2,
  "unhealthyThreshold": 3
}
```

---

## Compliance Considerations

Setting `complianceFrameworks` enables CloudForge's infrastructure controls and validation rules for those frameworks. It does not certify the deployment; certification requires an assessment of your whole environment and processes.

### SOC 2

**Infrastructure controls CloudForge can configure:**
- Encryption at rest (EBS/EFS) and in transit (TLS)
- Network isolation with security groups
- CloudWatch logging
- Scoped IAM roles

**Controls you configure in Jenkins and your processes:**
- Build audit logging
- Approval gates for production deployments
- Secrets stored with the Credentials plugin
- Artifact retention
- OIDC authentication and role-based access control
- Separate development, test, and production pipelines

### PCI DSS

Additional practices for pipelines that deploy payment systems:
- Separate development, test, and production pipelines
- Code review and change approval before production deployment
- Automated security testing in the pipeline
- An audit trail for all deployments

### HIPAA

Additional practices for pipelines that deploy healthcare applications:
- An audit trail for all deployments
- Access controls on pipelines that handle PHI
- Encryption of build artifacts

---

## Post-Deployment Tasks

### 1. Initial Login

After deployment with `authMode: "application-oidc"`:

1. Open `https://jenkins.example.com` (your configured FQDN).
2. Sign in through Cognito.
3. Users in the admin group receive `Overall/Administer`.

### 2. Configure Build Agents (if enabled)

When `enableAgents: true`:

1. Go to **Manage Jenkins** > **Nodes**.
2. Create an inbound agent.
3. Start the agent with the secret Jenkins shows for it.
4. The agent connects on port 50000.

### 3. Install Additional Plugins

Commonly used plugins:
- Pipeline
- Git
- Credentials Binding

### 4. Configure Secrets

1. Go to **Manage Jenkins** > **Credentials**
2. Add credentials for:
   - Source control (GitHub, GitLab tokens)
   - Container registries
   - Cloud providers (AWS credentials)
   - Deployment targets

---

## Troubleshooting

### Jenkins won't start

**Check logs:**
```bash
# Fargate (log group name when storage is not retained; otherwise find the
# stack's log group in the CloudWatch console)
aws logs tail /aws/ecs/<stack-name>/fargate/<security-profile> --follow

# EC2 (via SSM Session Manager; no port 22 or SSH key needed)
aws ssm start-session --target <instance-id>
# then: tail -f /var/log/jenkins/jenkins.log
```

### OIDC login fails

1. Verify the Cognito domain prefix is globally unique.
2. Check that the callback URL (`/securityRealm/finishLogin`) is registered in Cognito.
3. Verify the app client's OAuth settings.

### Build agents can't connect

1. Ensure `enableAgents` is `true` in the deployment context.
2. Check that the security group allows port 50000.
3. Verify the agent is using the correct secret.

---

## Related Documentation

- [OIDC Integration](../../applications/OIDC.md)
- [Compliance Guide](../../compliance/README.md)
- [Deployment Context Reference](../../examples/README.md)

# Advanced Guide

This guide covers configuration, deployment options, and tooling beyond the
[README](https://github.com/CloudForgeCI/cfc-core/blob/develop/readme.md) quick start. Property names and defaults come from
`DeploymentConfig` (`cloudforge-core/src/main/java/com/cloudforge/core/config/DeploymentConfig.java`),
which is the source of truth when this page and the code disagree.

- [How configuration is loaded](#how-configuration-is-loaded)
- [Configuration reference](#configuration-reference)
- [Security profile defaults](#security-profile-defaults)
- [Example configurations](#example-configurations)
- [Application-specific configurations](#application-specific-configurations)
- [Authentication](#authentication)
- [Databases](#databases)
- [Backups](#backups)
- [Scaling and health checks](#scaling-and-health-checks)
- [Compliance frameworks](#compliance-frameworks)
- [Local emulators](#local-emulators)
- [Testing and validation](#testing-and-validation)
- [SBOM and dependency scanning](#sbom-and-dependency-scanning)
- [Repository structure](#repository-structure)
- [Advanced commands](#advanced-commands)
- [Known limitations](#known-limitations)

## How configuration is loaded

A deployment is described by a flat JSON object. The sample application in `cfc-testing`
reads it from:

1. the file passed with `--context <file>` to the Interactive Deployer;
2. otherwise the file named by the `CFC_CONTEXT_FILE` environment variable;
3. otherwise `deployment-context.json` in the working directory.

When the CDK CLI runs the app (`cdk synth`, `cdk deploy`, `cdk diff`, `cdk destroy`), the
entry point in `cfc-testing/cdk.json` detects the CDK parent process, loads the context file
from step 2 or 3, and synthesizes without prompting. If no context file exists, nothing is
synthesized, so create one first with the Interactive Deployer or by copying an example.

Library consumers can instead set the object as the `cfc` CDK context value
(`app.getNode().setContext("cfc", config.toContextMap())`) and read it with
`DeploymentContext.from(app)`.

Parsing rules:

- Keys are the `DeploymentConfig` field names. `environment` may also be written as `env`.
- Enum values are case-insensitive (`fargate` and `FARGATE` are equivalent).
- `yes`/`on` and `no`/`off` are accepted for booleans.
- Unknown keys are ignored. A misspelled key is silently dropped, so check the summary the
  Interactive Deployer prints before deploying.
- Before synthesis, the Interactive Deployer fills missing values from the selected
  application and its supported authentication modes, and prints each default it applied.

## Configuration reference

All properties are optional except `applicationId`. "Profile" in the Default column means the
security profile decides when the value is unset; see
[Security profile defaults](#security-profile-defaults).

### Core

| Property | Type | Default | Description |
|---|---|---|---|
| `applicationId` | string | required | Application to deploy, for example `jenkins`, `gitlab`, `wordpress`. See [application IDs](#application-ids). |
| `stackName` | string | - | CloudFormation stack name. |
| `environment` / `env` | string | `dev` | Environment label: `dev`, `staging`, or `prod`. |
| `runtime` | string | `fargate` | `fargate` or `ec2`. |
| `topology` | string | `application-service`; `cms-service` for CMS applications | `application-service`, `cms-service`, `jenkins-service`, or `s3-website`. |
| `securityProfile` | string | `dev` | `dev`, `staging`, or `production`. |
| `region` | string | `us-east-1` | AWS region. |
| `availabilityZones` | string array | automatic | Availability zone suffixes, for example `["a", "b"]`. |
| `containerImage` | string | application default | Container image tag override, for example `"v1.2.3"`. |

### DNS and TLS

| Property | Type | Default | Description |
|---|---|---|---|
| `domain` | string | - | Base domain, for example `example.com`. Requires a Route 53 hosted zone unless `createZone` is `true`. |
| `subdomain` | string | - | Host prefix; `jenkins` with `example.com` gives `jenkins.example.com`. |
| `fqdn` | string | `subdomain.domain` | Override for the computed host name. |
| `enableSsl` | boolean | `false` | HTTPS listener with an ACM certificate. |
| `certificateArn` | string | - | Existing ACM certificate to use instead of issuing one. |
| `createZone` | boolean | `false` | Create the Route 53 hosted zone for `domain`. |

With `enableSsl` and a `domain`, CloudForge requests a DNS-validated public certificate. With
`enableSsl` and no `domain`, it issues a certificate for the load balancer's DNS name from AWS
Private CA. Browsers do not trust that certificate, and AWS Private CA is billed monthly for
as long as the CA exists. To use a publicly trusted certificate without a Route 53 zone, import
it into ACM and set `certificateArn`.

### Network

| Property | Type | Default | Description |
|---|---|---|---|
| `networkMode` | string | `public` | `public` (alias `public-no-nat`), `private-with-nat`, or `isolated` (requires VPC endpoints). |
| `lbType` | string | `alb` | `alb` or `nlb`. OIDC authentication requires `alb`. |
| `wafEnabled` | boolean | profile | AWS WAF web ACL on the load balancer. |
| `httpsStrictEnabled` | boolean | profile | With TLS enabled, serve HTTPS only (no HTTP listener on port 80). |
| `albAccessLogging` | boolean | `false` | Load balancer access logs to S3. |
| `cloudfrontEnabled` | boolean | - | CloudFront distribution in front of the load balancer. |
| `enableFlowlogs` | boolean | profile | VPC Flow Logs. |
| `restrictSecurityGroupEgress` | boolean | `false` | Restrict egress to the VPC CIDR. Requires VPC endpoints for AWS services. |
| `bastionCidr` | string | `10.0.1.0/24` | Retained for compatibility; no longer controls access. EC2 instances are reached through SSM Session Manager and Fargate tasks through ECS Exec. |

### Compute and scaling

| Property | Type | Default | Description |
|---|---|---|---|
| `cpu` | integer | `1024` | Fargate CPU units: 256, 512, 1024, 2048, 4096, 8192, or 16384. |
| `memory` | integer | `2048` | Fargate memory in MiB. Must be a valid combination with `cpu`. |
| `instanceType` | string | `t3.micro` | EC2 instance type. The Interactive Deployer shows each application's recommended minimum. |
| `minInstanceCapacity` | integer | `1` | Minimum tasks or instances. |
| `maxInstanceCapacity` | integer | `1` | Maximum tasks or instances. |
| `enableAutoScaling` | boolean | `true` when max > min | CPU-based scaling. |
| `cpuTargetUtilization` | integer | `60` | Target CPU percentage for scaling. |
| `healthCheckGracePeriod` | integer | `300` | Seconds before health checks count after a task or instance starts. |
| `healthCheckInterval` | integer | `30` | Seconds between checks. |
| `healthCheckTimeout` | integer | `5` | Seconds to wait for a response. |
| `healthyThreshold` | integer | `2` | Consecutive successes before healthy. |
| `unhealthyThreshold` | integer | `3` | Consecutive failures before unhealthy. |

### Storage

| Property | Type | Default | Description |
|---|---|---|---|
| `retainStorage` | boolean | `false` | Keep EFS/EBS volumes when the stack is deleted. |
| `existingFileSystemId` | string | - | Reuse an existing EFS file system, for example during recovery. |
| `artifactsBucket` | string | - | S3 bucket for build artifacts (Jenkins). |
| `artifactsPrefix` | string | `jenkins/job/${JOB_NAME}/${BUILD_NUMBER}` | S3 key prefix for build artifacts. |
| `automatedBackupEnabled` | boolean | profile | AWS Backup plan for EFS and RDS. |
| `crossRegionBackupEnabled` | boolean | profile | Copy backups to a second region. |

### Authentication

| Property | Type | Default | Description |
|---|---|---|---|
| `authMode` | string | `none` | `none`, `alb-oidc`, or `application-oidc`. |
| `oidcProvider` | string | `none` (`cognito` when an OIDC mode is set) | `cognito`, `identity-center`, `external-idp`, or `cloudforge-manager`. |
| `cognitoAutoProvision` | boolean | `false` | Create a Cognito user pool. |
| `cognitoDomainPrefix` | string | derived from `stackName` | Globally unique Cognito hosted UI domain prefix. |
| `cognitoUserPoolName` | string | `<stackName>-users` | User pool name. |
| `cognitoUserPoolId` | string | - | Use an existing user pool. |
| `cognitoAppClientId` | string | - | Use an existing app client. |
| `cognitoMfaEnabled` | boolean | `true` for `production` | Require MFA. |
| `cognitoMfaMethod` | string | `both` | `totp`, `sms`, or `both`. |
| `cognitoSelfSignupEnabled` | boolean | profile | Allow self-service sign-up. A synthesis warning is logged when enabled with a compliance framework. |
| `cognitoCreateGroups` | boolean | `true` | Create admin and user groups. |
| `cognitoAdminGroupName` | string | `<applicationId>-Admins` | Admin group name. |
| `cognitoUserGroupName` | string | `<applicationId>-Users` | User group name. |
| `cognitoInitialAdminEmail` | string | `admin@<domain>` | Email for the first admin user. |
| `cognitoInitialAdminPhone` | string | - | E.164 phone number, required for SMS MFA. |
| `oidcIssuer` | string | - | Issuer URL of an external provider. |
| `oidcAuthorizationEndpoint` | string | - | Authorization endpoint. |
| `oidcTokenEndpoint` | string | - | Token endpoint. |
| `oidcUserInfoEndpoint` | string | - | UserInfo endpoint. |
| `oidcClientId` | string | - | Client ID registered with the provider. |
| `oidcClientSecretName` | string | - | Secrets Manager secret holding the client secret. |
| `cloudforgeManagerIssuerUrl` | string | - | Public URL of a CloudForge Manager install used as the identity provider. |
| `autoProvisionIdentityCenter` | boolean | `false` | Create an IAM Identity Center application. |
| `ssoInstanceArn` | string | - | IAM Identity Center instance ARN. |
| `ssoGroupId` | string | - | Identity Center group ID. |
| `ssoTargetAccountId` | string | - | 12-digit target account ID. |
| `identityCenterGroupName` | string | - | Identity Center group for user assignment. |

The Cognito defaults marked "derived" are filled by the Interactive Deployer when an OIDC
`authMode` is selected; it also turns on `enableSsl`, because OIDC requires HTTPS.

### Database

| Property | Type | Default | Description |
|---|---|---|---|
| `provisionDatabase` | boolean | `false`; `true` when the application requires a database | Create an Amazon RDS instance. |
| `databaseEngine` | string | application default | `postgres`, `mysql`, `mariadb`, `aurora-postgresql`, or `aurora-mysql`. |
| `databaseVersion` | string | application default | Engine version. |
| `databaseInstanceClass` | string | application default | For example `db.t3.small`. |
| `databaseAllocatedStorageGB` | integer | application default | Allocated storage. |
| `databaseMultiAz` | boolean | profile | Multi-AZ deployment. |
| `databaseReadReplicaCount` | integer | - | Read replicas (`0` disables). |
| `databaseName` | string | application default | Initial database name. |
| `databaseBackupRetentionDays` | integer | profile | RDS automated backup retention (`0` disables). |

### Compliance and monitoring

| Property | Type | Default | Description |
|---|---|---|---|
| `complianceFrameworks` | string | - | Comma-separated: `soc2`, `pci-dss`, `hipaa`, `gdpr`. |
| `complianceMode` | string | `enforce` for `production`, otherwise `advisory` | `enforce`, `advisory`, or `disabled`. |
| `enableMonitoring` | boolean | `true` | CloudWatch metrics and alarms. |
| `logRetentionDays` | integer | profile | CloudWatch Logs retention. Values are rounded up to the next retention period CloudWatch supports (for example, 730 becomes 731). |
| `enableEncryption` | boolean | `true` | Encryption at rest. |
| `efsEncryptionInTransitEnabled` | boolean | `true` | TLS for EFS mounts. |
| `cloudWatchLogsKmsEncryptionEnabled` | boolean | `false` | Customer-managed KMS keys for log groups. |
| `awsConfigEnabled` | boolean | `false` | AWS Config rules for the selected frameworks. |
| `createConfigInfrastructure` | boolean | `false` | Create the Config recorder and delivery channel. Only one may exist per account and region. |
| `cloudTrailEnabled` | boolean | `false` | CloudTrail trail. |
| `cloudTrailInsightsEnabled` | boolean | `false` | CloudTrail Insights. |
| `guardDutyEnabled` | boolean | `false` | Use GuardDuty findings. |
| `createGuardDutyDetector` | boolean | `false` | Create the GuardDuty detector. Only one may exist per account and region. |
| `guardDutyAlertsConfigured` | boolean | `false` | EventBridge rules that forward GuardDuty findings. |
| `securityHubEnabled` | boolean | `false` | AWS Security Hub. |
| `inspectorEnabled` | boolean | `false` | Amazon Inspector. |
| `macieEnabled` | boolean | `false` | Amazon Macie. |
| `macieAutomatedDiscovery` | boolean | `false` | Macie automated discovery jobs. |
| `auditManagerEnabled` | boolean | `false` | AWS Audit Manager assessment. |
| `certificateExpirationMonitoring` | boolean | `false` | Alarms for ACM certificate expiry. |
| `route53QueryLoggingEnabled` | boolean | `false` | Route 53 query logging. |
| `s3ObjectLockEnabled` | boolean | `false` | S3 Object Lock on audit buckets. |
| `securityMonitoringEnabled` | boolean | `false` | Security monitoring alarms. |
| `antiMalwareEnabled`, `fileIntegrityMonitoring`, `containerRuntimeSecurity`, `containerImageScanning` | boolean | `false` | Workload security controls. |
| `enableS3VersioningRemediation` | boolean | `false` | Config remediation for S3 versioning. |
| `enableCloudTrailBucketAccessRemediation` | boolean | `false` | Config remediation for CloudTrail bucket access logging. |
| `enableRdsDeletionProtectionRemediation` | boolean | `false` | Config remediation for RDS deletion protection. |
| `enableRdsAutoMinorVersionUpgradeRemediation` | boolean | `false` | Config remediation for RDS minor version upgrades. |
| `gdprDataTransferApproved` | boolean | `false` | Confirms transfer mechanisms (SCCs, BCRs) are in place when GDPR is selected with a non-EU region. |

### Optional application ports

These flags open additional application ports when the application declares them.

| Property | Opens |
|---|---|
| `enableAgents` | Jenkins inbound agents (50000) |
| `enableSsh` | Git over SSH (GitLab 22, Gitea 2222) |
| `enableSmtp` / `enableSmtps` | SMTP 587 / 465 |
| `enableClustering` | Cluster ports (for example Mattermost, Vault) |
| `enableDockerRegistry` | Registry ports (GitLab 5050, Nexus 5000-5002) |
| `enableMetrics` | Prometheus metrics port |
| `enableNotary` / `enableTrivy` | Harbor Notary (4443) / Trivy |
| `enableSentinel` / `enableCluster` | Redis Sentinel (26379) / cluster bus (16379) |

See [Known limitations](#known-limitations) before relying on these flags.

### CloudForge Manager deployments

These apply only when `applicationId` is `cloudforge-manager`:
`provisionManagerRedisSessions`, `provisionManagerAccountCipherKey` (default `true`),
`managerLicenseKey`, and `managerDirectDeployEnabled` (default `false`).

## Security profile defaults

| Setting | `dev` | `staging` | `production` |
|---|---|---|---|
| `complianceMode` | `advisory` | `advisory` | `enforce` |
| Log retention | 7 days | 90 days | 6 years |
| Automated backups | off | on, 30 days | on, 90 days |
| Cross-region backup copy | off | off | on |
| Cognito MFA (Interactive Deployer default) | off | off | on |
| Cognito user pool on stack deletion | deleted | deleted | retained |
| WAF (`wafEnabled` unset) | off | on | off |
| VPC Flow Logs (`enableFlowlogs` unset) | off | on | on |

`cloudTrailEnabled`, `guardDutyEnabled`, `awsConfigEnabled`, and `albAccessLogging` default to
`false`, so they stay off in every profile unless you set them or a selected framework
requires them. See [Security Profiles](guides/SECURITY_RULES_README.md) for the full comparison.

Selected compliance frameworks can require controls (for example flow logs, WAF, backups, or
backup vault lock) regardless of these defaults. `logRetentionDays` overrides the profile's log
retention in `staging` and `production`. The profile implementations are in
`cloudforge-api/src/main/java/com/cloudforgeci/api/core/security/`.

## Example configurations

Complete example files are in [`docs/examples/`](examples/README.md) and
[`docs/examples/applications/`](examples/applications/README.md). The sample application's
own contexts are in `cfc-testing/deployment-contexts/`.

Development, no domain:

```json
{
  "stackName": "jenkins-dev",
  "applicationId": "jenkins",
  "runtime": "fargate",
  "securityProfile": "dev"
}
```

Production on EC2 with a custom domain, TLS, and Cognito with MFA:

```json
{
  "stackName": "jenkins-prod",
  "applicationId": "jenkins",
  "runtime": "ec2",
  "instanceType": "t3.medium",
  "securityProfile": "production",
  "networkMode": "private-with-nat",
  "domain": "example.com",
  "subdomain": "jenkins",
  "enableSsl": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "example-jenkins-auth",
  "cognitoMfaEnabled": true,
  "cognitoInitialAdminEmail": "admin@example.com",
  "minInstanceCapacity": 2,
  "maxInstanceCapacity": 4
}
```

## Application-specific configurations

### Application IDs

| Category | `applicationId` values |
|---|---|
| CI/CD and code quality | `jenkins`, `gitlab`, `drone`, `sonarqube` |
| Version control | `gitea` |
| Monitoring | `grafana`, `prometheus` |
| Analytics | `metabase`, `superset` |
| Databases | `postgresql`, `redis` |
| Artifact registries | `nexus`, `harbor` |
| Secrets | `vault` |
| Collaboration | `mattermost-team`, `mattermost-enterprise` |
| CMS | `wordpress`, `drupal`, `joomla`, `typo3`, `concrete-cms`, `october-cms` |
| E-commerce | `woocommerce`, `magento`, `prestashop`, `opencart`, `sylius`, `bagisto` |
| Forums, wiki, LMS, CRM, social | `phpbb`, `flarum`, `mybb`, `mediawiki`, `moodle`, `suitecrm`, `dolphin-una` |

The registered list is in
`cloudforge-api/src/main/resources/META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`.
`cloudforge-manager` is available when the `cloudforge-manager-deployment` artifact is on the
classpath, as it is in `cfc-testing`. Per-application details are in the
[application guides](guides/applications/README.md) and [CMS guides](guides/cms/README.md).

### GitLab

```json
{
  "stackName": "gitlab",
  "applicationId": "gitlab",
  "runtime": "ec2",
  "instanceType": "t3.large",
  "securityProfile": "production",
  "domain": "example.com",
  "subdomain": "gitlab",
  "enableSsl": true,
  "authMode": "application-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "example-gitlab-auth"
}
```

GitLab requires a database, so RDS is provisioned automatically.

### Mattermost

```json
{
  "stackName": "chat",
  "applicationId": "mattermost-team",
  "runtime": "fargate",
  "cpu": 2048,
  "memory": 4096,
  "securityProfile": "production",
  "domain": "example.com",
  "subdomain": "chat",
  "enableSsl": true
}
```

### Grafana and Metabase

Both can use an embedded database or RDS:

```json
{
  "stackName": "grafana",
  "applicationId": "grafana",
  "runtime": "fargate",
  "securityProfile": "staging",
  "provisionDatabase": true
}
```

Set `provisionDatabase` to `false` for the embedded database (single instance only).

### Vault

```json
{
  "stackName": "vault",
  "applicationId": "vault",
  "runtime": "ec2",
  "instanceType": "t3.small",
  "securityProfile": "production",
  "networkMode": "private-with-nat",
  "domain": "example.com",
  "subdomain": "vault",
  "enableSsl": true
}
```

### WordPress (CMS topology)

```json
{
  "stackName": "blog",
  "applicationId": "wordpress",
  "topology": "cms-service",
  "runtime": "fargate",
  "securityProfile": "staging",
  "domain": "example.com",
  "subdomain": "blog",
  "enableSsl": true
}
```

The `cms-service` topology provisions RDS, an S3 media bucket, Redis, and a CloudFront
distribution based on the application's CMS specification. See the
[CMS guides](guides/cms/README.md) for the conditions and current limitations.

## Authentication

`authMode` selects where authentication happens:

- `alb-oidc`: the Application Load Balancer authenticates users before forwarding requests.
- `application-oidc`: the application itself acts as the OIDC client.
- `none`: no authentication in front of the application.

Each application specification lists the modes it supports (`getSupportedAuthModes()`); some
applications, such as Vault, Nexus, and Harbor, support only `none`. The Interactive Deployer
offers only the supported modes and replaces an unsupported mode with a supported one,
printing a warning. The [application guides](guides/applications/README.md) list the modes
for each application.

`oidcProvider` selects the identity provider:

- `cognito`: set `cognitoAutoProvision: true` to create a user pool, or supply
  `cognitoUserPoolId` and `cognitoAppClientId` to reuse one.
- `external-idp`: set `oidcIssuer`, the endpoint URLs, `oidcClientId`, and
  `oidcClientSecretName` (a Secrets Manager secret you create).
- `identity-center`: IAM Identity Center (`ssoInstanceArn`, `ssoGroupId`, or
  `autoProvisionIdentityCenter`).
- `cloudforge-manager`: a CloudForge Manager install at `cloudforgeManagerIssuerUrl`.

On MiniStack and LocalStack, ALB OIDC actions are removed from the template because the
emulators have no ALB authentication; the Interactive Deployer warns when the configured mode
is not supported on the chosen target.

Setup guides: [OIDC integration](applications/OIDC.md),
[Cognito MFA](setup/COGNITO_MFA_COMPLIANCE_SETUP.md),
[IAM Identity Center](setup/AWS_IDENTITY_CENTER_SETUP.md).

## Databases

Applications declare whether they need a database. For applications that require one
(for example GitLab, Harbor, Mattermost, Superset, and the PHP CMS applications),
`provisionDatabase` is set to `true` automatically. Grafana and Metabase can run with an
embedded database or RDS. Credentials are generated and stored in AWS Secrets Manager.

Override the application's defaults with the `database*` properties above. See the
[Database Deployment Guide](databases/DATABASE-DEPLOYMENT-GUIDE.md).

## Backups

With `automatedBackupEnabled` (on by default in `staging` and `production`), CloudForge
creates an AWS Backup vault and plan covering EFS and RDS. Retention follows the profile
(30 days in `staging`, 90 days in `production`). `production` also copies backups to a second
region unless `crossRegionBackupEnabled` is `false`. When a selected framework requires
backup recovery controls, the vault is created with a vault lock.

## Scaling and health checks

Set `maxInstanceCapacity` above `minInstanceCapacity` to enable CPU-based scaling toward
`cpuTargetUtilization`. Applications with long startup times (GitLab runs database migrations
on first start) may need a larger `healthCheckGracePeriod`, for example `600`.

## Compliance frameworks

`complianceFrameworks` accepts `soc2`, `pci-dss`, `hipaa`, and `gdpr` in any combination.
Validation runs in layers:

1. **Framework rules** evaluate the configuration during synthesis.
2. **cdk-nag** packs check the constructs (HIPAA Security, PCI DSS 3.2.1, and AWS Solutions
   checks for SOC 2).
3. **cfn-guard** rules in `cloudforge-api/src/main/resources/cfn-guard/` check the synthesized
   template when `complianceMode` is `enforce` (requires the `cfn-guard` CLI).
4. **AWS Config** rules, with optional remediation, monitor the deployed resources when
   `awsConfigEnabled` is `true`.

`complianceMode` controls what happens on a failure: `enforce` blocks synthesis or
deployment, `advisory` reports violations as warnings, and `disabled` skips validation.

Example with SOC 2 controls:

```json
{
  "stackName": "jenkins-soc2",
  "applicationId": "jenkins",
  "runtime": "fargate",
  "securityProfile": "production",
  "complianceFrameworks": "soc2",
  "awsConfigEnabled": true,
  "createConfigInfrastructure": true,
  "enableS3VersioningRemediation": true,
  "logRetentionDays": 731,
  "domain": "example.com",
  "subdomain": "jenkins",
  "enableSsl": true,
  "authMode": "alb-oidc",
  "cognitoAutoProvision": true,
  "cognitoDomainPrefix": "example-jenkins-soc2",
  "cognitoMfaEnabled": true
}
```

For HIPAA, PCI DSS, and GDPR, change `complianceFrameworks` and add the controls the framework
calls for, for example `"networkMode": "private-with-nat"` and `"logRetentionDays": 2192`
(HIPAA), `"wafEnabled": true`, `"guardDutyEnabled": true`, `"albAccessLogging": true`, and
`"certificateExpirationMonitoring": true` (PCI DSS), or an EU `region` (GDPR). Multiple
frameworks can be combined, for example `"soc2,hipaa,pci-dss"`; the strictest requirement
applies.

Control mappings and coverage: [compliance documentation](compliance/README.md),
[Multi-Framework Compliance](compliance/MULTI_FRAMEWORK_COMPLIANCE.md),
[Auditor Compliance Mapping](AUDITOR_COMPLIANCE_MAPPING.md), and the
[compliance truth tables](testing/COMPLIANCE_TRUTH_TABLES.md).

## Local emulators

MiniStack and LocalStack run as Docker containers on the gateway port 4566, so only one can
run at a time. LocalStack requires `LOCALSTACK_AUTH_TOKEN`.

The platform menu (`InteractiveDeployer --platform`) lists the available platforms and these
actions:

| Action | Effect |
|---|---|
| `start` | Start the emulator and its companions (StackPort resource browser on port 8888, and the nginx emulator edge on port 80), then reconcile host routes. |
| `stop` | Stop the emulator and companions. |
| `restart` | Stop, then start. |
| `status` | Report container health. |
| `reconcile_edge` | Regenerate emulator edge routes for deployed stacks. |

Deployments to an emulator use the stack name with a `-ministack` or `-localstack` suffix.
Before deploying, a preflight step checks that the template is supported on the target.
Relevant environment variables:

| Variable | Purpose |
|---|---|
| `AWS_ENDPOINT_URL` | Emulator endpoint, normally `http://localhost:4566`. |
| `LOCALSTACK_AUTH_TOKEN` | LocalStack license token. |
| `MINISTACK_PREFLIGHT`, `LOCALSTACK_PREFLIGHT` | `enforce` (default), `warn`, or `off`. |
| `CFC_LOCALSTACK_SKIP_PREFLIGHT` | `true` skips LocalStack preflight. |
| `MINISTACK_REGION`, `MINISTACK_LOG_LEVEL` | MiniStack container settings. |
| `CFC_EDGE_HTTP_PORT` | Host port for the emulator edge (default 80). |
| `CFC_CONTEXT_FILE` | Deployment context file to load. |

Guides: [Local Emulator Quick Start](guides/LOCAL_EMULATOR_QUICK_START.md),
[application compatibility](guides/LOCAL_EMULATOR_APP_CATALOG.md),
[host names](guides/LOCAL_EMULATOR_HOSTS.md), [emulator edge](guides/LOCAL_EMULATOR_EDGE.md),
[MiniStack](ministack/README.md), [LocalStack](localstack/README.md).

## Testing and validation

Tests are skipped by default (`skipTests=true` in the root `pom.xml`) because the full
`cloudforge-api` suite is large. Enable them with the `ci` profile:

```bash
mvn clean verify -Pci                                  # all modules, with coverage checks
mvn -pl cloudforge-api -Pci test                       # one module (dependencies already installed)
mvn -pl cloudforge-api -am -Pci test                   # one module and the modules it depends on
mvn -pl cloudforge-api -Pci test -Dtest=ComplianceFactoryTest
mvn -pl cloudforge-api -Pci test -Dtest='ComplianceMatrix*Test'
mvn -pl cloudforge-api -Pci test -Dtest=ComplianceFactoryTest#testMethodName
```

When combining `-am` with `-Dtest`, add `-Dsurefire.failIfNoSpecifiedTests=false` so modules
without a matching test do not fail.

Tests that need a running emulator are tagged and excluded by default:

```bash
mvn -pl cloudforge-ministack -Pci,ministack test       # includes @Tag("ministack") tests
mvn -pl cloudforge-localstack -Pci,localstack test     # includes @Tag("localstack") tests
```

`cfc-testing` is a separate project that runs its tests by default; its `ministack` and
`localstack` profiles run only the tests with those tags:

```bash
mvn -f cfc-testing/pom.xml test
mvn -f cfc-testing/pom.xml test -Dtest=DeploymentContextPropagationTest
```

Synthesis and template validation scripts live in `cfc-testing/scripts/`; see
[Advanced commands](#cfc-testing-scripts) and the [Extended Testing](guides/EXTENDED-TESTING.md)
guide.

## SBOM and dependency scanning

The CycloneDX plugin writes an aggregate SBOM during `package`:

```bash
mvn clean package
ls target/cfc-core-sbom.*                # JSON and XML
```

OWASP Dependency-Check runs in the `security-scan` profile (the first run downloads the
vulnerability database into `~/.m2/dependency-check-data`):

```bash
mvn dependency-check:check -Psecurity-scan
open target/dependency-check-report.html
```

The `security-scan.yml` workflow runs both on pushes and pull requests to `develop` and on a
weekly schedule. See [SECURITY.md](https://github.com/CloudForgeCI/cfc-core/blob/develop/SECURITY.md) for vulnerability reporting.

## Repository structure

```text
cfc-core/
├── pom.xml                  # Parent POM and BOM (com.cloudforgeci:cfc-core)
├── cloudforge-core/         # Contracts: DeploymentConfig, enums, ApplicationSpec, local-emulator interfaces
├── cloudforge-api/          # CDK factories, application specifications, compliance rules, deployment API
├── cloudforge-ministack/    # MiniStack template adapter, deployer, and platform runtime
├── cloudforge-localstack/   # LocalStack template adapter, deployer, and platform runtime
├── cfc-testing/             # Sample application: Interactive Deployer, CDK launchers, example plugins, scripts
├── docs/                    # Documentation (Docusaurus site configuration in docs/web/)
├── scripts/                 # Local emulator, Docker, and compliance helper scripts
├── docker/, docker-compose.yml  # Local Docker development environment
└── .github/workflows/       # CI, security scanning, report publishing, releases
```

`cfc-testing` is not part of the Maven reactor; it consumes the modules through the BOM, the
same way an external project does. The release workflows publish `cfc-core` (the BOM),
`cloudforge-core`, `cloudforge-api`, `cloudforge-ministack`, and `cloudforge-localstack`.

## Advanced commands

### Interactive Deployer

Run from `cfc-testing` after building it:

```bash
alias cfc='java -cp "target/classes:target/dependency/*" com.cloudforgeci.samples.app.InteractiveDeployer'
```

| Invocation | Behavior |
|---|---|
| `cfc` | Uses `deployment-context.json` if it exists and shows the deploy menu; otherwise prompts for a new configuration and saves it. |
| `cfc --context <file>` / `-c <file>` | Loads the given context file. |
| `cfc --interactive` / `-i` | Prompts for a new configuration even if a context file exists. Setting `INTERACTIVE=true` in the environment has the same effect. |
| `cfc --force` / `-f` | Deletes the context file, then prompts for a new configuration. Combined with `--context`, the named file is deleted. |
| `cfc <stackName>` | Overrides `stackName`. |
| `cfc <option>` | Runs a deploy menu option (a single digit) without prompting. |
| `cfc --platform` | Opens the emulator platform menu. |

The deploy menu options are:

| Option | Action |
|---|---|
| `1` | Synthesize only (writes `cdk.out/`). |
| `2` | Deploy to AWS (`cdk deploy --require-approval never`). |
| `3` | Destroy the existing AWS stack, then deploy. |
| `4` | Dry run: adapt the template for MiniStack and report the result; prints the command for an AWS change set. |
| `5` | Export the CloudFormation template as JSON or YAML (prompted). |
| `6` | Deploy to MiniStack. |
| `7` | Run cfn-guard validation, deploy to MiniStack, and verify the stack. |
| `8` | Deploy to LocalStack. |
| `9` | Reconfigure (start a new interactive setup). |
| `0` | Cancel. |

Examples:

```bash
cfc --context deployment-contexts/Jenkins-Stack.json 1     # synthesize a saved context
cfc --context deployment-contexts/Jenkins-Stack.json 6     # deploy it to MiniStack
cfc my-jenkins 2                                           # deploy deployment-context.json to AWS as "my-jenkins"
```

### CDK CLI

`cfc-testing/cdk.json` runs the same entry point, which synthesizes the context file without
prompting when the CDK CLI invokes it:

```bash
cd cfc-testing
cdk bootstrap                                              # once per account and region
cdk synth
cdk diff
cdk deploy                                                 # uses deployment-context.json
CFC_CONTEXT_FILE=deployment-contexts/Jenkins-Stack.json cdk deploy
cdk deploy --no-execute --require-approval never           # create a change set without executing it
cdk destroy <stackName>
```

The target account and region come from your AWS credentials (`CDK_DEFAULT_ACCOUNT`,
`CDK_DEFAULT_REGION`) and the `region` property.

### Maven

```bash
mvn clean install                                          # build and install all modules; tests skipped
mvn -T1C clean install -Djacoco.skip=true                  # parallel build without coverage instrumentation
mvn clean verify -Pci                                      # build with tests and coverage checks
mvn -pl cloudforge-api -am install                         # one module and its dependencies
mvn -f cfc-testing/pom.xml package -Dmaven.test.skip=true  # sample app and target/dependency/
mvn javadoc:aggregate                                      # aggregate JavaDoc in target/site/apidocs/
```

### Scripts

Repository root (`scripts/`):

| Script | Purpose |
|---|---|
| `setup-cloudforge-local-hosts.sh [--remove\|--dry-run]` | Add or remove `*.cloudforge.localhost` host entries on systems that do not resolve `.localhost` automatically. |
| `emulator-edge-via-maven.sh <start\|stop\|restart\|rebuild\|status\|reconcile\|reload>` | Manage the emulator edge outside the platform menu. |
| `generate-compliance-report.sh [stackName]` | Summarize AWS Config, CloudTrail, GuardDuty, and encryption status for a deployed stack. |
| `generate-audit-evidence.sh --stack-name <name> --framework <SOC2\|HIPAA\|PCI-DSS\|GDPR>` | Collect an audit evidence package (`--help` for all options). |
| `docker-*.sh` | Local Docker Compose environment; see the [Docker quick start](guides/DOCKER_QUICK_START.md). |

#### cfc-testing scripts

`cfc-testing/scripts/` (run from `cfc-testing`):

| Script | Purpose |
|---|---|
| `deploy-ministack-apps.sh` | Deploy the MiniStack-compatible sample contexts, skipping stacks that already exist. |
| `deploy-localstack-apps.sh` | Deploy the LocalStack sample contexts. |
| `deploy-localstack-full-catalog.sh [results.tsv]` | Deploy every catalog application to LocalStack one at a time. |
| `deploy-localstack-compliance-matrix.sh [results.tsv]` | Deploy each framework and profile combination to LocalStack. |
| `deploy-localstack-template-batch.sh <dir> <results.tsv> <index> <count>` | Deploy a batch of pre-generated templates to LocalStack. |
| `redeploy-localstack-history.sh` | Redeploy LocalStack sample stacks. |
| `comprehensive-synth-test.sh [--serve]` | Synthesize EC2 and Fargate across all security profiles. |
| `enhanced-synth-test.sh` | Synthesis matrix including authentication modes. |
| `comprehensive-resource-validator.sh` | Compare synthesized resources with the expected resource matrix. |
| `drift-detector.sh <baseline\|detect\|history\|archive>` | Track changes in validation results between builds. |
| `master-validation-system.sh <full\|validate\|drift\|smoke\|baseline\|report>` | Run validation, truth-table, and drift steps together. |
| `quick-synth-benchmark.sh`, `performance-synth-benchmark.sh`, `run-all-benchmarks.sh` | Synthesis timing benchmarks. |
| `deployment-changeset-validator.sh`, `deployment-dry-run-tracker.sh` | AWS change set and dry-run checks (require AWS credentials). |

## Known limitations

- **Optional port flags**: the `enable*` port flags in
  [Optional application ports](#optional-application-ports) are read by the compute factories
  through `@DeploymentContext` injection, but `DeploymentContext` does not expose matching
  accessors, so the injected values are currently always unset and the ports are not opened.
- **`scopeConfigRulesToDeployment`**: referenced by `ComplianceFactory`, but not a
  `DeploymentConfig` property, so it is ignored when set in a context file.
- **SAML and Keycloak** integrations are under development and may change.
- **Local emulators** do not implement every AWS feature (for example ALB authentication
  actions). See the [application compatibility catalog](guides/LOCAL_EMULATOR_APP_CATALOG.md).

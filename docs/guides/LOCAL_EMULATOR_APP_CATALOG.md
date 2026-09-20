# Local Emulator Application Catalog

Which CloudForge applications can deploy to **MiniStack** (Interactive Deployer option **6**) and **LocalStack** (option **8**), and why others are blocked.

The Interactive Deployer discovers applications with `ServiceLoader` (`META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`). Built-in applications live in **cloudforge-api**; `cloudforge-manager` comes from the **cloudforge-manager-deployment** dependency; **cfc-testing** adds the sample Craft CMS plugin (`craft-cms`).

Example contexts: `cfc-testing/deployment-contexts/*.json`

---

## Quick Reference

| Target | Deploy option | Preflight | RDS-backed apps |
|--------|---------------|-----------|-----------------|
| **MiniStack** | **6** (option **7** runs synthesis, validation, deploy, and verification in one pipeline) | `MINISTACK_PREFLIGHT` (default `enforce`) | **Blocked**: no `AWS::RDS::*` support |
| **LocalStack** | **8** | Tier/capability probe, `LOCALSTACK_PREFLIGHT` (default `enforce`) | **Supported** when the RDS capability is available |

Use `authMode: none` in the deployment context for local smoke tests unless you are testing Cognito or application OIDC on LocalStack.

---

## MiniStack: Deployable Applications

Preflight blocks a deploy when:

- `topology` is set to anything other than `application-service` or `jenkins-service` (so `cms-service` is blocked)
- `provisionDatabase` is `true`, or the application's `requiresDatabase()` is `true` (from `@ApplicationPlugin` / `@CmsPlugin`)
- The canonical template contains unsupported CloudFormation types: `AWS::RDS::*`, `AWS::WAF::*`, `AWS::WAFv2::*`, `AWS::Config::*`, `AWS::CloudTrail::*`, `AWS::Backup::*`, or `AWS::GuardDuty::*`
- Another MiniStack stack already uses the same host port

EFS, Application Auto Scaling, and `AWS::EC2::SecurityGroupIngress` resources are adapted rather than blocked.

Override with `MINISTACK_PREFLIGHT=warn` or `MINISTACK_PREFLIGHT=off` (not recommended for CI).

### Supported

These deploy with `provisionDatabase: false` and pass preflight. Set `authMode: none` for local smoke tests.

| Application ID | Display name | Container port | Category | Notes |
|----------------|--------------|----------------|----------|-------|
| `cloudforge-manager` | CloudForge Manager | 1958 | operations | First-run setup wizard for sign-in |
| `jenkins` | Jenkins | 8080 | cicd | Unlock with `secrets/initialAdminPassword` in the Jenkins home |
| `grafana` | Grafana | 3000 | monitoring | Container image default login `admin` / `admin` |
| `prometheus` | Prometheus | 9090 | monitoring | |
| `metabase` | Metabase | 3000 | analytics | Embedded H2 database when RDS is not provisioned |
| `drone` | Drone | 80 | cicd | Host port 80 is a privileged port and may need elevated bind permissions |
| `gitea` | Gitea | 3000 | vcs | |
| `vault` | HashiCorp Vault | 8200 | secrets | Initialize and unseal after deploy |
| `redis` | Redis | 6379 | database | TCP service, not HTTP |
| `nexus` | Nexus Repository | 8081 | artifactregistry | Set `memory: 4096` (plugin recommendation) |
| `postgresql` | PostgreSQL | 5432 | database | Container PostgreSQL, not RDS; unrelated to `provisionDatabase` |
| `sonarqube` | SonarQube | 9000 | code-quality | Set `memory: 4096` (plugin recommendation) |

Sample context files: `CloudForgeManager-Fresh.json`, `Jenkins-Stack.json`, `Grafana-Stack.json`, `Prometheus-Stack.json`, `Metabase-Stack.json`, `Drone-Stack.json`, `Gitea-Stack.json`, `Vault-Stack.json`, `Redis-Stack.json`, `Nexus-Stack.json`, `PostgreSQL-Stack.json`, `SonarQube-Stack.json`.

### Host-Port Constraint

The MiniStack adapter maps `localhost:<containerPort>` to the ECS task and reports it in the `MiniStackApplicationUrl` stack output. Only one stack per host port can run at a time.

| Port | Applications (one at a time) |
|------|------------------------------|
| **3000** | Grafana, Gitea, Metabase |
| **80** | Drone (GitLab, Harbor, and most CMS applications also use 80 but are blocked on MiniStack) |
| **8080** | Jenkins |

The other ports in the supported set are unique: 1958, 5432, 6379, 8081, 8200, 9000, 9090.

### Blocked on MiniStack

Use LocalStack (option 8) or AWS (option 2) for these.

| Reason | Application IDs |
|--------|-----------------|
| **Requires RDS** (`requiresDatabase: true`) | `gitlab`, `harbor`, `superset`, `mattermost-enterprise`, `mattermost-team` |
| **CMS applications** (`cms-service` topology and required RDS) | `wordpress`, `woocommerce`, `drupal`, `joomla`, `typo3`, `concrete-cms`, `october-cms`, `magento`, `prestashop`, `opencart`, `sylius`, `bagisto`, `phpbb`, `flarum`, `mybb`, `suitecrm`, `mediawiki`, `moodle`, `dolphin-una`, `craft-cms` (sample plugin) |

The preflight message suggests Interactive Deployer option 8 (LocalStack).

---

## LocalStack: Deployable Applications

Preflight probes `/_localstack/health`, the edition/tier, and the capabilities the deployment needs:

| Requirement | When |
|-------------|------|
| **ECS + ELBV2** | All deployments |
| **RDS** | `provisionDatabase: true`, `requiresDatabase()`, or `AWS::RDS::*` in the template |
| **EC2 + Auto Scaling** | `runtime: ec2` |
| **Warnings** | `AWS::EFS::*` is adapted to bind mounts unless native EFS is available (`LOCALSTACK_TIER_PROFILE=ultimate`); `AWS::Backup::*` is removed unless the tier supports it |

Preflight also checks for host-port conflicts with other LocalStack stacks. Override with `LOCALSTACK_PREFLIGHT=warn|off` or `CFC_LOCALSTACK_SKIP_PREFLIGHT=true`. When the health response does not list RDS, `LOCALSTACK_CAPABILITIES=rds` declares it explicitly.

LocalStack requires `LOCALSTACK_AUTH_TOKEN`. Start it from the platform menu (`InteractiveDeployer --platform`, select `localstack`, action `start`). MiniStack and LocalStack both listen on port **4566**, so run only one emulator at a time.

### Supported

Every application the Interactive Deployer lists (the 35 built into cloudforge-api, CloudForge Manager, and the Craft CMS sample plugin) can deploy to LocalStack when:

1. The LocalStack tier provides ECS, ELBV2, and (for database-backed apps) RDS.
2. The deployment context matches the app (for example, `provisionDatabase: true` for GitLab, Mattermost, and CMS applications).
3. The container images can be pulled on the host.
4. Fargate `cpu` and `memory` meet the application's needs (Nexus, SonarQube, and GitLab need larger tasks).

#### Fargate Without RDS

The same applications as MiniStack:

| Application ID | Port | Notes |
|----------------|------|-------|
| `cloudforge-manager` | 1958 | The LocalStack adapter sets `CFC_MANAGER_TARGET=localstack` |
| `jenkins` | 8080 | |
| `grafana` | 3000 | |
| `prometheus` | 9090 | |
| `metabase` | 3000 | Embedded H2 when `provisionDatabase: false` |
| `drone` | 80 | |
| `gitea` | 3000 | |
| `vault` | 8200 | |
| `redis` | 6379 | |
| `nexus` | 8081 | |
| `postgresql` | 5432 | Container PostgreSQL |
| `sonarqube` | 9000 | |

#### Fargate With RDS

| Application ID | Port | Notes |
|----------------|------|-------|
| `gitlab` | 80 | Long startup; RDS PostgreSQL |
| `harbor` | 80 | RDS |
| `superset` | 8088 | RDS |
| `mattermost-enterprise` | 8065 | RDS |
| `mattermost-team` | 8065 | RDS |
| CMS, e-commerce, and forum applications | 80 (`craft-cms`: 8080) | All `@CmsPlugin` applications listed above |

Set `provisionDatabase: true` (it is also enabled automatically for applications that require a database).

Sample contexts: `Mattermost-Stack-LocalStack.json`, `Metabase-Stack-LocalStack.json`, `Joomla-Stack-LocalStack.json` (CMS with RDS), and `Jenkins-Stack-LocalStack.json` (domain with `application-oidc`).

#### EC2 Runtime

Applications that support EC2 can use `runtime: ec2` when LocalStack provides EC2 and Auto Scaling. UserData and AMI behavior differ from AWS, so treat these deployments as smoke tests.

#### Compliance Resources

WAF, AWS Config, CloudTrail, GuardDuty, and AWS Backup resources are added by the `staging` and `production` security profiles and by compliance settings. On LocalStack, Backup resources are removed and EFS is adapted unless the tier supports them natively. MiniStack blocks these resource types.

---

## Deploy Commands

Build `cfc-testing` first so that `target/classes` and `target/dependency` exist. The trailing number selects the menu option.

```bash
cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1

# MiniStack (option 6)
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/Jenkins-Stack.json 6

# LocalStack (option 8); start LocalStack first
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/Mattermost-Stack-LocalStack.json 8
```

Batch scripts (they skip stacks that are already `CREATE_COMPLETE`):

- `cfc-testing/scripts/deploy-ministack-apps.sh`: MiniStack-compatible applications
- `cfc-testing/scripts/deploy-localstack-apps.sh`: LocalStack applications with non-conflicting host ports

---

## Related Documentation

- [MiniStack overview](../ministack/README.md): preflight, architecture, StackPort
- [LocalStack overview](../localstack/README.md): token, StackPort, tier adapter
- [Interactive Deployer](INTERACTIVE_DEPLOYER.md): deployment options 6, 7, and 8
- [CMS Guides](cms/README.md)

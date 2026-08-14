# Local Emulator Application Catalog

Which CloudForge applications can deploy to **MiniStack** (option **6**) and **LocalStack** (option **8**), and why others are blocked.

Discovery: Interactive Deployer loads plugins via `ServiceLoader` (`META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec`). Built-in apps live in **cloudforge-api**; sample plugins in **cfc-testing** (SonarQube, Craft CMS).

Example contexts: `cfc-testing/deployment-contexts/*.json`

---

## Quick reference

| Target | Deploy option | Preflight | RDS-backed apps |
|--------|---------------|-----------|-----------------|
| **MiniStack** | **6** | Strict (`MINISTACK_PREFLIGHT=enforce`) | **Blocked** — no `AWS::RDS::*` |
| **LocalStack** | **8** | Tier/capability probe (`LOCALSTACK_PREFLIGHT=enforce`) | **Supported** on Base tier when RDS capability is probed |

Use **`authMode: none`** in deployment context for simplest local smoke tests unless you are explicitly testing Cognito/ALB auth on LocalStack.

---

## MiniStack — deployable applications

Preflight blocks deploys when:

- `provisionDatabase: true`, or the app **`requiresDatabase()`** (from `@ApplicationPlugin` / `@CmsPlugin`)
- The canonical template contains unsupported CFN types (`AWS::RDS::*`, `AWS::WAF*`, `AWS::Config::*`, `AWS::CloudTrail::*`, `AWS::Backup::*`, `AWS::GuardDuty::*`)

Override: `MINISTACK_PREFLIGHT=warn|off` (not recommended for CI).

### Supported (13 apps)

These deploy with `provisionDatabase: false` and pass preflight. Set `authMode: none` for local smoke.

| Application ID | Display name | Default port | Category | Notes |
|----------------|--------------|--------------|----------|-------|
| `cloudforge-manager` | CloudForge Manager | 1958 | operations | Operations panel; first-run setup wizard for login |
| `jenkins` | Jenkins | 8080 | cicd | Unlock via `initialAdminPassword` in container |
| `grafana` | Grafana | 3000 | monitoring | Default login `admin` / `admin` (image default) |
| `prometheus` | Prometheus | 9090 | monitoring | |
| `metabase` | Metabase | 3000 | analytics | Embedded H2 when RDS not provisioned |
| `drone` | Drone | 80 | cicd | Host port **80** may need elevated bind on some macOS setups |
| `gitea` | Gitea | 3000 | vcs | |
| `vault` | HashiCorp Vault | 8200 | secrets | Requires init/unseal after deploy |
| `redis` | Redis | 6379 | database | TCP cache, not HTTP |
| `nexus` | Nexus Repository | 8081 | artifactregistry | Default 4 GiB Fargate memory in plugin metadata |
| `postgresql` | PostgreSQL | 5432 | database | **Container** Postgres (not RDS); not the same as `provisionDatabase` |
| `sonarqube` | SonarQube | 9000 | code-quality | **cfc-testing** sample plugin; 4 GiB memory recommended |

**Sample context files** (repo): `CloudForgeManager-Dev.json`, `Jenkins-Stack.json`, `Grafana-Stack.json`, `Prometheus-Stack.json`, `Metabase-Stack.json`, `Drone-Stack.json`, `Gitea-Stack.json`, `Vault-Stack.json`, `Redis-Stack.json`, `Nexus-Stack.json`, `PostgreSQL-Stack.json`, `SonarQube-Stack.json`.

### MiniStack host-port constraint

The adapter maps **`localhost:<containerPort>`** to the ECS task (`MiniStackApplicationUrl`). Only **one stack per host port** at a time.

| Port | Apps (pick one at a time) |
|------|-----------------------------|
| **3000** | Grafana, Gitea, Metabase |
| **80** | Drone (GitLab/Harbor/CMS use 80 on AWS but are RDS-blocked on MiniStack) |
| **8080** | Jenkins |

Other ports are unique in the supported set (1958, 6379, 8081, 8200, 9090, 9000, 5432).

### Blocked on MiniStack (24+ apps) — use LocalStack or AWS

| Reason | Application IDs |
|--------|-----------------|
| **Requires RDS** (`requiresDatabase: true`) | `gitlab`, `harbor`, `superset`, `mattermost-enterprise`, `mattermost-team`, **all CMS/e-commerce/forum/CRM/LMS** (see below) |
| **CMS / `@CmsPlugin`** (MySQL/MariaDB RDS in template) | `wordpress`, `woocommerce`, `drupal`, `joomla`, `typo3`, `concrete-cms`, `october-cms`, `magento`, `prestashop`, `opencart`, `sylius`, `bagisto`, `phpbb`, `flarum`, `mybb`, `suitecrm`, `mediawiki`, `moodle`, `dolphin-una`, `craft-cms` (sample plugin) |

Preflight message points to **Interactive Deployer option 8** (LocalStack) or AWS option **2**.

---

## LocalStack — deployable applications

Preflight probes `/_localstack/health`, edition/tier, and required capabilities:

| Requirement | When |
|-------------|------|
| **ECS + ELBV2** | All Fargate stacks (default) |
| **RDS** | `provisionDatabase: true`, `requiresDatabase()`, or `AWS::RDS::*` in template |
| **EC2 + Auto Scaling** | `runtime: ec2` in deployment context |
| **Warnings (non-blocking on Base)** | `AWS::EFS::*` → bind mounts; `AWS::Backup::*` → stripped unless Ultimate |

Override: `LOCALSTACK_PREFLIGHT=warn|off` or `CFC_LOCALSTACK_SKIP_PREFLIGHT=true`.

Requires `LOCALSTACK_AUTH_TOKEN` and a running LocalStack selected from `InteractiveDeployer --platform`. MiniStack and LocalStack share port **4566** — only one emulator at a time.

### Supported — all discovered applications (37+)

Every application the Interactive Deployer lists can deploy to LocalStack **when**:

1. **Base (trial) tier** exposes ECS, ELBV2, and (for RDS apps) RDS.
2. Deployment context matches the app (e.g. `provisionDatabase: true` for GitLab/Mattermost/CMS).
3. Container images are pullable on the host (some enterprise images may 404 locally).
4. Fargate CPU/memory meet plugin defaults (Nexus, SonarQube, GitLab need larger tasks).

#### Fargate without RDS (same 13 as MiniStack, plus auth/domain variants)

| Application ID | Port | LocalStack notes |
|----------------|------|------------------|
| `cloudforge-manager` | 1958 | `CFC_MANAGER_TARGET=localstack` |
| `jenkins` | 8080 | ALB forward kept (unlike MiniStack redirect) |
| `grafana` | 3000 | |
| `prometheus` | 9090 | |
| `metabase` | 3000 | Embedded H2 when `provisionDatabase: false` |
| `drone` | 80 | |
| `gitea` | 3000 | |
| `vault` | 8200 | |
| `redis` | 6379 | |
| `nexus` | 8081 | |
| `postgresql` | 5432 | Container Postgres spec |
| `sonarqube` | 9000 | Sample plugin |

#### Fargate with RDS (LocalStack only among local emulators)

| Application ID | Port | Notes |
|----------------|------|-------|
| `gitlab` | 80 | Long startup; RDS Postgres |
| `harbor` | 80 | RDS + heavy stack |
| `superset` | 8088 | RDS |
| `mattermost-enterprise` | 8065 | RDS; verify image tag and EFS tier behavior |
| `mattermost-team` | 8065 | RDS |
| **CMS / e-commerce / forums** | mostly **80** | All `@CmsPlugin` apps: WordPress, WooCommerce, Drupal, Joomla, Typo3, Concrete, October, Magento, PrestaShop, OpenCart, Sylius, Bagisto, phpBB, Flarum, MyBB, SuiteCRM, MediaWiki, Moodle, Dolphin UNA, Craft CMS (sample) |

Set `provisionDatabase: true` (or rely on `requiresDatabase: true`) and use a context with full boolean fields (`wafEnabled`, `enableMonitoring`, etc.).

**Sample context:** `Mattermost-Stack-LocalStack.json`, `Jenkins-Stack-LocalStack.json` (domain + Cognito example).

#### EC2 runtime

Any app with `supportsEc2: true` can use `runtime: ec2` when LocalStack probes **EC2 + Auto Scaling**. Smoke fidelity only — UserData/AMI behavior differs from AWS.

#### Compliance-heavy AWS templates

WAF, AWS Config, CloudTrail, GuardDuty, and AWS Backup resources appear in **STAGING/PRODUCTION** profiles on real AWS. On LocalStack **Base** tier, Backup is stripped and EFS is adapted; Ultimate tier may retain native EFS/Backup when probed. MiniStack strips or blocks these types entirely.

---

## Deploy commands

```bash
cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1
unset CFC_DEPLOYING   # required for option 6/8 (not synth-only)

# MiniStack
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/Jenkins-Stack.json 8

# LocalStack (token + localstack-start first)
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/Mattermost-Stack-LocalStack.json 10
```

Batch MiniStack deploy (supported apps only): `cfc-testing/scripts/deploy-ministack-apps.sh`

---

## Related documentation

- [MiniStack overview](../ministack/README.md) — preflight, architecture, StackPort
- [LocalStack overview](../localstack/README.md) — token, StackPort, tier adapter
- [Interactive Deployer](INTERACTIVE_DEPLOYER.md) — options 8 and 10

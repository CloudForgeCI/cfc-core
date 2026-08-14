# MiniStack Local Deployment

Deploy CloudForge-generated CloudFormation to [MiniStack](https://github.com/ministackorg/ministack) — an MIT-licensed, open-source AWS emulator — without an AWS account.

**Quick start from repository root:** [Local Emulator Quick Start](../guides/LOCAL_EMULATOR_QUICK_START.md)

MiniStack support lives in **`cfc-testing`**, the utility that exercises `cloudforge-api` and `cloudforge-core`. Canonical AWS templates stay unchanged in the libraries; local deployment adaptations are applied and audited downstream.

MiniStack is **not** LocalStack. This project uses the open-source `ministackorg/ministack` image only.

**Local auth is deferred.** MiniStack MVP focuses on base → domain → TLS deploy and verification. The auth proxy, mock OIDC, and browser login flow are **not active by default** — evaluation waits until LocalStack is running, since LocalStack covers more services and may make local ALB/Cognito auth viable without a custom proxy. Canonical AWS templates still include auth when configured; only the **local runtime substitute** is paused.

---

## Available vs not available

Canonical CloudForge templates are **unchanged** for AWS. MiniStack deploys an **adapted** copy (see `cdk.out/<stack>.ministack-adaptations.json`). Use this table to set expectations for option **6** deployments.

### Available (local MiniStack deployments)

| Capability | Notes |
|------------|--------|
| CloudFormation create / update / delete / no-op | Via `MiniStackDeployer` + change sets |
| VPC, subnets, IGW, security groups | SG **rules are recorded** and queryable (`describe-security-groups`); not full packet-filter enforcement |
| ECS Fargate → Docker containers | App reachable on host port; logs via CloudWatch Logs APIs |
| ALB + listeners (control plane) | Inventory via ELBv2 APIs; local entry `MiniStackLocalUrl` (`/_alb/...`) |
| Route53 hosted zones + records | Emulator DNS only — assert via API, not OS/public resolver |
| ACM certificates + HTTPS listener resources | Presence in CFN/ACM; local TLS termination differs from AWS |
| IAM roles/policies in template | Accepted for stack create; not full IAM evaluation |
| Incremental add/remove domain / TLS | Same stack update path |
| EFS → host bind mount | Paths under `.ministack-volumes/`; `MiniStackHostVolume*` outputs |
| CloudForge Manager operations against MiniStack | Inventory, health, delete, history when CloudForge Manager is deployed |
| Automated native tests | `mvn -pl cloudforge-ministack test -P ministack` — see [Verification](VERIFICATION.md#automated-tests-maven) |

### Not available or adapted away

| Capability | MiniStack behavior |
|------------|-------------------|
| ALB → ECS **forward** / target health routing | Adapter rewrites to **HTTP redirect → `localhost:<appPort>`** |
| ALB Cognito / OIDC authenticate actions | Stripped from adapted template; auth proxy / mock OIDC **off by default** (deferred) |
| Security group **packet filtering** | Rules stored; do not treat allow/deny traffic tests as real |
| Public / OS DNS for FQDNs | Use `MiniStackApplicationUrl` / `MiniStackLocalUrl`; optional `/etc/hosts` |
| Real browser HTTPS / ACM validation flow | Cert + HTTPS listener resources exist; use output HTTP URLs for smoke |
| EFS NFS mounts | Replaced with host bind mounts |
| Application Auto Scaling | Removed by adapter |
| WAF, Config, CloudTrail, GuardDuty | Out of scope for MiniStack MVP (still in canonical AWS templates) |
| AWS Service Catalog | Not emulated — publish/test SC on real AWS |
| Full Cognito IdP / ALB login UX | Deferred pending LocalStack evaluation |

Per-resource canonical vs adapted vs deployed detail: **[Resource verification matrix](RESOURCE_VERIFICATION.md)**.

---

## Documentation

| Guide | Description |
|-------|-------------|
| **[Setup](SETUP.md)** | Prerequisites, build, start MiniStack |
| **[Deployment](DEPLOYMENT.md)** | Interactive Deployer, MiniStackCli, base Jenkins walkthrough |
| **[Jenkins on MiniStack](JENKINS.md)** | Jenkins Fargate setup, AWS CLI against MiniStack, CloudWatch logs, initial admin password |
| **[Verification](VERIFICATION.md)** | Confirm what deployed locally (AWS Console equivalent); includes [local DNS vs API](VERIFICATION.md#local-dns-vs-api-verification) |
| **[Resource verification matrix](RESOURCE_VERIFICATION.md)** | Per AWS resource: canonical vs adapted vs deployed, how to verify, local fidelity |
| **[Advanced Configuration](ADVANCED.md)** | Auth proxy, incremental updates, template adaptations, environment variables |
| **[Deployable applications](DEPLOYABLE_APPS.md)** | MiniStack app catalog (supported vs blocked) — see also [full catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md) |
| **[Troubleshooting](TROUBLESHOOTING.md)** | Common failures and debugging steps |

---

## Quick Start

**Full path from repository root:** [Local Emulator Quick Start](../guides/LOCAL_EMULATOR_QUICK_START.md)

```bash
# Repository root — build once, then start MiniStack from the platform menu
mvn clean install -DskipTests
mvn -f cfc-testing package -Dmaven.test.skip=true

# cfc-testing
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
export AWS_ENDPOINT_URL=http://localhost:4566   # optional; default for MiniStack clients

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
# Choose option 6 — Deploy to MiniStack
```

MiniStack is always menu option **6** (no `MINISTACK` env flag). Plain `cdk synth` without `INTERACTIVE=true` never prompts — it uses CDK defaults. Use `INTERACTIVE=true cdk synth` if you prefer the CDK CLI entry, then choose option 6.

### CloudForge Manager in five minutes (local panel)

This runs the CloudForge Manager panel on your laptop against MiniStack; it does **not**
deploy CloudForge Manager as a Fargate application first. After the initial Maven and npm
dependency download, the sequence is intended to take about five minutes.

```bash
# Repository root — build the Angular panel and package the CloudForge Manager server.
mvn -pl cloudforge-manager -am -Pui package -DskipTests

# Start exactly one emulator from the platform menu.
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
cd ..

# Point the locally running CloudForge Manager at MiniStack and start it on :1958.
mvn -pl cloudforge-manager spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--cfc.manager.target=ministack"
```

Open `http://127.0.0.1:1958`, complete the first-run local-admin setup, and
choose **MiniStack** in the target selector. To deploy CloudForge Manager as
an application inside MiniStack later, use `cfc-testing` Interactive Deployer
option **6** — option **8** is LocalStack only.

### Deploy CloudForge Manager *into* MiniStack

The laptop panel above inspects MiniStack from your host. To deploy CloudForge Manager as
a CloudForge Fargate application inside the emulator, use the standard
`cfc-testing → CDK synth → MiniStack adapter` path:

```bash
# Repository root — build the cfc-testing classpath. No manual `docker compose build` needed:
# CloudForgeManagerDeploymentExtension.beforeDeploy() builds the image automatically for both
# MiniStack and LocalStack now.
mvn -f cfc-testing package -Dmaven.test.skip=true

cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/CloudForgeManager-Dev.json 8
```

Option **6** synthesizes the canonical CDK template for
`applicationId: cloudforge-manager`, adapts it for MiniStack, and deploys it.

### Deploy preflight (option 6)

Before adapt/deploy, option **6** runs a **strict** preflight that blocks stacks MiniStack cannot create (for example RDS-backed apps like Mattermost). You get a clear message instead of a CloudFormation rollback.

| Variable | Default | Purpose |
|----------|---------|---------|
| `MINISTACK_PREFLIGHT` | `enforce` | `enforce` blocks unsupported deploys; `warn` prints warnings; `off` skips checks |

RDS-backed apps and templates with unsupported CFN types (for example `AWS::RDS::DBParameterGroup`) are blocked. Use Interactive Deployer option **8** (LocalStack) or deploy to AWS instead.

**Full application list:** [Local Emulator Application Catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md) — 13 supported MiniStack apps, port conflicts, and blocked RDS/CMS apps.

---

## Architecture

MiniStack deployment follows a fixed pipeline:

```text
DeploymentConfig
  → CDK synthesis (canonical AWS template)
  → MiniStackDeploymentPipeline (cloudforge-ministack)
  → MiniStackTemplateAdapter (audited local copy)
  → MiniStackDeployer (CloudFormation create/update via AWS SDK)
  → MiniStack emulator
```

Auth runtime (`MiniStackAuthProxy`, mock OIDC) is **deferred** — see note above. Adapter still strips unsupported ALB auth actions when auth is in the canonical template; set `authMode: none` in `deployment-context.json` for MiniStack testing until LocalStack is evaluated.

**Design principles**

- Canonical templates are never weakened in `cloudforge-api` / `cloudforge-core`.
- Cross-target local deploy **contracts** live in `cloudforge-core` (`com.cloudforge.core.local`); MiniStack **implementations** in `cloudforge-ministack`.
- MiniStack-specific changes happen only in `MiniStackTemplateAdapter`, with an adaptation report written beside the local template.
- Incremental deployments use CloudFormation change sets (create → update → no-op).
- No AWS CLI or real AWS credentials are required for local deployment (AWS CLI is optional for verification).

---

## Source Layout

Implementation code lives under `cloudforge-ministack/` (library) and `cfc-testing/` (Interactive Deployer, samples):

| Class | Module | Role |
|-------|--------|------|
| `DeploymentTarget`, `TemplateAdapter`, `LocalDeployer`, `LocalDeploymentPipeline` | cloudforge-core | Cross-module local deploy contracts |
| `CloudFormationTemplateDiff` | cloudforge-core | Semantic CFN template diff (canonical parity tests) |
| `MiniStackDeploymentPipeline` | cloudforge-ministack | MiniStack wiring for `LocalDeploymentPipeline` |
| `MiniStackTemplateAdapter` | cloudforge-ministack | MiniStack `TemplateAdapter` implementation |
| `MiniStackDeployer` | cloudforge-ministack | MiniStack `LocalDeployer` implementation |
| Type | Module | Role |
|------|--------|------|
| `LocalEmulatorDefaults` | cloudforge-core | Container names, images, ports, env var keys |
| `LocalEmulatorSpec` | cloudforge-core | Per-target metadata (`ministack()`, `localstack()`) |
| `LocalEmulatorRuntimes` | cloudforge-core | SPI catalog — `forTarget(MINISTACK)` |
| `EmulatorLifecycle` | cloudforge-core | Start/stop/restart/status orchestration |
| `MiniStackEmulatorRuntime` | cloudforge-ministack | Docker create args only (extends `AbstractLocalEmulatorRuntime`) |
| `MiniStackLocalRuntime` | cloudforge-ministack | mock-oidc + auth proxy lifecycle *(deferred — off by default)* |
| `MiniStackAuthProxy` | cloudforge-ministack | Local OIDC proxy *(deferred — code retained)* |
| `MiniStackCli` | cloudforge-ministack | Non-interactive deploy/verify/delete |
| `InteractiveDeployer` | cfc-testing | Interactive flow; option 6 calls `MiniStackDeploymentPipeline` |

---

## Related Documentation

- [CloudForge Manager](../../cloudforge-manager/README.md) — operations panel (list MiniStack/AWS instances)
- [Interactive Deployer](../guides/INTERACTIVE_DEPLOYER.md) — full configuration options
- [Docker Local Dev](../guides/DOCKER_LOCAL_DEV_README.md) — broader docker-compose environment
- [cfc-testing README](../../cfc-testing/README.md) — testing platform overview

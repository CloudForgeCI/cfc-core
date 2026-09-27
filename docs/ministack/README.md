# MiniStack Local Deployment

Deploy CloudForge-generated CloudFormation to [MiniStack](https://github.com/ministackorg/ministack), an MIT-licensed, open-source AWS emulator, without an AWS account.

**Quick start from repository root:** [Local Emulator Quick Start](../guides/LOCAL_EMULATOR_QUICK_START.md)

MiniStack support lives in the **`cloudforge-ministack`** module; the `cfc-testing` Interactive Deployer drives it. Canonical AWS templates stay unchanged in the libraries; local adaptations are applied downstream and recorded in an adaptation report.

MiniStack is **not** LocalStack. CloudForge runs the open-source `ministackorg/ministack` image (pinned in `LocalEmulatorDefaults.MINISTACK_IMAGE`) as the `cfc-ministack` container. MiniStack and LocalStack both bind gateway port **4566**, so run one emulator at a time.

**Authentication:** MiniStack cannot execute ALB `authenticate-oidc` / `authenticate-cognito` actions, so the adapter strips them. The optional local auth proxy and mock OIDC provider are off by default (`MINISTACK_AUTH_AUTOSTART=false`). Use `authMode: none` on MiniStack, or use [LocalStack](../localstack/README.md) with `application-oidc` to test sign-in locally. Canonical AWS templates still include auth when configured.

---

## Available vs not available

Canonical CloudForge templates are **unchanged** for AWS. MiniStack deploys an **adapted** copy (see `cdk.out/<stack>.ministack-adaptations.json`). Use this table to set expectations for option **6** deployments.

### Available

| Capability | Notes |
|------------|--------|
| CloudFormation create / update / delete / no-op | Via `MiniStackDeployer` + change sets |
| VPC, subnets, IGW, security groups | SG **rules are recorded** and queryable (`describe-security-groups`); not full packet-filter enforcement |
| ECS Fargate → Docker containers | App reachable on host port; logs via CloudWatch Logs APIs |
| ALB + listeners (control plane) | Inventory via ELBv2 APIs; local entry `MiniStackLocalUrl` (`/_alb/...`) |
| Route53 hosted zones + records | Emulator DNS only; assert via API, not OS/public resolver |
| ACM certificates + HTTPS listener resources | Presence in CFN/ACM; local TLS termination differs from AWS |
| IAM roles/policies in template | Accepted for stack create; not full IAM evaluation |
| Incremental add/remove domain / TLS | Same stack update path |
| EFS → host bind mount | Paths under `.ministack-volumes/`; `MiniStackHostVolume*` outputs |
| CloudForge Manager operations against MiniStack | Inventory, health, delete, history |
| Automated native tests | `mvn -pl cloudforge-ministack test -P ministack`; see [Verification](VERIFICATION.md#automated-tests-maven) |

### Not available or adapted away

| Capability | MiniStack behavior |
|------------|-------------------|
| ALB → ECS **forward** / target health routing | Adapter rewrites to **HTTP redirect → `localhost:<appPort>`** |
| ALB Cognito / OIDC authenticate actions | Stripped from adapted template; auth proxy / mock OIDC off by default |
| Security group **packet filtering** | Rules stored; do not treat allow/deny traffic tests as real |
| Public / OS DNS for FQDNs | Use `MiniStackApplicationUrl` / `MiniStackLocalUrl`; optional `/etc/hosts` |
| Browser HTTPS / ACM validation flow | Cert + HTTPS listener resources exist; use output HTTP URLs for smoke tests |
| EFS NFS mounts | Replaced with host bind mounts |
| Application Auto Scaling | Removed by adapter |
| RDS, WAF, Config, CloudTrail, GuardDuty, Backup | Not supported; preflight blocks templates that contain these types |
| AWS Service Catalog | Not emulated; publish and test Service Catalog products on AWS |

Per-resource canonical vs adapted vs deployed detail: **[Resource verification matrix](RESOURCE_VERIFICATION.md)**.

---

## Documentation

| Guide | Description |
|-------|-------------|
| **[Setup](SETUP.md)** | Prerequisites, build, start MiniStack |
| **[Deployment](DEPLOYMENT.md)** | Interactive Deployer, MiniStackCli, base Jenkins walkthrough |
| **[Jenkins on MiniStack](JENKINS.md)** | Jenkins Fargate setup, AWS CLI against MiniStack, CloudWatch logs, initial admin password |
| **[Verification](VERIFICATION.md)** | Confirm what deployed locally; includes [local DNS vs API](VERIFICATION.md#local-dns-vs-api-verification) |
| **[Resource verification matrix](RESOURCE_VERIFICATION.md)** | Per AWS resource: canonical vs adapted vs deployed, how to verify, local fidelity |
| **[Advanced Configuration](ADVANCED.md)** | Stack outputs, template adaptations, auth proxy, incremental updates, environment variables |
| **[Deployable applications](DEPLOYABLE_APPS.md)** | Supported vs blocked applications; see also the [full catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md) |
| **[Troubleshooting](TROUBLESHOOTING.md)** | Common failures and debugging steps |

---

## Quick Start

```bash
# Repository root: build once
mvn clean install -DskipTests
mvn -f cfc-testing package -Dmaven.test.skip=true

# Start MiniStack: choose the ministack platform, then the `start` action
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform

# Configure and deploy; choose option 6 (Deploy to MiniStack)
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
```

`AWS_ENDPOINT_URL` defaults to `http://localhost:4566`. MiniStack is always menu option **6**; no flag or environment variable is needed to enable it. `cdk synth` / `cdk deploy` never show the menu; run `InteractiveDeployer` directly for local targets.

### Run CloudForge Manager locally against MiniStack

CloudForge Manager lives in its own repository. With a `cloudforge-manager` checkout at the repository root, you can run the Manager panel on your host against MiniStack without deploying it into the emulator:

```bash
# Build the Angular panel and package the Manager server
mvn -f cloudforge-manager/pom.xml -Pui package -DskipTests

# Start MiniStack from the platform menu (see Quick Start), then:
mvn -f cloudforge-manager/pom.xml spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--cfc.manager.target=ministack"
```

Open `http://127.0.0.1:1958`, complete the first-run local-admin setup, and choose **MiniStack** in the target selector.

### Deploy CloudForge Manager into MiniStack

To run CloudForge Manager as a Fargate application inside the emulator, use the standard `cfc-testing → CDK synth → MiniStack adapter` path. The Manager deployment extension (`CloudForgeManagerDeploymentExtension`, from `cloudforge-manager-deployment`) resolves the container image before deploy: a local build when a `cloudforge-manager` checkout is present, otherwise the published image.

```bash
# Repository root
mvn -f cfc-testing package -Dmaven.test.skip=true

cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/CloudForgeManager-Fresh.json 6
```

Use a context without `provisionDatabase: true`; MiniStack preflight blocks RDS. Option **6** synthesizes the canonical CDK template for `applicationId: cloudforge-manager`, adapts it for MiniStack, and deploys it. Option **8** is the LocalStack equivalent.

### Deploy preflight (option 6)

Before adapt/deploy, option **6** runs a preflight that blocks stacks MiniStack cannot create, so you get a clear message instead of a CloudFormation rollback. It blocks:

- applications that require RDS (`requiresDatabase`) or contexts with `provisionDatabase: true`
- canonical templates containing unsupported types (`AWS::RDS::*`, `AWS::WAFv2::*`, `AWS::Config::*`, `AWS::CloudTrail::*`, `AWS::GuardDuty::*`, `AWS::Backup::*`)
- topologies other than `application-service` / `jenkins-service`
- host-port conflicts with another running MiniStack stack

| Variable | Default | Purpose |
|----------|---------|---------|
| `MINISTACK_PREFLIGHT` | `enforce` | `enforce` blocks unsupported deploys; `warn` prints warnings; `off` skips checks |

Use Interactive Deployer option **8** (LocalStack) or deploy to AWS for blocked applications. See [Deployable applications](DEPLOYABLE_APPS.md).

---

## Architecture

```text
DeploymentConfig
  → CDK synthesis (canonical AWS template)
  → MiniStackDeploymentPipeline (cloudforge-ministack)
  → MiniStackTemplateAdapter (audited local copy)
  → MiniStackDeployer (CloudFormation create/update via AWS SDK)
  → MiniStack emulator
```

**Design principles**

- Canonical templates are never weakened in `cloudforge-api` / `cloudforge-core`.
- Cross-target local deploy **contracts** live in `cloudforge-core` (`com.cloudforge.core.local`); MiniStack **implementations** live in `cloudforge-ministack`.
- MiniStack-specific changes happen only in `MiniStackTemplateAdapter`, with an adaptation report written beside the local template.
- Incremental deployments use CloudFormation change sets (create → update → no-op).
- No AWS CLI or AWS credentials are required for local deployment (the AWS CLI is optional for verification).

---

## Source Layout

| Type | Module | Role |
|------|--------|------|
| `DeploymentTarget`, `TemplateAdapter`, `LocalDeployer`, `LocalDeploymentPipeline` | cloudforge-core | Cross-module local deploy contracts |
| `CloudFormationTemplateDiff` | cloudforge-core | Semantic CFN template diff (canonical parity tests) |
| `LocalEmulatorDefaults` | cloudforge-core | Container names, images, ports, env var keys |
| `LocalEmulatorSpec` | cloudforge-core | Per-target metadata (`ministack()`, `localstack()`) |
| `LocalEmulatorRuntimes` | cloudforge-core | Runtime catalog (`forTarget(MINISTACK)`) |
| `EmulatorLifecycle` | cloudforge-core | Start/stop/restart/status orchestration |
| `MiniStackDeploymentPipeline` | cloudforge-ministack | MiniStack wiring for `LocalDeploymentPipeline` |
| `MiniStackTemplateAdapter` | cloudforge-ministack | MiniStack `TemplateAdapter` implementation |
| `MiniStackDeployer` | cloudforge-ministack | MiniStack `LocalDeployer` implementation |
| `MiniStackDeployPreflight` | cloudforge-ministack | Pre-deploy checks for option 6 |
| `MiniStackPlatformRuntimeProvider` | cloudforge-ministack | `--platform` menu actions (start, stop, restart, status, reconcile_edge) |
| `MiniStackEmulatorRuntime` | cloudforge-ministack | Docker create arguments (extends `AbstractLocalEmulatorRuntime`) |
| `MiniStackLocalRuntime` | cloudforge-ministack | Mock OIDC + auth proxy lifecycle (off by default) |
| `MiniStackAuthProxy` | cloudforge-ministack | Local OIDC proxy (off by default) |
| `MiniStackCli` | cloudforge-ministack | Non-interactive deploy/verify/delete |
| `InteractiveDeployer` | cfc-testing | Interactive flow; options 4, 6, and 7 use the MiniStack pipeline |

---

## Related Documentation

- [cloudforge-cli](https://github.com/CloudForgeCI/cloudforge-cli): deploy and manage the emulator from the command line
- [Docker Local Dev](../guides/DOCKER_LOCAL_DEV_README.md): broader docker-compose environment
- [cfc-testing README](https://github.com/CloudForgeCI/cfc-core/blob/develop/cfc-testing/README.md): testing platform overview
- [LocalStack](../localstack/README.md): the other supported local emulator

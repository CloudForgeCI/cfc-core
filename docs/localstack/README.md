# LocalStack Local Deployment

Deploy CloudForge-generated CloudFormation to [LocalStack](https://localstack.cloud/) without an AWS account. A trial or paid `LOCALSTACK_AUTH_TOKEN` is required to start the emulator container.

LocalStack support lives in **`cloudforge-localstack`**. Canonical AWS templates stay unchanged in the libraries; local deployment adaptations are applied downstream and recorded in an adaptation report (`cdk.out/<stackName>.localstack-adaptations.json`).

CloudForge runs `localstack/localstack:latest` as the `cfc-localstack` container, with emulator state under `.localstack-volumes/` (override with `CFC_LOCALSTACK_VOLUME_DIR`). MiniStack and LocalStack both bind gateway port **4566**. Start one emulator at a time from the Interactive Deployer's **platform lifecycle** menu; starting either target stops the other.

---

## Quick Start

**Full path from repository root:** [Local Emulator Quick Start](../guides/LOCAL_EMULATOR_QUICK_START.md) · [Local hostnames (`*.cloudforge.localhost`)](../guides/LOCAL_EMULATOR_HOSTS.md)

```bash
mvn clean install -DskipTests
mvn -f cfc-testing package -Dmaven.test.skip=true
export LOCALSTACK_AUTH_TOKEN=...

# Start LocalStack: choose the localstack platform, then the `start` action
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
curl -s http://localhost:4566/_localstack/health

# Configure and deploy; choose option 8 (Deploy to LocalStack)
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
```

Option **8** synthesizes the canonical template, runs [preflight](#deploy-preflight-option-8), adapts the template, and deploys the `<stackName>-localstack` stack. It is not a raw `cdk deploy`, which targets AWS. Pass `--context <file>` and a trailing option number to skip prompts, for example `--context deployment-contexts/Jenkins-Stack-LocalStack.json 8`.

### Run CloudForge Manager locally against LocalStack

CloudForge Manager lives in its own repository. With a `cloudforge-manager` checkout at the repository root, you can run the Manager panel on your host against LocalStack without deploying it into the emulator:

```bash
# Build the Angular panel and package the Manager server
mvn -f cloudforge-manager/pom.xml -Pui package -DskipTests

# Start LocalStack from the platform menu (see Quick Start), then:
mvn -f cloudforge-manager/pom.xml spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--cfc.manager.target=localstack"
```

Open `http://127.0.0.1:1958`, complete the first-run local-admin setup, and choose **LocalStack** in the target selector (or set `CFC_MANAGER_TARGET=localstack`).

### Deploy CloudForge Manager into LocalStack

To run CloudForge Manager as a Fargate application inside the emulator, use the same `cfc-testing → CDK synth → LocalStack adapter` path as every other application. The Manager deployment extension (`CloudForgeManagerDeploymentExtension`, from `cloudforge-manager-deployment`) resolves the container image (a local build when a `cloudforge-manager` checkout is present, otherwise the published image), reconciles the emulator edge, and waits for Manager health at `http://manager.cloudforge.localhost`.

```bash
# Repository root
mvn -f cfc-testing package -Dmaven.test.skip=true

cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/CloudForgeManager-Dev.json 8
```

---

## Resource browser (StackPort)

There is no AWS Console for LocalStack. [StackPort](https://github.com/DaviReisVieira/stackport) is an optional third-party Docker image (`davireis/stackport`) that reads `AWS_ENDPOINT_URL` and serves a web UI on port **8888**.

The platform `start` action launches `cfc-localstack-stackport` on the same Docker network as LocalStack (`cfc-network`), together with the shared nginx emulator edge. Set `CFC_EMULATOR_COMPANIONS=false` (or `CFC_STACKPORT_AUTOSTART=false` / `CFC_EDGE_AUTOSTART=false`) to skip them. StackPort reaches LocalStack at `http://cfc-localstack:4566`, which resolves **only inside the Docker network**. On your host (browser, `curl`, AWS CLI, Interactive Deployer), use **`http://localhost:4566`**.

```bash
curl -s http://localhost:8888/api/endpoints
```

Use the platform menu (`status`, `restart`, `reconcile_edge`) to manage the emulator and its companions. Override StackPort's endpoint with `CFC_STACKPORT_AWS_ENDPOINT_URL` or `STACKPORT_ENDPOINTS` when needed.

---

## Module

| Piece | Location |
|-------|----------|
| Adapter, deployer, pipeline | `cloudforge-localstack` |
| Emulator, StackPort, edge lifecycle | `cloudforge-localstack` via `PlatformRuntimeProvider` |
| Shared contracts | `cloudforge-core` (`local.*`, `StackPortRuntimes`) |

`LocalStackTemplateAdapter` changes the canonical template only where LocalStack requires it:

- strips ALB `authenticate-oidc` / `authenticate-cognito` actions (use `application-oidc` or `authMode: none`)
- pins the ECS host port to the container port and redirects ALB listeners to `http://localhost:<appPort>`; when the app port is 80, 4566, or 8888 (held by the edge, gateway, or StackPort) the ALB forward is kept instead
- replaces EFS with host bind mounts under `.localstack-volumes/` and removes `AWS::Backup::*`, unless the probed tier keeps them (Ultimate)
- keeps Application Auto Scaling
- rewrites database endpoints, OIDC URLs, and path-style URLs so tasks and browsers can reach them locally

Stack names use the `-localstack` suffix. Each change is recorded in the adaptation report.

**Deployable applications:** [LocalStack app catalog](DEPLOYABLE_APPS.md) and the [full emulator catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md).

### Deploy preflight (option 8)

Before adapt/deploy, option **8** probes LocalStack health and tier capabilities (ECS, ELBv2, RDS, EC2, Auto Scaling, EFS, Backup) and checks host-port conflicts with other LocalStack stacks.

| Variable | Default | Purpose |
|----------|---------|---------|
| `LOCALSTACK_PREFLIGHT` | `enforce` | `enforce` blocks missing capabilities; `warn` prints warnings; `off` skips |
| `CFC_LOCALSTACK_SKIP_PREFLIGHT` | `false` | Set `true` to skip (same as `LOCALSTACK_PREFLIGHT=off`) |
| `LOCALSTACK_TIER_PROFILE` | probed | Override the probed tier (for example `ultimate` to keep native EFS) |
| `LOCALSTACK_CAPABILITIES` | probed | Declare capabilities (for example `rds`) when the health response is sparse |

### Other settings

| Variable | Default | Purpose |
|----------|---------|---------|
| `LOCALSTACK_AUTH_TOKEN` | none (required) | LocalStack auth token passed to the container at start |
| `LOCALSTACK_ENDPOINT` / `AWS_ENDPOINT_URL` | `http://localhost:4566` | Gateway used by `LocalStackDeployer` (`LOCALSTACK_ENDPOINT` wins) |
| `CFC_LOCALSTACK_VOLUME_DIR` | `.localstack-volumes` | Host directory for emulator state |
| `CFC_LOCALSTACK_REPLACE_SAME_APP` | enabled | Set `false` or `0` to keep earlier LocalStack stacks for the same `applicationId` |

---

## Opt-in tests

```bash
mvn -pl cloudforge-localstack -P localstack test
mvn -f cfc-testing/pom.xml -P localstack test
```

Default builds exclude `@Tag("localstack")` tests, so CI needs no token. Unit tests for the adapter and capability contracts run without a running LocalStack.

---

## Troubleshooting

### Path-style ELB URL loads but has no styling

LocalStack path-style URLs look like:

`http://localhost.localstack.cloud:4566/_aws/elb/cfc-xxxxx/login`

Jenkins serves static assets at `/static/...`. Without a `--prefix`, the browser requests `http://localhost.localstack.cloud:4566/static/...` (404) instead of under `/_aws/elb/cfc-xxxxx/static/...`.

The adapter adds `--prefix=/_aws/elb/{name}` to `JENKINS_OPTS` when it adapts the template. Redeploy to pick up adapter changes, or use `LocalStackApplicationUrl` (`http://localhost:8080/` for Jenkins) instead of the path-style URL.

### Hostname ELB URL loads but has no styling (Chrome)

Example: `http://cfc-xxxxx.elb.localhost.localstack.cloud:4566/login`

Chrome Local Network Access rules can block stylesheets and scripts (`403`) while `curl` returns `200`. Prefer `LocalStackApplicationUrl` (the direct ECS port), Safari, or Chrome site settings → allow **Local network access**.

For day-to-day UI testing, `LocalStackApplicationUrl` is the supported entry point. `LocalStackElbHostnameUrl` is for ALB/routing checks.

# LocalStack Local Deployment

Deploy CloudForge-generated CloudFormation to [LocalStack](https://localstack.cloud/) without an AWS account. A trial or paid `LOCALSTACK_AUTH_TOKEN` is required to start the emulator container.

LocalStack support lives in **`cloudforge-localstack`**. Canonical AWS templates stay unchanged in the libraries; local deployment adaptations are applied and audited downstream.

MiniStack and LocalStack both bind gateway port **4566**. Start one emulator at a time from the Interactive Deployer's **platform lifecycle** menu; starting either target stops a conflicting emulator.

---

## Quick Start

**Full path from repository root:** [Local Emulator Quick Start](../guides/LOCAL_EMULATOR_QUICK_START.md) · [Local hostnames (`*.cloudforge.localhost`)](../guides/LOCAL_EMULATOR_HOSTS.md)

```bash
mvn clean install -DskipTests
mvn -f cfc-testing package -Dmaven.test.skip=true
export LOCALSTACK_AUTH_TOKEN=...
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
curl -s http://localhost:4566/_localstack/health
```

Deploy from `cfc-testing` — Interactive Deployer option **8**, or `LocalDeploymentShell` / `CloudForgeDeployment` after `CFC_DEPLOYING=1 cdk synth`. CloudForge Manager uses target **LocalStack** (`?target=localstack` or `CFC_MANAGER_TARGET=localstack`).

### CloudForge Manager in five minutes (local panel)

This runs the CloudForge Manager panel on your laptop against LocalStack; it does **not**
deploy CloudForge Manager as a Fargate application first. After the initial Maven and npm
dependency download, the sequence is intended to take about five minutes.

```bash
# Repository root — build the Angular panel and package the CloudForge Manager server.
mvn -pl cloudforge-manager -am -Pui package -DskipTests

# Start exactly one emulator from the platform menu. LocalStack requires its token.
export LOCALSTACK_AUTH_TOKEN=...
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
cd ..

# Point the locally running CloudForge Manager at LocalStack and start it on :1958.
mvn -pl cloudforge-manager spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--cfc.manager.target=localstack"
```

Open `http://127.0.0.1:1958`, complete the first-run local-admin setup, and
choose **LocalStack** in the target selector. To deploy CloudForge Manager as
an application inside LocalStack later, use `cfc-testing` Interactive Deployer
option **8** — option **6** is MiniStack only.

### Deploy CloudForge Manager *into* LocalStack

The laptop panel above inspects LocalStack from your host. To deploy CloudForge Manager as
a CloudForge Fargate application inside the emulator, use the same
`cfc-testing → CDK synth → LocalStack adapter` path as every other application.
The Manager-owned deployment extension builds the custom image, reconciles the edge,
and validates canonical Manager health. Select the CloudForge Manager deployment context
and option **8**:

```bash
# Repository root
mvn -f cfc-testing package -Dmaven.test.skip=true

cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/CloudForgeManager-Dev.json 8
```

Option **8** synthesizes the canonical CDK template for
`applicationId: cloudforge-manager`, adapts it for LocalStack, and deploys it.
It is not a raw `cdk deploy`; raw CDK deploy targets real AWS.

---

## Resource browser (StackPort)

There is no AWS Console for LocalStack. [StackPort](https://github.com/DaviReisVieira/stackport) is an optional third-party Docker image (`davireis/stackport`) that reads `AWS_ENDPOINT_URL` and serves a web UI on port **8888**.

CloudForge starts `cfc-localstack-stackport` on the same Docker network as the running LocalStack container (typically `cfc-network`, or the compose project network if LocalStack was started via `docker-compose`). StackPort talks to LocalStack using the Docker DNS name `http://cfc-localstack:4566` **from inside the network only**. On your host (browser, `curl`, AWS CLI, Interactive Deployer), use **`http://localhost:4566`**.

```bash
# LocalStack must already be running. Choose `reconcile_edge` from the platform menu
# when a manual edge refresh is needed.
java -cp "cfc-testing/target/classes:cfc-testing/target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
curl -s http://localhost:8888/api/endpoints
```

StackPort is started with the platform. Use the platform menu for target status, restart,
or edge reconciliation; it owns the target's companion lifecycle.

Override the in-container endpoint with `CFC_STACKPORT_AWS_ENDPOINT_URL` or `STACKPORT_ENDPOINTS` when needed.

---

## Module

| Piece | Location |
|-------|----------|
| Adapter, deployer, pipeline | `cloudforge-localstack` |
| Emulator, StackPort, edge lifecycle | `cloudforge-localstack` via `PlatformRuntimeProvider` |
| Shared contracts | `cloudforge-core` (`local.*`, `StackPortRuntimes`) |

The adapter keeps ALB→ECS forward, strips ALB `authenticate-*` on Base tier, maps EFS to bind mounts under `.localstack-volumes/`, and strips `AWS::Backup::*` unless Ultimate-tier capabilities are detected. Stack names use the `-localstack` suffix.

**Deployable applications:** [LocalStack app catalog](DEPLOYABLE_APPS.md) and [full emulator catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md) — all 37+ plugins; RDS/CMS apps require option **8** and Base-tier RDS capability.

### Deploy preflight (option 8)

Before adapt/deploy, option **8** probes LocalStack health and tier capabilities (ECS, ELBV2, RDS, EC2, etc.).

| Variable | Default | Purpose |
|----------|---------|---------|
| `LOCALSTACK_PREFLIGHT` | `enforce` | `enforce` blocks missing capabilities; `warn` prints warnings; `off` skips |
| `CFC_LOCALSTACK_SKIP_PREFLIGHT` | `false` | Set `true` to skip (same as `LOCALSTACK_PREFLIGHT=off`) |

---

## Opt-in tests

```bash
mvn -pl cloudforge-localstack -P localstack test
mvn -pl cfc-testing -P localstack test
```

Default PR CI excludes `@Tag("localstack")` tests (no token required). Unit tests for adapter and capability contracts run without a running LocalStack.

---

## Troubleshooting

### Path-style ELB URL loads but has no styling

LocalStack path-style URLs look like:

`http://localhost.localstack.cloud:4566/_aws/elb/cfc-xxxxx/login`

Jenkins serves static assets at `/static/...`. Without a `--prefix`, the browser requests `http://localhost.localstack.cloud:4566/static/...` (404) instead of under `/_aws/elb/cfc-xxxxx/static/...`.

The adapter injects `JENKINS_OPTS --prefix=/_aws/elb/{name}` on adapt+deploy. Redeploy after adapter changes, or use the direct ECS port from `docker ps` (for example `http://localhost:27994/login`).

### Hostname ELB URL loads but has no styling (Chrome)

Example: `http://cfc-xxxxx.elb.localhost.localstack.cloud:4566/login`

Chrome Local Network Access rules can block stylesheets and scripts (`403`) while `curl` returns `200`. Prefer the direct ECS port from `docker ps`, Safari, or Chrome site settings → allow **Local network access**.

For day-to-day UI testing, the direct ECS port is the supported application entry point. ELB hostname URLs are for ALB/routing fidelity checks.

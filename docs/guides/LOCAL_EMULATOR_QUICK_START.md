# Local Emulator Quick Start

Use the Interactive Deployer for every local platform action and application deployment.
MiniStack and LocalStack share gateway port `4566`, so run only one at a time.

## Prerequisites

- Java 25, Maven 3.9+, and Docker.
- `LOCALSTACK_AUTH_TOKEN` only when using LocalStack.
- Optional friendly hostnames: `./scripts/setup-cloudforge-local-hosts.sh`.

## Build

```bash
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core
mvn clean install                  # tests are skipped by default
mvn -f cfc-testing/pom.xml package -Dmaven.test.skip=true
```

## Start a platform

The lifecycle implementation lives in `cloudforge-ministack` and `cloudforge-localstack`;
`cfc-testing` discovers and invokes it through `PlatformRuntimeProvider`.

```bash
# Required before selecting LocalStack in the menu.
export LOCALSTACK_AUTH_TOKEN=...

cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
```

Choose `ministack` or `localstack`, then `start`. This starts the emulator and its companions
(the StackPort resource browser on port 8888 and the nginx emulator edge on port 80) and
reconciles host routes. Use the same menu
for `stop`, `restart`, `status`, or `reconcile_edge`.

Verify the selected platform:

```bash
curl -s http://localhost:4566/_localstack/health
# or
curl -s http://localhost:4566/_ministack/health
```

## Deploy an application

```bash
cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
```

Answer the prompts (or pass `--context <file>`), then choose option **6** for MiniStack or
**8** for LocalStack. Both synthesize the canonical template, adapt it for the target, run a
preflight check, and deploy it. The stack is named `<stackName>-ministack` or
`<stackName>-localstack`.

## Deploy CloudForge Manager

CloudForge Manager is provided by the `cloudforge-manager-deployment` artifact, which
`cfc-testing` depends on. Select **CloudForge Manager** from the application list (or use
`--context deployment-contexts/CloudForgeManager-Fresh.json`) and choose option 6 or 8. For
MiniStack and LocalStack, its deployment extension:

1. uses the `cloudforgeci/cloudforge-manager` image, building it from a sibling
   `cloudforge-manager` source checkout when one exists, otherwise pulling the published
   image from Docker Hub;
2. deploys through the standard local target path;
3. reconciles the emulator edge; and
4. waits for `http://manager.cloudforge.localhost/api/v1/health`.

Contexts that set `provisionDatabase: true` (for example `CloudForgeManager-Dev.json`) deploy
to LocalStack only; MiniStack preflight blocks RDS. After deployment, open
`http://manager.cloudforge.localhost/`.

CloudForge Manager itself is developed in a separate repository.

## Troubleshooting

| Symptom | Resolution |
|---|---|
| Docker daemon unavailable | Start Docker Desktop, then select platform `start` again. |
| Port `4566` busy | Use the platform menu to stop the other emulator. |
| LocalStack refuses to start | Export a valid `LOCALSTACK_AUTH_TOKEN`. |
| Application URL missing | Select `reconcile_edge` from the platform menu. |
| Manager health check fails | Confirm option 6 or 8 was used, run `reconcile_edge`, and inspect the Manager ECS task logs. |

See [MiniStack](../ministack/README.md), [LocalStack](../localstack/README.md), and
[Interactive Deployer](INTERACTIVE_DEPLOYER.md) for target-specific detail.

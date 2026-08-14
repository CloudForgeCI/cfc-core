# Local Emulator Quick Start

Use the Interactive Deployer for every local platform action and application deployment.
MiniStack and LocalStack share gateway port `4566`, so run only one at a time.

## Prerequisites

- Java 21+, Maven 3.9+, Docker, and the AWS CDK CLI.
- `LOCALSTACK_AUTH_TOKEN` only when using LocalStack.
- Optional friendly hostnames: `./scripts/setup-cloudforge-local-hosts.sh`.

## Build

```bash
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core
mvn clean install -DskipTests
mvn -f cfc-testing package -Dmaven.test.skip=true
```

## Start a platform

The target artifacts own lifecycle implementation. `cfc-testing` only discovers and
invokes them through `PlatformRuntimeProvider`.

```bash
# Required before selecting LocalStack in the menu.
export LOCALSTACK_AUTH_TOKEN=...

cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
```

Choose MiniStack or LocalStack, then `start`. The platform starts its emulator and
companions (StackPort and emulator edge) and reconciles host routes. Use the same menu
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

Choose option 6** for MiniStack or option **8** for LocalStack. These paths synthesize
the canonical template, apply the selected target adapter, and deploy it locally.

## Deploy CloudForge Manager

CloudForge Manager is discovered from `cloudforge-manager-deployment`, not from
`cloudforge-api`. Select **CloudForge Manager** from the application list and choose the
same target option. Its deployment extension does the LocalStack-only work:

1. builds `cloudforgeci/cloudforge-manager:latest`;
2. deploys through the generic LocalStack path;
3. reconciles the emulator edge; and
4. verifies `http://manager.cloudforge.localhost/api/v1/health`.

The default Manager preset uses embedded H2 and no read replica. Choose RDS explicitly
when persistent managed storage is wanted; replica count is an explicit advanced setting.

For a host-run Manager during development:

```bash
cd ..
mvn -pl cloudforge-manager -am -Pui spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--cfc.manager.target=localstack" # or ministack
```

Open `http://127.0.0.1:1958` for the host-run service or
`http://manager.cloudforge.localhost/` after an in-emulator deployment.

## Troubleshooting

| Symptom | Resolution |
|---|---|
| Docker daemon unavailable | Start Docker Desktop, then select platform `start` again. |
| Port `4566` busy | Use the platform menu to stop the other emulator. |
| LocalStack refuses to start | Export a valid `LOCALSTACK_AUTH_TOKEN`. |
| Application URL missing | Select `reconcile_edge` from the platform menu. |
| Manager health check fails | Confirm option 8 was used and inspect the Manager ECS task logs. |

See [MiniStack](../ministack/README.md), [LocalStack](../localstack/README.md), and
[Interactive Deployer](INTERACTIVE_DEPLOYER.md) for target-specific detail.

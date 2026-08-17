# CloudForge Manager

Operations Panel for CloudForge stacks on MiniStack and AWS. Deployed through
Interactive Deployer like Jenkins or Mattermost (`applicationId: cloudforge-manager`).

## Spec

| Field | Value |
|-------|-------|
| Application ID | `cloudforge-manager` |
| Category | `operations` |
| Runtime | Fargate (recommended) or EC2 |
| Port | `1958` |
| Health | `/api/v1/health` |
| Image | `cloudforgeci/cloudforge-manager:latest` |
| Database | Optional PostgreSQL (`DatabaseSpec`) |

Class: `com.cloudforgeci.manager.deployment.CloudForgeManagerApplicationSpec` (module `cloudforge-manager-deployment`).

## CloudFormation history

CloudForge Manager reads the authoritative stack timeline directly from CloudFormation using
its CDK-provisioned task role. Application deployments never call Manager back.

```bash
export CFC_MANAGER_SETUP_TOKEN=...     # optional; required for remote first-boot setup
export CFC_MANAGER_BIND=127.0.0.1      # laptop default; use 0.0.0.0 in Docker/Fargate
```

Application deployments do not depend on Manager availability. The local action journal uses H2 under
`~/.cloudforge/manager` (or `/var/lib/cloudforge-manager` in containers / `CFC_MANAGER_DATA_DIR`).

## Build the container image

Unlike Jenkins (`jenkins/jenkins:lts` from Docker Hub — no in-repo build), CloudForge Manager
uses a custom image. The Dockerfile lives in `cloudforge-manager`'s own repo (checked out as a
local sibling here for dev convenience) and builds against published Maven artifacts rather than
this repo's source. Build from repo root, same as any other app in this compose file:

```bash
docker compose build cloudforge-manager
```

`CloudForgeManagerDeploymentExtension` builds this image locally whenever the `cloudforge-manager`
sibling checkout is present, or pulls the latest prebuilt image from GHCR
(`ghcr.io/cloudforgeci/cloudforge-manager:latest`, published by that repo's own CI on every push
to `develop`) whenever it isn't — CI included. Nothing to build or push manually in the normal
case.

## Deploy

```bash
cp docs/examples/applications/cloudforge-manager-dev.json cfc-testing/deployment-context.json
# or: cloudforge-manager-production.json (includes provisionDatabase)

# Interactive Deployer — option 2 (AWS) | 6 (MiniStack) | 8 (LocalStack)
cd cfc-testing && mvn exec:java -Dexec.mainClass=com.cloudforgeci.samples.app.InteractiveDeployer
```

### Optional PostgreSQL

Set `provisionDatabase: true` (see production example). CloudForge injects:

| Env var | Source |
|---------|--------|
| `CFC_MANAGER_DB_HOST` / `_PORT` / `_NAME` / `_USER` | Spec env mapping |
| `CFC_MANAGER_DATABASE_PASSWORD` | Secrets Manager via ContainerFactory |

Without a database, the panel still serves inventory and CloudFormation history APIs.

## Which auth mode?

Manager supports three `authMode` values, but not every target supports all three — pick based
on where you're deploying, not just what sounds most secure:

| Target | Supported | Recommended | Why |
|--------|-----------|-------------|-----|
| Laptop bootstrap (`spring-boot:run`) | `none` | `none` | No ALB, no Cognito to talk to — see [Laptop bootstrap](#laptop-bootstrap-no-deployer) below for the `local-oidc` profile if you want to exercise real Cognito login locally. |
| MiniStack / LocalStack | `none`, `application-oidc` | `none` | Neither emulator gives you a real ALB, so `alb-oidc` has nothing to attach to. |
| AWS | `none`, `application-oidc`, `alb-oidc` | `application-oidc` or `alb-oidc` | Full ALB + Cognito available; `none` works but skips auth entirely — fine for a quick smoke test, not for anything left running. |

**If you pick `alb-oidc` and then deploy to MiniStack or LocalStack anyway:** the local-emulator
template adapter silently strips the ALB's OIDC listener action (there's no real ALB to enforce
it against) — you'd get an unauthenticated deployment that doesn't match what you configured.
Interactive Deployer now warns about this at deploy time (compares your `authMode` against
[`CloudForgeManagerApplicationSpec.getSupportedAuthModes(target)`](../../../cloudforge-manager-deployment/src/main/java/com/cloudforgeci/manager/deployment/CloudForgeManagerApplicationSpec.java)
right before deploying), but the warning is non-blocking — read it rather than dismiss it.

`application-oidc` vs `alb-oidc` on AWS: `application-oidc` has Manager itself perform the
OAuth2 authorization-code + PKCE exchange server-side (works the same locally and on AWS, easier
to test against `local-oidc`); `alb-oidc` puts Cognito enforcement at the load balancer, so
unauthenticated requests never reach the container, at the cost of the ALB owning the redirect
(no equivalent local-emulator test path). Either is a reasonable AWS default; `application-oidc`
is easier to develop against since it's the only one you can also run against MiniStack/LocalStack.

## Laptop bootstrap (no Deployer)

```bash
mvn -pl cloudforge-manager -am spring-boot:run -Dspring-boot.run.profiles=local
```

`local` wires `application-local.properties` (port `1958`, LocalStack/MiniStack endpoint
`http://localhost:4566`, `auth-mode=none`) so no manual `export AWS_ENDPOINT_URL=...` is
needed. Add `,local-oidc` to exercise the Cognito login path against a configured pool.

See [`cloudforge-manager/README.md`](../../../cloudforge-manager/README.md).

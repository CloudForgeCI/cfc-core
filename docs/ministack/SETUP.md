# MiniStack Setup

Prerequisites and environment preparation for local MiniStack deployment through `cfc-testing`.

See also: [README](README.md) · [Deployment](DEPLOYMENT.md) · [Troubleshooting](TROUBLESHOOTING.md)

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| **Docker** | MiniStack runs ECS tasks as containers and needs `/var/run/docker.sock` |
| **Java 21+** | Required to build and run `cfc-testing` |
| **Maven** | Build libraries and the testing platform |
| **AWS CDK CLI** | `npm install -g aws-cdk` for synthesis |
| **Network** | Port `4566` (MiniStack gateway), `3001` (mock OIDC), `4180` (auth proxy, default) |
| **AWS CLI** | Optional — used for verification queries against MiniStack; see [Jenkins on MiniStack](JENKINS.md#4-configure-aws-cli-for-ministack) for profile setup |

---

## Build

From the repository root:

```bash
mvn clean install -DskipTests
cd cfc-testing
mvn package -Dmaven.test.skip=true
```

Rebuild after library changes. Stale JARs cause `NoSuchMethodError` during `cdk synth` — see [Troubleshooting](TROUBLESHOOTING.md).

### Automated tests

```bash
# Units (excludes live MiniStack tags)
mvn -pl cloudforge-core,cloudforge-api,cloudforge-ministack,cloudforge-manager -am test

# Live MiniStack integration — prefer compose already on :4566
export AWS_ENDPOINT_URL=http://localhost:4566
mvn -pl cloudforge-ministack test -P ministack
```

See [Verification — Automated tests](VERIFICATION.md#automated-tests-maven) for the fidelity boundary (SG rule inventory vs packet filtering, etc.).

---

## Start MiniStack

MiniStack is started via the CloudForge Maven plugin (not docker compose). It starts **empty** — there is no bootstrap script and no connection to a real AWS account.

```bash
# From repository root (after mvn install)
cd cfc-testing && java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform

# Verify health
curl -s http://localhost:4566/_ministack/health
```

### Important behavior

- **`PERSIST_STATE=0`** — each container restart begins with a clean emulator. CloudFormation stack metadata is not preserved across restarts.
- Incremental updates (domain, TLS, auth add/remove) work **within one running MiniStack instance**.
- After restarting MiniStack, redeploy from scratch or delete and recreate the stack.

### Mock OIDC (optional)

Simulates Cognito for local authentication testing. Started automatically by the Interactive Deployer when auth is enabled, or manually:

```bash
docker compose up -d mock-oidc
curl -s http://localhost:3001/health
```

---

## Run the Interactive Deployer

MiniStack deploy is always available as menu option **6** (no mode flag).

```bash
export AWS_ENDPOINT_URL=http://localhost:4566     # default MiniStack gateway
export AWS_DEFAULT_REGION=us-east-1               # default

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
# Choose 6 — Deploy to MiniStack
```

To open the same menus via the CDK CLI:

```bash
INTERACTIVE=true cdk synth
# then choose option 6
```

Plain `cdk synth` / `cdk deploy` without `INTERACTIVE=true` stay non-interactive (CDK defaults or saved `deployment-context.json`).

---

## Environment Variables (Quick Reference)

| Variable | Default | Purpose |
|----------|---------|---------|
| `AWS_ENDPOINT_URL` | `http://localhost:4566` | MiniStack gateway URL (same key as AWS CLI) |
| `AWS_DEFAULT_REGION` | `us-east-1` | Region passed to AWS SDK clients |
| `INTERACTIVE` | unset | When `true`, `cdk synth` enters Interactive Deployer |

Full list including auth proxy settings: [Advanced Configuration](ADVANCED.md#environment-variables).

---

## Next Steps

- [Local hostnames (`*.cloudforge.localhost`)](../guides/LOCAL_EMULATOR_HOSTS.md) — optional `/etc/hosts` for browser-friendly names
- [Deploy an application](DEPLOYMENT.md)
- [Jenkins on MiniStack — AWS CLI, logs, admin password](JENKINS.md)
- [Verify what deployed](VERIFICATION.md)

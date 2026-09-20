# MiniStack Setup

Prerequisites and environment preparation for local MiniStack deployment through `cfc-testing`.

See also: [README](README.md) · [Deployment](DEPLOYMENT.md) · [Troubleshooting](TROUBLESHOOTING.md)

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| **Docker** | MiniStack runs ECS tasks as containers and needs `/var/run/docker.sock` |
| **Java 21+** | Required to build and run `cfc-testing` |
| **Maven** | Builds the libraries and the testing platform |
| **AWS CDK CLI** | Optional; `npm install -g aws-cdk` if you want `cdk synth` / `cdk deploy` for AWS |
| **Network** | Port `4566` (MiniStack gateway), `8888` (StackPort), `80` (emulator edge); `3001` and `4180` only when the optional auth runtime is enabled |
| **AWS CLI** | Optional; used for verification queries against MiniStack. See [Jenkins on MiniStack](JENKINS.md#5-configure-aws-cli-for-ministack) for profile setup |

---

## Build

From the repository root:

```bash
mvn clean install -DskipTests
mvn -f cfc-testing package -Dmaven.test.skip=true
```

Rebuild after library changes. Stale JARs cause `NoSuchMethodError` during synthesis; see [Troubleshooting](TROUBLESHOOTING.md#stale-classes--nosuchmethoderror-during-synth).

### Automated tests

```bash
# Unit tests (live MiniStack tests are excluded by default)
mvn -pl cloudforge-core,cloudforge-api,cloudforge-ministack -am test

# Live MiniStack integration: reuses the emulator on AWS_ENDPOINT_URL when set,
# otherwise starts one with Testcontainers
export AWS_ENDPOINT_URL=http://localhost:4566
mvn -pl cloudforge-ministack test -P ministack
```

See [Verification: Automated tests](VERIFICATION.md#automated-tests-maven) for the fidelity boundary (SG rule inventory vs packet filtering, etc.).

---

## Start MiniStack

Start MiniStack from the Interactive Deployer platform lifecycle menu. Choose the `ministack` platform and the `start` action; `stop`, `restart`, `status`, and `reconcile_edge` are available from the same menu. Starting MiniStack stops a running LocalStack container, since both bind port 4566.

```bash
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform

# Verify health
curl -s http://localhost:4566/_ministack/health
```

The platform start also launches the StackPort resource browser (`http://localhost:8888`) and the shared nginx emulator edge unless `CFC_EMULATOR_COMPANIONS=false` (or `CFC_STACKPORT_AUTOSTART=false` / `CFC_EDGE_AUTOSTART=false`).

MiniStack starts **empty**: there is no bootstrap script and no connection to an AWS account.

### State is not persisted

- The container runs with **`PERSIST_STATE=0`**, so each restart begins with a clean emulator. CloudFormation stack metadata is not preserved across restarts.
- Incremental updates (domain, TLS) work **within one running MiniStack instance**.
- After restarting MiniStack, redeploy from scratch.

### Emulator container settings

| Variable | Default | Purpose |
|----------|---------|---------|
| `MINISTACK_REGION` | `us-east-1` | Region passed to the MiniStack container |
| `MINISTACK_LOG_LEVEL` | `WARNING` | MiniStack container log level |

---

## Run the Interactive Deployer

MiniStack deploy is always menu option **6**.

```bash
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
# Choose 6 (Deploy to MiniStack)
```

Pass `--context <file>` to use a saved deployment context and a trailing option number to skip the menu, for example `--context deployment-contexts/Jenkins-Stack.json 6`.

`cdk synth` / `cdk deploy` run the same class as the CDK app but never prompt: they synthesize `deployment-context.json` (or `CFC_CONTEXT_FILE`) for AWS only.

---

## Environment Variables (Quick Reference)

| Variable | Default | Purpose |
|----------|---------|---------|
| `AWS_ENDPOINT_URL` | `http://localhost:4566` | MiniStack gateway URL (same key as the AWS CLI) |
| `AWS_DEFAULT_REGION` | `us-east-1` | Region passed to AWS SDK clients |
| `CFC_CONTEXT_FILE` | `deployment-context.json` | Deployment context file read by the Interactive Deployer |
| `INTERACTIVE` | unset | When `true`, the Interactive Deployer ignores a saved context and runs the full questionnaire |

Full list including preflight and auth runtime settings: [Advanced Configuration](ADVANCED.md#environment-variables).

---

## Next Steps

- [Local hostnames (`*.cloudforge.localhost`)](../guides/LOCAL_EMULATOR_HOSTS.md): optional `/etc/hosts` entries for browser-friendly names
- [Deploy an application](DEPLOYMENT.md)
- [Jenkins on MiniStack: AWS CLI, logs, admin password](JENKINS.md)
- [Verify what deployed](VERIFICATION.md)

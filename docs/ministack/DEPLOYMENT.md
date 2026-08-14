# MiniStack Deployment

Deploy synthesized CloudFormation to MiniStack through the Interactive Deployer or MiniStackCli.

See also: [Setup](SETUP.md) · [Jenkins on MiniStack](JENKINS.md) · [Verification](VERIFICATION.md) · [Advanced Configuration](ADVANCED.md)

---

## Interactive Deployer (Recommended)

From `cfc-testing`:

```bash
mvn package -Dmaven.test.skip=true
export AWS_ENDPOINT_URL=http://localhost:4566   # optional; default for MiniStack clients

# Walks configuration prompts when deployment-context.json is missing,
# then shows the deploy menu (options 1–9 always include MiniStack)
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
```

Via CDK CLI (same menus when interactive is on):

```bash
INTERACTIVE=true cdk synth
```

**When prompts appear**

| Situation | Behavior |
|-----------|----------|
| No `deployment-context.json`, Interactive Deployer | Full configuration questionnaire |
| `INTERACTIVE=true cdk synth` | Full questionnaire (even if context exists, with `-i` / force reconfigure) |
| Saved `deployment-context.json`, Interactive Deployer | Skips questionnaire; shows deploy menu |
| Plain `cdk synth` without `INTERACTIVE` | No prompts — synthesizes CDK defaults (jenkins/fargate) |

To re-run all prompts with an existing context file:

```bash
rm -f deployment-context.json
# or
INTERACTIVE=true cdk synth
# use Deployer option 9 (Reconfigure) or --force on InteractiveDeployer
```

### Menu options (always shown)

| Option | Action |
|--------|--------|
| **1** | Synthesize only (canonical AWS template in `cdk.out/`) |
| **2** | Deploy to AWS (`cdk deploy`) |
| **3** | Redeploy to AWS (delete + deploy) |
| **4** | Dry-run: write MiniStack adapted template + report; print AWS changeset hint |
| **5** | Export template (YAML/JSON) |
| **6** | Deploy to MiniStack (adapt template, create/update stack, reconcile auth runtime) |
| **7** | Full MiniStack pipeline: cfn-guard validation → deploy → stack verification |
| **8** | Deploy to LocalStack (adapt template, create/update stack, reconcile auth runtime) |
| **9** | Reconfigure (fresh interactive setup) |
| **0** | Cancel |

### Typical first-time flow

1. Complete [Setup](SETUP.md) — build and start MiniStack.
2. Run Interactive Deployer and complete configuration (or copy an example `deployment-context.json`).
3. Choose **6** (Deploy to MiniStack).

Stack name in MiniStack is always `<stackName>-ministack`.

### Deployment artifacts

Each deploy writes to `cfc-testing/cdk.out/`:

| File | Description |
|------|-------------|
| `<stack>.template.json` | Canonical AWS template (unchanged) |
| `<stack>.ministack.template.json` | Adapted template deployed to MiniStack |
| `<stack>.ministack-adaptations.json` | Audit trail of every local change |

---

## MiniStackCli (Non-Interactive)

For scripts and CI (once test coverage is in place):

```bash
cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli \
  deploy <stack-name>-ministack cdk.out/<stack>.template.json

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli \
  verify <stack-name>-ministack

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli \
  delete <stack-name>-ministack
```

`deploy` adapts the canonical template, writes `.ministack.template.json` + adaptations report, then create/updates the stack.

---

## Base Jenkins on MiniStack (walkthrough)

Minimal Fargate Jenkins: no domain, no auth — fastest path to a running app URL.

### 1. Prerequisites

```bash
# Repo root — MiniStack up
cd cfc-testing && java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
curl -s http://localhost:4566/_ministack/health

# cfc-testing — build deployer
cd cfc-testing
mvn package -Dmaven.test.skip=true

# Fresh interactive run (optional — delete saved context to walk all prompts)
rm -f deployment-context.json

export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1
```

### 2. Configure and deploy

```bash
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
```

Complete prompts (Jenkins / Fargate / no domain is fine), then choose **6**.

If you already have `deployment-context.json`, the Deployer skips prompts and shows the menu — choose **6**.

### 3. Confirm deployment succeeded

The deployer prints change-set actions and stack outputs. You should see at minimum:

- `MiniStackLocalUrl` — ALB data-plane entry (`http://localhost:4566/_alb/<name>/`)
- `MiniStackApplicationUrl` — direct ECS port (`http://localhost:<port>`)

Auth outputs are **absent** for this configuration (no `MiniStackAuthenticatedUrl`).

### 4. Reach Jenkins

MiniStack ALB cannot forward to ECS; the adapter redirects listeners to the local ECS port. Use the application URL:

```bash
# From stack outputs (example — port varies per deploy)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:<port>/
open http://localhost:<port>/
```

Or follow the ALB redirect:

```bash
curl -sIL "<MiniStackLocalUrl-from-outputs>" | tail -5
```

Jenkins can take 1–3 minutes after ECS task start before HTTP returns `< 500`. Prefer `MiniStackApplicationUrl` over the `/_alb/...` URL day-to-day — see [Troubleshooting](TROUBLESHOOTING.md).

### 5. Verify

Follow [Verification](VERIFICATION.md) to confirm all services deployed as expected.

---

## Next Steps

- [Verify the deployment](VERIFICATION.md)
- [Configure auth or incremental updates](ADVANCED.md)
- [Jenkins admin password and AWS CLI](JENKINS.md)

# MiniStack Deployment

Deploy synthesized CloudFormation to MiniStack through the Interactive Deployer or `MiniStackCli`.

See also: [Setup](SETUP.md) · [Jenkins on MiniStack](JENKINS.md) · [Verification](VERIFICATION.md) · [Advanced Configuration](ADVANCED.md)

---

## Interactive Deployer (Recommended)

From `cfc-testing`, after building (see [Setup](SETUP.md#build)) and starting MiniStack:

```bash
# Walks configuration prompts when deployment-context.json is missing,
# then shows the deploy menu
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
```

**When prompts appear**

| Situation | Behavior |
|-----------|----------|
| No `deployment-context.json` | Full configuration questionnaire, then the deploy menu |
| Saved `deployment-context.json` | Skips the questionnaire; shows the deploy menu |
| `--interactive` / `-i` or `INTERACTIVE=true` | Ignores the saved context and runs the questionnaire |
| `--force` / `-f` | Deletes the saved context file, then runs the questionnaire |
| `cdk synth` / `cdk deploy` | No prompts; synthesizes the saved context for AWS |

Other arguments: `--context <file>` (or `-c`, or `CFC_CONTEXT_FILE`) selects the context file, and a trailing digit selects the menu option without prompting, for example:

```bash
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer \
  --context deployment-contexts/Jenkins-Stack.json 6
```

### Menu options

Every option except **9** and **0** synthesizes the canonical template first.

| Option | Action |
|--------|--------|
| **1** | Synthesize only (canonical AWS template in `cdk.out/`) |
| **2** | Deploy to AWS (`cdk deploy`) |
| **3** | Redeploy to AWS (delete + deploy) |
| **4** | Dry run: write the MiniStack adapted template + report; print the AWS change-set command |
| **5** | Export template (YAML/JSON) |
| **6** | Deploy to MiniStack (preflight, adapt template, create/update stack) |
| **7** | MiniStack pipeline: cfn-guard validation → deploy → stack verification |
| **8** | Deploy to LocalStack (see [LocalStack](../localstack/README.md)) |
| **9** | Reconfigure (fresh interactive setup) |
| **0** | Cancel |

### Typical first-time flow

1. Complete [Setup](SETUP.md): build and start MiniStack.
2. Run the Interactive Deployer and complete configuration (or pass a sample context with `--context`).
3. Choose **6** (Deploy to MiniStack).

The MiniStack CloudFormation stack is always named `<stackName>-ministack`.

Redeploying the same `applicationId` under a new stack name deletes the earlier MiniStack stack for that application first. Set `CFC_MINISTACK_REPLACE_SAME_APP=false` to keep both.

### Deployment artifacts

Each deploy writes to `cfc-testing/cdk.out/`:

| File | Description |
|------|-------------|
| `<stackName>.template.json` | Canonical AWS template (unchanged) |
| `<stackName>.ministack.template.json` | Adapted template deployed to MiniStack |
| `<stackName>.ministack-adaptations.json` | Audit trail of every local change |

---

## MiniStackCli (Non-Interactive)

For scripts, run `MiniStackCli` against an already-synthesized canonical template:

```bash
cd cfc-testing

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli \
  deploy <stackName>-ministack cdk.out/<stackName>.template.json

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli \
  verify <stackName>-ministack

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli \
  delete <stackName>-ministack
```

`deploy` derives the stack name from the template file name (`<stackName>.template.json` → `<stackName>-ministack`), writes the adapted template and adaptation report beside it, then creates or updates the stack. It does not run preflight. `verify` prints stack outputs and polls `MiniStackLocalUrl` until it returns HTTP `< 500` (up to 3 minutes; set `MINISTACK_HTTP_VERIFY=false` to skip the HTTP check).

---

## Base Jenkins on MiniStack (walkthrough)

Minimal Fargate Jenkins with no domain and no auth.

### 1. Prerequisites

```bash
# Repository root
mvn -f cfc-testing package -Dmaven.test.skip=true
cd cfc-testing

# Start MiniStack (ministack → start) and check health
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
curl -s http://localhost:4566/_ministack/health

# Optional: delete the saved context to walk all prompts
rm -f deployment-context.json
```

### 2. Configure and deploy

```bash
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
```

Complete the prompts (Jenkins, Fargate, no domain), then choose **6**. With a saved `deployment-context.json`, the Deployer skips the prompts and shows the menu.

### 3. Confirm deployment succeeded

The deployer prints change-set actions and stack outputs. You should see at minimum:

- `MiniStackLocalUrl`: ALB data-plane entry (`http://localhost:4566/_alb/<name>/`)
- `MiniStackApplicationUrl`: direct ECS port (`http://localhost:<port>`)

`MiniStackAuthenticatedUrl` is absent for this configuration.

### 4. Reach Jenkins

MiniStack's ALB cannot forward to ECS; the adapter redirects listeners to the local ECS port. Use the application URL:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/
open http://localhost:8080/
```

Or follow the ALB redirect:

```bash
curl -sIL "<MiniStackLocalUrl>" | tail -5
```

Jenkins can take 1–3 minutes after the ECS task starts before HTTP returns `< 500`. Prefer `MiniStackApplicationUrl` over the `/_alb/...` URL; see [Troubleshooting](TROUBLESHOOTING.md#jenkins-browser-stuck-on-ministacklocalurl-_alb).

### 5. Verify

Follow [Verification](VERIFICATION.md) to confirm all resources deployed as expected.

---

## Next Steps

- [Verify the deployment](VERIFICATION.md)
- [Incremental updates and environment variables](ADVANCED.md)
- [Jenkins admin password and AWS CLI](JENKINS.md)

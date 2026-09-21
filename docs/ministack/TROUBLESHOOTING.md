# MiniStack Troubleshooting

Common failures when deploying CloudForge to MiniStack locally.

See also: [Setup](SETUP.md) · [Deployment](DEPLOYMENT.md) · [Verification](VERIFICATION.md)

---

## Canonical Template Not Found

**Symptom:** `Canonical template not found: cdk.out/<stackName>.template.json` or `Stack '<stackName>' not found in cloud assembly`.

**Cause:** The deploy step could not find a synthesized template for the configured stack name. Common when:

- `MiniStackCli deploy` is pointed at a template that was never synthesized, or at a different stack name
- Stale templates remain in `cdk.out/` from an earlier run with a different `stackName`

**Fix:** Let the Interactive Deployer synthesize and deploy in one step (options 4, 6, and 7 all synthesize first):

```bash
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
# Choose 6, or 7 for cfn-guard + deploy + verify
```

Verify the template exists:

```bash
ls cdk.out/*.template.json
# Expect: cdk.out/<stackName>.template.json (matches stackName in deployment-context.json)
```

Use a stack name **without** the `-ministack` suffix in `deployment-context.json` (for example `my-jenkins`). The deployer appends `-ministack` for the MiniStack CloudFormation stack name (`my-jenkins-ministack`).

---

## `cdk synth` Does Not Show the Menu

**Symptom:** `cdk synth` synthesizes with no questionnaire or deploy menu.

**Cause:** `cdk.json` runs `InteractiveDeployer` as the CDK app. When the CDK CLI launches it, the deployer synthesizes the saved `deployment-context.json` (or `CFC_CONTEXT_FILE`) quietly and never prompts, so scripts and CI stay non-interactive.

**Fix:** Run the deployer directly for prompts and local targets:

```bash
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --force   # --force deletes the saved context first
```

---

## Stack Not Found After MiniStack Restart

**Symptom:** `MiniStackCli verify` or deploy fails with “Stack not found.”

**Cause:** MiniStack runs with `PERSIST_STATE=0`. Restarting the container wipes CloudFormation stack metadata (orphaned Docker resources may remain).

**Fix:** Redeploy from scratch or delete and recreate the stack. Keep MiniStack running for incremental update sessions.

```bash
cd cfc-testing
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform   # ministack → status / start
# Then re-run deploy (Interactive Deployer option 6) or MiniStackCli deploy
```

---

## Deployment Fails on Unsupported Resource Types

**Symptom:** Preflight reports `UNSUPPORTED_CFN_TYPES`, or the stack fails with `UPDATE_FAILED` / `CREATE_FAILED` on an `AWS::...` type.

**Cause:** The canonical template includes a CloudFormation resource MiniStack does not implement. Preflight blocks the types listed in `MiniStackCfnResourceCatalog` (RDS, WAF, Config, CloudTrail, GuardDuty, Backup); other unsupported types fail at create time.

**Fix:** For a deployment, disable the feature that adds the resource (for example `wafEnabled: false`, no compliance frameworks), or deploy to LocalStack (option 8) or AWS. For a new adapter rule, change `MiniStackTemplateAdapter` or `MiniStackCfnResourceCatalog`; do **not** weaken canonical factories in `cloudforge-api` / `cloudforge-core`.

Check the adaptation report and stack events:

```bash
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566

jq '.[] | {path, reason}' cdk.out/<stack>.ministack-adaptations.json
aws cloudformation describe-stack-events \
  --stack-name <stack>-ministack \
  --query 'StackEvents[?contains(ResourceStatus, `FAILED`)].[ResourceType,LogicalResourceId,ResourceStatusReason]' \
  --output table
```

---

## HTTP Verify Times Out

**Symptom:** `MiniStackCli verify` fails with `MiniStack endpoint did not become ready within 3 minutes`.

**Causes and fixes:**

| Cause | Fix |
|-------|-----|
| ECS task still starting | Wait longer; Jenkins can take 1–3 minutes |
| Redirect target not reachable | `verify` polls `MiniStackLocalUrl`, which redirects to `MiniStackApplicationUrl`; check the app port directly |
| Container crash | `docker ps -a` and check the application container logs |
| Port conflict | Check `MiniStackApplicationUrl` port is free |

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
curl -v http://localhost:<port>/
```

Skip the HTTP poll and print outputs only:

```bash
MINISTACK_HTTP_VERIFY=false java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli verify <stackName>-ministack
```

---

## Domain FQDN Does Not Open in Browser

**Symptom:** `http://jenkins.ministack.local` (or your configured FQDN) fails in Chrome/Safari, but `http://localhost:<port>` works.

**Cause:** Route53 records exist **inside MiniStack**, not in your host's DNS resolver. This is expected: domain deployment is verified via the AWS API, not browser hostname resolution.

**Fix (verification):**

```bash
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566

# Stack has Route53 resources
aws cloudformation list-stack-resources --stack-name <stack>-ministack \
  --query 'StackResourceSummaries[?contains(ResourceType,`Route53`)]'

# FQDN aliases to ALB
aws route53 list-resource-record-sets --hosted-zone-id <zone-id> \
  --query 'ResourceRecordSets[?Name==`jenkins.ministack.local.`]'
```

Use stack outputs for browser access: `MiniStackApplicationUrl` or `MiniStackLocalUrl`.

**Optional (browser hostname only):** Use the shared `*.cloudforge.localhost` names and include the port; see [Local DNS vs API verification](VERIFICATION.md#local-dns-vs-api-verification).

---

## Jenkins: Browser Stuck on MiniStackLocalUrl (`/_alb/...`)

**Symptom:** Opening `http://localhost:4566/_alb/<name>/` (stack output `MiniStackLocalUrl`) never finishes loading — looks like a redirect loop.

**Cause**

1. MiniStack's ALB cannot forward to ECS. The adapted listener returns a redirect to the local app port (`MiniStackApplicationUrl`, for Jenkins `http://localhost:8080/`). That redirect is expected for any app.
2. Jenkins `/` then returns **403** with a client-side redirect to `/login` (setup or sign-in required).
3. If Jenkins `/login` hangs, the browser spins after the ALB hop; it is not an ALB redirect loop.

**Prefer the application URL** (see [Jenkins on MiniStack](JENKINS.md)):

```bash
# From stack outputs — use MiniStackApplicationUrl
open http://localhost:8080/login

# Confirm the ALB only redirects (not a loop)
curl -sI "http://localhost:4566/_alb/<name>/" | grep -i location
# Expect: Location: ...://localhost:8080/
```

**If `/` is fast but `/login` times out**

```bash
curl -s -m 3 -o /dev/null -w "%{http_code}\n" http://localhost:8080/        # often 403
curl -s -m 3 -o /dev/null -w "%{http_code}\n" http://localhost:8080/login   # 000 = hung

docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -i jenkins
docker restart <jenkins-ecs-container-name>

# After restart
curl -s -m 5 -o /dev/null -w "%{http_code}\n" http://localhost:8080/login   # expect 200
```

Initial admin password (bind-mount volume):

```bash
cat cfc-testing/.ministack-volumes/<stackName>/jenkinsHome/secrets/initialAdminPassword
# or CloudWatch / docker logs; see JENKINS.md section 8
```

---

## Auth Proxy Not Reachable

**Symptom:** `MiniStackAuthenticatedUrl` does not respond; login redirect fails.

**Checks:**

1. The auth runtime is opt-in: set `MINISTACK_AUTH_AUTOSTART=true` before deploying (default `false`). See [Local Auth Runtime](ADVANCED.md#local-auth-runtime).
2. Auth must be enabled in the deployment config (`MiniStackAuthenticatedUrl` in outputs).
3. mock-oidc must be healthy: `curl http://localhost:3001/health`
4. Proxy log: `cfc-testing/target/ministack-runtime/auth-proxy.log`

```bash
curl -s http://localhost:4180/_ministack/auth/health
docker compose ps mock-oidc
```

To manage the proxy yourself, leave `MINISTACK_AUTH_AUTOSTART=false` and run `com.cloudforgeci.ministack.MiniStackAuthProxy` with `MINISTACK_AUTH_UPSTREAM` set to the application URL.

---

## Stale Classes / NoSuchMethodError During Synth

**Symptom:** Synthesis fails with `NoSuchMethodError` or missing methods on library classes.

**Cause:** Stale JARs in `cfc-testing/target/dependency/`.

**Fix:**

```bash
mvn clean install -DskipTests
mvn -f cfc-testing clean package -Dmaven.test.skip=true
```

If the error persists, remove stale SNAPSHOT JARs from `cfc-testing/target/dependency/` and rebuild.

---

## AWS CLI Errors Against MiniStack

**Symptom:** `Bad CPU type in executable` or connection errors with `aws` CLI.

Deployment uses the AWS SDK for Java (`MiniStackDeployer`); the AWS CLI is **optional** and only needed for verification. If the host `aws` binary is the wrong architecture, use the built-in verify or the Dockerized CLI in [Resource verification](RESOURCE_VERIFICATION.md#three-sources-of-truth):

```bash
# Built-in verify (no AWS CLI)
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli verify <stackName>-ministack
```

Or install the AWS CLI build that matches your CPU architecture.

---

## No-Op Deploy Still Shows Changes

**Symptom:** Redeploy without config changes triggers an update.

**Cause:** Adapted template differs from deployed template (non-deterministic values, manual stack edits, or adapter change).

**Fix:** Compare templates:

```bash
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566

aws cloudformation get-template \
  --stack-name <stack>-ministack --query TemplateBody --output text \
  > /tmp/deployed.json
diff <(jq -S . cdk.out/<stack>.ministack.template.json) <(jq -S . /tmp/deployed.json)
```

`MiniStackDeployer` treats identical templates as no-op via deep JSON comparison before creating a change set.

---

## Stack Resources Complete but Service APIs Empty

**Symptom:** CloudFormation resources show `CREATE_COMPLETE` but `ecs list-clusters` or `elbv2 describe-load-balancers` returns nothing.

**Fix:** Check stack events for partial backend failures. Some MiniStack services materialize asynchronously; wait and retry service API calls. If the problem persists, check the MiniStack container logs:

```bash
docker logs cfc-ministack --tail 100
```

---

## Getting Help

When reporting issues, include:

1. Stack events (last 15 failures)
2. Adaptation report (`*.ministack-adaptations.json`)
3. `deployment-context.json` (redact secrets)
4. MiniStack health: `curl http://localhost:4566/_ministack/health`
5. Relevant `docker ps` output

See [Verification](VERIFICATION.md) for the full diagnostic workflow.

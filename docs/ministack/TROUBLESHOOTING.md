# MiniStack Troubleshooting

Common failures when deploying CloudForge to MiniStack locally.

See also: [Setup](SETUP.md) · [Deployment](DEPLOYMENT.md) · [Verification](VERIFICATION.md)

---

## Template Not Found in cdk.out

**Symptom:** `Template not found: cdk.out/<stackName>.template.json`

**Cause:** MiniStack deploy ran before synthesis produced a template for your configured stack name. Common when:

- `cdk synth` ran without loading `deployment-context.json` (wrote `JenkinsFargate.template.json` instead of your stack name)
- Option **6** was chosen before option **1** completed synthesis
- Stale templates remain in `cdk.out/` from an earlier default synth

**Fix:**

```bash
cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer

# Choose 1 (synthesize), then 6 (deploy to MiniStack)
# Or option 7 for synth + deploy + verify
```

Verify the template exists:

```bash
ls cdk.out/*.template.json
# Expect: cdk.out/<stackName>.template.json  (matches stackName in deployment-context.json)
```

Use a stack name **without** the `-ministack` suffix in `deployment-context.json` (e.g. `my-jenkins`). The deployer appends `-ministack` for the MiniStack CloudFormation stack name (`my-jenkins-ministack`).

---

**Symptom:** `cdk synth` synthesizes immediately with no questionnaire.

**Cause:** `cdk.json` runs `CloudForgeCommunitySample`, which only forwards to the Interactive Deployer when `INTERACTIVE=true`. Plain `cdk synth` uses CDK defaults (jenkins/fargate) for scripts and CI.

**Fix:**

```bash
cd cfc-testing
rm -f deployment-context.json
export AWS_ENDPOINT_URL=http://localhost:4566

# Preferred: Deployer entry point
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --force

# Or via CDK CLI
INTERACTIVE=true cdk synth
```

---

## Stack Not Found After MiniStack Restart

**Symptom:** `MiniStackCli verify` or deploy fails with “Stack not found.”

**Cause:** MiniStack runs with `PERSIST_STATE=0`. Restarting the container wipes CloudFormation stack metadata (orphaned Docker resources may remain).

**Fix:** Redeploy from scratch or delete and recreate the stack. Keep MiniStack running for incremental update sessions.

```bash
cd cfc-testing && java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566
# Re-run deploy (Interactive Deployer option 6) or MiniStackCli deploy
```

---

## Deployment Fails on Unsupported Resource Types

**Symptom:** `UPDATE_FAILED` / `CREATE_FAILED` with `Unsupported resource type: AWS::...`

**Cause:** Canonical template includes a CloudFormation resource MiniStack does not implement.

**Fix:** Add an adaptation rule in `MiniStackTemplateAdapter` (in `cloudforge-ministack`). Do **not** weaken canonical factories in `cloudforge-api` / `cloudforge-core`.

Check the adaptation report — if the resource was not stripped, the adapter needs a new rule:

```bash
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566

jq '.[] | select(.reason | contains("Unsupported"))' cdk.out/*.ministack-adaptations.json
aws cloudformation describe-stack-events \
  --stack-name <stack>-ministack \
  --query 'StackEvents[?contains(ResourceStatus, `FAILED`)].[ResourceType,LogicalResourceId,ResourceStatusReason]' \
  --output table
```

---

## HTTP Verify Times Out

**Symptom:** `MiniStackCli verify` or deployer verification fails after 3 minutes.

**Causes and fixes:**

| Cause | Fix |
|-------|-----|
| ECS task still starting | Wait longer; Jenkins can take 1–3 minutes |
| Wrong URL | Use `MiniStackApplicationUrl` (direct port), not ALB forward |
| Container crash | `docker ps -a \| grep jenkins` and check logs |
| Port conflict | Check `MiniStackApplicationUrl` port is free |

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
curl -v http://localhost:<port>/
```

---

## Domain FQDN Does Not Open in Browser

**Symptom:** `http://jenkins.ministack.local` (or your configured FQDN) fails in Chrome/Safari, but `http://localhost:<port>` works.

**Cause:** Route53 records exist **inside MiniStack**, not on your laptop's DNS resolver. This is expected — domain deployment is verified via the AWS API, not browser hostname resolution.

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

Use stack outputs for browser access: `MiniStackApplicationUrl`, `MiniStackLocalUrl`, or `MiniStackAuthenticatedUrl` (with auth).

**Optional (browser FQDN only):** Add `/etc/hosts` and include the port — see [Local DNS vs API verification](VERIFICATION.md#local-dns-vs-api-verification).

---

Disable HTTP polling for output-only verify:

```bash
MINISTACK_HTTP_VERIFY=false java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli verify <stack-name>
```

---

## Jenkins: Browser Stuck on MiniStackLocalUrl (`/_alb/...`)

**Symptom:** Opening `http://localhost:4566/_alb/<name>/` (stack output `MiniStackLocalUrl`) never finishes loading — looks like a redirect loop.

**What is actually happening**

1. MiniStack ALB cannot forward to ECS. The adapted listener returns **302** to the local app port (`MiniStackApplicationUrl`, often `http://localhost:8080/`). That redirect is expected for any app, not only Jenkins.
2. Jenkins `/` then returns **403** with a client-side redirect to `/login` (setup / auth required).
3. If Jenkins `/login` is hung, the browser sits on a spinner after the ALB hop — it is not an ALB redirect loop.

**Prefer the application URL** (see [Jenkins on MiniStack](JENKINS.md)):

```bash
# From stack outputs — use MiniStackApplicationUrl
open http://localhost:8080/login

# Confirm ALB is only a 302 (not a loop)
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
# or CloudWatch / docker logs — see JENKINS.md §8
```

---

## Auth Proxy Not Reachable

**Symptom:** `MiniStackAuthenticatedUrl` does not respond; login redirect fails.

**Checks:**

1. Auth must be enabled in deployment config (`MiniStackAuthenticatedUrl` in outputs).
2. mock-oidc must be healthy: `curl http://localhost:3001/health`
3. Proxy log: `cfc-testing/target/ministack-runtime/auth-proxy.log`
4. Auto-start enabled: `MINISTACK_AUTH_AUTOSTART=true` (default)

```bash
curl -s http://localhost:4180/_ministack/auth/health
docker compose ps mock-oidc
```

If managing auth manually: `MINISTACK_AUTH_AUTOSTART=false` and start `MiniStackAuthProxy` yourself with correct `MINISTACK_AUTH_UPSTREAM`.

---

## Stale Classes / NoSuchMethodError During Synth

**Symptom:** `cdk synth` fails with `NoSuchMethodError` or missing methods on library classes.

**Cause:** Stale JARs in `cfc-testing/target/dependency/`.

**Fix:**

```bash
mvn clean install -DskipTests
cd cfc-testing && mvn clean package -Dmaven.test.skip=true
```

If the error persists, remove stale SNAPSHOT JARs from `cfc-testing/target/dependency/` and rebuild.

---

## AWS CLI Errors Against MiniStack

**Symptom:** `Bad CPU type in executable` or connection errors with `aws` CLI.

**Note:** Deployment uses the AWS SDK for Java (`MiniStackDeployer`) — AWS CLI is **optional** and only needed for verification. If the host `aws` binary is wrong architecture, use SDK-based tools instead:

```bash
# Built-in verify (no AWS CLI)
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli verify <stack-name>
```

Or install a native ARM/x86 AWS CLI matching your Mac.

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

## Layer 2 Passes but Layer 3 Empty

**Symptom:** CloudFormation resources show `CREATE_COMPLETE` but `ecs list-clusters` or `elbv2 describe-load-balancers` returns nothing.

**Fix:** Check stack events for partial backend failures. Some MiniStack services materialize asynchronously — wait and retry service API calls. If persistent, check MiniStack container logs:

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

# Jenkins on MiniStack

Deploy Jenkins on Fargate locally, query MiniStack with the AWS CLI, and retrieve the initial admin password the same way you would on AWS (CloudWatch Logs) — plus local fallbacks.

See also: [Setup](SETUP.md) · [Deployment](DEPLOYMENT.md) · [Verification](VERIFICATION.md) · [Advanced Configuration](ADVANCED.md) · [Troubleshooting](TROUBLESHOOTING.md)

---

## What you get locally

A DEV Jenkins Fargate stack (VPC, ALB, ECS, IAM, CloudWatch log group) adapted for MiniStack:

| AWS (canonical) | MiniStack (adapted) |
|-----------------|---------------------|
| EFS for `/var/jenkins_home` | Host bind mount under `.ministack-volumes/` |
| ALB forward → ECS | ALB redirect → `http://localhost:<port>` |
| Application Auto Scaling | **Removed** (not emulated) |
| CloudWatch Logs (`awslogs`) | **Supported** — primary path for bootstrap output |

Naming (from `stackName` in `deployment-context.json`):

| Concept | Pattern | Example (`stackName=my-jenkins`) |
|---------|---------|----------------------------------|
| MiniStack CloudFormation stack | `<stackName>-ministack` | `my-jenkins-ministack` |
| Canonical template | `cdk.out/<stackName>.template.json` | `cdk.out/my-jenkins.template.json` |
| CloudWatch log group | `/aws/ecs/<stackName>/fargate/<profile>` | `/aws/ecs/my-jenkins/fargate/dev` |
| Host bind mount | `.ministack-volumes/<stackName>/jenkinsHome` | `.ministack-volumes/my-jenkins/jenkinsHome` |
| Log stream prefix | `<applicationId>/...` | `jenkins/...` |

---

## 1. Prerequisites and build

Same as [Setup](SETUP.md). From the repository root:

```bash
mvn clean install -DskipTests
cd cfc-testing && java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
curl -s http://localhost:4566/_ministack/health

cd cfc-testing
mvn package -Dmaven.test.skip=true
```

---

## 2. Recommended `deployment-context.json`

Save as `cfc-testing/deployment-context.json` for a minimal Jenkins Fargate deploy (no domain, auth, or autoscaling). Choose any valid `stackName` — it drives log groups, templates, and volume paths.

```json
{
  "stackName": "my-jenkins",
  "applicationId": "jenkins",
  "runtime": "FARGATE",
  "topology": "application-service",
  "securityProfile": "dev",
  "networkMode": "public",
  "domain": "",
  "enableSsl": false,
  "authMode": "none",
  "cpu": 1024,
  "memory": 2048,
  "minInstanceCapacity": 1,
  "maxInstanceCapacity": 1,
  "enableAutoScaling": false,
  "wafEnabled": false,
  "cloudfrontEnabled": false,
  "enableMonitoring": false,
  "complianceFrameworks": "",
  "complianceMode": "DISABLED",
  "region": "us-east-1",
  "env": "dev"
}
```

| Field | Value | Why |
|-------|-------|-----|
| `stackName` | your choice (e.g. `my-jenkins`) | Used for templates, log group, bind-mount path |
| `enableAutoScaling` | `false` | Application Auto Scaling is stripped locally anyway |
| `minInstanceCapacity` / `maxInstanceCapacity` | `1` / `1` | Single task — avoids port conflicts on `:8080` |
| `domain` / `authMode` | empty / `none` | Simplest path for first deploy |

---

## 3. Deploy

```bash
cd cfc-testing
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
# Choose 6 — Deploy to MiniStack
```

With `deployment-context.json` present, the deployer skips prompts and shows the menu — choose **6**.

Expected artifacts in `cdk.out/` (replace `<stackName>` with your value):

- `<stackName>.template.json` — canonical AWS template
- `<stackName>.ministack.template.json` — adapted template deployed to MiniStack
- `<stackName>.ministack-adaptations.json` — audit of local changes

Expected stack outputs (names may vary slightly):

| Output | Example | Use |
|--------|---------|-----|
| `MiniStackApplicationUrl` | `http://localhost:8080` | Open Jenkins in browser |
| `MiniStackLocalUrl` | `http://localhost:4566/_alb/<name>/` | ALB entry (redirects to app port) |
| `MiniStackHostVolumeJenkinsHome` | `.../.ministack-volumes/<stackName>/jenkinsHome` | Persistent home directory on host |

---

## 4. Shell variables (use throughout)

Set these once per terminal session from your `deployment-context.json`:

```bash
# Required — must match stackName in deployment-context.json
export STACK_NAME=my-jenkins

# Derived — used in AWS CLI examples below
export MINISTACK_STACK="${STACK_NAME}-ministack"
export LOG_GROUP="/aws/ecs/${STACK_NAME}/fargate/dev"
export VOLUME_HOME=".ministack-volumes/${STACK_NAME}/jenkinsHome"
```

Interactive Deployer prompts also accept any stack name matching `^[A-Za-z][A-Za-z0-9-]*$`.

---

## 5. Configure AWS CLI for MiniStack

MiniStack accepts any credentials. Point every AWS CLI call at `http://localhost:4566`.

### Option A — Shell exports (quick session)

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
```

With `AWS_ENDPOINT_URL` set (AWS CLI v2.13+), you do not need `--endpoint-url` on each command:

```bash
aws cloudformation describe-stacks --stack-name "$MINISTACK_STACK"
aws logs describe-log-groups --log-group-name-prefix "/aws/ecs/${STACK_NAME}"
```

### Option B — Named profile (persistent)

`~/.aws/credentials`:

```ini
[ministack]
aws_access_key_id = test
aws_secret_access_key = test
```

`~/.aws/config`:

```ini
[profile ministack]
region = us-east-1
output = json
endpoint_url = http://localhost:4566
```

Use it:

```bash
aws --profile ministack cloudformation describe-stacks --stack-name "$MINISTACK_STACK"
```

### Option C — Env only (no named profile)

Same as Option A: export `AWS_ENDPOINT_URL` (and dummy credentials). Prefer that over per-command `--endpoint-url` so CLI and Java stay on one key.

```bash
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1
aws cloudformation describe-stacks --stack-name "$MINISTACK_STACK"
```

### Sanity check

```bash
# Replace --profile ministack with exports if using Option A
aws --profile ministack sts get-caller-identity
aws --profile ministack cloudformation list-stacks \
  --query "StackSummaries[?contains(StackName, \`${STACK_NAME}\`)].{Name:StackName,Status:StackStatus}" \
  --output table
```

---

## 6. Jenkins CloudWatch log group

CloudForge creates a log group for Fargate container output:

```text
/aws/ecs/<stackName>/fargate/<securityProfile>
```

With `securityProfile: dev` that is `$LOG_GROUP` → `/aws/ecs/<stackName>/fargate/dev`.

ECS uses the `awslogs` driver with stream prefix **`jenkins`** (`applicationId`). Jenkins prints first-run setup text and password hints to **stdout**, which lands in this log group on AWS and in MiniStack.

Confirm the log group exists:

```bash
aws --profile ministack logs describe-log-groups \
  --log-group-name-prefix "/aws/ecs/${STACK_NAME}" \
  --query 'logGroups[].logGroupName' \
  --output table
```

List recent log streams (newest first):

```bash
aws --profile ministack logs describe-log-streams \
  --log-group-name "$LOG_GROUP" \
  --order-by LastEventTime \
  --descending \
  --max-items 10 \
  --query 'logStreams[].[logStreamName,lastEventTimestamp]' \
  --output table
```

Stream names typically look like: `jenkins/<container-or-task-id>`.

---

## 7. Read logs with AWS CLI

### FilterLogEvents — search for password-related lines

Best when you do not know the stream name:

```bash
aws --profile ministack logs filter-log-events \
  --log-group-name "$LOG_GROUP" \
  --filter-pattern "?password ?Password ?admin ?Jenkins ?unlock" \
  --limit 50 \
  --query 'events[].[timestamp,message]' \
  --output text
```

Narrower search:

```bash
aws --profile ministack logs filter-log-events \
  --log-group-name "$LOG_GROUP" \
  --filter-pattern "initialAdminPassword" \
  --limit 20
```

Follow-style polling (run a few times after deploy):

```bash
aws --profile ministack logs filter-log-events \
  --log-group-name "$LOG_GROUP" \
  --start-time $(($(date +%s) * 1000 - 600000)) \
  --filter-pattern "password" \
  --output text
```

### GetLogEvents — tail a specific stream

After `describe-log-streams`, pick the newest `jenkins/...` stream:

```bash
STREAM=$(aws --profile ministack logs describe-log-streams \
  --log-group-name "$LOG_GROUP" \
  --order-by LastEventTime --descending --max-items 1 \
  --query 'logStreams[0].logStreamName' --output text)

aws --profile ministack logs get-log-events \
  --log-group-name "$LOG_GROUP" \
  --log-stream-name "$STREAM" \
  --start-from-head \
  --limit 100 \
  --query 'events[].message' \
  --output text
```

Tail the last events:

```bash
aws --profile ministack logs get-log-events \
  --log-group-name "$LOG_GROUP" \
  --log-stream-name "$STREAM" \
  --no-start-from-head \
  --limit 50 \
  --query 'events[].message' \
  --output text
```

---

## 8. Initial admin password — all methods

Jenkins writes the password to **`/var/jenkins_home/secrets/initialAdminPassword`** inside the container. On first boot it also prints guidance to the console (→ CloudWatch Logs).

### Method 1 — CloudWatch Logs (AWS-equivalent, recommended)

Use [FilterLogEvents or GetLogEvents](#7-read-logs-with-aws-cli) above. Look for:

- `Jenkins initial setup is required`
- `please use the following password`
- A 32-character hex string

Allow **1–3 minutes** after ECS task start before logs appear.

### Method 2 — Host bind mount file (local adaptation)

The MiniStack adapter replaces EFS with a host directory. Read the file directly:

```bash
cd cfc-testing

# Path from stack output MiniStackHostVolumeJenkinsHome, or:
cat "${VOLUME_HOME}/secrets/initialAdminPassword"
```

If permission denied, fix ownership once (Jenkins runs as uid **1000**):

```bash
sudo chown -R 1000:1000 "${VOLUME_HOME}"
```

### Method 3 — Docker logs (ground truth)

```bash
CONTAINER=$(docker ps --filter ancestor=jenkins/jenkins:lts --format '{{.Names}}' | head -1)
docker logs "$CONTAINER" 2>&1 | tail -80
```

Search for the password line:

```bash
docker logs "$CONTAINER" 2>&1 | rg -i 'password|unlock|initial'
```

### Method 4 — Exec into the container

```bash
CONTAINER=$(docker ps --filter ancestor=jenkins/jenkins:lts --format '{{.Names}}' | head -1)
docker exec "$CONTAINER" cat /var/jenkins_home/secrets/initialAdminPassword
```

### Method 5 — ECS Exec (if enabled in template)

CloudForge enables ECS Exec on Fargate services. Through MiniStack:

```bash
CLUSTER=$(aws --profile ministack ecs list-clusters \
  --query 'clusterArns[0]' --output text)

SERVICE=$(aws --profile ministack ecs list-services --cluster "$CLUSTER" \
  --query 'serviceArns[0]' --output text)

TASK=$(aws --profile ministack ecs list-tasks --cluster "$CLUSTER" \
  --service-name "$SERVICE" --query 'taskArns[0]' --output text)

CONTAINER=$(aws --profile ministack ecs describe-task-definition \
  --task-definition "$(aws --profile ministack ecs describe-services \
    --cluster "$CLUSTER" --services "$SERVICE" \
    --query 'services[0].taskDefinition' --output text)" \
  --query 'taskDefinition.containerDefinitions[0].name' --output text)

aws --profile ministack ecs execute-command \
  --cluster "$CLUSTER" \
  --task "$TASK" \
  --container "$CONTAINER" \
  --interactive \
  --command "cat /var/jenkins_home/secrets/initialAdminPassword"
```

---

## 9. Open Jenkins

```bash
# From stack output (port may differ — check MiniStackApplicationUrl)
open http://localhost:8080/

# Or follow ALB redirect
curl -sIL "$(aws --profile ministack cloudformation describe-stacks \
  --stack-name "$MINISTACK_STACK" \
  --query 'Stacks[0].Outputs[?OutputKey==`MiniStackLocalUrl`].OutputValue' \
  --output text)"
```

Paste the initial admin password on the **Unlock Jenkins** page. Complete the setup wizard (install suggested plugins, create admin user). With the bind mount, wizard progress persists under `.ministack-volumes/<stackName>/` across redeploys.

---

## 10. Useful AWS CLI queries (MiniStack)

```bash
# Stack status and outputs
aws --profile ministack cloudformation describe-stacks \
  --stack-name "$MINISTACK_STACK" \
  --query 'Stacks[0].{Status:StackStatus,Outputs:Outputs}'

# Resources (Console → Stack → Resources)
aws --profile ministack cloudformation list-stack-resources \
  --stack-name "$MINISTACK_STACK" \
  --query 'StackResourceSummaries[].[LogicalResourceId,ResourceType,ResourceStatus]' \
  --output table

# ECS cluster and service
aws --profile ministack ecs list-clusters
aws --profile ministack ecs list-services --cluster <cluster-arn>
aws --profile ministack ecs describe-services \
  --cluster <cluster-arn> --services <service-arn>

# Recent stack failures
aws --profile ministack cloudformation describe-stack-events \
  --stack-name "$MINISTACK_STACK" \
  --query 'StackEvents[?contains(ResourceStatus,`FAILED`) || contains(ResourceStatus,`ROLLBACK`)].[Timestamp,ResourceStatus,LogicalResourceId,ResourceStatusReason]' \
  --output table
```

---

## 11. Redeploy and clean up

**Update after config change** — edit `deployment-context.json`, redeploy option **6**.

**Clean redeploy** — delete the stack with `MiniStackCli` (there is no single Interactive
Deployer option that both deletes and redeploys a MiniStack stack; option **3** is AWS-only),
then choose option **6** again:

```bash
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli \
  delete "$MINISTACK_STACK"
```

**Stop stale containers** (frees port 8080):

```bash
docker ps --filter publish=8080
docker stop <container-name>
```

**Remove persisted Jenkins home** (fresh initial password):

```bash
rm -rf "cfc-testing/${VOLUME_HOME}"
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `aws: bad CPU type` or command not found | AWS CLI not installed for your Mac architecture | Install [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) or use Methods 2–4 for password |
| Empty `filter-log-events` | Task still starting or logs not shipped yet | Wait 1–3 min; try `docker logs` |
| `Log group not found` | Wrong `STACK_NAME` or stack not deployed | Verify `stackName` in context; `describe-log-groups --log-group-name-prefix /aws/ecs` |
| `8080 already allocated` | Old ECS container still running | `docker stop` container on 8080; redeploy |
| Rollback on deploy | Unsupported resources (historically autoscaling) | Rebuild after adapter updates; use `enableAutoScaling: false` |
| Permission denied on bind mount | Host dir not owned by uid 1000 | `sudo chown -R 1000:1000 "${VOLUME_HOME}"` |

More: [Troubleshooting](TROUBLESHOOTING.md)

---

## Next steps

- [Verify the full stack](VERIFICATION.md)
- [Add domain, TLS, or auth](ADVANCED.md)
- [Interactive Deployer options](../guides/INTERACTIVE_DEPLOYER.md)

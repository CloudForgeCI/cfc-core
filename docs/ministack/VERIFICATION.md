# MiniStack Verification

Confirm what CloudForge actually deployed to MiniStack — the local equivalent of browsing the AWS Console (CloudFormation → Stack → Resources, ECS, ELB, EC2).

See also: [Deployment](DEPLOYMENT.md) · [Jenkins on MiniStack](JENKINS.md) · [Resource verification matrix](RESOURCE_VERIFICATION.md) · [Troubleshooting](TROUBLESHOOTING.md) · [Advanced Configuration](ADVANCED.md)

---

## Verification Layers

There is no AWS Console for MiniStack. Use layered checks — each layer confirms a different part of “what actually got deployed.”

For a **per-resource matrix** (canonical vs adapted vs deployed, with CLI commands), see **[Resource Verification Matrix](RESOURCE_VERIFICATION.md)**.

```text
Layer 1  CloudFormation stack     →  “Did the stack create/update?”
Layer 2  Stack resources          →  “Which logical resources exist?” (Console → Resources tab)
Layer 3  Service APIs             →  “Are VPC/ECS/ALB records populated?”
Layer 4  Stack outputs + HTTP     →  “Is the app reachable?”
Layer 5  Docker                    →  “Is the application container running?”
Layer 6  Template + adaptations   →  “What was intended vs. what MiniStack supports?”
```

---

## Layer 1 — Stack Status (Built-In)

**Interactive Deployer option 7** runs `verifyMiniStackDeployment` after deploy.

**MiniStackCli:**

```bash
cd cfc-testing
export STACK_NAME=my-jenkins   # must match stackName in deployment-context.json
export MINISTACK_STACK="${STACK_NAME}-ministack"

java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli \
  verify "$MINISTACK_STACK"
```

Prints outputs and polls `MiniStackLocalUrl` until HTTP status `< 500` (up to 3 minutes).

---

## Layer 2 — Stack Resources

MiniStack implements CloudFormation APIs. Point AWS CLI at MiniStack with dummy credentials and `AWS_ENDPOINT_URL` (AWS CLI v2.13+ — same as [Jenkins on MiniStack](JENKINS.md#5-configure-aws-cli-for-ministack)):

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
export STACK_NAME=my-jenkins   # must match stackName in deployment-context.json
export MINISTACK_STACK="${STACK_NAME}-ministack"

# Stack summary
aws cloudformation describe-stacks --stack-name "$MINISTACK_STACK"

# Resource inventory (logical ID, type, status)
aws cloudformation list-stack-resources --stack-name "$MINISTACK_STACK" \
  --query 'StackResourceSummaries[].[LogicalResourceId,ResourceType,ResourceStatus]' \
  --output table

# Recent events (failures show here first)
aws cloudformation describe-stack-events --stack-name "$MINISTACK_STACK" \
  --query 'StackEvents[0:15].[Timestamp,ResourceStatus,ResourceType,LogicalResourceId,ResourceStatusReason]' \
  --output table
```

### Resource browser (StackPort, optional)

MiniStack has no AWS Console UI. [StackPort](https://github.com/DaviReisVieira/stackport) is a third-party resource browser that reads `AWS_ENDPOINT_URL`. CloudForge CI can start it against the running emulator:

```bash
cd cfc-testing && java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8888
```

Use StackPort to browse CloudFormation, ECS, ELB, IAM, and other services exposed by MiniStack or LocalStack. Platform start owns its lifecycle (see [LocalStack verification](../localstack/README.md#resource-browser-stackport)).

### Expected resource types — base Jenkins Fargate (no domain/auth)

| Type | Present? |
|------|----------|
| `AWS::EC2::VPC`, `Subnet`, `SecurityGroup`, `InternetGateway` | Yes |
| `AWS::ElasticLoadBalancingV2::LoadBalancer`, `TargetGroup`, `Listener` | Yes |
| `AWS::ECS::Cluster`, `TaskDefinition`, `Service` | Yes |
| `AWS::IAM::Role` | Yes |
| `AWS::Logs::LogGroup` | Yes (Fargate container logs via `awslogs`) |
| `AWS::Route53::*`, `AWS::CertificateManager::*` | **No** (no domain) |
| `AWS::Cognito::*` | **No** (no auth) |
| `AWS::EFS::*` | In canonical template; **removed** in adapted template |

### Compare canonical vs adapted templates

```bash
cd cfc-testing

# Resource types in canonical AWS template
jq -r '.Resources | to_entries[] | .value.Type' "cdk.out/${STACK_NAME}.template.json" | sort -u

# Resource types actually deployed to MiniStack
jq -r '.Resources | to_entries[] | .value.Type' "cdk.out/${STACK_NAME}.ministack.template.json" | sort -u

# What the adapter changed
jq '.[].reason' "cdk.out/${STACK_NAME}.ministack-adaptations.json"
```

---

## Layer 3 — Service APIs

Equivalent to browsing ECS, EC2, and ELB in the AWS Console:

Requires the same exports as Layer 2 (`AWS_ENDPOINT_URL`, credentials, `STACK_NAME`).

```bash
# ECS — cluster and running tasks
aws ecs list-clusters
aws ecs list-services --cluster <cluster-arn-from-above>
aws ecs describe-services --cluster <cluster> --services <service-arn>

# ALB
aws elbv2 describe-load-balancers
aws elbv2 describe-target-groups

# VPC
aws ec2 describe-vpcs
aws ec2 describe-subnets
```

If Layer 2 shows `CREATE_COMPLETE` but Layer 3 lists are empty, the emulator recorded the stack but a service backend may not have fully materialized — check stack events.

### Route53 (when domain is enabled)

```bash
# Hosted zones
aws route53 list-hosted-zones \
  --query 'HostedZones[?contains(Name, `ministack.local`) || contains(Name, `example`)].{Name:Name,Id:Id}' \
  --output table

# Records for your FQDN (replace domain/subdomain from deployment-context.json)
ZONE_ID=$(aws route53 list-hosted-zones \
  --query 'HostedZones[?Name==`ministack.local.`].Id' --output text | awk '{print $1}')
FQDN=jenkins.ministack.local.

aws route53 list-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --query "ResourceRecordSets[?Name==\`${FQDN}\`]" --output json
```

Success: **A** and **AAAA** alias records point at the ALB DNS name (`dualstack....elb.amazonaws.com`). See [Local DNS vs API verification](#local-dns-vs-api-verification) — you do **not** need the FQDN to open in your browser locally.

---

## Local DNS vs API verification

When `domain` / `subdomain` are set in `deployment-context.json`, CloudForge creates Route53 hosted zones and alias records in the **canonical** template — the same resources that would exist on AWS.

**Local browser hostname resolution is optional.** MiniStack stores Route53 state in the emulator; your Mac does not use it for DNS unless you add `/etc/hosts` yourself.

| Question | Local MiniStack | Real AWS |
|----------|-----------------|----------|
| Do Route53 resources exist in CloudFormation? | Verify with `list-stack-resources` | Same |
| Do alias records point at the ALB? | Verify with `route53 list-resource-record-sets` | Same |
| Does `http://<fqdn>` open in Chrome/Safari? | **Not required** — use stack output URLs | Yes (public DNS) |
| What URL do I use in a browser locally? | `MiniStackApplicationUrl` (e.g. `http://localhost:8080`) | FQDN or ALB DNS name |

```text
What you validate locally          What you use in a browser
─────────────────────────          ───────────────────────────
aws cloudformation list-stack-     http://localhost:<port>     ← MiniStackApplicationUrl
  resources (Route53 present)

aws route53 list-resource-         http://localhost:4566/_alb/… ← MiniStackLocalUrl
  record-sets (alias → ALB)

(with auth enabled)                http://localhost:4180          ← MiniStackAuthenticatedUrl
                                   or MiniStackAuthenticatedUrl from outputs
```

**Verify domain wiring (API — source of truth):**

```bash
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
export STACK_NAME=my-jenkins
export MINISTACK_STACK="${STACK_NAME}-ministack"

# CloudFormation: Route53 resources in the stack
aws cloudformation list-stack-resources --stack-name "$MINISTACK_STACK" \
  --query 'StackResourceSummaries[?contains(ResourceType,`Route53`)].{Logical:LogicalResourceId,Type:ResourceType,Status:ResourceStatus}' \
  --output table

# Route53: FQDN aliases to ALB (replace FQDN if your subdomain/domain differ)
ZONE_ID=$(aws route53 list-hosted-zones \
  --query 'HostedZones[?Name==`ministack.local.`].Id' --output text | awk '{print $1}')

aws route53 list-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --query 'ResourceRecordSets[?Name==`jenkins.ministack.local.`]' --output json
```

Expected alias target shape:

```json
{
  "Name": "jenkins.ministack.local.",
  "Type": "A",
  "AliasTarget": {
    "DNSName": "dualstack.<alb-name>....elb.amazonaws.com."
  }
}
```

**Optional — browser hostname (not required for verification):**

If you want a friendly name in the address bar, use the shared [`*.cloudforge.localhost`](../guides/LOCAL_EMULATOR_HOSTS.md) hosts block (works for MiniStack and LocalStack) and **include the port**:

```bash
./scripts/setup-cloudforge-local-hosts.sh
open "http://jenkins.cloudforge.localhost:8080"
```

This is convenience only. CI and incremental-transition tests should assert API state, not browser DNS.

---

## Layer 4 — Application HTTP

```bash
# Direct ECS port (most reliable for Jenkins on MiniStack)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:<port>/

# ALB data-plane URL (redirects to ECS port after adaptation)
curl -sIL "<MiniStackLocalUrl>" | grep -E '^HTTP|^Location'
```

Success: HTTP `200` or `403` (Jenkins login page) — anything `< 500` means the container is responding.

---

## Layer 5 — Docker (Ground Truth)

MiniStack starts real containers via Docker:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -i jenkins
```

You should see a Jenkins container with a host port matching `MiniStackApplicationUrl`.

---

## Layer 6 — Template Truth Table (Pre-Deploy)

Before deploying, validate the **canonical** synthesized template matches expectations:

```bash
cd cfc-testing
./scripts/comprehensive-resource-validator.sh
```

This checks `cdk.out/` templates against expected resource matrices (runtime × profile × domain × SSL). It validates **what AWS would get**, not what MiniStack deployed — pair it with Layer 2 on the adapted template.

---

## Automated tests (Maven)

Live MiniStack JUnit tests hit real emulator APIs (CFN, EC2, ELBv2, Route53, ACM) — not Java mocks.

```bash
# Unit suites (reactor modules; MiniStack-tagged tests excluded by default)
cd /path/to/cfc-core
mvn -pl cloudforge-core,cloudforge-api,cloudforge-ministack,cloudforge-manager -am test

# Live MiniStack integration (Testcontainers, or reuse compose)
export AWS_ENDPOINT_URL=http://localhost:4566   # preferred when compose MiniStack is up
mvn -pl cloudforge-ministack test -P ministack

# Synth-only MiniStack-related parity (no emulator) in cfc-testing
cd cfc-testing
mvn test -P ministack
```

| Suite | Module | What it asserts |
|-------|--------|-----------------|
| Adapter unit | `cloudforge-ministack` (default `mvn test`) | Template adaptations (OIDC strip, ingress inline, ECS→localhost redirect, …) |
| Deployer / incremental / **native network** | `cloudforge-ministack` `-P ministack` | Stack create/update; SG **rule records**; Route53 alias → ALB DNS; ALB/listeners; ACM presence; `/_alb` reachability |
| Canonical parity | `cfc-testing` `-P ministack` | CDK synth counts/diffs for LB/domain/TLS/Cognito (no emulator) |

### Fidelity boundary (native asserts vs out of scope)

| Assert on MiniStack | Do **not** claim |
|---------------------|------------------|
| SG exists + ingress/egress **records** via `describe-security-groups` | Packet filter allow/deny (enforcement is partial/absent) |
| Standalone `SecurityGroupIngress` inlined onto parent SG after adapt | — |
| Route53 hosted zone + alias A/AAAA → ALB `DNSName` | OS/public DNS resolving the FQDN |
| ALB / listener inventory; adapted ECS forward → localhost redirect | Real ALB→ECS target-health forward |
| ACM cert + HTTPS listener **presence** | Browser TLS termination parity |
| `MiniStackLocalUrl` / app port HTTP | Cognito / ALB OIDC edge (stripped; auth proxy deferred) |

`MiniStackNativeNetworkVerificationTest` documents this boundary in code comments.

---

## Quick Verification Checklist

After a base Jenkins deploy, confirm:

- [ ] `describe-stacks` → `StackStatus: CREATE_COMPLETE` or `UPDATE_COMPLETE`
- [ ] `list-stack-resources` → VPC, ALB, ECS cluster/service present
- [ ] No Route53, Cognito, or ACM resources
- [ ] Adaptation report documents EFS removal and ALB redirect
- [ ] `MiniStackApplicationUrl` returns HTTP `< 500`
- [ ] `docker ps` shows a running Jenkins container
- [ ] Redeploy with no config change → deployer reports **no-op**

After enabling **domain** (see [Local DNS vs API verification](#local-dns-vs-api-verification)):

- [ ] `list-stack-resources` → `AWS::Route53::HostedZone` + `RecordSet` present
- [ ] `route53 list-resource-record-sets` → FQDN **A/AAAA** alias to ALB DNS name
- [ ] Browser FQDN **not** required — use `MiniStackApplicationUrl` for HTTP checks

After enabling **TLS**:

- [ ] `AWS::CertificateManager::Certificate` in stack resources
- [ ] HTTPS listener present in `elbv2 describe-listeners`

After enabling **auth**:

- [ ] Cognito resources in stack; `MiniStackAuthenticatedUrl` in outputs
- [ ] Auth proxy health: `curl http://localhost:4180/_ministack/auth/health`

---

## Next Steps

- [Resource verification matrix](RESOURCE_VERIFICATION.md)
- [Advanced: incremental transitions](ADVANCED.md#incremental-deployments)
- [Troubleshooting failed verification](TROUBLESHOOTING.md)

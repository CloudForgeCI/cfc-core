# AWS Resource Verification Matrix

What CloudForge deploys to MiniStack, how it differs from the canonical AWS template, and **how to verify each resource** against live state.

See also: [Verification layers](VERIFICATION.md) · [Template adaptations](ADVANCED.md#template-adaptations) · [Extended Testing](../guides/EXTENDED-TESTING.md)

---

## Three Sources of Truth

Every verification question maps to one of three artifacts:

```text
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. Canonical template     cdk.out/<stack>.template.json               │
│    What CDK would deploy to real AWS. Never modified by MiniStack.     │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ MiniStackTemplateAdapter
┌───────────────────────────────▼─────────────────────────────────────────┐
│ 2. Adapted template       cdk.out/<stack>.ministack.template.json       │
│    What CloudFormation sends to MiniStack. Audit: .ministack-adaptations│
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ MiniStackDeployer (create/update)
┌───────────────────────────────▼─────────────────────────────────────────┐
│ 3. Deployed + runtime     CFN stack + service APIs + Docker + auth proxy│
│    What MiniStack recorded and what actually runs on your machine.      │
└─────────────────────────────────────────────────────────────────────────┘
```

| Question | Where to look |
|----------|---------------|
| “Should this resource exist for my config?” | **Canonical** template (Layer 6 / `comprehensive-resource-validator.sh`) |
| “Did we deploy the adapted shape?” | **Adapted** template + adaptation report |
| “Did MiniStack accept and materialize it?” | **CFN** `list-stack-resources` + **service APIs** |
| “Does the app actually work?” | **Stack outputs**, HTTP, Docker, auth proxy |

Always set endpoint credentials before API checks:

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
export STACK_NAME=my-jenkins          # deployment-context.json stackName
export MINISTACK_STACK="${STACK_NAME}-ministack"
```

On ARM Macs where the host `aws` binary fails, use Docker:

```bash
alias aws='docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
  -e AWS_DEFAULT_REGION -e AWS_ENDPOINT_URL \
  --add-host=host.docker.internal:host-gateway amazon/aws-cli'
export AWS_ENDPOINT_URL=http://host.docker.internal:4566
```

---

## Verification Methods (How)

| Method | AWS Console equivalent | What it proves |
|--------|------------------------|----------------|
| **CFN stack status** | CloudFormation → Stacks | Create/update/delete succeeded |
| **CFN resource inventory** | Stack → Resources tab | Logical resources and physical IDs exist |
| **CFN stack events** | Stack → Events | Which resource failed and why |
| **CFN outputs** | Stack → Outputs | Adapter-generated local URLs |
| **Service API describe/list** | EC2, ECS, ELB, Route53, etc. | Emulator backend populated records |
| **Template JSON diff** | — (pre-deploy) | Intended add/remove/change between transitions |
| **Adaptation report** | — (MiniStack-specific) | Explicit local divergences from canonical |
| **HTTP probe** | Browser / curl | Application responds |
| **Docker inspect** | ECS task → container | Real container running with expected port/volume |
| **Auth proxy / mock OIDC** | — (local substitute) | OIDC flow when ALB auth is in canonical template |

**Rule:** For infrastructure wiring (VPC, Route53 alias → ALB, Cognito pool present), trust **CFN + service APIs**. For user-facing reachability locally, trust **stack outputs + HTTP**, not public DNS or ALB forward behavior.

---

## Resource Matrix — Jenkins Fargate (MiniStack MVP)

Legend:

| Column | Meaning |
|--------|---------|
| **Canonical** | In `cdk.out/<stack>.template.json` when config enables the feature |
| **Deployed CFN** | In adapted template and expected in `list-stack-resources` after deploy |
| **Adapter** | Change applied before deploy (`none`, `remove`, `transform`, `local-only`) |
| **Verify via** | Primary checks (combine CFN inventory + service API where both apply) |
| **Local fidelity** | How closely runtime matches real AWS |

### Core networking and compute

| Resource type | Canonical | Deployed CFN | Adapter | Verify via | Local fidelity |
|---------------|-----------|--------------|---------|------------|----------------|
| `AWS::EC2::VPC` | Always | Yes | none | CFN inventory; `aws ec2 describe-vpcs` | Metadata + partial networking |
| `AWS::EC2::Subnet` | Always | Yes | none | CFN; `aws ec2 describe-subnets` | Same |
| `AWS::EC2::InternetGateway` | Always | Yes | none | CFN; `aws ec2 describe-internet-gateways` | Same |
| `AWS::EC2::SecurityGroup` | Always | Yes | none | CFN; `aws ec2 describe-security-groups` | Same |
| `AWS::EC2::SecurityGroupIngress` | Often (standalone) | **No** (inlined) | remove → merge into parent SG | Adaptation report; parent SG rules in `describe-security-groups` | Equivalent rule intent, different CFN shape |
| `AWS::ECS::Cluster` | Always | Yes | none | CFN; `aws ecs list-clusters` | Same |
| `AWS::ECS::TaskDefinition` | Always | Yes | none | CFN; `aws ecs describe-task-definition` | Runs real Docker task locally |
| `AWS::ECS::Service` | Always | Yes | none | CFN; `aws ecs describe-services` | Service exists; task maps to container |
| `AWS::IAM::Role` | Always | Yes | none | CFN; `aws iam get-role` (if supported) | Metadata / pass-through for task |
| `AWS::Logs::LogGroup` | Always | Yes | none | CFN; `aws logs describe-log-groups` | Log group recorded; log delivery varies |

### Load balancer

| Resource type | Canonical | Deployed CFN | Adapter | Verify via | Local fidelity |
|---------------|-----------|--------------|---------|------------|----------------|
| `AWS::ElasticLoadBalancingV2::LoadBalancer` | Always | Yes | deterministic name | CFN; `aws elbv2 describe-load-balancers` | ALB metadata; data plane via `/_alb/` path |
| `AWS::ElasticLoadBalancingV2::TargetGroup` | Always | Yes | none | CFN; `aws elbv2 describe-target-groups` | Registered; **not used for forward** |
| `AWS::ElasticLoadBalancingV2::Listener` | Always | Yes | redirect replaces forward/TLS redirect | CFN; `aws elbv2 describe-listeners` | **Redirect to localhost port**, not forward to ECS |
| `AWS::ElasticLoadBalancingV2::ListenerRule` | If present | Yes | auth actions stripped | CFN; `aws elbv2 describe-rules` | Same redirect/auth stripping rules |

**ALB behavior check:**

```bash
# Listener action should be redirect → localhost:<port> in adapted template
jq '.Resources[] | select(.Type=="AWS::ElasticLoadBalancingV2::Listener") |
  .Properties.DefaultActions' "cdk.out/${STACK_NAME}.ministack.template.json"

# Data-plane URL from stack outputs (redirect chain)
aws cloudformation describe-stacks --stack-name "$MINISTACK_STACK" \
  --query 'Stacks[0].Outputs[?OutputKey==`MiniStackLocalUrl`].OutputValue' --output text
curl -sIL "$(aws cloudformation describe-stacks --stack-name "$MINISTACK_STACK" \
  --query 'Stacks[0].Outputs[?OutputKey==`MiniStackLocalUrl`].OutputValue' --output text)" \
  | grep -E '^HTTP|^Location'
```

### Storage and scaling (adapted away)

| Resource type | Canonical | Deployed CFN | Adapter | Verify via | Local fidelity |
|---------------|-----------|--------------|---------|------------|----------------|
| `AWS::EFS::FileSystem` | Jenkins default | **No** | remove | Adaptation report; **absent** from CFN inventory | Replaced by host bind mount |
| `AWS::EFS::MountTarget` | Jenkins default | **No** | remove | Same | — |
| `AWS::EFS::AccessPoint` | Jenkins default | **No** | remove | Same | — |
| Host bind mount | — | **Runtime only** | local-only | Output `MiniStackHostVolume*`; `docker inspect` mount | Persists under `.ministack-volumes/<stack>/` |
| `AWS::ApplicationAutoScaling::*` | If configured | **No** | remove | Adaptation report; absent from CFN | No local autoscaling |

```bash
# Bind mount path from stack outputs
aws cloudformation describe-stacks --stack-name "$MINISTACK_STACK" \
  --query 'Stacks[0].Outputs[?starts_with(OutputKey, `MiniStackHostVolume`)].{Key:OutputKey,Path:OutputValue}' \
  --output table

docker ps --format '{{.Names}} {{.Mounts}}' | grep -i jenkins
```

### Domain and TLS (incremental)

| Resource type | Canonical | Deployed CFN | Adapter | Verify via | Local fidelity |
|---------------|-----------|--------------|---------|------------|----------------|
| `AWS::Route53::HostedZone` | `domain` set | Yes | none | CFN; `aws route53 list-hosted-zones` | **Emulator DNS only** — not your laptop resolver |
| `AWS::Route53::RecordSet` | domain / subdomain | Yes | none | CFN; `aws route53 list-resource-record-sets` | Alias → ALB DNS verifiable via API |
| `AWS::CertificateManager::Certificate` | `enableSsl: true` | Yes | none | CFN; `aws acm list-certificates` | Certificate resource exists; local HTTPS termination differs |
| HTTPS listener + cert | TLS enabled | Yes | TLS redirect may become HTTP redirect locally | `aws elbv2 describe-listeners` | Use outputs for browser URLs |

**Domain API check (source of truth — not browser DNS):**

```bash
aws cloudformation list-stack-resources --stack-name "$MINISTACK_STACK" \
  --query 'StackResourceSummaries[?contains(ResourceType,`Route53`)]' --output table

ZONE_ID=$(aws route53 list-hosted-zones \
  --query 'HostedZones[?Name==`ministack.local.`].Id' --output text | awk '{print $1}')
aws route53 list-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --query 'ResourceRecordSets[?Name==`jenkins.ministack.local.`]' --output json
```

See [Local DNS vs API verification](VERIFICATION.md#local-dns-vs-api-verification).

### Authentication (incremental) — *deferred for MiniStack MVP*

> Local auth runtime and browser login flow are **tabled** pending LocalStack evaluation. Adapter behavior and CFN inventory checks below still apply if you deploy with auth enabled; use `authMode: none` for MiniStack day-to-day testing.

| Resource type | Canonical | Deployed CFN | Adapter | Verify via | Local fidelity |
|---------------|-----------|--------------|---------|------------|----------------|
| `AWS::Cognito::UserPool` | `authMode: alb-oidc` + auto-provision | Yes | none | CFN; `aws cognito-idp list-user-pools` | Pool exists in emulator |
| `AWS::Cognito::UserPoolClient` | Auth enabled | Yes | none | CFN; `aws cognito-idp list-user-pool-clients` | Same |
| `AWS::Cognito::UserPoolDomain` | Auth enabled | Yes | none | CFN inventory | Same |
| ALB `authenticate-oidc` / `authenticate-cognito` | Auth enabled | **Stripped from listener** | transform | Adaptation report; listener `describe-listeners` has no auth action | **Not executed on ALB** |
| `MiniStackAuthProxy` + mock OIDC | — | **Runtime only** | local-only | `curl http://localhost:4180/_ministack/auth/health`; stack output `MiniStackAuthenticatedUrl` | Substitutes ALB edge auth |

```bash
# Cognito in stack
aws cloudformation list-stack-resources --stack-name "$MINISTACK_STACK" \
  --query 'StackResourceSummaries[?contains(ResourceType,`Cognito`)]' --output table

# Auth stripped in adapted template
jq '.[] | select(.reason | contains("authenticate"))' \
  "cdk.out/${STACK_NAME}.ministack-adaptations.json"

# Local auth runtime
curl -s http://localhost:4180/_ministack/auth/health
```

When auth is **removed** from config, expect Cognito resources absent from CFN inventory after update (same as AWS stack update).

---

## By Deployment Phase

What to assert after each incremental step (matches [Advanced — incremental deployments](ADVANCED.md#incremental-deployments)):

| Phase | Config flags | Assert in CFN inventory | Assert via service API | Assert runtime |
|-------|--------------|-------------------------|------------------------|----------------|
| **0 — Base** | no domain, no SSL, no auth | VPC, ALB, ECS, IAM, Logs | `describe-load-balancers`, `list-clusters` | `MiniStackApplicationUrl` HTTP `< 500`; Docker container |
| **1 — Domain** | `domain`, optional `subdomain`, `createZone` | + Route53 zone + records | `list-resource-record-sets` alias → ALB | FQDN in browser **optional** |
| **2 — TLS** | `enableSsl: true` | + ACM cert, HTTPS listener | `acm list-certificates`, listener port 443 | Canonical has cert; local browser may still use output URLs |
| **3 — Auth** | `authMode: alb-oidc` *(deferred)* | + Cognito resources in CFN if deployed | Cognito APIs | Auth proxy — **not active by default** |
| **4 — Remove auth** | `authMode: none` *(deferred)* | Cognito gone | — | — |
| **5 — Remove domain** | clear domain | Route53 gone | Zones/records removed | — |

**No-op redeploy:** identical adapted template → deployer reports no change set; CFN stack status unchanged.

---

## Template-Level Verification (Pre- and Post-Deploy)

Compare **canonical vs adapted vs deployed inventory**:

```bash
cd cfc-testing

# Resource types — canonical AWS
jq -r '.Resources | to_entries[] | .value.Type' \
  "cdk.out/${STACK_NAME}.template.json" | sort | uniq -c

# Resource types — adapted (what CFN receives)
jq -r '.Resources | to_entries[] | .value.Type' \
  "cdk.out/${STACK_NAME}.ministack.template.json" | sort | uniq -c

# Every adapter change with reason
jq '.[] | {path, reason}' "cdk.out/${STACK_NAME}.ministack-adaptations.json"

# Deployed inventory (live)
aws cloudformation list-stack-resources --stack-name "$MINISTACK_STACK" \
  --query 'StackResourceSummaries[].ResourceType' --output text | tr '\t' '\n' | sort | uniq -c
```

**Parity rule:** Deployed CFN resource **types** should match the adapted template (not the canonical template). Differences from canonical must appear in `.ministack-adaptations.json`.

For transition testing, use `CloudFormationTemplateDiff` (in `cloudforge-ministack`) between canonical templates at each config step — see [Verification](VERIFICATION.md).

---

## What Is Not Verified on MiniStack

These appear in **canonical** templates for AWS compliance/production profiles but are **out of scope** for MiniStack local MVP. Do not expect them in deployed CFN inventory or emulator APIs:

| Resource / concern | Verified on AWS | MiniStack local |
|--------------------|-----------------|-----------------|
| `AWS::Config::*` | CDK `Template.fromStack()` integration tests | Not deployed |
| `AWS::CloudTrail::*` | Same | Not deployed |
| `AWS::GuardDuty::*` | Same | Not deployed |
| `AWS::WAFv2::*` | Same | Not deployed |
| Compliance Config rules / audit posture | [COMPLIANCE_TRUTH_TABLES.md](../testing/COMPLIANCE_TRUTH_TABLES.md) | Not emulated |
| Public DNS propagation | Route53 + registrar | Emulator-only Route53 |
| ALB → ECS forward | Real target health | Redirect to localhost |
| ALB edge OIDC/Cognito | Listener authenticate actions | Auth proxy + mock OIDC |
| EFS NFS | Mount in task | Host bind mount |
| Application Auto Scaling | CFN + ECS scaling | Removed by adapter |

---

## Quick Commands Reference

```bash
# Full stack picture
aws cloudformation describe-stacks --stack-name "$MINISTACK_STACK"
aws cloudformation list-stack-resources --stack-name "$MINISTACK_STACK" --output table
aws cloudformation describe-stacks --stack-name "$MINISTACK_STACK" \
  --query 'Stacks[0].Outputs' --output table

# Built-in verify (outputs + HTTP poll)
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.ministack.MiniStackCli verify "$MINISTACK_STACK"

# Ground truth container
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -i jenkins
```

---

## Related Documentation

- [Verification layers](VERIFICATION.md) — operational walkthrough
- [Advanced adaptations](ADVANCED.md#template-adaptations) — why resources change
- [INTEGRATION_TESTS.md](../testing/INTEGRATION_TESTS.md) — AWS template tests (separate from MiniStack deploy)
- [Extended Testing](../guides/EXTENDED-TESTING.md) — synthesis and validation scripts

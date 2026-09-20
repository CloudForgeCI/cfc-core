# MiniStack Advanced Configuration

Stack outputs, template adaptations, the optional local auth runtime, incremental deployments, and environment variables.

See also: [Deployment](DEPLOYMENT.md) · [Verification](VERIFICATION.md) · [Troubleshooting](TROUBLESHOOTING.md)

---

## Stack Outputs

The adapter adds local outputs without replacing AWS outputs:

| Output | When present | Example |
|--------|--------------|---------|
| `MiniStackLocalUrl` | ALB present | `http://localhost:4566/_alb/<lb-name>/` |
| `MiniStackApplicationUrl` | ECS service with container port | `http://localhost:<port>` |
| `MiniStackAuthenticatedUrl` | ALB auth in canonical template | `http://<lb-name>.ministack.localhost:4180` |
| `MiniStackHostVolume<VolumeName>` | EFS volume replaced with bind mount | `/path/to/.ministack-volumes/<stack>/<volume>` |

Use `MiniStackApplicationUrl` for browser and HTTP checks and `MiniStackLocalUrl` for ALB data-plane checks. `MiniStackAuthenticatedUrl` is only reachable when the [local auth runtime](#local-auth-runtime) is enabled.

Route53 FQDNs from the canonical template (for example `jenkins.ministack.local`) are verified via the AWS API against MiniStack; they do not need to resolve in your browser. See [Verification: Local DNS vs API](VERIFICATION.md#local-dns-vs-api-verification).

---

## Template Adaptations

`MiniStackTemplateAdapter` copies the canonical template and applies only the changes MiniStack requires. Each change is recorded in `<stackName>.ministack-adaptations.json` as `{path, reason, original}`.

| Adaptation | Reason |
|------------|--------|
| Remove `AWS::ApplicationAutoScaling::*` | MiniStack CloudFormation does not support Application Auto Scaling |
| Remove `AWS::EFS::*` | MiniStack CloudFormation does not support EFS |
| Replace EFS task volumes with host bind mounts | Local persistence at `MINISTACK_VOLUME_ROOT/<stack>/<volume>` (default `.ministack-volumes/`) |
| Inline `AWS::EC2::SecurityGroupIngress` into the parent security group | Standalone ingress resources are not supported |
| Replace ALB `forward` to ECS with `redirect` to `localhost:<appPort>` | MiniStack ALB cannot forward to ECS targets |
| Remove `authenticate-oidc` / `authenticate-cognito` actions | MiniStack ALB supports forward/redirect/fixed-response actions only |
| Add a deterministic ALB name | Stable local URL generation |

Inspect adaptations:

```bash
jq '.[] | {path, reason}' cdk.out/<stackName>.ministack-adaptations.json
```

Resource types MiniStack cannot create at all (RDS, WAF, Config, CloudTrail, GuardDuty, Backup) are not adapted; [preflight](README.md#deploy-preflight-option-6) blocks the deploy instead.

---

## Local Auth Runtime

MiniStack's ALB does not execute `authenticate-oidc` or `authenticate-cognito` listener actions. When the canonical template includes ALB authentication, the adapter removes those actions and adds a `MiniStackAuthenticatedUrl` output, but no proxy or mock OIDC provider starts unless you opt in. For day-to-day MiniStack testing, use `authMode: none`; to test sign-in locally, prefer [LocalStack](../localstack/README.md) with `application-oidc`. `CanonicalTemplateParityTest` (in `cfc-testing`) still validates auth resources in the canonical AWS template.

With `MINISTACK_AUTH_AUTOSTART=true`, the deploy pipeline reconciles local auth services after each MiniStack deploy:

| Auth in template | Runtime action |
|------------------|----------------|
| Enabled | Start `mock-oidc` (from the repository `docker-compose.yml`) and a detached `MiniStackAuthProxy` JVM |
| Disabled | Stop the managed proxy and `mock-oidc` |

Auth proxy defaults:

- Listens on `http://localhost:4180` (override with `MINISTACK_AUTH_PORT`)
- Proxies to the ECS application URL from stack outputs
- Uses mock OIDC at `http://localhost:3001`
- Health endpoint: `http://localhost:4180/_ministack/auth/health`

```bash
export MINISTACK_AUTH_AUTOSTART=true      # opt in (default: false)
export MINISTACK_MOCK_OIDC_MANAGED=false  # manage mock-oidc yourself instead of via docker compose
```

Proxy log, PID file, and upstream state: `cfc-testing/target/ministack-runtime/`.

```text
Browser → MiniStackAuthProxy (:4180)
       → mock-oidc (:3001) for authorization + token exchange
       → ECS application (MiniStackApplicationUrl)
```

---

## Incremental Deployments

CloudForge supports incremental configuration changes on one stack: load balancer only → domain → subdomain → TLS. Change `deployment-context.json` and redeploy with option **6**.

1. Deploy with no domain: VPC, ALB, ECS only
2. Add `domain` / `subdomain`: Route53 resources appear in the change set
3. Enable TLS (`enableSsl: true`): listener protocol and certificate changes

Adding or removing ALB auth also produces a valid change set, but the auth actions are stripped locally (see above).

Identical redeployments are treated as **no-op** when the deployed template matches the candidate (deep JSON comparison).

**Constraint:** MiniStack must stay running. It runs with `PERSIST_STATE=0`, so a container restart wipes stack metadata.

**Domain step:** Confirm Route53 via `aws route53 list-resource-record-sets` against MiniStack, not by opening the FQDN in a browser. See [Local DNS vs API verification](VERIFICATION.md#local-dns-vs-api-verification).

Use `CloudFormationTemplateDiff` (in `cloudforge-core`) to compare template versions between transitions in tests.

---

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `AWS_ENDPOINT_URL` | `http://localhost:4566` | MiniStack gateway for the AWS CLI and `MiniStackDeployer` |
| `AWS_DEFAULT_REGION` | `us-east-1` | Region passed to AWS SDK clients |
| `CFC_CONTEXT_FILE` | `deployment-context.json` | Deployment context read by the Interactive Deployer |
| `INTERACTIVE` | unset | When `true`, the Interactive Deployer ignores a saved context and re-runs the questionnaire |
| `MINISTACK_PREFLIGHT` | `enforce` | Deploy preflight mode: `enforce`, `warn`, or `off` |
| `CFC_MINISTACK_REPLACE_SAME_APP` | enabled | Set `false` or `0` to keep earlier MiniStack stacks for the same `applicationId` |
| `MINISTACK_VOLUME_ROOT` | `.ministack-volumes` | Host directory root for EFS → bind-mount replacements (`<root>/<stackName>/<volumeName>`) |
| `MINISTACK_REGION` | `us-east-1` | Region passed to the MiniStack container at start |
| `MINISTACK_LOG_LEVEL` | `WARNING` | MiniStack container log level |
| `CFC_EMULATOR_COMPANIONS` | `true` | Set `false` to skip StackPort and the emulator edge on platform start |
| `MINISTACK_HTTP_VERIFY` | `true` | HTTP poll in `MiniStackCli verify` |
| `MINISTACK_AUTH_AUTOSTART` | `false` | Start/stop the local auth runtime after deploy |
| `MINISTACK_MOCK_OIDC_MANAGED` | `true` | Manage `mock-oidc` via Docker Compose |
| `MINISTACK_AUTH_PORT` | `4180` | Local auth proxy listen port |
| `MINISTACK_AUTH_UPSTREAM` | from stack output | ECS application URL for the proxy |
| `MINISTACK_OIDC_AUTHORIZATION_ENDPOINT` | `http://localhost:3001/oauth/authorize` | Mock OIDC authorize URL |
| `MINISTACK_OIDC_TOKEN_ENDPOINT` | `http://localhost:3001/oauth/token` | Mock OIDC token URL |
| `MINISTACK_OIDC_CLIENT_ID` | `cfc-client` | OIDC client ID |
| `MINISTACK_OIDC_CLIENT_SECRET` | `cfc-secret` | OIDC client secret (local mock only) |
| `MINISTACK_OIDC_REDIRECT_URI` | `http://localhost:4180/oauth2/callback` | Auth proxy callback |

---

## Dry Run Without Deploying

Interactive Deployer option **4** synthesizes, adapts the template, and writes the adaptation report without calling MiniStack:

```bash
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
# Choose 4
```

Review `cdk.out/<stackName>.ministack.template.json` and `<stackName>.ministack-adaptations.json` before the first deploy.

---

## Next Steps

- [Verify deployments](VERIFICATION.md)
- [Extended Testing](../guides/EXTENDED-TESTING.md)
- [Troubleshooting](TROUBLESHOOTING.md)

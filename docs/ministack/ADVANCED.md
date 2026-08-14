# MiniStack Advanced Configuration

Auth proxy, incremental deployments, template adaptations, stack outputs, and environment variables.

See also: [Deployment](DEPLOYMENT.md) · [Verification](VERIFICATION.md) · [Troubleshooting](TROUBLESHOOTING.md)

---

## Stack Outputs

The adapter adds local outputs without replacing AWS outputs:

| Output | When present | Example |
|--------|--------------|---------|
| `MiniStackLocalUrl` | ALB present | `http://localhost:4566/_alb/<lb-name>/` |
| `MiniStackApplicationUrl` | ECS service with container port | `http://localhost:<port>` |
| `MiniStackAuthenticatedUrl` | ALB auth in canonical template | `http://<lb-name>.ministack.localhost:4180` |
| `MiniStackHostVolume<Volumename>` | EFS volume replaced with bind mount | `/path/to/.ministack-volumes/<stack>/<volume>` |

Use `MiniStackLocalUrl` for direct ALB data-plane access. Use `MiniStackAuthenticatedUrl` when testing OIDC login flows locally. For base deployments without auth, only the first two outputs appear.

Route53 FQDNs from the canonical template (e.g. `jenkins.ministack.local`) are verified via the AWS API against MiniStack — they do not need to resolve in your local browser. See [Verification — Local DNS vs API](VERIFICATION.md#local-dns-vs-api-verification).

---

## Template Adaptations

`MiniStackTemplateAdapter` copies the canonical template and applies only the changes MiniStack requires. Each change is recorded in `.ministack-adaptations.json`.

| Adaptation | Reason |
|------------|--------|
| Remove `AWS::EFS::*` | MiniStack CloudFormation does not support EFS |
| Replace EFS task volumes with host bind mounts | Local persistence at `MINISTACK_VOLUME_ROOT/<stack>/<volume>` (default `.ministack-volumes/`) |
| Remove `AWS::ApplicationAutoScaling::*` | Application Auto Scaling is not supported in MiniStack CFN |
| Inline `AWS::EC2::SecurityGroupIngress` | Standalone ingress resources unsupported |
| Replace ALB `forward` to ECS with `redirect` | MiniStack ALB cannot forward to ECS targets |
| Remove `authenticate-oidc` / `authenticate-cognito` | ALB cannot execute auth actions locally; stripped from adapted template *(local auth runtime deferred — see below)* |
| Add deterministic ALB name | Stable local URL generation |

Review the adaptation report after every deploy to confirm local behavior matches expectations.

Inspect adaptations:

```bash
jq '.[] | {path, reason}' cdk.out/<stack>.ministack-adaptations.json
```

---

## Local Auth Runtime *(deferred)*

> **Status:** Local auth is **tabled** for MiniStack MVP. Code remains (`MiniStackAuthProxy`, `MiniStackLocalRuntime`, mock OIDC) but **`MINISTACK_AUTH_AUTOSTART` defaults to `false`**. Revisit when LocalStack is running — LocalStack covers more services and may support ALB/Cognito auth without a custom proxy. If not, auth runtime may live only in the LocalStack module.

MiniStack ALB does not execute `authenticate-oidc` or `authenticate-cognito` listener actions. When the canonical template includes ALB authentication, the adapter still removes those actions and may add a `MiniStackAuthenticatedUrl` output — but **no proxy or mock OIDC starts** unless you explicitly opt in.

For MiniStack testing, use `authMode: none` in `deployment-context.json`. Canonical AWS templates and `CanonicalTemplateParityTest` still validate auth resources for the real AWS path.

When re-enabled (`MINISTACK_AUTH_AUTOSTART=true`), the Interactive Deployer reconciles local auth services after deploy:

| Auth in template | Runtime action |
|------------------|----------------|
| Enabled | Start `mock-oidc` (Docker Compose) and a detached `MiniStackAuthProxy` JVM |
| Disabled | Stop managed proxy and `mock-oidc` |

Auth proxy defaults:

- Listens on `http://localhost:4180` (override with `MINISTACK_AUTH_PORT`)
- Proxies to the ECS application URL from stack outputs
- Uses mock OIDC at `http://localhost:3001`

Disable auto-start (default) or manage processes yourself:

```bash
export MINISTACK_AUTH_AUTOSTART=false   # default — auth deferred
export MINISTACK_AUTH_AUTOSTART=true    # opt-in to legacy MiniStack auth proxy
export MINISTACK_MOCK_OIDC_MANAGED=false   # skip docker compose for mock-oidc
```

Proxy logs and PID file: `cfc-testing/target/ministack-runtime/`.

### Auth flow

```text
Browser → MiniStackAuthProxy (:4180)
       → mock-oidc (:3001) for authorization + token exchange
       → ECS application (MiniStackApplicationUrl)
```

---

## Incremental Deployments

CloudForge supports incremental configuration changes (load balancer only → domain → subdomain → TLS → Cognito auth → remove auth). **MiniStack MVP tests and docs cover through TLS only**; auth transitions are deferred pending LocalStack evaluation.

Example transition sequence (MiniStack MVP):

1. Deploy with domain disabled — VPC, ALB, ECS only
2. Add domain/subdomain — Route53 resources appear in change set
3. Enable TLS — listener protocol/certificate changes

Deferred (LocalStack evaluation):

4. Enable Cognito ALB auth — canonical template gains auth actions; adapter strips them
5. Remove auth — auth actions gone from canonical template

Identical redeployments are treated as **no-op** when the deployed template matches the candidate (deep JSON comparison).

**Constraint:** MiniStack must stay running (`PERSIST_STATE=0` means restarts wipe stack metadata). Incremental updates do not survive a MiniStack container restart.

**Domain step:** Confirm Route53 via `aws route53 list-resource-record-sets` against MiniStack — not via opening the FQDN in a browser. See [Local DNS vs API verification](VERIFICATION.md#local-dns-vs-api-verification).

Use `CloudFormationTemplateDiff` (in `cloudforge-core`) to compare template versions between transitions during test development.

---

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `AWS_ENDPOINT_URL` | `http://localhost:4566` | MiniStack gateway — AWS CLI **and** Java (`MiniStackDeployer`). Sole endpoint env var. |
| `AWS_DEFAULT_REGION` | `us-east-1` | Region passed to AWS SDK clients |
| `INTERACTIVE` | unset | When `true`, `cdk synth` opens Interactive Deployer |
| `MINISTACK_VOLUME_ROOT` | `.ministack-volumes` | Host directory root for EFS→bind-mount replacements (`<root>/<stackName>/<volumeName>`) |
| `MINISTACK_AUTH_AUTOSTART` | `false` | Auto-start/stop auth runtime after deploy *(deferred — set `true` to opt in)* |
| `MINISTACK_MOCK_OIDC_MANAGED` | `true` | Manage `mock-oidc` via Docker Compose |
| `MINISTACK_AUTH_PORT` | `4180` | Local auth proxy listen port |
| `MINISTACK_AUTH_UPSTREAM` | from stack output | ECS application URL for proxy |
| `MINISTACK_OIDC_AUTHORIZATION_ENDPOINT` | `http://localhost:3001/oauth/authorize` | Mock OIDC authorize URL |
| `MINISTACK_OIDC_TOKEN_ENDPOINT` | `http://localhost:3001/oauth/token` | Mock OIDC token URL |
| `MINISTACK_OIDC_CLIENT_ID` | `cfc-client` | OIDC client ID |
| `MINISTACK_OIDC_CLIENT_SECRET` | `cfc-secret` | OIDC client secret |
| `MINISTACK_OIDC_REDIRECT_URI` | `http://localhost:4180/oauth2/callback` | Auth proxy callback |
| `MINISTACK_HTTP_VERIFY` | `true` | HTTP poll in `MiniStackCli verify` |

---

## Dry-Run Without Deploying

Interactive Deployer option **4** adapts the template and writes the audit report without calling MiniStack:

```bash
java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer
# Choose 4 after synthesis (writes adapted template + report; does not deploy)
```

Review `cdk.out/<stack>.ministack.template.json` and `.ministack-adaptations.json` before first deploy.

---

## Next Steps

- [Verify deployments](VERIFICATION.md)
- [Extended Testing](../guides/EXTENDED-TESTING.md)
- [Troubleshooting](TROUBLESHOOTING.md)

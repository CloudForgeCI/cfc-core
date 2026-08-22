# LocalStack Deployable Applications

Summary of CloudForge apps on LocalStack (Interactive Deployer option **8**). For MiniStack-only apps and shared commands, see the **[full catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md)**.

Requires `LOCALSTACK_AUTH_TOKEN` and a running LocalStack selected from `InteractiveDeployer --platform`.

---

## All discovered applications (37+)

LocalStack supports **every** ServiceLoader plugin when the probed tier exposes required capabilities:

| Capability | Required for |
|------------|--------------|
| ECS + ELBV2 | All Fargate stacks (default) |
| RDS | `provisionDatabase: true`, `requiresDatabase()`, or `AWS::RDS::*` in template |
| EC2 + Auto Scaling | `runtime: ec2` |
| EFS / Backup (Ultimate) | Native resources; Base tier adapts EFS to bind mounts and strips Backup |

Preflight: `LOCALSTACK_PREFLIGHT=enforce` (default). Details are in the [README preflight](README.md#deploy-preflight-option-8).

---

## Without RDS (same 13 as MiniStack)

| Application ID | Port | Sample context |
|----------------|------|----------------|
| `cloudforge-manager` | 1958 | Extend `CloudForgeManager-Dev.json` |
| `jenkins` | 8080 | `Jenkins-Stack-LocalStack.json` (domain/Cognito example) |
| `grafana` | 3000 | `Grafana-Stack.json` |
| `prometheus` | 9090 | `Prometheus-Stack.json` |
| `metabase` | 3000 | `Metabase-Stack.json` |
| `gitea` | 3000 | `Gitea-Stack.json` |
| `drone` | 80 | `Drone-Stack.json` |
| `vault` | 8200 | `Vault-Stack.json` |
| `redis` | 6379 | `Redis-Stack.json` |
| `nexus` | 8081 | `Nexus-Stack.json` |
| `postgresql` | 5432 | `PostgreSQL-Stack.json` |
| `sonarqube` | 9000 | `SonarQube-Stack.json` |

LocalStack keeps ALB→ECS **forward** (MiniStack redirects to `localhost:<port>`).

---

## With RDS (LocalStack only among local emulators)

Set `provisionDatabase: true` and use full deployment context (boolean fields, `authMode`, etc.).

| Application ID | Port | Notes |
|----------------|------|-------|
| `gitlab` | 80 | Long startup |
| `harbor` | 80 | Heavy stack |
| `superset` | 8088 | |
| `mattermost-enterprise` | 8065 | Sample: `Mattermost-Stack-LocalStack.json` |
| `mattermost-team` | 8065 | |
| **All CMS plugins** | mostly 80 | WordPress, Drupal, Magento, WooCommerce, Joomla, Typo3, phpBB, Moodle, MediaWiki, OpenCart, PrestaShop, Sylius, Bagisto, Flarum, MyBB, SuiteCRM, Concrete, October, Dolphin UNA, Craft CMS (sample) |

Verify container images exist locally; some enterprise tags may fail to pull.

---

## Blocked on MiniStack only

Apps in the RDS/CMS tables above are **blocked on MiniStack option 6** by preflight but deploy here on option **8**.

---

## Related

- [LocalStack README](README.md)
- [Full emulator app catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md)

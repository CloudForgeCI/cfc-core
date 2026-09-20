# LocalStack Deployable Applications

Which CloudForge applications deploy on LocalStack (Interactive Deployer option **8**). For MiniStack and shared commands, see the **[full catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md)**.

Requires `LOCALSTACK_AUTH_TOKEN` and a LocalStack container started from `InteractiveDeployer --platform`.

---

## Capability requirements

LocalStack can deploy every discovered application plugin when the probed tier exposes the capabilities the deployment needs:

| Capability | Required for |
|------------|--------------|
| ECS + ELBv2 | All Fargate stacks |
| RDS | `provisionDatabase: true`, applications that require a database, or `AWS::RDS::*` in the template |
| EC2 + Auto Scaling | `runtime: EC2` |
| EFS / Backup (Ultimate) | Native resources; on Base tier the adapter uses bind mounts for EFS and removes Backup resources |

Preflight runs with `LOCALSTACK_PREFLIGHT=enforce` by default. See [Deploy preflight](README.md#deploy-preflight-option-8).

Sample contexts are in `cfc-testing/deployment-contexts/`.

---

## Without RDS

The same 12 applications MiniStack supports:

| Application ID | Port | Sample context |
|----------------|------|----------------|
| `cloudforge-manager` | 1958 | `CloudForgeManager-Dev.json` (also provisions RDS; on LocalStack the Manager container uses its embedded H2 store) |
| `jenkins` | 8080 | `Jenkins-Stack-LocalStack.json` (domain, TLS, Cognito `application-oidc`) |
| `grafana` | 3000 | `Grafana-Stack.json` |
| `prometheus` | 9090 | `Prometheus-Stack.json` |
| `metabase` | 3000 | `Metabase-Stack-LocalStack.json` |
| `gitea` | 3000 | `Gitea-Stack.json` |
| `drone` | 80 | `Drone-Stack.json` |
| `vault` | 8200 | `Vault-Stack.json` |
| `redis` | 6379 | `Redis-Stack.json` |
| `nexus` | 8081 | `Nexus-Stack.json` |
| `postgresql` | 5432 | `PostgreSQL-Stack.json` |
| `sonarqube` | 9000 | `SonarQube-Stack.json` |

The adapter redirects ALB listeners to `http://localhost:<appPort>`, so only one stack can hold a host port at a time. Applications on port 80 (Drone and most CMS images) keep the ALB forward and are reached through the path-style URL instead, because the emulator edge holds host port 80.

---

## With RDS (LocalStack only among local emulators)

These applications require a database, so `provisionDatabase` is enabled automatically in the interactive flow; set `provisionDatabase: true` in a saved context.

| Application ID | Port | Notes |
|----------------|------|-------|
| `gitlab` | 80 | Long startup |
| `harbor` | 80 | |
| `superset` | 8088 | |
| `mattermost-enterprise` | 8065 | Sample: `Mattermost-Stack-LocalStack.json` |
| `mattermost-team` | 8065 | |
| CMS and commerce plugins | 80 (phpBB: 8080) | WordPress, WooCommerce, Drupal, Joomla (`Joomla-Stack-LocalStack.json`), Magento, TYPO3, Concrete CMS, October CMS, MediaWiki, Moodle, OpenCart, PrestaShop, Sylius, Bagisto, Flarum, MyBB, phpBB, SuiteCRM, UNA (`dolphin-una`), and the Craft CMS sample plugin in `cfc-testing` |

Check that the container images pull on your machine before deploying; some tags are large or may be unavailable.

These applications are blocked on MiniStack (option **6**) by preflight.

---

## Related

- [LocalStack README](README.md)
- [MiniStack deployable applications](../ministack/DEPLOYABLE_APPS.md)
- [Full emulator app catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md)

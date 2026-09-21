# MiniStack Deployable Applications

Which CloudForge applications deploy on MiniStack (Interactive Deployer option **6**). For LocalStack and shared deploy commands, see the **[full catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md)**.

---

## Supported (12 applications)

Applications that do not require RDS. Deploy with `provisionDatabase` unset or `false`, and `authMode: none`. Sample contexts are in `cfc-testing/deployment-contexts/`.

| Application ID | Port | Sample context |
|----------------|------|----------------|
| `cloudforge-manager` | 1958 | `CloudForgeManager-Fresh.json` |
| `jenkins` | 8080 | `Jenkins-Stack.json` |
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

`cloudforge-manager` comes from the `cloudforge-manager-deployment` plugin; the others are built into `cloudforge-api`.

Batch deploy of the non-Jenkins, non-Grafana, non-Manager samples: `cfc-testing/scripts/deploy-ministack-apps.sh` (skips stacks already in `CREATE_COMPLETE`).

---

## Host-port limits

MiniStack binds each application to **`localhost:<appPort>`**, so only one stack can use a port at a time. Preflight blocks a deploy whose port is held by another MiniStack stack.

- **3000**: Grafana, Gitea, or Metabase (one at a time)
- **80**: Drone (privileged port on some hosts; also used by the emulator edge)

---

## Blocked: use LocalStack or AWS

Preflight blocks applications that require RDS:

- GitLab, Harbor, Superset, Mattermost (`mattermost-enterprise`, `mattermost-team`)
- All CMS, e-commerce, forum, wiki, LMS, and CRM plugins (WordPress, WooCommerce, Drupal, Joomla, Magento, …) and the Craft CMS sample plugin in `cfc-testing`

Deploy these with option **8** ([LocalStack](../localstack/DEPLOYABLE_APPS.md)) or to AWS. See [preflight](README.md#deploy-preflight-option-6).

---

## Related

- [MiniStack README](README.md)
- [Deployment guide](DEPLOYMENT.md)
- [Full emulator app catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md)

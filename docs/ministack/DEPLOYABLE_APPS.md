# MiniStack Deployable Applications

Summary of which CloudForge apps work on MiniStack (Interactive Deployer option **6**). For LocalStack and shared deploy commands, see the **[full catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md)**.

---

## Supported (13 apps)

Deploy with `provisionDatabase: false` and `authMode: none` unless testing auth elsewhere.

| Application ID | Port | Sample context |
|----------------|------|----------------|
| `cloudforge-manager` | 1958 | `deployment-contexts/CloudForgeManager-Dev.json` |
| `jenkins` | 8080 | `deployment-contexts/Jenkins-Stack.json` |
| `grafana` | 3000 | `deployment-contexts/Grafana-Stack.json` |
| `prometheus` | 9090 | `deployment-contexts/Prometheus-Stack.json` |
| `metabase` | 3000 | `deployment-contexts/Metabase-Stack.json` |
| `gitea` | 3000 | `deployment-contexts/Gitea-Stack.json` |
| `drone` | 80 | `deployment-contexts/Drone-Stack.json` |
| `vault` | 8200 | `deployment-contexts/Vault-Stack.json` |
| `redis` | 6379 | `deployment-contexts/Redis-Stack.json` |
| `nexus` | 8081 | `deployment-contexts/Nexus-Stack.json` |
| `postgresql` | 5432 | `deployment-contexts/PostgreSQL-Stack.json` |
| `sonarqube` | 9000 | `deployment-contexts/SonarQube-Stack.json` (cfc-testing plugin) |

Batch deploy: `cfc-testing/scripts/deploy-ministack-apps.sh`

---

## Host-port limits

MiniStack binds **`localhost:<appPort>`**. Only one stack per port:

- **3000** — Grafana, Gitea, or Metabase (one at a time)
- **80** — Drone (privileged port on some hosts)

---

## Blocked — use LocalStack (10) or AWS (2)

**RDS required:** GitLab, Harbor, Superset, Mattermost, all CMS/e-commerce/forum apps (WordPress, Drupal, Magento, …), Craft CMS sample plugin.

Preflight blocks before CloudFormation. See [preflight in README](README.md#deploy-preflight-option-8).

---

## Related

- [MiniStack README](README.md)
- [Deployment guide](DEPLOYMENT.md)
- [Full emulator app catalog](../guides/LOCAL_EMULATOR_APP_CATALOG.md)

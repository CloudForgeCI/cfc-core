# Local Emulator Edge (nginx)

Port-free browser URLs for MiniStack and LocalStack apps. **nginx** terminates HTTP on `:80`, routes by `Host` (`*.cloudforge.localhost`) to Docker-published ECS ports on the host.

See also: [Local hostnames](LOCAL_EMULATOR_HOSTS.md) · [Quick Start](LOCAL_EMULATOR_QUICK_START.md) · [StackPort](../localstack/README.md#resource-browser-stackport)

---

## nginx vs Caddy (and “web console”)

| | **nginx** (this guide) | **Caddy** | **Traefik** |
|--|------------------------|-----------|-------------|
| Reverse proxy by `Host` | Excellent | Excellent | Excellent |
| Auto HTTPS / ACME | Manual or certbot | Built-in | Built-in / labels |
| Config style | Familiar `server { }` | Caddyfile | Dynamic file/labels |
| Built-in **app** console | **No** (OSS) | No | Dashboard of *routers*, not app UIs |
| Commercial dashboard | nginx Plus (paid) | — | — |

**Neither nginx nor Caddy opens a console “into” Jenkins/Grafana.** They only proxy HTTP.

| Need | Use |
|------|-----|
| Browse CloudFormation / ECS / RDS in the emulator | **[StackPort](../localstack/README.md#resource-browser-stackport)** on `:8888` |
| Manage proxy hostnames in a GUI | Optional later: [nginx Proxy Manager](https://nginxproxymanager.com/) (separate product) |
| Day-to-day CloudForge edge | **nginx** + generated `conf.d` (this doc) |

We chose **nginx** because CloudForge operators often already know it, configs are easy to audit in git, and the “deep dive” into apps is already covered by StackPort + Manager — not by the reverse proxy.

---

## Architecture

```text
  http://jenkins.cloudforge.localhost/          ← no port
            │
            ▼
  /etc/hosts  →  127.0.0.1
            │
            ▼
  cfc-emulator-edge (nginx :80)
            │  proxy_pass http://host.docker.internal:8080
            ▼
  ECS task on Docker host (MiniStack or LocalStack)
```

**Prerequisites**

1. Install hostnames: `./scripts/setup-cloudforge-local-hosts.sh`
2. One emulator running on `:4566`
3. Docker (edge runs as `cfc-emulator-edge`)

---

## Quick start (Maven — preferred)

`ministack-start` / `localstack-start` bring up the **full local stack** in one process: emulator on `:4566`, **StackPort** (simulated AWS console) on `:8888`, and **nginx edge** on `:80`. Companion lifecycle (start / stop / restart / status) is owned by `EmulatorLifecycle` in `cloudforge-core`. After a local deploy, `CloudForgeDeployment` reconciles edge vhosts automatically.

```bash
# From repository root — once
./scripts/setup-cloudforge-local-hosts.sh

cd cfc-testing && java -cp "target/classes:target/dependency/*" \
  com.cloudforgeci.samples.app.InteractiveDeployer --platform

# Deploy apps (Interactive Deployer option 6 / 8) — edge reconciles after deploy
open "http://localstack.cloudforge.localhost/"
open "http://stackport.cloudforge.localhost/"
open "http://nginx.cloudforge.localhost/"
open "http://jenkins.cloudforge.localhost/"     # after Jenkins publish
```

Opt out of companions: `CFC_EMULATOR_COMPANIONS=false`, or individually `CFC_STACKPORT_AUTOSTART=false` / `CFC_EDGE_AUTOSTART=false`. Alternate HTTP port: `CFC_EDGE_HTTP_PORT=8088`.

### Edge lifecycle goals (also driven by emulator start/stop)

| Goal | Action |
|------|--------|
| `cloudforge:emulator-edge-start` | Create/start `cfc-emulator-edge` |
| `cloudforge:emulator-edge-stop` | Remove edge container |
| `cloudforge:emulator-edge-restart` | Stop + start |
| `cloudforge:emulator-edge-rebuild` | Pull image, recreate |
| `cloudforge:emulator-edge-status` | Running / healthy + routes |
| `cloudforge:emulator-edge-reconcile` | Rewrite vhosts from live host ports |
| `cloudforge:emulator-edge-reload` | `nginx -s reload` |

Shell scripts under `scripts/emulator-edge-*.sh` call these Maven goals (no parallel Docker logic).

Generated files live under `.emulator-edge/` (gitignored).

---

## How reconcile maps apps

Reconcile scans `docker ps` for host port publishes and matches **container ports** to the same table as [LOCAL_EMULATOR_HOSTS](LOCAL_EMULATOR_HOSTS.md):

| Container port | Hostname |
|----------------|----------|
| 4566 | `localstack.cloudforge.localhost` and/or `ministack.cloudforge.localhost` (+ `emulator.cloudforge.localhost`) |
| 8888 | `stackport.cloudforge.localhost` |
| (edge self) | `nginx.cloudforge.localhost` — status page, not a proxy |
| 1958 | `manager.cloudforge.localhost` |
| 8080 | `jenkins.cloudforge.localhost` |
| 3000 | `grafana.cloudforge.localhost` *(first occupant wins if clash)* |
| 9090 | `prometheus.cloudforge.localhost` |
| 8200 | `vault.cloudforge.localhost` |
| 8081 | `nexus.cloudforge.localhost` |
| 9000 | `sonarqube.cloudforge.localhost` |

Always available when the platform is up: `localstack` / `ministack` / `emulator`, `stackport`, and `nginx` (edge status).

### Route53 (next phase)

Emulator Route53 records already exist for stacks with `domain` / `subdomain`. A later reconcile pass will:

1. `route53 list-resource-record-sets` against `:4566`
2. Map FQDN → stack → ECS host port
3. Emit `server_name` blocks for those FQDNs (in addition to `*.cloudforge.localhost`)

Until then, use the shared `*.cloudforge.localhost` names.

---

## LocalStack Jenkins note

If Jenkins was adapted with a path `--prefix`, prefer the **direct ECS host port** (reconcile does this). Opening `http://jenkins.cloudforge.localhost/` should hit that port without the ELB path prefix.

---

## Troubleshooting

| Symptom | Check |
|---------|--------|
| Connection refused on `:80` | `mvn -f cfc-testing cloudforge:emulator-edge-status` — is Docker running? |
| 502 Bad Gateway | Backend port down — `docker ps` / reconcile again |
| Wrong app on hostname | Port collision (e.g. two apps on 3000) — stop one stack |
| Name does not resolve | Re-run `./scripts/setup-cloudforge-local-hosts.sh` |
| macOS “port 80 in use” | Something else bound `:80` (AirPlay Receiver on some macOS versions) — disable or set `CFC_EDGE_HTTP_PORT=8088` |

---

## What this does not do

- TLS / HTTPS (HTTP-only for local; mkcert optional later)
- ALB OIDC / Cognito (still stripped / deferred on MiniStack)
- Replace StackPort or CloudForge Manager
- Fix host-port collisions between apps

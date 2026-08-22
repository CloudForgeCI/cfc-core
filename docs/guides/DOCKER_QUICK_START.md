# CloudForge Docker Local Development — Quick Start

## Pre-Flight Checks

Before starting, ensure you have:

- [ ] **Docker Desktop 24.0+** (or Docker Engine 24+ with Compose plugin): `docker --version`
- [ ] **Docker Compose v2** (built-in plugin, NOT the standalone `docker-compose`): `docker compose version`
- [ ] At least **4 GB RAM** allocated to Docker (8 GB for full stack)
- [ ] At least **10 GB free disk** for volumes
- [ ] **Ports 3000–9099** available (or modify `docker-compose.yml`)
- [ ] Terminal open in the project root

### macOS only: Bash 5+

The management scripts use associative arrays and require **bash 4+**. macOS ships bash 3.2 by default.

```bash
brew install bash        # installs bash 5.x to /opt/homebrew/bin/bash
bash --version           # confirm: GNU bash, version 5.x
```

The scripts use `#!/usr/bin/env bash` which automatically picks up the Homebrew version.

---

## First-Time Startup

**1. Verify prerequisites**

```bash
docker ps                # Docker daemon is running
docker compose version   # Compose v2 plugin is available
bash --version           # bash 5.x (macOS requirement)
```

**2. Start the environment** — pick a group or start everything:

```bash
# Fastest useful subset (infrastructure + CMS apps)
./scripts/docker-start.sh infrastructure cms

# Just infrastructure (databases + mock OIDC)
./scripts/docker-start.sh infrastructure

# Full stack
./scripts/docker-start.sh all
```

Available groups: `infrastructure`, `cicd`, `monitoring`, `analytics`, `services`, `collaboration`, `cms`, `databases`, `core`, `all`

You can also combine groups or individual names:

```bash
./scripts/docker-start.sh infrastructure monitoring wordpress
```

The script pulls images, starts containers, waits for health checks, then prints URLs and credentials.

**3. Watch logs while services come up** (optional, separate terminal):

```bash
./scripts/docker-logs.sh -f
```

**4. Verify all services are healthy:**

```bash
./scripts/docker-status.sh
```

All services should show `Up (healthy)`.

---

## Service URLs & Credentials

### Infrastructure
| Service | URL | Credentials |
|---------|-----|-------------|
| Mock OIDC (Cognito) | http://localhost:3001 | — |
| OIDC discovery | http://localhost:3001/.well-known/openid-configuration | — |
| PostgreSQL | localhost:5432 | cfc_admin / cfc_password_dev |
| Redis | localhost:6379 | password: cfc_redis_dev |
| MySQL | localhost:3306 | cfc_dev / cfc_mysql_dev |
| MariaDB | localhost:3307 | cfc_dev / cfc_mariadb_dev |

### CI/CD & VCS
| Service | URL | Credentials |
|---------|-----|-------------|
| Jenkins | http://localhost:8080 | Check logs for initial password |
| GitLab | http://localhost:8081 | root / cfc_gitlab_dev |
| Gitea | http://localhost:8083 | Set on first visit |
| Drone | http://localhost:8082 | — |

### CMS & E-Commerce
| Service | URL | DB |
|---------|-----|----|
| WordPress | http://localhost:8087 | MySQL |
| WooCommerce | http://localhost:8089 | MySQL |
| Drupal | http://localhost:8090 | PostgreSQL |
| Joomla | http://localhost:8091 | MySQL |

### Monitoring & Analytics
| Service | URL | Credentials |
|---------|-----|-------------|
| Grafana | http://localhost:3000 | admin / cfc_grafana_dev |
| Prometheus | http://localhost:9090 | — |
| Metabase | http://localhost:3002 | Set on first visit |
| Superset | http://localhost:8088 | Set on first visit |

### Infrastructure Services
| Service | URL | Credentials |
|---------|-----|-------------|
| Nexus | http://localhost:8084 | admin / (check logs first run) |
| Vault | http://localhost:8200 | Token: cfc_vault_dev_token |
| Mattermost | http://localhost:8065 | Set on first visit |
| HAProxy Stats | http://localhost:8404/stats | — |

---

## Common Tasks

### Connect to a database

```bash
# PostgreSQL (psql)
docker compose exec postgres-main psql -U cfc_admin

# MySQL
docker compose exec mysql mysql -u cfc_dev -pcfc_mysql_dev cfc_apps
```

### Run a command in any container

```bash
docker compose exec jenkins bash
docker compose exec wordpress bash
docker compose exec redis-main redis-cli -a cfc_redis_dev ping
```

### View logs

```bash
./scripts/docker-logs.sh jenkins
./scripts/docker-logs.sh wordpress --tail 100
./scripts/docker-logs.sh -f          # follow all
```

### Add a single service to a running stack

```bash
docker compose up -d metabase
```

### Stop and restart

```bash
./scripts/docker-stop.sh           # stop, keep volumes
./scripts/docker-start.sh <group>  # start again
```

### Wipe everything (start fresh)

```bash
./scripts/docker-clean.sh
# Warning: destroys all volumes / data
```

---

## Troubleshooting

### Script fails: "cannot convert indexed to associative array"

Your shell is bash 3.2 (macOS default). Install Homebrew bash:

```bash
brew install bash
bash --version    # must show 5.x
```

### Port already in use

```bash
lsof -i :8080         # find what's using a port
kill -9 <PID>         # free it
# or change the host port in docker-compose.yml
```

### Service unhealthy / won't start

```bash
./scripts/docker-logs.sh <service>    # read the error
docker compose restart <service>      # quick retry
```

For persistent failures: check RAM allocation (Docker Desktop → Settings → Resources). The full stack needs 8 GB.

### Mock OIDC returns 404 on all routes

The `oidc-mock.json` data file must be in **Mockoon v9 format** (the repo ships the correct version). If you see 404s after pulling updates with an older `oidc-mock.json`, the fix is already in `docker/mock-oidc/oidc-mock.json` — just restart:

```bash
docker compose restart mock-oidc
docker logs cfc-mock-oidc | tail -5   # should say "Server started on port 3000"
```

### Can't connect to database

```bash
./scripts/docker-status.sh | grep postgres
docker compose exec postgres-main pg_isready -U cfc_admin
```

---

## Directory Structure

```
cfc-core/
├── docker-compose.yml           # All service definitions
├── docker/
│   ├── mock-oidc/oidc-mock.json # Mockoon v9 OIDC routes (5 endpoints)
│   ├── postgres-init.sql        # Creates databases on first boot
│   ├── haproxy/haproxy.cfg      # Path/host-based routing config
│   ├── prometheus/prometheus.yml # Scrape targets
│   └── grafana/provisioning/    # Auto-wired Prometheus datasource
├── scripts/
│   ├── docker-start.sh          # Start by group or service name
│   ├── docker-stop.sh           # Stop (keep volumes)
│   ├── docker-status.sh         # Health dashboard
│   ├── docker-logs.sh           # Log viewer with filtering
│   ├── docker-services.sh       # List all available services/groups
│   └── docker-clean.sh          # Nuclear option — removes volumes
└── DOCKER_LOCAL_DEV_README.md   # Full documentation
```

---

## Next Steps

- [ ] `./scripts/docker-start.sh infrastructure` — confirm all 5 infra services are healthy
- [ ] `curl http://localhost:3001/.well-known/openid-configuration` — verify OIDC mock works
- [ ] `./scripts/docker-start.sh cms` — boot WordPress, Drupal, Joomla, WooCommerce
- [ ] Read [CMS Guide](docs/applications/CMS.md) for deploying Craft CMS and other platforms to AWS
- [ ] Read [DOCKER_LOCAL_DEV_README.md](DOCKER_LOCAL_DEV_README.md) for advanced usage

---

**Full documentation:** [DOCKER_LOCAL_DEV_README.md](DOCKER_LOCAL_DEV_README.md)  
**Report issues** with the `docker-environment` label on GitHub

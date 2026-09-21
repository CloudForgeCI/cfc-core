# CloudForge CI Docker Quick Start

This guide starts the local Docker environment defined in `docker-compose.yml`. For the full
reference, see the [Local Docker Environment guide](DOCKER_LOCAL_DEV_README.md).

## Prerequisites

- **Docker Desktop 24.0+** (or Docker Engine 24+ with the Compose v2 plugin): `docker --version`
- **Docker Compose v2** (the `docker compose` plugin, not the standalone `docker-compose`): `docker compose version`
- At least **4 GB RAM** allocated to Docker (8 GB for the full stack)
- At least **10 GB free disk** for volumes
- Free host ports for the services you start (see [Service URLs and Credentials](#service-urls-and-credentials)), or edit `docker-compose.yml`
- `python3` on the `PATH`
- A terminal in the repository root

### macOS: bash 4+

The management scripts use associative arrays and require **bash 4+**. macOS ships bash 3.2.

```bash
brew install bash
bash --version
```

The scripts use `#!/usr/bin/env bash`, so they pick up the Homebrew bash when it is first on your `PATH`.

---

## First Start

**1. Verify prerequisites**

```bash
docker ps                # Docker daemon is running
docker compose version   # Compose v2 plugin is available
bash --version           # 4.0+ (macOS)
```

**2. Start a group of services**

```bash
# Infrastructure plus CMS applications
./scripts/docker-start.sh infrastructure cms

# Infrastructure only (databases and mock OIDC)
./scripts/docker-start.sh infrastructure

# Every group
./scripts/docker-start.sh all
```

Groups: `infrastructure`, `cicd`, `monitoring`, `analytics`, `services`, `collaboration`,
`cms`, `databases`, `core`, `all`. Run `./scripts/docker-start.sh --list` to see the
services in each, or run the script with no arguments for a selection menu.

You can combine groups and individual service names:

```bash
./scripts/docker-start.sh infrastructure monitoring wordpress
```

The script pulls images, starts the containers, waits up to two minutes for health checks,
then prints URLs and credentials.

**3. Follow logs while services start** (optional, separate terminal):

```bash
./scripts/docker-logs.sh -f
```

**4. Check status**

```bash
./scripts/docker-status.sh
```

Services with a health check show `Up (healthy)` once ready. Mattermost has no health
check and shows `Up`. GitLab, Nexus, and Metabase can take several minutes on first start.

---

## Service URLs and Credentials

All credentials are development defaults defined in `docker-compose.yml`.

### Infrastructure

| Service | URL | Credentials |
|---------|-----|-------------|
| Mock OIDC (Cognito stand-in) | http://localhost:3001 | None |
| OIDC discovery | http://localhost:3001/.well-known/openid-configuration | None |
| PostgreSQL | localhost:5432 | `cfc_admin` / `cfc_password_dev` |
| Redis | localhost:6379 | Password `cfc_redis_dev` |
| MySQL | localhost:3306 | `cfc_dev` / `cfc_mysql_dev` (database `cfc_apps`) |
| MariaDB | localhost:3307 | `cfc_dev` / `cfc_mariadb_dev` |
| PostgreSQL (standalone, `databases` group) | localhost:5433 | `appuser` / `apppass` (database `appdb`) |
| Redis (standalone, `databases` group) | localhost:6380 | Password `apppass` |

### CI/CD and Version Control

| Service | URL | Credentials |
|---------|-----|-------------|
| Jenkins | http://localhost:8080/jenkins | Initial admin password is in the container log |
| GitLab | http://localhost:8081 | `root` / `GITLAB_ROOT_PASSWORD` in `docker-compose.yml` |
| Gitea | http://localhost:8083 | Set in the install wizard on first visit |
| Drone | http://localhost:8082 | None |

### CMS and E-Commerce

| Service | URL | Database |
|---------|-----|----------|
| WordPress | http://localhost:8087 | MySQL |
| WooCommerce | http://localhost:8089 | MySQL |
| Drupal | http://localhost:8090 | PostgreSQL |
| Joomla | http://localhost:8091 | MySQL |

UNA (8092), Magento (8093), and OpenCart (8094) are not in the `cms` group. Start them with
`./scripts/docker-app.sh start <app>`.

### Monitoring and Analytics

| Service | URL | Credentials |
|---------|-----|-------------|
| Grafana | http://localhost:3000 | `admin` / `cfc_grafana_dev` |
| Prometheus | http://localhost:9090 | None |
| Metabase | http://localhost:3002 | Set on first visit |
| Superset | http://localhost:8088 | Set on first visit |

### Other Services

| Service | URL | Credentials |
|---------|-----|-------------|
| Nexus | http://localhost:8084 | `admin` / generated on first start (stored in `/nexus-data/admin.password` in the container) |
| Vault | http://localhost:8200 | Token `cfc_vault_dev_token` |
| Mattermost | http://localhost:8065 | Set on first visit |
| HAProxy stats | http://localhost:8404/stats | None |

---

## Common Tasks

### Connect to a database

```bash
docker compose exec postgres-main psql -U cfc_admin
docker compose exec mysql mysql -u cfc_dev -pcfc_mysql_dev cfc_apps
```

### Run a command in a container

```bash
docker compose exec jenkins bash
docker compose exec wordpress bash
docker compose exec redis-main redis-cli -a cfc_redis_dev ping
```

### View logs

```bash
./scripts/docker-logs.sh jenkins
./scripts/docker-logs.sh wordpress --tail 100
./scripts/docker-logs.sh -f          # follow all services
```

### Add a service to a running stack

```bash
docker compose up -d metabase
```

### Test one application with only its dependencies

```bash
./scripts/docker-app.sh list
./scripts/docker-app.sh start wordpress
```

### Stop and restart

```bash
./scripts/docker-stop.sh           # stop and remove containers, keep volumes
./scripts/docker-start.sh <group>  # start again
```

### Remove all data

```bash
./scripts/docker-clean.sh          # removes containers and volumes
```

---

## Troubleshooting

### `declare: -A: invalid option`

The script is running under bash 3.2 (the macOS default). Install bash 4+ with Homebrew
and make sure it is first on your `PATH`.

### Port already in use

```bash
lsof -i :8080         # find the process using the port
```

Stop that process, or change the host port in `docker-compose.yml`.

### Service unhealthy or not starting

```bash
./scripts/docker-logs.sh <service>
docker compose restart <service>
```

If failures persist, check the RAM allocated to Docker (Docker Desktop → Settings →
Resources). The full stack needs about 8 GB.

### Mock OIDC returns 404 on all routes

`docker/mock-oidc/oidc-mock.json` must be in the Mockoon data format expected by the
`mockoon/cli` image. If you replaced or edited it, restore the repository version and restart:

```bash
docker compose restart mock-oidc
docker compose logs --tail 5 mock-oidc
```

### Cannot connect to a database

```bash
./scripts/docker-status.sh | grep postgres
docker compose exec postgres-main pg_isready -U cfc_admin
```

---

## Files

```
cfc-core/
├── docker-compose.yml             # Service definitions
├── docker/
│   ├── mock-oidc/oidc-mock.json   # Mockoon OIDC routes
│   ├── postgres-init.sql          # Creates application databases on first start
│   ├── haproxy/haproxy.cfg        # Path- and host-based routing
│   ├── prometheus/prometheus.yml  # Scrape targets
│   ├── grafana/provisioning/      # Prometheus data source
│   └── woocommerce/, una/, magento/, opencart/   # Custom image builds
└── scripts/
    ├── docker-start.sh            # Start by group or service name
    ├── docker-app.sh              # Start one application with its dependencies
    ├── docker-stop.sh             # Stop (keep volumes)
    ├── docker-status.sh           # Container status and health
    ├── docker-logs.sh             # Logs (wraps docker compose logs)
    ├── docker-services.sh         # List running services by category
    └── docker-clean.sh            # Remove containers and volumes
```

---

## Next Steps

- Start `./scripts/docker-start.sh infrastructure` and confirm the five infrastructure services are healthy.
- Run `curl http://localhost:3001/.well-known/openid-configuration` to check the OIDC mock.
- Read the [CMS Guide](../applications/CMS.md) for deploying CMS platforms to AWS.
- Read the [Local Docker Environment guide](DOCKER_LOCAL_DEV_README.md) for configuration, backups, and maintenance.
- To deploy synthesized stacks to an AWS emulator instead, see the [Local Emulator Quick Start](LOCAL_EMULATOR_QUICK_START.md).

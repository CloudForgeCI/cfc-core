# CloudForge CI Local Docker Environment

The repository's `docker-compose.yml` runs the applications that CloudForge deploys, plus
local stand-ins for the AWS services they depend on, so you can try an application without
deploying to AWS or an emulator. The compose file includes:

- **Infrastructure:** PostgreSQL, MySQL, MariaDB, Redis, and a mock OIDC provider that stands in for Amazon Cognito
- **CI/CD and version control:** Jenkins, GitLab, Gitea, Drone
- **Monitoring and analytics:** Prometheus, Grafana, Metabase, Apache Superset
- **Services:** Nexus Repository, HashiCorp Vault, Mattermost
- **CMS and e-commerce:** WordPress, WooCommerce, Drupal, Joomla, UNA, Magento (with OpenSearch), OpenCart
- **Reverse proxy:** HAProxy, standing in for an Application Load Balancer

Services have health checks and use named volumes, so data survives restarts.

For a shorter walkthrough, see the [Docker Quick Start](DOCKER_QUICK_START.md).

---

## Prerequisites

- **Docker Desktop** 24.0+ (or Docker Engine 24+ with the Compose v2 plugin). The standalone `docker-compose` v1 binary is not supported.
- **4 GB+ RAM** allocated to Docker (8 GB for the full stack)
- **10 GB+ disk space** for volumes
- The host ports listed under [Services and Ports](#services-and-ports) must be free, or you must change them in `docker-compose.yml`
- `python3` on the `PATH` (`docker-start.sh` uses it to read container health)

### macOS: Bash Version

The management scripts use associative arrays and require **bash 4+**. macOS ships bash 3.2. Install a newer bash with Homebrew:

```bash
brew install bash
```

The scripts use `#!/usr/bin/env bash`, so they pick up the Homebrew bash when it is first on your `PATH`.

### Check Prerequisites

```bash
docker --version              # 24.0+
docker compose version        # Compose v2 plugin
docker info | grep "Memory"   # RAM allocated to Docker
bash --version                # 4.0+ (macOS)
```

---

## Quick Start

```bash
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core

# Infrastructure plus the CMS applications
./scripts/docker-start.sh infrastructure cms

# Or every service in the "all" group
./scripts/docker-start.sh all

# Check status
./scripts/docker-status.sh
```

`docker-start.sh` pulls images, starts the selected containers, waits up to two minutes for
health checks, and then prints the URL (and, where applicable, the credentials) for each
service it started. Run it with no arguments, or with `--interactive`, for a selection menu.

### Service Groups

```bash
./scripts/docker-start.sh infrastructure   # Mock OIDC, PostgreSQL, Redis, MySQL, MariaDB
./scripts/docker-start.sh cicd             # Jenkins, GitLab, Gitea, Drone
./scripts/docker-start.sh monitoring       # Prometheus, Grafana
./scripts/docker-start.sh analytics        # Metabase, Superset
./scripts/docker-start.sh services         # Nexus, Vault
./scripts/docker-start.sh collaboration    # Mattermost
./scripts/docker-start.sh cms              # WordPress, WooCommerce, Drupal, Joomla
./scripts/docker-start.sh databases        # Standalone PostgreSQL and Redis (postgresql-app, redis-app)
./scripts/docker-start.sh core             # Infrastructure, CI/CD, monitoring, Metabase, Vault, Mattermost
./scripts/docker-start.sh all              # Every group above, plus HAProxy
./scripts/docker-start.sh --list           # Print groups and service names
```

You can combine groups and individual service names:

```bash
./scripts/docker-start.sh infrastructure monitoring wordpress
```

UNA (`dolphin-una`), Magento, OpenCart, and OpenSearch are not part of any group. Start them
with `docker-app.sh` (see [Testing One Application at a Time](#testing-one-application-at-a-time))
or directly with `docker compose up -d <service>`.

---

## Architecture

### Services and Ports

Host ports as mapped in `docker-compose.yml`:

| Service | Compose name | Host port | Notes |
|---------|--------------|-----------|-------|
| Mock OIDC | `mock-oidc` | 3001 | Mockoon; stands in for Cognito |
| PostgreSQL | `postgres-main` | 5432 | Shared database for Gitea, Drone, Metabase, Superset, Mattermost, Drupal |
| Redis | `redis-main` | 6379 | Shared cache |
| MySQL | `mysql` | 3306 | Shared database for WordPress, WooCommerce, Joomla, UNA, Magento, OpenCart |
| MariaDB | `mariadb` | 3307 | |
| Jenkins | `jenkins` | 8080, 50000 | Served under `/jenkins` |
| GitLab | `gitlab` | 8081, 8444 (HTTPS), 2222 (SSH) | |
| Drone | `drone` | 8082 | |
| Gitea | `gitea` | 8083, 2223 (SSH) | |
| Prometheus | `prometheus` | 9090 | |
| Grafana | `grafana` | 3000 | |
| Metabase | `metabase` | 3002 | |
| Apache Superset | `superset` | 8088 | |
| PostgreSQL (standalone) | `postgresql-app` | 5433 | |
| Redis (standalone) | `redis-app` | 6380 | |
| Nexus Repository | `nexus` | 8084 | |
| HashiCorp Vault | `vault` | 8200 | Dev mode |
| Mattermost | `mattermost` | 8065 | |
| WordPress | `wordpress` | 8087 | |
| WooCommerce | `woocommerce` | 8089 | Built from `docker/woocommerce` |
| Drupal | `drupal` | 8090 | Uses PostgreSQL |
| Joomla | `joomla` | 8091 | |
| UNA | `dolphin-una` | 8092 | Built from `docker/una` |
| Magento | `magento` | 8093 | Built from `docker/magento`; requires OpenSearch |
| OpenCart | `opencart` | 8094 | Built from `docker/opencart` |
| OpenSearch | `opensearch` | 9200 | |
| HAProxy | `haproxy` | 80, 8404 (stats) | Path- and host-based routing, like ALB listener rules |

Harbor is not included: it needs the official offline installer (multiple containers and
shared configuration), so it cannot run as a single compose service. `docker-compose.yml`
contains a comment with the installer steps, and `docker/harbor/harbor.yml` holds a sample
configuration.

### Emulators

MiniStack and LocalStack are not defined in `docker-compose.yml`. Start them from the
Interactive Deployer's platform lifecycle menu (`--platform`); both listen on port 4566. See
[MiniStack Local Deployment](../ministack/README.md) and the
[Local Emulator Quick Start](LOCAL_EMULATOR_QUICK_START.md).

### Network

All services join a single bridge network, `cfc-network`, and reach each other by compose
service name (for example `postgres-main:5432` or `mock-oidc:3000`).

```
┌──────────────────────────────────────────────────────────┐
│                       cfc-network                        │
│                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │ PostgreSQL  │  │ Redis       │  │ MySQL       │       │
│  │ (5432)      │  │ (6379)      │  │ (3306)      │       │
│  └─────────────┘  └─────────────┘  └─────────────┘       │
│         │                │                │              │
│  ┌──────────────────────────────────────────────────┐    │
│  │            Application services                  │    │
│  │  Jenkins  GitLab  Gitea  Grafana  Metabase       │    │
│  │  Vault  Nexus  Mattermost  WordPress ...         │    │
│  └──────────────────────────────────────────────────┘    │
│         │                                                │
│  ┌──────────────────────────────────────────────────┐    │
│  │  HAProxy (80, stats on 8404)                     │    │
│  │  Mock OIDC provider (3001 on host, 3000 inside)  │    │
│  └──────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
```

---

## Usage

### Start Services

```bash
./scripts/docker-start.sh <group|service> ...
```

Some services (GitLab, Nexus, Magento) take several minutes to initialize on first start.
Follow their progress with:

```bash
./scripts/docker-logs.sh -f jenkins    # or any service name
```

### View Service Status

```bash
./scripts/docker-status.sh
```

Shows `docker compose ps` output: container state, health, and port mappings.

### Access Services

| Service | URL | Credentials |
|---------|-----|-------------|
| Jenkins | http://localhost:8080/jenkins | Initial admin password is printed in the container log |
| GitLab | http://localhost:8081 | `root` / value of `GITLAB_ROOT_PASSWORD` in `docker-compose.yml` |
| Gitea | http://localhost:8083 | Set in the install wizard on first visit |
| Grafana | http://localhost:3000 | `admin` / `cfc_grafana_dev` |
| Metabase | http://localhost:3002 | Set on first visit |
| Superset | http://localhost:8088 | Set on first visit |
| Vault | http://localhost:8200 | Token `cfc_vault_dev_token` |
| Mock OIDC | http://localhost:3001 | None (mock provider) |
| HAProxy stats | http://localhost:8404/stats | None |
| Magento | http://localhost:8093 | `cfc_admin` / `MAGENTO_ADMIN_PASSWORD` in `docker-compose.yml` |
| OpenCart | http://localhost:8094 | `cfc_admin` / `OC_ADMIN_PASS` in `docker-compose.yml` |

Database credentials are listed in the [Docker Quick Start](DOCKER_QUICK_START.md#infrastructure).
All credentials are development defaults; do not reuse them outside local testing.

### View Logs

`docker-logs.sh` passes its arguments to `docker compose logs`:

```bash
./scripts/docker-logs.sh -f                  # follow all services
./scripts/docker-logs.sh jenkins             # one service
./scripts/docker-logs.sh jenkins --tail 100  # last 100 lines

# Search logs
docker compose logs | grep "ERROR"
```

### Run Commands Inside Containers

```bash
docker compose exec jenkins bash
docker compose exec postgres-main psql -U cfc_admin
docker compose exec postgres-main pg_dump -U cfc_admin gitea > gitea.sql
```

### List Services

```bash
./scripts/docker-services.sh
```

Lists the running compose services, grouped by category.

### Stop Services

```bash
./scripts/docker-stop.sh
```

Stops and removes the containers. Volumes are preserved.

### Remove Everything

```bash
./scripts/docker-clean.sh
```

**Warning:** this removes all containers and volumes, and all data stored in them. It cannot be undone.

---

## Testing One Application at a Time

`docker-app.sh` starts one application together with only the infrastructure it needs,
creates the application's database if required, and prints its URL. The shared
infrastructure stays running between applications, the same way RDS and ElastiCache
persist independently of an application deployment on AWS.

```bash
./scripts/docker-app.sh list              # show testable applications
./scripts/docker-app.sh start magento     # start MySQL, Redis, OpenSearch, then Magento
./scripts/docker-app.sh logs magento      # follow logs
./scripts/docker-app.sh restart magento   # restart the application container
./scripts/docker-app.sh stop magento      # stop the application, keep infrastructure
```

---

## Configuration

### Credentials and Environment Variables

Credentials and application settings are set directly in the `environment:` blocks of
`docker-compose.yml`; the compose file does not read a `.env` file. Edit the relevant
service, then recreate it:

```bash
docker compose up -d --force-recreate <service>
```

Some settings (for example, PostgreSQL and MySQL user passwords) are applied only when the
data volume is first initialized. To apply a change to those, remove the volume
(`./scripts/docker-clean.sh`, or `docker volume rm` for a single volume) and start again.

### Custom Ports

Change the host side of a port mapping (`HOST_PORT:CONTAINER_PORT`) in `docker-compose.yml`:

```yaml
services:
  jenkins:
    ports:
      - "9080:8080"  # host port 9080 instead of 8080
```

Then recreate the service with `docker compose up -d jenkins`.

Services use fixed `container_name` values and host ports, so `docker compose --scale` does not work with this file.

### Persistent Data

Services use Docker named volumes, created automatically. Compose prefixes volume names
with the project name, which defaults to the directory name (`cfc-core`):

```bash
# List volumes
docker volume ls | grep cfc-core

# Inspect a volume
docker volume inspect cfc-core_postgres_main_data

# Back up a volume to the current directory
docker run --rm -v cfc-core_postgres_main_data:/data -v "$(pwd)":/backup \
  alpine tar czf /backup/postgres-backup.tar.gz /data
```

---

## Application Testing Workflows

### Jenkins with Gitea

1. Open Jenkins at http://localhost:8080/jenkins.
2. Create a pipeline job.
3. Use the Gitea instance as the SCM source. Inside the network, Gitea is at `http://gitea:3000/`.

### OIDC Integration

The mock OIDC provider is a Mockoon server (`docker/mock-oidc/oidc-mock.json`). It is
exposed on port **3001** on the host and at `mock-oidc:3000` inside the Docker network.

From the host:

```bash
curl http://localhost:3001/.well-known/openid-configuration
curl http://localhost:3001/health
```

The discovery document advertises `http://localhost:3001` as the issuer and endpoint base.
Applications running inside the network reach the same routes at `mock-oidc:3000`:

| Setting | Value |
|---------|-------|
| Issuer | `http://localhost:3001` |
| Authorization endpoint | `/oauth/authorize` |
| Token endpoint | `/oauth/token` |
| UserInfo endpoint | `/oauth/userinfo` |

The userinfo response is static:

```json
{
  "sub": "cfc_dev_user",
  "name": "CloudForge Developer",
  "email": "dev@example.com",
  "cognito:groups": ["ManagerAdmins", "ManagerUsers"],
  "cognito:username": "dev-user"
}
```

### Databases

`docker/postgres-init.sql` creates one database per application in `postgres-main` the
first time its volume is initialized.

```bash
docker compose exec postgres-main psql -U cfc_admin
```

```sql
\l                -- list databases
\c mattermost     -- connect to an application database
\dt               -- list its tables
```

### Service-to-Service Calls

```bash
# From the GitLab container to Jenkins
docker compose exec gitlab curl http://jenkins:8080/jenkins/login

# From the Jenkins container to Nexus
docker compose exec jenkins curl http://nexus:8081/service/rest/v1/status
```

---

## Troubleshooting

### Service Won't Start

```bash
./scripts/docker-logs.sh jenkins
docker info | grep -E "Memory|CPUs"

# Start again from empty volumes
./scripts/docker-clean.sh
./scripts/docker-start.sh <group>
```

### Port Already in Use

```bash
lsof -i :8080    # find the process using the port
```

Stop that process, or change the host port in `docker-compose.yml`.

### Out of Memory

Increase Docker's memory allocation (Docker Desktop: Settings → Resources → Memory), then
restart the services. The full stack needs about 8 GB.

### Health Check Failing

Slow services (GitLab, Nexus, Metabase, Magento) can report `starting` for several minutes.

```bash
docker compose ps
./scripts/docker-logs.sh <service>
```

### Database Connection Refused

```bash
./scripts/docker-status.sh | grep postgres
docker compose exec postgres-main pg_isready -U cfc_admin
```

### Mock OIDC Returns 404 on All Routes

`docker/mock-oidc/oidc-mock.json` must be in the Mockoon data format expected by the
`mockoon/cli` image. If you replaced it, restore the repository version and restart:

```bash
docker compose restart mock-oidc
docker compose logs --tail 5 mock-oidc
```

---

## Advanced Usage

### Mount a Local Directory

```yaml
services:
  jenkins:
    volumes:
      - jenkins_home:/var/jenkins_home
      - /path/to/local/workspace:/workspace
```

### Use a Custom Image

Replace a service's `image:` with a `build:` entry that points at a directory containing a
Dockerfile, as `woocommerce`, `dolphin-una`, `magento`, and `opencart` already do:

```yaml
services:
  jenkins:
    build: ./path/to/jenkins-image
```

### Resource Limits

```yaml
services:
  jenkins:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

### Monitor Resource Usage

```bash
docker stats --no-stream
docker stats cfc-jenkins
```

---

## Maintenance

### Back Up Databases

```bash
docker compose exec postgres-main pg_dump -U cfc_admin gitea > gitea_backup.sql
docker compose exec postgresql-app pg_dump -U appuser appdb > appdb_backup.sql
```

To back up a whole volume, use the `docker run ... tar` command under [Persistent Data](#persistent-data).

### Update Images

Most services use `latest` tags.

```bash
docker compose pull
./scripts/docker-stop.sh
./scripts/docker-start.sh <group>
```

### Clean Up Unused Resources

```bash
docker image prune -f     # dangling images
docker system prune -f    # stopped containers, unused networks, dangling images
```

`docker volume prune` also removes the volumes of any stopped services, including this
environment's data.

---

## Adding a Service

1. Add the service to `docker-compose.yml` under the appropriate section, on `cfc-network`.
2. Add named volumes and a health check.
3. Add it to `SERVICE_GROUPS`, `ALL_SERVICES`, and `URLS` in `scripts/docker-start.sh`, and to the registries in `scripts/docker-app.sh`.
4. Test with `./scripts/docker-start.sh <service> && ./scripts/docker-status.sh`.
5. Document its port and credentials in this guide.

---

## Scripts Reference

All scripts are in `scripts/` and run from any directory.

| Script | Purpose |
|--------|---------|
| `docker-start.sh` | Start groups or services, wait for health checks, print URLs |
| `docker-app.sh` | Start, stop, restart, or follow one application with its infrastructure |
| `docker-stop.sh` | Stop and remove containers (keeps volumes) |
| `docker-status.sh` | Show container status and health |
| `docker-logs.sh` | Show logs (arguments pass through to `docker compose logs`) |
| `docker-services.sh` | List running services by category |
| `docker-clean.sh` | Remove containers and volumes |

---

## Related Documentation

- [Docker Quick Start](DOCKER_QUICK_START.md)
- [Local Emulator Quick Start](LOCAL_EMULATOR_QUICK_START.md)
- [CMS Guide](../applications/CMS.md)
- [Documentation index](../README.md)
- [Docker Compose file reference](https://docs.docker.com/reference/compose-file/)

# CloudForge Local Development Environment

Local dockerized environment for developing and testing CloudForge applications. Spins up infrastructure services plus CI/CD, monitoring, CMS, and analytics applications — all pre-wired and health-checked.

**Features:**
- ✅ Infrastructure layer: PostgreSQL, MySQL, MariaDB, Redis, Mock OIDC (Cognito simulator)
- ✅ CMS & e-commerce: WordPress, WooCommerce, Drupal, Joomla
- ✅ CI/CD & VCS: Jenkins, GitLab, Gitea, Drone
- ✅ Monitoring: Prometheus, Grafana
- ✅ Selective startup — start only what you need (groups or individual services)
- ✅ Health checks and service monitoring
- ✅ Persistent volumes for data preservation

**Status:** ✓ Fully operational | Tested with Docker 28.1+

---

## Prerequisites

- **Docker Desktop** 24.0+ (or Docker Engine 24+ with Compose plugin)
- **4GB+ RAM** allocated to Docker (8GB recommended for full stack)
- **10GB+ disk space** for volumes
- **Ports 80, 3000-9090** available (or modify `docker-compose.yml`)

### macOS: Bash Version

The management scripts require **bash 4+** (macOS ships bash 3.2 by default). Install via Homebrew:

```bash
brew install bash
```

Verify: `bash --version` should show 5.x. The scripts use `#!/usr/bin/env bash` which picks up the Homebrew bash automatically.

### Check Prerequisites

```bash
docker --version              # 24.0+ required
docker compose version        # built-in Compose plugin (v2)
docker info | grep "Memory"   # confirm RAM allocation
bash --version                # 5.x required on macOS
```

---

## Quick Start

```bash
# Clone and navigate to project root
git clone https://github.com/CloudForgeCI/cfc-core.git
cd cfc-core

# Start just infrastructure + CMS (fastest useful subset)
./scripts/docker-start.sh infrastructure cms

# Or start everything
./scripts/docker-start.sh all

# Check status
./scripts/docker-status.sh
```

The script prints URLs and credentials for every service it started.

### Service Groups

```bash
./scripts/docker-start.sh infrastructure     # Mock OIDC, PostgreSQL, Redis, MySQL, MariaDB
./scripts/docker-start.sh cicd               # Jenkins, GitLab, Gitea, Drone
./scripts/docker-start.sh monitoring         # Prometheus, Grafana
./scripts/docker-start.sh cms                # WordPress, WooCommerce, Drupal, Joomla
./scripts/docker-start.sh core              # Everything except CMS and analytics extras
./scripts/docker-start.sh all               # All containers (~26)
```

You can combine groups and individual service names:

```bash
./scripts/docker-start.sh infrastructure monitoring wordpress
```

---

## Architecture

### Infrastructure Layer
- **PostgreSQL** (port 5432) - Primary relational database
- **Redis** (port 6379) - Caching & sessions
- **MySQL** (port 3306) - Additional relational DB
- **MariaDB** (port 3307) - MySQL-compatible DB
- **Mock OIDC** (port 3001) - Authentication testing

### Service Categories

#### CI/CD & Version Control (4 apps)
- **Jenkins** (8080) - Automation server
- **GitLab** (8081) - DevOps platform
- **Gitea** (8083) - Lightweight Git service
- **Drone** (8082) - Container CI/CD

#### Monitoring & Analytics (5 apps)
- **Prometheus** (9090) - Metrics collection
- **Grafana** (3000) - Observability dashboards
- **Metabase** (3002) - BI & analytics
- **Apache Superset** (8088) - Data exploration
- **HAProxy Stats** (8404) - Load balancer stats

#### Infrastructure Services (3 apps)
- **Nexus Repository** (8084) - Artifact management
- **Harbor** (8085) - Container registry
- **HashiCorp Vault** (8200) - Secrets management

#### Collaboration (1 app)
- **Mattermost** (8065) - Team communications

#### CMS & E-Commerce
- **WordPress** (8087) - Blogging & content management
- **WooCommerce** (8089) - E-commerce (WordPress + WooCommerce plugin)
- **Drupal** (8090) - Enterprise CMS
- **Joomla** (8091) - Publishing platform
- *Craft CMS and other platforms supported via `cms-service` topology — see [CMS Guide](docs/applications/CMS.md)*

### Network Architecture
```
┌─────────────────────────────────────────────────────────┐
│                    cfc-network                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │ PostgreSQL   │  │ Redis        │  │ MySQL       │  │
│  │ (5432)       │  │ (6379)       │  │ (3306)      │  │
│  └──────────────┘  └──────────────┘  └─────────────┘  │
│         │                │                    │         │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Application Services Layer               │  │
│  │                                                  │  │
│  │  Jenkins  GitLab  Gitea  Grafana  Metabase     │  │
│  │  Vault    Harbor  Nexus  Mattermost ...        │  │
│  │                                                  │  │
│  └──────────────────────────────────────────────────┘  │
│         │                                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │    HAProxy Load Balancer (ports 80, 443)        │  │
│  │    Mock OIDC Provider (port 3001)               │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## Usage

### Start Environment

```bash
./scripts/docker-start.sh
```

**Output:** URLs and access credentials for all services

**First-time setup:** Services initialize in background. Monitor with:
```bash
./scripts/docker-logs.sh -f jenkins    # or any service name
```

### View Service Status

```bash
./scripts/docker-status.sh
```

Shows container status, health checks, port mappings.

### Access Services

| Service | URL | Credentials |
|---------|-----|-------------|
| Jenkins | http://localhost:8080 | Auto-generated (check logs) |
| GitLab | http://localhost:8081 | root / cfc_gitlab_dev |
| Gitea | http://localhost:8083 | admin / (auto-generated) |
| Grafana | http://localhost:3000 | admin / cfc_grafana_dev |
| Metabase | http://localhost:3002 | admin@localhost / auto-generated |
| Vault | http://localhost:8200 | Token: cfc_vault_dev_token |
| Harbor | http://localhost:8085 | admin / cfc_harbor_dev |
| Mock OIDC | http://localhost:3001 | (mock provider) |
| HAProxy Stats | http://localhost:8404/stats | (read-only) |

### View Logs

```bash
# Follow all logs
./scripts/docker-logs.sh -f

# View specific service
./scripts/docker-logs.sh jenkins
./scripts/docker-logs.sh jenkins --tail 100

# Search logs
docker compose -f docker-compose.yml logs | grep "ERROR"
```

### Execute Commands Inside Containers

```bash
# Access a service shell
docker compose exec jenkins bash
docker compose exec postgres-main psql -U cfc_admin

# Run single commands
docker compose exec postgres-main pg_dump -U cfc_admin
```

### List All Services

```bash
./scripts/docker-services.sh
```

### Stop Services

```bash
./scripts/docker-stop.sh
```

**Note:** Volumes are preserved. Run `docker-clean.sh` to remove all data.

### Clean Everything (Remove All Data)

```bash
./scripts/docker-clean.sh
```

**Warning:** This deletes all containers, volumes, and data. Cannot be undone.

---

## Configuration

### Environment Variables

Edit or create `.env.local` in project root:

```env
# PostgreSQL
POSTGRES_USER=cfc_admin
POSTGRES_PASSWORD=cfc_password_dev

# Redis
REDIS_PASSWORD=cfc_redis_dev

# Grafana
GRAFANA_ADMIN_PASSWORD=cfc_grafana_dev

# GitLab
GITLAB_ROOT_PASSWORD=cfc_gitlab_dev
```

Changes require service restart:
```bash
./scripts/docker-stop.sh
./scripts/docker-start.sh
```

### Scale Individual Services

Scale Jenkins to 3 instances:
```bash
docker compose up -d --scale jenkins=3
```

### Custom Ports

Modify `docker-compose.yml` port mappings (format: `HOST_PORT:CONTAINER_PORT`):

```yaml
services:
  jenkins:
    ports:
      - "9080:8080"  # Changed from 8080 to 9080
```

Restart: `./scripts/docker-start.sh`

### Persistent Data Storage

All volumes use Docker named volumes (auto-created):

```bash
# List volumes
docker volume ls | grep cfc

# Inspect volume
docker volume inspect cfc_postgres_main_data

# Backup volume
docker run --rm -v cfc_postgres_main_data:/data -v $(pwd):/backup \
  alpine tar czf /backup/postgres-backup.tar.gz /data
```

---

## Application Testing Workflows

### Test Jenkins Pipeline

1. Access Jenkins: http://localhost:8080
2. Create new pipeline job
3. Use Gitea instance as SCM source: `http://gitea:3000/..`
4. Configure pipeline with CloudForge templates

### Test OIDC Integration

The mock OIDC provider simulates AWS Cognito. It is exposed on **port 3001** from the host and at `mock-oidc:3000` within the Docker network.

**From the host:**
```bash
# Verify discovery endpoint
curl http://localhost:3001/.well-known/openid-configuration

# Health check
curl http://localhost:3001/health
```

**Configure an application (use container-internal address):**

| Setting | Value |
|---------|-------|
| Issuer | `http://localhost:3001` |
| Authorization endpoint | `http://mock-oidc:3000/oauth/authorize` |
| Token endpoint | `http://mock-oidc:3000/oauth/token` |
| UserInfo endpoint | `http://mock-oidc:3000/oauth/userinfo` |

**Mock token/userinfo response:**
```json
{
  "sub": "cfc_dev_user",
  "email": "dev@cloudforgeci.com",
  "name": "CloudForge Developer",
  "cognito:groups": ["developers", "admins"]
}
```

### Test Database Applications

1. Connect to PostgreSQL:
   ```bash
   docker compose exec postgres-main psql -U cfc_admin
   ```

2. List available databases:
   ```sql
   \l
   ```

3. Connect to app database:
   ```sql
   \c mattermost
   SELECT * FROM users;
   ```

### Test Multi-App Communication

Example: Connect GitLab → Jenkins → Nexus

```bash
# From GitLab container
docker compose exec gitlab curl http://jenkins:8080/

# From Jenkins container
docker compose exec jenkins curl http://nexus:8081/service/rest/v1/status
```

---

## Troubleshooting

### Service Won't Start

```bash
# Check service logs
./scripts/docker-logs.sh jenkins

# Check resource constraints
docker info | grep -E "Memory|CPUs|Disk"

# Restart from scratch
./scripts/docker-clean.sh
./scripts/docker-start.sh
```

### Port Already in Use

```bash
# Find process using port 8080
lsof -i :8080

# Change port in docker-compose.yml (line with "8080:8080")
# Or kill the process
kill -9 <PID>
```

### Out of Memory

Increase Docker memory allocation:
- Docker Desktop: Settings → Resources → Memory (increase to 6GB+)
- Then restart services

### Failed Health Checks

```bash
# Wait longer for services to initialize
sleep 60

# Check health status
docker compose ps

# View detailed logs
./scripts/docker-logs.sh [service_name]
```

### Database Connection Refused

```bash
# Verify database is running
./scripts/docker-status.sh | grep postgres

# Test connection manually
docker compose exec postgres-main \
  pg_isready -U cfc_admin
```

### Volume Permissions Issues

```bash
# Fix volume permissions
docker volume prune
./scripts/docker-start.sh
```

---

## Advanced Usage

### Run Custom Shell Script in Service

```bash
docker compose exec jenkins \
  bash -c "java -version && mvn -v"
```

### Mount Local Directory

Edit `docker-compose.yml`:
```yaml
services:
  jenkins:
    volumes:
      - jenkins_home:/var/jenkins_home
      - /path/to/local/workspace:/workspace  # Add this
```

### Export Database Backup

```bash
docker compose exec postgres-main \
  pg_dump -U cfc_admin gitea > gitea_backup.sql
```

### Monitor Resource Usage

```bash
docker stats --no-stream

# For specific service
docker stats cfc-jenkins
```

### Build Custom Image

```bash
# Extend a service with Dockerfile
vi docker/jenkins/Dockerfile

# In docker-compose.yml, change:
# image: jenkins/jenkins:lts
# To:
# build: ./docker/jenkins
```

---

## Maintenance

### Regular Backups

```bash
# Backup all PostgreSQL data
docker compose exec postgres-main \
  pg_dump -U cfc_admin appdb > appdb_backup.sql

# Backup all volumes
tar czf cfc-volumes-backup.tar.gz $(docker volume ls -q | grep cfc)
```

### Cleanup Old Containers/Images

```bash
# Remove unused resources
docker system prune -f

# Remove unused volumes
docker volume prune -f

# Remove dangling images
docker image prune -f
```

### Update Service Images

```bash
# Pull latest images
docker compose pull

# Restart services with new images
./scripts/docker-stop.sh
./scripts/docker-start.sh
```

---

## Performance Optimization

### For Large Deployments

Edit `docker-compose.yml`:

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

### Reduce Memory Usage

Comment out unused services in `docker-compose.yml` (e.g., Superset, Harbor):

```bash
# Then restart
./scripts/docker-start.sh
```

### Use External Storage

Mount high-capacity storage for volumes:
```yaml
volumes:
  postgres_main_data:
    driver_opts:
      type: nfs
      o: addr=your.nfs.server,vers=4,soft,timeo=180,bg,tcp,rw
      device: ":/export/postgres"
```

---

## Integration with CloudForge Development

### Test Local Changes

1. Rebuild CloudForge libraries:
   ```bash
   cd cfc-core          # your project root
   mvn install -DskipTests -Djacoco.skip=true -q
   ```

2. Use local JAR in application:
   ```bash
   docker compose exec jenkins \
     aws s3 cp /local/cloudforge-core-3.1.1.jar s3://...
   ```

### Deploy Custom Application

1. Create Dockerfile for application
2. Add service to docker-compose.yml
3. Start: `./scripts/docker-start.sh`

---

## Contributing

To add more applications:

1. **Add service to docker-compose.yml** (under appropriate category)
2. **Configure volumes** (if needed)
3. **Add health checks**
4. **Test startup**: `./scripts/docker-start.sh && ./scripts/docker-status.sh`
5. **Document access details** in this README
6. **Update scripts** if new categories needed

---

## Scripts Reference

| Script | Purpose |
|--------|---------|
| `docker-start.sh` | Start all services with health checks |
| `docker-stop.sh` | Stop all services (preserve data) |
| `docker-status.sh` | Show container status & health |
| `docker-logs.sh` | View service logs with filtering |
| `docker-services.sh` | List all available services |
| `docker-clean.sh` | Remove all containers & volumes |

---

## Useful Docker Commands

```bash
# General
docker ps                                    # List running containers
docker ps -a                                 # List all containers
docker volume ls                             # List volumes
docker network ls                            # List networks

# Inspection
docker inspect cfc-jenkins                   # Detailed container info
docker logs --tail 50 -f cfc-jenkins         # Follow logs
docker stats                                 # Real-time resource usage

# Interaction
docker exec -it cfc-jenkins bash             # Execute command in container
docker attach cfc-jenkins                    # Attach to container

# Cleanup
docker rm $(docker ps -aq)                   # Remove all containers
docker volume rm $(docker volume ls -q)      # Remove all volumes
docker system prune -a                       # Complete cleanup
```

---

## Support & Resources

- **Docker Docs:** https://docs.docker.com/
- **Docker Compose Reference:** https://docs.docker.com/compose/compose-file/
- **CloudForge Docs:** https://github.com/CloudForgeCI/cfc-core/docs
- **Issues:** Report in GitHub with `docker-environment` label

---

**Last Updated:** June 2026  
**Docker Version:** 28.1+ (tested)  
**Compose Plugin:** v2 (`docker compose`) — Compose v1 (`docker-compose`) not supported

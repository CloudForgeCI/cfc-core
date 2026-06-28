#!/usr/bin/env bash
# CloudForge Local Development Environment — Start Script
# Simulates the AWS plugin ecosystem locally via Docker Compose

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

# ── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; MAGENTA='\033[0;35m'; NC='\033[0m'

log_info()    { echo -e "${BLUE}ℹ${NC}  $1"; }
log_success() { echo -e "${GREEN}✓${NC}  $1"; }
log_warning() { echo -e "${YELLOW}⚠${NC}  $1"; }
log_error()   { echo -e "${RED}✗${NC}  $1"; }

# ── Service groups ────────────────────────────────────────────────────────────
# Maps group name → space-separated list of compose service names
declare -A SERVICE_GROUPS=(
  [infrastructure]="mock-oidc postgres-main redis-main mysql mariadb"
  [cicd]="jenkins gitlab gitea drone"
  [monitoring]="prometheus grafana"
  [analytics]="metabase superset"
  [services]="nexus vault"
  [collaboration]="mattermost"
  [cms]="wordpress woocommerce drupal joomla"
  [databases]="postgresql-app redis-app"
  [core]="mock-oidc postgres-main redis-main mysql mariadb jenkins gitlab gitea drone prometheus grafana metabase vault mattermost"
  [all]="mock-oidc postgres-main redis-main mysql mariadb jenkins gitlab gitea drone prometheus grafana metabase superset nexus vault mattermost wordpress woocommerce drupal joomla haproxy postgresql-app redis-app"
)

ALL_SERVICES=(
  mock-oidc postgres-main redis-main mysql mariadb
  jenkins gitlab gitea drone
  prometheus grafana metabase superset
  nexus vault
  mattermost
  wordpress woocommerce drupal joomla
  postgresql-app redis-app
  haproxy
)

# ── Helpers ───────────────────────────────────────────────────────────────────
show_usage() {
  cat <<EOF

Usage: $(basename "$0") [OPTIONS] [SERVICE|GROUP ...]

OPTIONS:
  -h, --help        Show this help
  -i, --interactive Interactive selection menu
  -l, --list        List all services and groups

GROUPS:
  infrastructure    Mock OIDC, PostgreSQL, Redis, MySQL, MariaDB
  cicd              Jenkins, GitLab, Gitea, Drone
  monitoring        Prometheus, Grafana
  analytics         Metabase, Superset
  services          Nexus, Vault
  collaboration     Mattermost
  cms               WordPress, WooCommerce, Drupal, Joomla
  databases         PostgreSQL-app, Redis-app (standalone)
  core              All core services (no CMS, no analytics extras)
  all               Every service (26 containers)

EXAMPLES:
  $(basename "$0") infrastructure          # Start databases + mock OIDC only
  $(basename "$0") infrastructure cicd     # Infrastructure + CI/CD apps
  $(basename "$0") jenkins grafana         # Specific services by name
  $(basename "$0") core                    # Full stack minus CMS
  $(basename "$0") --interactive           # Menu-driven selection
  $(basename "$0") all                     # Everything

EOF
}

list_services() {
  log_info "Available services and groups:"
  echo
  printf "  ${MAGENTA}%-22s${NC} %s\n" "GROUP" "SERVICES"
  printf "  %-22s %s\n" "──────────────────────" "────────────────────────────────────────────────"
  for g in infrastructure cicd monitoring analytics services collaboration cms databases core all; do
    printf "  ${GREEN}%-22s${NC} %s\n" "$g" "${SERVICE_GROUPS[$g]}"
  done
  echo
  printf "  ${MAGENTA}Individual services:${NC} %s\n" "${ALL_SERVICES[*]}"
  echo
}

interactive_menu() {
  echo
  log_info "CloudForge Service Selection"
  echo
  echo "  1) Infrastructure only  (PostgreSQL, Redis, MySQL, MariaDB, Mock OIDC)"
  echo "  2) CI/CD & VCS          (Jenkins, GitLab, Gitea, Drone)"
  echo "  3) Monitoring           (Prometheus, Grafana)"
  echo "  4) Analytics            (Metabase, Superset)"
  echo "  5) Services             (Nexus, Vault)"
  echo "  6) Collaboration        (Mattermost)"
  echo "  7) CMS / E-commerce     (WordPress, WooCommerce, Drupal, Joomla)"
  echo "  8) Core stack           (Everything except CMS and extras)"
  echo "  9) Full stack           (All 26 containers)"
  echo "  0) Exit"
  echo
  read -rp "Choice (0-9): " choice
  case "$choice" in
    1) SELECTED="${SERVICE_GROUPS[infrastructure]}" ;;
    2) SELECTED="${SERVICE_GROUPS[cicd]}" ;;
    3) SELECTED="${SERVICE_GROUPS[monitoring]}" ;;
    4) SELECTED="${SERVICE_GROUPS[analytics]}" ;;
    5) SELECTED="${SERVICE_GROUPS[services]}" ;;
    6) SELECTED="${SERVICE_GROUPS[collaboration]}" ;;
    7) SELECTED="${SERVICE_GROUPS[cms]}" ;;
    8) SELECTED="${SERVICE_GROUPS[core]}" ;;
    9) SELECTED="${SERVICE_GROUPS[all]}" ;;
    0) log_info "Exiting."; exit 0 ;;
    *) log_error "Invalid choice."; interactive_menu; return ;;
  esac
}

# ── Parse arguments ───────────────────────────────────────────────────────────
SELECTED=""
INTERACTIVE=false

if [[ $# -eq 0 ]]; then
  INTERACTIVE=true
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)       show_usage; exit 0 ;;
    -l|--list)       list_services; exit 0 ;;
    -i|--interactive) INTERACTIVE=true ;;
    *)
      if [[ -n "${SERVICE_GROUPS[$1]+x}" ]]; then
        SELECTED="$SELECTED ${SERVICE_GROUPS[$1]}"
      elif printf '%s\n' "${ALL_SERVICES[@]}" | grep -qx "$1"; then
        SELECTED="$SELECTED $1"
      else
        log_error "Unknown service or group: '$1'"
        show_usage
        exit 1
      fi
      ;;
  esac
  shift
done

if $INTERACTIVE; then
  interactive_menu
fi

# Deduplicate
SELECTED=$(echo "$SELECTED" | tr ' ' '\n' | awk 'NF && !seen[$0]++' | tr '\n' ' ' | xargs)

if [[ -z "$SELECTED" ]]; then
  log_error "No services selected."
  show_usage
  exit 1
fi

# ── Pre-flight checks ─────────────────────────────────────────────────────────
if ! docker info &>/dev/null; then
  log_error "Docker daemon is not running. Start Docker Desktop and retry."
  exit 1
fi

if ! docker compose version &>/dev/null; then
  log_error "Docker Compose v2 plugin not found. Update Docker Desktop."
  exit 1
fi

# ── Start ─────────────────────────────────────────────────────────────────────
echo
log_info "Starting CloudForge local environment..."
log_info "Services: $(echo "$SELECTED" | tr ' ' ', ' | sed 's/, $//')"
echo

# Pull images first (silent, best-effort)
log_info "Pulling latest images..."
docker compose -f "$COMPOSE_FILE" pull --quiet $SELECTED 2>/dev/null \
  || log_warning "Some images couldn't be pulled — using cached versions."

# Bring up selected services
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans $SELECTED

# ── Wait and health-check ─────────────────────────────────────────────────────
echo
log_info "Waiting for containers to initialise (up to 2 minutes)..."
DEADLINE=$((SECONDS + 120))

while [[ $SECONDS -lt $DEADLINE ]]; do
  ALL_HEALTHY=true
  for svc in $SELECTED; do
    STATE=$(docker compose -f "$COMPOSE_FILE" ps --format json "$svc" 2>/dev/null \
            | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('Health','') or d.get('State',''))" 2>/dev/null || echo "unknown")
    if [[ "$STATE" == "unhealthy" || "$STATE" == "exited" ]]; then
      ALL_HEALTHY=false
      break
    elif [[ "$STATE" != "healthy" && "$STATE" != "running" ]]; then
      ALL_HEALTHY=false
    fi
  done
  $ALL_HEALTHY && break
  sleep 5
done

# ── Status summary ────────────────────────────────────────────────────────────
echo
echo -e "${BLUE}══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  CloudForge Local Environment — Service Status        ${NC}"
echo -e "${BLUE}══════════════════════════════════════════════════════${NC}"
echo

FAILED=()
for svc in $SELECTED; do
  STATE=$(docker compose -f "$COMPOSE_FILE" ps --format json "$svc" 2>/dev/null \
          | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('Health','') or d.get('State',''))" 2>/dev/null || echo "unknown")
  if [[ "$STATE" == "healthy" || "$STATE" == "running" ]]; then
    log_success "$svc"
  elif [[ "$STATE" == "starting" ]]; then
    log_warning "$svc (still starting — check logs in a moment)"
  else
    log_error "$svc ($STATE)"
    FAILED+=("$svc")
  fi
done

# ── Access URLs ───────────────────────────────────────────────────────────────
echo
echo -e "${BLUE}══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Access URLs                                          ${NC}"
echo -e "${BLUE}══════════════════════════════════════════════════════${NC}"
echo

declare -A URLS=(
  [mock-oidc]="http://localhost:3001  (Mock Cognito OIDC)"
  [postgres-main]="postgres://cfc_admin:cfc_password_dev@localhost:5432"
  [redis-main]="redis://:cfc_redis_dev@localhost:6379"
  [mysql]="mysql://cfc_dev:cfc_mysql_dev@localhost:3306"
  [mariadb]="mysql://cfc_dev:cfc_mariadb_dev@localhost:3307"
  [jenkins]="http://localhost:8080/jenkins"
  [gitlab]="http://localhost:8081  (root / cfc_gitlab_dev)"
  [drone]="http://localhost:8082"
  [gitea]="http://localhost:8083"
  [prometheus]="http://localhost:9090"
  [grafana]="http://localhost:3000  (admin / cfc_grafana_dev)"
  [metabase]="http://localhost:3002"
  [superset]="http://localhost:8088"
  [nexus]="http://localhost:8084"
  [vault]="http://localhost:8200  (token: cfc_vault_dev_token)"
  [mattermost]="http://localhost:8065"
  [wordpress]="http://localhost:8087"
  [woocommerce]="http://localhost:8089"
  [drupal]="http://localhost:8090"
  [joomla]="http://localhost:8091"
  [postgresql-app]="postgres://appuser:apppass@localhost:5433"
  [redis-app]="redis://:apppass@localhost:6380"
  [haproxy]="http://localhost:80  (stats: http://localhost:8404/stats)"
)

for svc in $SELECTED; do
  if [[ -n "${URLS[$svc]+x}" ]]; then
    printf "  ${GREEN}%-16s${NC} %s\n" "$svc" "${URLS[$svc]}"
  fi
done

# ── Useful commands ───────────────────────────────────────────────────────────
echo
echo -e "${BLUE}══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Useful Commands                                      ${NC}"
echo -e "${BLUE}══════════════════════════════════════════════════════${NC}"
echo
echo "  Logs:     ./scripts/docker-logs.sh <service>"
echo "  Follow:   ./scripts/docker-logs.sh -f"
echo "  Status:   ./scripts/docker-status.sh"
echo "  Stop:     ./scripts/docker-stop.sh"
echo "  Wipe:     ./scripts/docker-clean.sh"
echo "  Add svc:  docker compose -f docker-compose.yml up -d <service>"
echo

if [[ ${#FAILED[@]} -gt 0 ]]; then
  log_warning "Failed to start: ${FAILED[*]}"
  log_info "Check logs with: ./scripts/docker-logs.sh ${FAILED[0]}"
  exit 1
else
  log_success "All selected services are up."
fi

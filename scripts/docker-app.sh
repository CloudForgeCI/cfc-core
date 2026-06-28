#!/usr/bin/env bash
# CloudForge — Test an application locally, one at a time.
#
# Usage:
#   ./scripts/docker-app.sh start <app>    # Start infra + one app
#   ./scripts/docker-app.sh stop  <app>    # Stop the app (keep infra)
#   ./scripts/docker-app.sh restart <app>  # Restart just the app container
#   ./scripts/docker-app.sh logs  <app>    # Follow logs for that app
#   ./scripts/docker-app.sh list          # Show all testable apps
#
# The shared infrastructure (MySQL, PostgreSQL, Redis, mock-oidc) stays
# running between app switches — same as how RDS/ElastiCache persist
# independently of the application deployment on AWS.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE="docker compose -f $PROJECT_ROOT/docker-compose.yml"

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'

log_info()    { echo -e "${BLUE}ℹ${NC}  $1"; }
log_success() { echo -e "${GREEN}✓${NC}  $1"; }
log_warning() { echo -e "${YELLOW}⚠${NC}  $1"; }
log_error()   { echo -e "${RED}✗${NC}  $1"; }
log_step()    { echo -e "${CYAN}▶${NC}  $1"; }

# ── App registry ──────────────────────────────────────────────────────────────
# Maps applicationId → required infra + compose service name + local URL
declare -A APP_INFRA=(
  [wordpress]="mysql redis-main"
  [woocommerce]="mysql redis-main"
  [drupal]="postgres-main"
  [joomla]="mysql"
  [jenkins]=""
  [gitlab]=""
  [gitea]="postgres-main"
  [drone]="postgres-main"
  [grafana]="prometheus"
  [prometheus]=""
  [metabase]="postgres-main"
  [superset]="postgres-main"
  [nexus]=""
  [vault]=""
  [mattermost]="postgres-main"
  [dolphin-una]="mysql redis-main"
  [magento]="mysql redis-main opensearch"
  [opencart]="mysql"
  [postgresql-app]=""
  [redis-app]=""
)

declare -A APP_URL=(
  [wordpress]="http://localhost:8087"
  [woocommerce]="http://localhost:8089"
  [drupal]="http://localhost:8090"
  [joomla]="http://localhost:8091"
  [jenkins]="http://localhost:8080/jenkins"
  [gitlab]="http://localhost:8081"
  [gitea]="http://localhost:8083"
  [drone]="http://localhost:8082"
  [grafana]="http://localhost:3000  (admin / cfc_grafana_dev)"
  [prometheus]="http://localhost:9090"
  [metabase]="http://localhost:3002"
  [superset]="http://localhost:8088"
  [nexus]="http://localhost:8084"
  [vault]="http://localhost:8200  (token: cfc_vault_dev_token)"
  [mattermost]="http://localhost:8065"
  [dolphin-una]="http://localhost:8092"
  [magento]="http://localhost:8093  (admin: cfc_admin / cfc_Magento_dev1!)"
  [opencart]="http://localhost:8094  (admin: cfc_admin / cfc_opencart_dev)"
  [postgresql-app]="postgres://appuser:apppass@localhost:5433"
  [redis-app]="redis://:apppass@localhost:6380"
)

declare -A APP_DB_SETUP=(
  [wordpress]="mysql_create_db wordpress utf8mb4"
  [woocommerce]="mysql_create_db woocommerce utf8mb4"
  [joomla]="mysql_create_db joomla utf8mb4"
  [gitea]="pg_create_db gitea"
  [drone]="pg_create_db drone"
  [metabase]="pg_create_db metabase"
  [superset]="pg_create_db superset"
  [mattermost]="pg_create_db mattermost"
  [drupal]="pg_create_db drupal"
  [dolphin-una]="mysql_create_db una utf8mb4"
  [magento]="mysql_create_db magento utf8mb4"
  [opencart]="mysql_create_db opencart utf8mb4"
)

# ── DB helpers ────────────────────────────────────────────────────────────────
mysql_create_db() {
  local db=$1 charset=${2:-utf8mb4}
  log_step "Ensuring MySQL database '$db' exists..."
  docker exec cfc-mysql mysql -u root -pcfc_mysql_root -e "
    CREATE DATABASE IF NOT EXISTS \`$db\` CHARACTER SET $charset COLLATE ${charset}_unicode_ci;
    GRANT ALL PRIVILEGES ON \`$db\`.* TO 'cfc_dev'@'%';
    FLUSH PRIVILEGES;" 2>/dev/null \
    && log_success "MySQL: $db" \
    || log_warning "MySQL: $db (may already exist)"
}

pg_create_db() {
  local db=$1
  log_step "Ensuring PostgreSQL database '$db' exists..."
  docker exec cfc-postgres-main psql -U cfc_admin -tc \
    "SELECT 1 FROM pg_database WHERE datname='$db'" 2>/dev/null | grep -q 1 \
    || docker exec cfc-postgres-main psql -U cfc_admin -c "CREATE DATABASE $db;" 2>/dev/null
  log_success "PostgreSQL: $db"
}

# ── Infrastructure ────────────────────────────────────────────────────────────
start_infra() {
  local services=("$@")
  [[ ${#services[@]} -eq 0 ]] && return

  log_step "Starting shared infrastructure: ${services[*]}"
  $COMPOSE up -d "${services[@]}"

  # Wait for each infra service to be healthy
  for svc in "${services[@]}"; do
    local deadline=$((SECONDS + 60))
    while [[ $SECONDS -lt $deadline ]]; do
      local status
      status=$(docker inspect --format='{{.State.Health.Status}}' "cfc-$svc" 2>/dev/null || echo "running")
      [[ "$status" == "healthy" || "$status" == "running" ]] && break
      sleep 3
    done
    log_success "Infrastructure ready: $svc"
  done
}

# ── List ──────────────────────────────────────────────────────────────────────
list_apps() {
  echo
  echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${BLUE}║  CloudForge Testable Applications                            ║${NC}"
  echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
  echo
  printf "  ${CYAN}%-18s  %-28s  %s${NC}\n" "APP (applicationId)" "URL" "REQUIRES"
  printf "  %-18s  %-28s  %s\n" "──────────────────" "───────────────────────────" "────────────────"

  for app in wordpress woocommerce drupal joomla dolphin-una magento opencart jenkins gitlab gitea drone \
             grafana prometheus metabase superset nexus vault mattermost; do
    local url="${APP_URL[$app]:-—}"
    local infra="${APP_INFRA[$app]:-(none)}"
    printf "  ${GREEN}%-18s${NC}  %-28s  %s\n" "$app" "$url" "$infra"
  done
  echo
  echo "  Usage: $(basename "$0") start <app>"
  echo
}

# ── Start one app ─────────────────────────────────────────────────────────────
start_app() {
  local app=$1

  if [[ -z "${APP_URL[$app]+x}" ]]; then
    log_error "Unknown app: '$app'"
    list_apps
    exit 1
  fi

  echo
  echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
  printf "${BLUE}║${NC}  Starting: ${GREEN}%-48s${BLUE}║${NC}\n" "$app"
  echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
  echo

  # Start required infra
  if [[ -n "${APP_INFRA[$app]}" ]]; then
    read -ra infra_services <<< "${APP_INFRA[$app]}"
    start_infra "${infra_services[@]}"
  fi

  # Run any DB setup needed
  if [[ -n "${APP_DB_SETUP[$app]+x}" ]]; then
    read -ra db_cmd <<< "${APP_DB_SETUP[$app]}"
    "${db_cmd[@]}"
  fi

  # Stop any previous instance of this app cleanly
  if docker ps -q --filter "name=cfc-$app" | grep -q .; then
    log_step "Stopping previous instance of $app..."
    $COMPOSE stop "$app"
    $COMPOSE rm -f "$app"
  fi

  # Start the app
  log_step "Starting $app..."
  $COMPOSE up -d "$app"

  # Wait for healthy/running
  echo
  log_info "Waiting for $app to become ready..."
  local deadline=$((SECONDS + 120))
  local last_status=""
  while [[ $SECONDS -lt $deadline ]]; do
    local status
    status=$(docker inspect --format='{{.State.Health.Status}}' "cfc-$app" 2>/dev/null \
             || docker inspect --format='{{.State.Status}}' "cfc-$app" 2>/dev/null \
             || echo "unknown")
    if [[ "$status" != "$last_status" ]]; then
      log_info "  Status: $status"
      last_status="$status"
    fi
    [[ "$status" == "healthy" ]] && break
    [[ "$status" == "unhealthy" ]] && { log_error "$app is unhealthy"; break; }
    sleep 5
  done

  echo
  # Final status
  local final
  final=$(docker inspect --format='{{.State.Health.Status}}' "cfc-$app" 2>/dev/null \
          || docker inspect --format='{{.State.Status}}' "cfc-$app" 2>/dev/null \
          || echo "unknown")

  if [[ "$final" == "healthy" || "$final" == "running" ]]; then
    log_success "$app is running"
    echo
    echo -e "  ${GREEN}URL:${NC}  ${APP_URL[$app]}"
    echo
    echo -e "  ${CYAN}Commands:${NC}"
    echo "    Logs:    ./scripts/docker-app.sh logs $app"
    echo "    Restart: ./scripts/docker-app.sh restart $app"
    echo "    Stop:    ./scripts/docker-app.sh stop $app"
    echo "    Shell:   docker exec -it cfc-$app bash"
    echo
  else
    log_error "$app ended in state: $final"
    echo
    log_info "Check logs: ./scripts/docker-app.sh logs $app"
    exit 1
  fi
}

# ── Stop one app (keep infra running) ────────────────────────────────────────
stop_app() {
  local app=$1
  log_step "Stopping $app (infrastructure stays running)..."
  $COMPOSE stop "$app" && log_success "$app stopped"
}

# ── Restart ───────────────────────────────────────────────────────────────────
restart_app() {
  local app=$1
  log_step "Restarting $app..."
  $COMPOSE restart "$app" && log_success "$app restarted"
}

# ── Logs ──────────────────────────────────────────────────────────────────────
logs_app() {
  local app=$1
  shift
  $COMPOSE logs -f "$app" "$@"
}

# ── Main ──────────────────────────────────────────────────────────────────────
CMD="${1:-}"
APP="${2:-}"

case "$CMD" in
  start)
    [[ -z "$APP" ]] && { log_error "Specify an app: $(basename "$0") start <app>"; list_apps; exit 1; }
    start_app "$APP"
    ;;
  stop)
    [[ -z "$APP" ]] && { log_error "Specify an app: $(basename "$0") stop <app>"; exit 1; }
    stop_app "$APP"
    ;;
  restart)
    [[ -z "$APP" ]] && { log_error "Specify an app: $(basename "$0") restart <app>"; exit 1; }
    restart_app "$APP"
    ;;
  logs)
    [[ -z "$APP" ]] && { log_error "Specify an app: $(basename "$0") logs <app>"; exit 1; }
    shift 2
    logs_app "$APP" "$@"
    ;;
  list|"")
    list_apps
    ;;
  *)
    log_error "Unknown command: '$CMD'"
    echo "  Usage: $(basename "$0") start|stop|restart|logs|list <app>"
    exit 1
    ;;
esac

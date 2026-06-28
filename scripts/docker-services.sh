#!/usr/bin/env bash

# CloudForge Local Docker Environment - Service List Script
# Shows all available services

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

# Color output
BLUE='\033[0;34m'
GREEN='\033[0;32m'
NC='\033[0m'

echo -e "${BLUE}CloudForge Local Development Services${NC}"
echo "═══════════════════════════════════════════════════════"
echo

echo -e "${GREEN}Infrastructure:${NC}"
docker compose -f "$COMPOSE_FILE" ps --services | grep -E "mock-oidc|postgres|redis|mysql|mariadb"

echo
echo -e "${GREEN}CI/CD & Version Control:${NC}"
docker compose -f "$COMPOSE_FILE" ps --services | grep -E "jenkins|gitlab|drone|gitea"

echo
echo -e "${GREEN}Monitoring & Analytics:${NC}"
docker compose -f "$COMPOSE_FILE" ps --services | grep -E "prometheus|grafana|metabase|superset"

echo
echo -e "${GREEN}Artifact Registry & Secrets:${NC}"
docker compose -f "$COMPOSE_FILE" ps --services | grep -E "nexus|harbor|vault"

echo
echo -e "${GREEN}Collaboration:${NC}"
docker compose -f "$COMPOSE_FILE" ps --services | grep "mattermost"

echo
echo -e "${GREEN}CMS & E-Commerce:${NC}"
docker compose -f "$COMPOSE_FILE" ps --services | grep -E "wordpress|woocommerce|drupal|joomla|magento|opencart|una"

echo
echo -e "${GREEN}Networking:${NC}"
docker compose -f "$COMPOSE_FILE" ps --services | grep "haproxy"

echo
echo "═══════════════════════════════════════════════════════"
echo
echo "Run 'docker compose -f docker-compose.yml exec [service] bash' to access a service"

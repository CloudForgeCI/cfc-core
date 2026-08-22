#!/usr/bin/env bash

# CloudForge Local Docker Environment - Status Script
# Shows status of all services

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

# Color output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}CloudForge Docker Services Status${NC}"
echo "═══════════════════════════════════════════════════════"
echo

docker compose -f "$COMPOSE_FILE" ps

echo
echo "═══════════════════════════════════════════════════════"
echo
echo -e "${GREEN}Legend:${NC}"
echo "  Up (healthy):        Service is running and responding"
echo "  Up (unhealthy):      Service is running but not responding"
echo "  Exited:              Service has stopped"
echo "  Created:             Container created but not started"

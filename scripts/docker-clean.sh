#!/usr/bin/env bash

# CloudForge Local Docker Environment - Clean Script
# Removes all containers, volumes, and data

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

log_warning "This will remove ALL containers, volumes, and data!"
read -p "Type 'yes' to confirm: " -r RESPONSE

if [ "$RESPONSE" != "yes" ]; then
    log_info "Cleanup cancelled"
    exit 0
fi

log_info "Stopping services..."
docker compose -f "$COMPOSE_FILE" down

log_info "Removing volumes..."
docker compose -f "$COMPOSE_FILE" down -v

# Remove orphaned images
log_info "Cleaning up Docker resources..."
docker system prune -f

log_info "Cleanup complete. All data has been removed."

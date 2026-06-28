#!/usr/bin/env bash

# CloudForge Local Docker Environment - Logs Script
# Shows logs from services with filtering

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

if [ -z "$1" ]; then
    echo "Usage: $0 [service_name] [options]"
    echo
    echo "Services:"
    docker compose -f "$COMPOSE_FILE" ps --services | sort
    echo
    echo "Examples:"
    echo "  $0 jenkins                              # View Jenkins logs"
    echo "  $0 jenkins --tail 100                   # Last 100 lines"
    echo "  $0 -f                                   # Follow all logs"
    exit 1
fi

docker compose -f "$COMPOSE_FILE" logs "$@"

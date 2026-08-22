#!/bin/bash
set -euo pipefail

MAGENTO_ROOT=/var/www/html
MAGENTO="${MAGENTO_ROOT}/bin/magento"

# ── Step 1: Start Apache in the background so health checks pass ──────────────
apache2-foreground &
APACHE_PID=$!
echo "[magento] Apache started (PID $APACHE_PID)."

# ── Step 2: Wait for MySQL ─────────────────────────────────────────────────────
DB_HOST="${MAGENTO_DATABASE_HOST:-mysql}"
DB_PORT="${MAGENTO_DATABASE_PORT:-3306}"
DB_NAME="${MAGENTO_DATABASE_NAME:-magento}"
DB_USER="${MAGENTO_DATABASE_USER:-cfc_dev}"
DB_PASS="${MAGENTO_DATABASE_PASSWORD:-cfc_mysql_dev}"

echo "[magento] Waiting for MySQL at ${DB_HOST}:${DB_PORT}..."
until bash -c "echo > /dev/tcp/${DB_HOST}/${DB_PORT}" 2>/dev/null; do
    echo "[magento]   MySQL not ready yet, retrying..."
    sleep 3
done
echo "[magento] MySQL ready."

# ── Step 3: Wait for OpenSearch ───────────────────────────────────────────────
OS_HOST="${MAGENTO_OPENSEARCH_HOST:-opensearch}"
OS_PORT="${MAGENTO_OPENSEARCH_PORT:-9200}"

echo "[magento] Waiting for OpenSearch at ${OS_HOST}:${OS_PORT}..."
until curl -sf "http://${OS_HOST}:${OS_PORT}" >/dev/null 2>&1; do
    sleep 3
done
echo "[magento] OpenSearch ready."

# ── Step 4: Install Magento if not already installed ──────────────────────────
if [ ! -f "${MAGENTO_ROOT}/app/etc/env.php" ]; then
    echo "[magento] Running setup:install (first boot — this takes a few minutes)..."

    MAGENTO_HOST="${MAGENTO_BASE_URL:-http://localhost:8093/}"
    ADMIN_USER="${MAGENTO_ADMIN_USER:-cfc_admin}"
    ADMIN_PASS="${MAGENTO_ADMIN_PASSWORD:-cfc_Magento_dev1!}"
    ADMIN_EMAIL="${MAGENTO_ADMIN_EMAIL:-admin@cloudforgeci.com}"

    php "$MAGENTO" setup:install \
        --base-url="${MAGENTO_HOST}" \
        --db-host="${DB_HOST}" \
        --db-name="${DB_NAME}" \
        --db-user="${DB_USER}" \
        --db-password="${DB_PASS}" \
        --admin-firstname="CloudForge" \
        --admin-lastname="Admin" \
        --admin-email="${ADMIN_EMAIL}" \
        --admin-user="${ADMIN_USER}" \
        --admin-password="${ADMIN_PASS}" \
        --language=en_US \
        --currency=USD \
        --timezone=UTC \
        --use-rewrites=1 \
        --search-engine=opensearch \
        --opensearch-host="${OS_HOST}" \
        --opensearch-port="${OS_PORT}" \
        --opensearch-index-prefix=magento2 \
        --session-save=db

    echo "[magento] setup:install complete."

    # Fix permissions — setup:install runs as root and creates var/cache, var/page_cache, etc.
    chown -R www-data:www-data "${MAGENTO_ROOT}/var" "${MAGENTO_ROOT}/app/etc" "${MAGENTO_ROOT}/pub"
    find "${MAGENTO_ROOT}/var" -type d -exec chmod 775 {} \;
    find "${MAGENTO_ROOT}/var" -type f -exec chmod 664 {} \;

    # Set developer mode for local testing
    php "$MAGENTO" deploy:mode:set developer
    echo "[magento] Developer mode enabled."

    # Generate static content for adminhtml (storefront is lazy in dev mode)
    echo "[magento] Deploying admin static content..."
    php "$MAGENTO" setup:static-content:deploy -f en_US --area adminhtml 2>&1 | tail -5

    # Create health_check.php in pub/
    echo "<?php http_response_code(200); echo 'OK';" > "${MAGENTO_ROOT}/pub/health_check.php"
    chown www-data:www-data "${MAGENTO_ROOT}/pub/health_check.php"

    # OPcache caches env.php state BEFORE setup:install finishes (health checks race ahead).
    # Gracefully restart Apache to clear OPcache so the installed env.php is picked up.
    echo "[magento] Restarting Apache to clear OPcache..."
    kill -USR1 "$APACHE_PID" 2>/dev/null || true

    echo "[magento] First-boot setup complete."
else
    echo "[magento] Magento already installed — skipping setup."

    # Fix permissions on directories that may be root-owned after image rebuild
    chown -R www-data:www-data "${MAGENTO_ROOT}/var" "${MAGENTO_ROOT}/app/etc" \
                                "${MAGENTO_ROOT}/pub" "${MAGENTO_ROOT}/generated" 2>/dev/null || true

    # Ensure health check file exists
    if [ ! -f "${MAGENTO_ROOT}/pub/health_check.php" ]; then
        echo "<?php http_response_code(200); echo 'OK';" > "${MAGENTO_ROOT}/pub/health_check.php"
        chown www-data:www-data "${MAGENTO_ROOT}/pub/health_check.php"
    fi
fi

echo "[magento] Ready — keeping Apache in foreground."
wait $APACHE_PID

#!/bin/bash
set -euo pipefail

OC_ROOT=/var/www/html

DB_HOST="${OC_DB_HOST:-mysql}"
DB_PORT="${OC_DB_PORT:-3306}"
DB_NAME="${OC_DB_NAME:-opencart}"
DB_USER="${OC_DB_USER:-cfc_dev}"
DB_PASS="${OC_DB_PASS:-cfc_mysql_dev}"

# ── Step 1: Start Apache in background ────────────────────────────────────────
apache2-foreground &
APACHE_PID=$!
echo "[opencart] Apache started (PID $APACHE_PID)."

# ── Step 2: Wait for MySQL ─────────────────────────────────────────────────────
echo "[opencart] Waiting for MySQL at ${DB_HOST}:${DB_PORT}..."
until bash -c "echo > /dev/tcp/${DB_HOST}/${DB_PORT}" 2>/dev/null; do
    sleep 3
done
echo "[opencart] MySQL ready."

# ── Step 3: Install if not already done ───────────────────────────────────────
if [ ! -f "${OC_ROOT}/.opencart_installed" ]; then
    echo "[opencart] Running CLI installer..."

    STORE_URL="${OC_STORE_URL:-http://localhost:8094/}"
    ADMIN_USER="${OC_ADMIN_USER:-cfc_admin}"
    ADMIN_PASS="${OC_ADMIN_PASS:-cfc_opencart_dev}"
    ADMIN_EMAIL="${OC_ADMIN_EMAIL:-admin@cloudforgeci.com}"

    php "${OC_ROOT}/install/cli_install.php" install \
        --db_driver      mysqli \
        --db_hostname    "$DB_HOST" \
        --db_port        "$DB_PORT" \
        --db_database    "$DB_NAME" \
        --db_username    "$DB_USER" \
        --db_password    "$DB_PASS" \
        --db_prefix      oc_ \
        --username       "$ADMIN_USER" \
        --password       "$ADMIN_PASS" \
        --email          "$ADMIN_EMAIL" \
        --http_server    "$STORE_URL"

    echo "[opencart] Installation complete."

    # Remove the install directory (standard OpenCart post-install step)
    rm -rf "${OC_ROOT}/install"

    # Mark as installed so restarts skip the CLI installer
    touch "${OC_ROOT}/.opencart_installed"
    chown www-data:www-data "${OC_ROOT}/.opencart_installed"

    # Restart Apache to clear OPcache
    kill -USR1 "$APACHE_PID" 2>/dev/null || true
    echo "[opencart] Ready."
else
    echo "[opencart] Already installed — skipping setup."
fi

wait $APACHE_PID

#!/bin/bash
set -euo pipefail

WP="wp --path=/var/www/html --allow-root"

# ── Step 1: Copy WordPress core files if not present ──────────────────────────
if [ ! -f /var/www/html/wp-login.php ]; then
    echo "[woo] Copying WordPress files..."
    cp -a /usr/src/wordpress/. /var/www/html/
    chown -R www-data:www-data /var/www/html
    echo "[woo] WordPress files copied."
fi

# ── Step 2: Write wp-config.php if not present ────────────────────────────────
if [ ! -f /var/www/html/wp-config.php ]; then
    echo "[woo] Writing wp-config.php..."
    $WP config create \
        --dbname="${WORDPRESS_DB_NAME:-wordpress}" \
        --dbuser="${WORDPRESS_DB_USER:-wordpress}" \
        --dbpass="${WORDPRESS_DB_PASSWORD:-wordpress}" \
        --dbhost="${WORDPRESS_DB_HOST:-mysql}" \
        --skip-check
    # Add Redis config
    $WP config set WP_REDIS_HOST "${REDIS_HOST:-redis-main}" --type=constant
    $WP config set WP_REDIS_PORT "${REDIS_PORT:-6379}" --type=constant
    $WP config set WP_REDIS_PASSWORD "${REDIS_PASSWORD:-}" --type=constant
    # Skip SSL verification for MySQL 8.0 self-signed cert in local dev
    $WP config set MYSQL_CLIENT_FLAGS MYSQLI_CLIENT_SSL_DONT_VERIFY_SERVER_CERT --type=constant --raw
    echo "[woo] wp-config.php written."
fi

# ── Step 3: Start Apache in the background so health checks pass ───────────────
apache2-foreground &
APACHE_PID=$!
echo "[woo] Apache started (PID $APACHE_PID)."

# ── Step 4: Wait for MySQL ─────────────────────────────────────────────────────
echo "[woo] Waiting for MySQL..."
until mysqladmin ping -h mysql -u cfc_dev -pcfc_mysql_dev --ssl=0 --silent 2>/dev/null; do
    sleep 2
done
echo "[woo] MySQL ready."

# ── Step 5: Install WordPress core if needed ───────────────────────────────────
if ! $WP core is-installed 2>/dev/null; then
    echo "[woo] Installing WordPress core..."
    $WP core install \
        --url="http://localhost:8089" \
        --title="CloudForge WooCommerce Store" \
        --admin_user="cfc_admin" \
        --admin_password="cfc_woo_dev" \
        --admin_email="admin@cloudforgeci.com" \
        --skip-email
    echo "[woo] WordPress installed."
fi

# ── Step 6: Install & activate WooCommerce ────────────────────────────────────
if ! $WP plugin is-active woocommerce 2>/dev/null; then
    echo "[woo] Installing WooCommerce..."
    $WP plugin install woocommerce --activate
    echo "[woo] WooCommerce activated."
fi

# ── Step 7: Install & activate Redis Object Cache ────────────────────────────
if ! $WP plugin is-active redis-cache 2>/dev/null; then
    echo "[woo] Installing Redis Object Cache..."
    $WP plugin install redis-cache --activate
    $WP redis enable 2>/dev/null || true
    echo "[woo] Redis cache enabled."
fi

# ── Step 8: Create WooCommerce default pages ──────────────────────────────────
$WP wc tool run install_pages --user=1 2>/dev/null || true

echo "[woo] Setup complete."
wait $APACHE_PID

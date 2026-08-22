FROM php:8.1-apache

# ── System packages ────────────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpng-dev libjpeg-dev libwebp-dev libfreetype6-dev \
    libzip-dev libxml2-dev libcurl4-openssl-dev libonig-dev \
    libicu-dev unzip curl \
  && rm -rf /var/lib/apt/lists/*

# ── PHP extensions ─────────────────────────────────────────────────────────────
RUN docker-php-ext-configure gd --with-freetype --with-jpeg --with-webp \
 && docker-php-ext-install \
      gd curl mbstring xml dom zip intl opcache \
      mysqli pdo pdo_mysql fileinfo exif

# ── PHP tuning ─────────────────────────────────────────────────────────────────
RUN { \
    echo 'memory_limit = 256M'; \
    echo 'upload_max_filesize = 64M'; \
    echo 'post_max_size = 64M'; \
    echo 'max_execution_time = 300'; \
    echo 'date.timezone = UTC'; \
    echo 'opcache.enable = 1'; \
    echo 'opcache.memory_consumption = 128'; \
    echo 'opcache.max_accelerated_files = 10000'; \
    echo 'opcache.validate_timestamps = 1'; \
  } > /usr/local/etc/php/conf.d/opencart.ini

# ── Apache config ──────────────────────────────────────────────────────────────
RUN a2enmod rewrite \
 && sed -ri 's!AllowOverride None!AllowOverride All!g' /etc/apache2/apache2.conf

# ── Download OpenCart ──────────────────────────────────────────────────────────
ARG OPENCART_VERSION=4.0.2.3
RUN curl -fsSL \
      "https://github.com/opencart/opencart/releases/download/${OPENCART_VERSION}/opencart-${OPENCART_VERSION}.zip" \
      -o /tmp/opencart.zip \
 && unzip -q /tmp/opencart.zip -d /tmp/opencart \
 && cp -r "/tmp/opencart/opencart-${OPENCART_VERSION}/upload/." /var/www/html/ \
 && mv /var/www/html/.htaccess.txt /var/www/html/.htaccess 2>/dev/null || true \
 && rm -rf /tmp/opencart.zip /tmp/opencart

# ── Seed config files (installer will overwrite with real values) ──────────────
RUN cp /var/www/html/config-dist.php /var/www/html/config.php \
 && cp /var/www/html/admin/config-dist.php /var/www/html/admin/config.php

# ── Permissions ───────────────────────────────────────────────────────────────
RUN chown -R www-data:www-data /var/www/html \
 && find /var/www/html -type d -exec chmod 755 {} \; \
 && find /var/www/html -type f -exec chmod 644 {} \;

# ── Entrypoint ────────────────────────────────────────────────────────────────
COPY docker-entrypoint-opencart.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint-opencart.sh

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=120s \
  CMD curl -sf http://localhost/ || exit 1

ENTRYPOINT ["docker-entrypoint-opencart.sh"]
CMD ["apache2-foreground"]

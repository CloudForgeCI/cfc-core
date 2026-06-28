FROM php:8.2-apache

# ── System packages ────────────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpng-dev libjpeg-dev libwebp-dev libfreetype6-dev \
    libzip-dev libxml2-dev libcurl4-openssl-dev libonig-dev \
    libxslt1-dev libsodium-dev libicu-dev \
    unzip curl git nginx supervisor \
  && rm -rf /var/lib/apt/lists/*

# ── PHP extensions required by Magento 2.4 ───────────────────────────────────
RUN docker-php-ext-configure gd --with-freetype --with-jpeg --with-webp \
 && docker-php-ext-install \
      gd bcmath ctype curl dom fileinfo \
      iconv intl mbstring opcache pdo_mysql \
      simplexml soap sockets sodium xsl zip

RUN pecl install redis && docker-php-ext-enable redis

# ── PHP tuning for Magento ────────────────────────────────────────────────────
RUN { \
    echo 'memory_limit = 2G'; \
    echo 'max_execution_time = 18000'; \
    echo 'upload_max_filesize = 128M'; \
    echo 'post_max_size = 128M'; \
    echo 'date.timezone = UTC'; \
    echo 'opcache.enable = 1'; \
    echo 'opcache.memory_consumption = 512'; \
    echo 'opcache.max_accelerated_files = 60000'; \
    echo 'opcache.validate_timestamps = 0'; \
  } > /usr/local/etc/php/conf.d/magento.ini

# ── Apache config for Magento ─────────────────────────────────────────────────
RUN a2enmod rewrite headers deflate \
 && sed -ri 's!AllowOverride None!AllowOverride All!g' /etc/apache2/apache2.conf

ENV APACHE_DOCUMENT_ROOT=/var/www/html/pub
RUN sed -ri 's!DocumentRoot /var/www/html!DocumentRoot /var/www/html/pub!g' \
      /etc/apache2/sites-available/000-default.conf

# ── Composer ──────────────────────────────────────────────────────────────────
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

# ── Download Magento Open Source via Mage-OS public mirror (no auth needed) ───
ARG MAGENTO_VERSION=2.4.7-p3
WORKDIR /var/www/html

# Clear the default Apache index.html — composer create-project requires an empty dir
RUN rm -rf /var/www/html/*

RUN composer create-project \
      --repository-url=https://mirror.mage-os.org/ \
      magento/project-community-edition="${MAGENTO_VERSION}" \
      /var/www/html \
      --no-interaction \
      --no-progress \
 && find /var/www/html -type f -name "*.sample" -delete \
 && rm -rf /var/www/html/dev/tests /var/www/html/.git

# ── Permissions ───────────────────────────────────────────────────────────────
RUN chown -R www-data:www-data /var/www/html \
 && find /var/www/html -type d -exec chmod 755 {} \; \
 && find /var/www/html -type f -exec chmod 644 {} \; \
 && chmod +x /var/www/html/bin/magento

# ── Entrypoint ────────────────────────────────────────────────────────────────
COPY docker-entrypoint-magento.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint-magento.sh

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=600s \
  CMD curl -sf http://localhost/health_check.php || exit 1

ENTRYPOINT ["docker-entrypoint-magento.sh"]
CMD ["apache2-foreground"]

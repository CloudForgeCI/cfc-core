FROM php:8.2-apache

# Install required system packages (including ffmpeg — replaces Composer's Windows-only binary)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpng-dev libjpeg-dev libwebp-dev libfreetype6-dev \
    libzip-dev libxml2-dev libcurl4-openssl-dev \
    libmagickwand-dev libonig-dev libintl-perl \
    unzip curl git ffmpeg \
  && rm -rf /var/lib/apt/lists/*

# Install core PHP extensions (no PECL, no parallelism issues)
RUN docker-php-ext-configure gd --with-freetype --with-jpeg --with-webp \
 && docker-php-ext-install \
      gd curl mbstring xml dom zip intl opcache \
      mysqli pdo pdo_mysql bcmath exif fileinfo

# Install imagick via PECL (separate step — arm64 compile can race with -j)
RUN pecl install imagick \
 && docker-php-ext-enable imagick

# Install redis via PECL (separate step for isolation)
RUN pecl install redis \
 && docker-php-ext-enable redis

# Enable opcache
RUN docker-php-ext-enable opcache

# PHP tuning for UNA
RUN { \
    echo 'upload_max_filesize = 64M'; \
    echo 'post_max_size = 64M'; \
    echo 'memory_limit = 256M'; \
    echo 'max_execution_time = 120'; \
    echo 'date.timezone = UTC'; \
  } > /usr/local/etc/php/conf.d/una.ini

# Enable Apache mod_rewrite (required for UNA clean URLs)
RUN a2enmod rewrite

# Configure Apache document root
ENV APACHE_DOCUMENT_ROOT=/var/www/html
RUN sed -ri 's!AllowOverride None!AllowOverride All!g' /etc/apache2/apache2.conf

# Install Composer
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

# Download and install UNA
ARG UNA_VERSION=14.0.0
RUN curl -fsSL "https://github.com/unacms/una/archive/refs/tags/${UNA_VERSION}.tar.gz" \
      -o /tmp/una.tar.gz \
 && tar -xzf /tmp/una.tar.gz -C /var/www/html --strip-components=1 \
 && rm /tmp/una.tar.gz

# Install Composer dependencies (populates plugins/ vendor-dir)
RUN cd /var/www/html \
 && composer install --no-dev --no-interaction --optimize-autoloader \
 && rm -f /usr/bin/composer

# The ffmpeg/ffmpeg Composer package (v4.4.0) ships only a Windows .exe binary.
# Replace it with a symlink to the system ffmpeg so UNA's installer check passes.
RUN rm -f /var/www/html/plugins/ffmpeg/ffmpeg.exe \
 && ln -s /usr/bin/ffmpeg /var/www/html/plugins/ffmpeg/ffmpeg.exe

# The GitHub source tarball omits plugins_public/tailwind/ (it's only in upgrade packages).
# Copy the Tailwind CSS build from the latest upgrade bundle so the UI renders correctly.
RUN mkdir -p /var/www/html/plugins_public/tailwind/css \
 && cp -r /var/www/html/upgrade/files/14.0.0.RC3-14.0.0.RC4/files/plugins_public/tailwind/. \
          /var/www/html/plugins_public/tailwind/

# Set permissions
RUN chown -R www-data:www-data /var/www/html \
 && find /var/www/html -type d -exec chmod 755 {} \; \
 && find /var/www/html -type f -exec chmod 644 {} \;

# Create upload/storage directories UNA expects
RUN mkdir -p /var/www/html/storage /var/www/html/tmp \
 && chown -R www-data:www-data /var/www/html/storage /var/www/html/tmp

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
  CMD curl -sf http://localhost/ || exit 1

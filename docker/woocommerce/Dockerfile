FROM wordpress:php8.2-apache

# Install WP-CLI + MySQL client (needed for wp db commands)
RUN apt-get update && apt-get install -y --no-install-recommends default-mysql-client \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /usr/local/bin/wp \
      https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar \
 && chmod +x /usr/local/bin/wp

# Install WooCommerce setup entrypoint
COPY docker-entrypoint-woo.sh /usr/local/bin/docker-entrypoint-woo.sh
RUN chmod +x /usr/local/bin/docker-entrypoint-woo.sh

ENTRYPOINT ["docker-entrypoint-woo.sh"]
CMD ["apache2-foreground"]

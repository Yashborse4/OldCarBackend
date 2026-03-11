#!/bin/bash

# ============================================================================
# WheelDeals - Let's Encrypt SSL Initialization Script
# Run this ONCE on your VPS to bootstrap HTTPS.
#
# USAGE:
#   chmod +x init-letsencrypt.sh
#   ./init-letsencrypt.sh
#
# PREREQUISITES:
#   - DNS A record for your domain pointing to this server's IP
#   - Docker and Docker Compose installed
#   - Port 80 open on your firewall
# ============================================================================

domains=(wheeldeals.co.in api.wheeldeals.co.in)
rsa_key_size=4096
data_path="./docker/nginx/certbot"
email="support@wheeldeals.co.in"
staging=0 # Set to 1 if you're testing to avoid hitting rate limits

echo "============================================"
echo "  WheelDeals SSL Certificate Setup"
echo "============================================"
echo ""

# Check if existing data exists
if [ -d "$data_path/conf/live" ]; then
  read -p "Existing data found for ${domains[0]}. Continue and replace existing certificate? (y/N) " decision
  if [ "$decision" != "Y" ] && [ "$decision" != "y" ]; then
    exit
  fi
fi

# Step 1: Download recommended TLS parameters
if [ ! -e "$data_path/conf/options-ssl-nginx.conf" ] || [ ! -e "$data_path/conf/ssl-dhparams.pem" ]; then
  echo "### Downloading recommended TLS parameters ..."
  mkdir -p "$data_path/conf"
  curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot-nginx/certbot_nginx/_internal/tls_configs/options-ssl-nginx.conf > "$data_path/conf/options-ssl-nginx.conf"
  curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot/certbot/ssl-dhparams.pem > "$data_path/conf/ssl-dhparams.pem"
  echo "  ✓ TLS parameters downloaded"
  echo
fi

# Step 2: Create dummy certificate so Nginx can start
echo "### Creating dummy certificate for ${domains[0]} ..."
path="/etc/letsencrypt/live/${domains[0]}"
mkdir -p "$data_path/conf/live/${domains[0]}"
docker compose run --rm --entrypoint "\
  openssl req -x509 -nodes -newkey rsa:$rsa_key_size -days 1\
    -keyout '$path/privkey.pem' \
    -out '$path/fullchain.pem' \
    -subj '/CN=localhost'" certbot
echo "  ✓ Dummy certificate created"
echo

# Step 3: Switch nginx.conf to SSL version and start Nginx
echo "### Activating SSL nginx config ..."
if [ -f "./docker/nginx/nginx-ssl.conf" ]; then
  cp ./docker/nginx/nginx.conf ./docker/nginx/nginx-http.conf.bak
  cp ./docker/nginx/nginx-ssl.conf ./docker/nginx/nginx.conf
  echo "  ✓ Switched to SSL config"
else
  echo "  ⚠ nginx-ssl.conf not found, using existing nginx.conf"
fi
echo

echo "### Starting nginx ..."
docker compose up --force-recreate -d nginx
echo

# Step 4: Delete dummy certificate
echo "### Deleting dummy certificate for ${domains[0]} ..."
docker compose run --rm --entrypoint "\
  rm -Rf /etc/letsencrypt/live/${domains[0]} && \
  rm -Rf /etc/letsencrypt/archive/${domains[0]} && \
  rm -Rf /etc/letsencrypt/renewal/${domains[0]}.conf" certbot
echo "  ✓ Dummy certificate removed"
echo

# Step 5: Request real Let's Encrypt certificate
echo "### Requesting Let's Encrypt certificate for ${domains[@]} ..."

# Join domains to -d args
domain_args=""
for domain in "${domains[@]}"; do
  domain_args="$domain_args -d $domain"
done

# Select appropriate email arg
case "$email" in
  "") email_arg="--register-unsafely-without-email" ;;
  *) email_arg="--email $email" ;;
esac

# Enable staging mode if needed
if [ $staging != "0" ]; then staging_arg="--staging"; fi

docker compose run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    $staging_arg \
    $email_arg \
    $domain_args \
    --rsa-key-size $rsa_key_size \
    --agree-tos \
    --force-renewal" certbot
echo

# Step 6: Reload nginx with real certs
echo "### Reloading nginx with real certificates ..."
docker compose exec nginx nginx -s reload

echo ""
echo "============================================"
echo "  ✓ SSL Setup Complete!"
echo "  Your site is now live at:"
echo "    https://${domains[0]}"
echo "============================================"

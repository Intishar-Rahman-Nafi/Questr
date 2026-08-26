#!/bin/sh
set -e

# Render the nginx template at container start. The backend hostname/port and
# listen port differ by platform:
#   - Docker Compose: backend service on 8080, nginx on port 80 (defaults below)
#   - Railway:        backend service "backend", nginx on Railway's $PORT (8080)
# Defaults keep the image working everywhere with zero env configuration.
: "${BACKEND_HOST:=backend}"
: "${BACKEND_PORT:=8080}"
: "${PORT:=80}"
export BACKEND_HOST BACKEND_PORT PORT

envsubst '$BACKEND_HOST $BACKEND_PORT $PORT' \
  < /etc/nginx/nginx.conf.template \
  > /etc/nginx/nginx.conf

# Fail fast on an invalid rendered config rather than crashing nginx at boot.
nginx -t

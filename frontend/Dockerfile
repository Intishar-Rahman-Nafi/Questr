# =============================================================================
# QUESTR Frontend — Multi-stage production Dockerfile (self-contained service)
#
# Stage 1 (build):   Installs deps + runs `vite build` to produce static
#                     HTML/JS/CSS in dist/. Node.js itself never ships to prod.
# Stage 2 (runtime): nginx:alpine serves the static dist/ files AND reverse-
#                     proxies /api/* to the backend container. This is the
#                     SAME container that acts as the whole stack's public
#                     entry point (port 80) in docker-compose.prod.yml.
#
# Build context = frontend/ (self-contained), so it works identically from:
#   - Docker Compose:   context: ./frontend
#   - Railway:          service root = frontend/, Dockerfile = Dockerfile
#   - GHCR/CI:          context: ./frontend
#   docker build -t questr-frontend:latest frontend/
# =============================================================================

# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /build

# Copy lockfile + package.json first for the same Docker-layer-caching reason
# as the backend Dockerfile — `npm ci` only re-runs when dependencies change.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# VITE_API_BASE_URL="" (empty) means the frontend calls relative paths like
# `/api/v1/auth/login` instead of an absolute `http://localhost:8080`. Nginx
# (this same container, see nginx.conf.template) then reverse-proxies `/api/`
# to the backend service. This is what makes the frontend "just work" regardless
# of what domain/IP the app is deployed under — no hardcoded backend URL baked
# into the JS bundle.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ── Stage 2: Runtime (Nginx) ─────────────────────────────────────────────────
FROM nginx:1.25-alpine

# curl (HEALTHCHECK) + gettext (envsubst to render the nginx template below)
RUN apk add --no-cache curl gettext

# The template substitutes ${BACKEND_HOST}/${BACKEND_PORT} at container start,
# so the SAME image proxies to "backend:8080" in Docker Compose or to Railway's
# internal "backend" hostname — no rebuild needed, no hardcoded URL baked in.
COPY nginx.conf.template /etc/nginx/nginx.conf.template

# Rendered into /etc/nginx/nginx.conf before nginx starts (nginx image runs
# every script in /docker-entrypoint.d/ prior to the CMD).
COPY 30-envsubst-nginx.sh /docker-entrypoint.d/30-envsubst-nginx.sh
RUN chmod +x /docker-entrypoint.d/30-envsubst-nginx.sh

COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

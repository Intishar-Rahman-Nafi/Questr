# =============================================================================
# QUESTR Frontend — Multi-stage production Dockerfile
#
# Stage 1 (build):   Installs deps + runs `vite build` to produce static
#                     HTML/JS/CSS in dist/. Node.js itself never ships to prod.
# Stage 2 (runtime): nginx:alpine serves the static dist/ files AND reverse-
#                     proxies /api/* to the backend container. This is the
#                     SAME container that acts as the whole stack's public
#                     entry point (port 80/443) in docker-compose.prod.yml.
#
# Build context MUST be the repo root (not frontend/) because this Dockerfile
# also needs nginx/nginx.conf, which lives one level up from frontend/.
#   docker build -f frontend/Dockerfile -t questr-frontend:latest .
# =============================================================================

# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /build

# Copy lockfile + package.json first for the same Docker-layer-caching reason
# as the backend Dockerfile — `npm ci` only re-runs when dependencies change.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ .

# VITE_API_BASE_URL="" (empty) means the frontend calls relative paths like
# `/api/v1/auth/login` instead of an absolute `http://localhost:8080`. Nginx
# (this same container, see nginx.conf) then reverse-proxies `/api/` to the
# backend service. This is what makes the frontend "just work" regardless of
# what domain/IP the app is deployed under — no hardcoded backend URL baked
# into the JS bundle.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ── Stage 2: Runtime (Nginx) ─────────────────────────────────────────────────
FROM nginx:1.25-alpine

RUN apk add --no-cache curl

COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]


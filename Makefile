# QUESTR — Developer Makefile
# Usage: make <target>
# Run `make help` to see all available commands.

.PHONY: help \
        infra-up infra-down infra-restart infra-logs infra-clean infra-status \
        db-connect db-migrate db-reset \
        redis-connect redis-flush \
        kafka-topics kafka-log \
        build test run run-dev \
        docker-build docker-push \
        clean

# ── Shell & Colours ──────────────────────────────────────────────────────────
SHELL := /bin/bash
BOLD  := \033[1m
RESET := \033[0m
GREEN := \033[32m
CYAN  := \033[36m

# ── Project variables ────────────────────────────────────────────────────────
APP_NAME     := questr
DOCKER_IMAGE := questr-backend
BACKEND_PORT := 8080
DB_CONTAINER := questr-postgres
REDIS_CONT   := questr-redis
KAFKA_CONT   := questr-kafka

# Load .env if it exists (won't fail if missing)
-include .env
export

# ─────────────────────────────────────────────────────────────────────────────
default: help

help: ## Show this help message
	@echo ""
	@echo "  $(BOLD)$(CYAN)QUESTR — Developer Commands$(RESET)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-22s$(RESET) %s\n", $$1, $$2}'
	@echo ""

# ── Infrastructure ────────────────────────────────────────────────────────────
infra-up: ## Start all infrastructure services (Postgres, Redis, Kafka)
	@echo "$(BOLD)▶ Starting QUESTR infrastructure...$(RESET)"
	docker compose up -d
	@echo "$(GREEN)✓ Infrastructure started. Waiting for health checks...$(RESET)"
	@docker compose ps

infra-down: ## Stop all infrastructure services
	@echo "$(BOLD)▶ Stopping QUESTR infrastructure...$(RESET)"
	docker compose down
	@echo "$(GREEN)✓ Infrastructure stopped.$(RESET)"

infra-restart: infra-down infra-up ## Restart all infrastructure services

infra-logs: ## Tail logs for all infrastructure services
	docker compose logs -f

infra-status: ## Show status of all infrastructure services
	docker compose ps

infra-clean: ## ⚠️  Stop infrastructure AND delete all data volumes
	@echo "$(BOLD)⚠️  This will DELETE all Postgres, Redis, and Kafka data!$(RESET)"
	@read -p "Are you sure? [y/N] " confirm && [ "$$confirm" = "y" ] || exit 1
	docker compose down -v --remove-orphans
	@echo "$(GREEN)✓ Infrastructure and volumes removed.$(RESET)"

# ── Database ──────────────────────────────────────────────────────────────────
db-connect: ## Open a psql shell in the Postgres container
	docker exec -it $(DB_CONTAINER) psql -U admin -d questr_db

db-migrate: ## Run Flyway migrations (via Maven)
	./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:$(or $(DB_PORT),5433)/questr_db \
	                      -Dflyway.user=$(or $(DB_USER),admin) \
	                      -Dflyway.password=$(or $(DB_PASSWORD),secret)

db-reset: ## ⚠️  Drop and recreate the database schema
	@echo "$(BOLD)⚠️  This will DROP the entire questr_db schema!$(RESET)"
	@read -p "Are you sure? [y/N] " confirm && [ "$$confirm" = "y" ] || exit 1
	docker exec -it $(DB_CONTAINER) psql -U admin -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
	@echo "$(GREEN)✓ Schema reset. Run 'make db-migrate' or restart the app to re-migrate.$(RESET)"

# ── Redis ─────────────────────────────────────────────────────────────────────
redis-connect: ## Open a redis-cli shell in the Redis container
	docker exec -it $(REDIS_CONT) redis-cli

redis-flush: ## ⚠️  Flush ALL Redis keys (clear the cache)
	@echo "$(BOLD)⚠️  This will FLUSH all Redis data!$(RESET)"
	@read -p "Are you sure? [y/N] " confirm && [ "$$confirm" = "y" ] || exit 1
	docker exec -it $(REDIS_CONT) redis-cli FLUSHALL
	@echo "$(GREEN)✓ Redis flushed.$(RESET)"

# ── Kafka ─────────────────────────────────────────────────────────────────────
kafka-topics: ## List all Kafka topics
	docker exec -it $(KAFKA_CONT) kafka-topics --bootstrap-server localhost:9092 --list

kafka-log: ## Usage: make kafka-log TOPIC=xp-events  — consume from start
	docker exec -it $(KAFKA_CONT) kafka-console-consumer \
	  --bootstrap-server localhost:9092 \
	  --topic $(TOPIC) \
	  --from-beginning

# ── Spring Boot Application ───────────────────────────────────────────────────
build: ## Compile and package the application (skip tests)
	./mvnw clean package -DskipTests -q
	@echo "$(GREEN)✓ Build complete: target/$(APP_NAME)-*.jar$(RESET)"

test: ## Run all tests
	./mvnw test

run: ## Run the packaged JAR (requires: make build first)
	java -jar target/$(APP_NAME)-*.jar

run-dev: ## Run in dev mode via Maven Spring Boot plugin
	./mvnw spring-boot:run \
	  -Dspring-boot.run.jvmArguments="-Xmx512m" \
	  -Dspring-boot.run.profiles=default

# ── Docker Production ─────────────────────────────────────────────────────────
docker-build: ## Build the production Docker image
	docker build -t $(DOCKER_IMAGE):latest .
	@echo "$(GREEN)✓ Docker image built: $(DOCKER_IMAGE):latest$(RESET)"

docker-push: ## Push the production Docker image (set REGISTRY env var)
	docker tag $(DOCKER_IMAGE):latest $(REGISTRY)/$(DOCKER_IMAGE):latest
	docker push $(REGISTRY)/$(DOCKER_IMAGE):latest

# ── Misc ──────────────────────────────────────────────────────────────────────
clean: ## Remove build artifacts
	./mvnw clean -q
	@echo "$(GREEN)✓ Build artifacts cleaned.$(RESET)"


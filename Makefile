.PHONY: help setup up down restart stop ps status build logs logs-backend logs-frontend logs-db \
        db-shell backend-shell frontend-shell db-reset \
        backend-test backend-package backend-run \
        frontend-install frontend-dev frontend-build frontend-lint \
        desktop-build desktop-run desktop-test

DOCKER_COMPOSE = docker compose

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-22s\033[0m %s\n", $$1, $$2}'

# ─── Setup ─────────────────────────────────────────────────

setup: ## First-time setup: env file, install frontend deps, build + start stack
	@[ -f .env ] || (cp .env.example .env && echo "Created .env from .env.example")
	cd frontend && npm install
	$(DOCKER_COMPOSE) up -d --build
	@echo "✅ Stack up: frontend http://localhost:3000 · backend http://localhost:8081 · db localhost:5433"

# ─── Docker stack ──────────────────────────────────────────

up: ## Build + start the full stack (db, backend, frontend)
	$(DOCKER_COMPOSE) up -d --build

down: ## Stop and remove containers (postgres data volume kept)
	$(DOCKER_COMPOSE) down

restart: ## Rebuild images and recreate all containers (use after code changes)
	$(DOCKER_COMPOSE) up -d --build --force-recreate

stop: ## Stop containers without removing them
	$(DOCKER_COMPOSE) stop

ps: ## Show container status
	$(DOCKER_COMPOSE) ps

status: ps ## Alias for ps

build: ## Build backend + frontend images
	$(DOCKER_COMPOSE) build

logs: ## Tail logs from all containers
	$(DOCKER_COMPOSE) logs -f --tail=100

logs-backend: ## Tail backend logs
	$(DOCKER_COMPOSE) logs -f --tail=100 backend

logs-frontend: ## Tail frontend logs
	$(DOCKER_COMPOSE) logs -f --tail=100 frontend

logs-db: ## Tail database logs
	$(DOCKER_COMPOSE) logs -f --tail=100 db

# ─── Shells ────────────────────────────────────────────────

db-shell: ## Open psql in the db container
	$(DOCKER_COMPOSE) exec db psql -U sandook -d sandook

backend-shell: ## Open a shell in the backend container
	$(DOCKER_COMPOSE) exec backend sh

frontend-shell: ## Open a shell in the frontend container
	$(DOCKER_COMPOSE) exec frontend sh

db-reset: ## Wipe the database volume and recreate the stack (DESTRUCTIVE!)
	@echo "⚠️  This deletes ALL data in the sandook database!"
	@read -p "Type 'reset' to confirm: " ans; [ "$$ans" = "reset" ] || (echo "Aborted."; exit 1)
	$(DOCKER_COMPOSE) down -v
	$(DOCKER_COMPOSE) up -d --build
	@echo "✅ Fresh stack up (migrations will re-apply on backend boot)"

# ─── Backend (local dev, uses compose db on :5433) ─────────

backend-test: ## Run backend tests (Testcontainers — needs Docker running)
	cd backend && ./mvnw test

backend-package: ## Build the backend jar
	cd backend && ./mvnw -DskipTests package

backend-run: ## Run backend locally against the compose db (needs: make up)
	cd backend && ./mvnw spring-boot:run

# ─── Frontend (local dev) ──────────────────────────────────

frontend-install: ## Install frontend dependencies
	cd frontend && npm install

frontend-dev: ## Next.js dev server on :3000 (needs backend running)
	cd frontend && npm run dev

frontend-build: ## Production build
	cd frontend && npm run build

frontend-lint: ## ESLint check
	cd frontend && npm run lint

# ─── Desktop build (single executable, H2 embedded DB) ────

desktop-build: ## Build desktop app (jpackage app-image)
	./build-desktop.sh

desktop-run: ## Run the desktop app (builds first if needed)
	@test -f dist/Sandook/bin/Sandook || $(MAKE) desktop-build
	dist/Sandook/bin/Sandook

desktop-test: ## Run backend tests against embedded H2
	cd backend && ./mvnw test -Pembedded

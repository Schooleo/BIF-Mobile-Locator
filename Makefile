# Makefile - Docker Compose helper for this project
# Usage: make target [PROFILES="profile1 profile2"]

# OS Detection for Null Device (Fixes Windows NUL vs Linux /dev/null)
ifeq ($(OS),Windows_NT)
    NULL_DEVICE := NUL
else
    NULL_DEVICE := /dev/null
endif

# Configuration
DC := docker compose
DC_ROOT_FILES := -f docker-compose.yml
DC_DEBUG := -f docker-compose.debug.yml
DC_PROD_FILES := -f docker-compose.yml -f docker-compose.image.yml
ENV_FILE := .env
NETWORK := shared-dev-network

# Profile Logic: Turns PROFILES="ai db" into "--profile ai --profile db"
PROFILES ?=
COMPOSE_PROFILES := $(addprefix --profile ,$(PROFILES))

# All Profiles for Global Shutdown/Logs
ALL_PROFILES := --profile ollama --profile osrm --profile typesense --profile tailscale --profile db --profile ai --profile logs
PROD_DEFAULT_PROFILES := --profile ollama --profile osrm --profile typesense --profile tailscale
DEBUG_PROFILES := --profile db --profile ai --profile logs

.DEFAULT_GOAL := help

.PHONY: help env network-create up up-debug prod build down down-debug destroy logs ps shell init-map init-maps init-city-map init-brouter-cache init-gh-cache

help:
	@echo ========================================================
	@echo  BIF INFRASTRUCTURE MANAGER
	@echo ========================================================
	@echo Usage: make target [PROFILES="ollama typesense"] [SERVICE=name]
	@echo.
	@echo STARTUP COMMANDS:
	@echo   up           - Start CORE services (Server, Mongo). Use PROFILES to enable optional components.
	@echo   up-debug     - Start DEBUG tools only (mongo-express, Open-WebUI, Dozzle)
	@echo   up-prod      - Start demo-production stack (no deployed server images)
	@echo   prod         - Start full production stack (images from docker-compose.image.yml)
	@echo.
	@echo SETUP COMMANDS:
	@echo   init-maps    - Download Overture places + OSM data and compile OSRM routing graph
	@echo   init-city-map - Build city-level OSRM graph from LAT/LON in Vietnam
	@echo                  Example: make init-city-map LAT=10.7769 LON=106.7009 RADIUS_KM=20
	@echo   init-brouter-cache - Build BRouter rd5 cache + city-map-brouter-cache.zip for Android offline download
	@echo                        Example: make init-brouter-cache
	@echo.
	@echo SHUTDOWN COMMANDS:
	@echo   down         - Stop CORE services (keeps volumes)
	@echo   down-debug   - Stop DEBUG tools only
	@echo   destroy      - Stop everything and wipe volumes
	@echo.
	@echo PROFILES:
	@echo   ollama       - Run Ollama (AI)
	@echo   typesense    - Run Typesense search
	@echo   tailscale    - Run Tailscale
	@echo   db           - Run debug DB UI (mongo-express)
	@echo   ai           - Run debug AI UIs (open-webui)
	@echo   logs         - Run debug Dozzle logs UI
	@echo.
	@echo COMMON EXAMPLES:
	@echo   make env
	@echo   make network-create
	@echo   make init-maps
	@echo   make up PROFILES="ollama typesense"
	@echo   make up-debug PROFILES="db ai logs"
	@echo   make logs SERVICE=bif-server
	@echo   make shell SERVICE=bif-server
	@echo ========================================================

env:
ifeq ($(wildcard $(ENV_FILE)),)
	@echo Creating $(ENV_FILE) from .env.example...
	-@cp .env.example $(ENV_FILE) 2>$(NULL_DEVICE) || copy .env.example $(ENV_FILE) >$(NULL_DEVICE) 2>&1
else
	@echo $(ENV_FILE) already exists, skipping.
endif

network-create:
	@echo Ensuring docker network $(NETWORK) exists...
	@docker network inspect $(NETWORK) >$(NULL_DEVICE) 2>&1 || docker network create $(NETWORK)

up: network-create env
	@echo Starting core application...
	$(DC) $(DC_ROOT_FILES) $(COMPOSE_PROFILES) up -d --build

init-map: env
	@echo Downloading map data and compiling OSRM routing graph...
	@bash -lc "sed -i 's/\r$$//' init-scripts/init-map-data.sh && bash init-scripts/init-map-data.sh"

init-maps: init-map

init-city-map: env
	@echo Building city-level map around LAT=$(LAT), LON=$(LON), RADIUS_KM=$(RADIUS_KM)...
	@bash -lc "sed -i 's/\r$$//' init-scripts/init-city-map.sh && LAT=\"$(LAT)\" LON=\"$(LON)\" RADIUS_KM=\"$(RADIUS_KM)\" bash init-scripts/init-city-map.sh"

init-brouter-cache: env
	@echo Building BRouter rd5 cache archive for Android offline routing...
	@bash -lc "sed -i 's/\r$$//' init-scripts/build-brouter-cache.sh && bash init-scripts/build-brouter-cache.sh"

init-gh-cache: init-brouter-cache

up-debug: network-create env
	@echo Starting debug tools only...
	$(DC) $(DC_DEBUG) $(COMPOSE_PROFILES) up -d --build

up-prod: network-create env
	@echo Starting demo-production stack (no deployed server images)...
	$(DC) $(DC_ROOT_FILES) $(PROD_DEFAULT_PROFILES) up -d --build

prod: network-create env
	@echo Starting production stack...
	$(DC) $(DC_PROD_FILES) $(PROD_DEFAULT_PROFILES) $(COMPOSE_PROFILES) up -d --no-build

down:
	@echo Stopping core services...
	$(DC) $(DC_ROOT_FILES) $(ALL_PROFILES) down --remove-orphans

down-debug:
	@echo Stopping debug tools...
	$(DC) $(DC_DEBUG) $(DEBUG_PROFILES) down

destroy:
	@echo WARNING: Wiping everything and all volume data...
	$(DC) -f docker-compose.yml -f docker-compose.debug.yml $(ALL_PROFILES) down --volumes --remove-orphans

logs:
	@echo Tailing logs (use SERVICE=name to filter)...
	$(DC) -f docker-compose.yml -f docker-compose.debug.yml $(ALL_PROFILES) logs -f --tail=100 $(SERVICE)

ps:
	@echo Current service status:
	$(DC) -f docker-compose.yml -f docker-compose.debug.yml $(ALL_PROFILES) ps

shell:
ifeq ($(SERVICE),)
	$(error Usage: make shell SERVICE=name)
endif
	@echo Opening shell in $(SERVICE)...
	$(DC) $(DC_ROOT_FILES) exec $(SERVICE) sh

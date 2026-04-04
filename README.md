# Bring In Friends

![Android CI](https://github.com/Schooleo/bif-mobile-locator/actions/workflows/android-ci.yml/badge.svg)
![Android CD](https://github.com/Schooleo/bif-mobile-locator/actions/workflows/android-cd.yml/badge.svg)
![Server CI](https://github.com/Schooleo/bif-mobile-locator/actions/workflows/server-ci.yml/badge.svg)
![Server CD](https://github.com/Schooleo/bif-mobile-locator/actions/workflows/server-cd.yml/badge.svg)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Server-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java-orange.svg)](https://www.java.com)
[![API](https://img.shields.io/badge/API-29%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=29)

**Bring In Friends** is an offline-first location social platform composed of:

- A modular native Android app for map, favorites, social, profile, and auth experiences.
- A Spring Boot backend for sync, user/group/trip/chat/favorite/place APIs via REST and GraphQL.

The project is built in Java end-to-end and is organized for scalability, testing, and CI/CD automation.

## Core Features

- **Offline-first sync workflow** with server-side change metadata and synchronization endpoints.
- **Interactive mapping and place discovery** with map markers, place details, and location support.
- **Favorites management** with add/remove actions and list/detail UI flows.
- **Social features** including friend/group management and trip planning scaffolding.
- **Auth and profile management** with local credential handling and profile editing UI.
- **Dual API surface** on backend: REST for straightforward CRUD and GraphQL for nested sync-friendly payloads.
- **Comprehensive validation pipeline**: security scanning, linting, testing, checkstyle, and coverage gates.

## AI Features

The server includes an AI-assisted place suggestion and trip drafting flow under `server/src/main/java/com/bif/server/features/ai/`.

### Current AI capabilities

- **Structured place-query extraction** from natural language into `keywords`, `category`, and `vibe`
- **Grounded place suggestions** that only return server-known `Place` entities
- **Preview trip drafting** that only uses validated candidate `placeId` values from the grounded place pool
- **Typed GraphQL wrapper responses** with warnings and explicit failure codes

### AI hardening in place

- **Schema-constrained generation** at the Ollama client and agent layer
- **Post-generation validation** for stop count, duration bounds, total duration, unique `placeId` usage, and candidate membership
- **AI request guardrails** for unauthorized and rate-limited requests
- **Defensive transport handling** for malformed, empty, or error-bearing upstream Ollama payloads

### Current AI GraphQL contract

- `suggestPlacesFromQuery(query: String!): AiPlaceSuggestionResult!`
- `draftTripFromQuery(query: String!): AiTripDraftResult!`

Clients should always inspect `failureCode` and `warnings` before trusting the payload body.

## Technology Stack

### Android Application

- **Language**: Java
- **Build**: Gradle (multi-module + version catalogs)
- **Architecture**: Modular Clean Architecture
  - `android/app`
  - `android/core`
  - `android/data`
  - `android/domain`
  - `android/feature/*`
- **DI**: Dagger Hilt
- **Persistence**: Room
- **Networking**: Retrofit + Apollo GraphQL
- **UI**: Android Fragments + Material components + Edge-to-Edge layout
- **SDK**: Min API 29, Target API 36

### Spring Boot Server

- **Framework**: Spring Boot 4.x
- **Language**: Java (toolchain JDK 21)
- **Build**: Gradle wrapper `9.3.1`
- **Data Store**: MongoDB
- **APIs**:
  - Spring Web (REST)
  - Spring GraphQL
- **Containerization**: Docker + GitHub Container Registry (GHCR)
- **Quality/Security**: Checkstyle, JaCoCo (70% gate), Gitleaks, Snyk

## Project Structure

```text
BIF-Mobile-App/
├── .github/workflows/       # CI/CD and security automation
├── android/
│   ├── app/                 # App shell, navigation, DI bootstrap
│   ├── core/                # Shared utils, network, common UI resources
│   ├── data/                # Repositories, Room DB/DAO, data sources, mappers
│   ├── domain/              # Domain models and repository interfaces
│   ├── feature/
│   │   ├── auth/
│   │   ├── favorites/
│   │   ├── map/
│   │   ├── profile/
│   │   └── social/
│   └── config/checkstyle/
├── init-scripts/             # Local map/routing/bootstrap helpers
├── map-data/                 # Generated map, routing, and place seed artifacts
├── server/
│   ├── src/main/java/com/bif/server/
│   │   ├── common/           # Shared config/models (sync metadata, mongo config)
│   │   └── features/
│   │       ├── ai/
│   │       ├── auth/
│   │       ├── chat/
│   │       ├── favorite/
│   │       ├── friendship/
│   │       ├── group/
│   │       ├── map/
│   │       ├── place/
│   │       ├── route/
│   │       ├── search/
│   │       ├── sync/
│   │       ├── trip/
│   │       └── user/
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── graphql/
│   ├── src/test/             # Service and controller unit tests
│   ├── gradle/wrapper/       # Server Gradle wrapper config
│   ├── build.gradle
│   └── Dockerfile
├── Makefile                  # Local infrastructure shortcuts
├── TESTING_GUIDE.md
└── README.md
```

## CI/CD Pipeline

This project uses split CI and CD workflows with security as a prerequisite.

### Android Workflows

- **CI**: `.github/workflows/android-ci.yml`
  - Trigger: push/PR on `dev` for Android-related paths.
  - Flow: `security -> lint + unit_test + checkstyle -> build debug artifact`.
- **CD**: `.github/workflows/android-cd.yml`
  - Trigger: manual `workflow_dispatch` with `version_number`.
  - Flow: `security -> build release bundle -> publish to Play Store`.

### Server Workflows

- **CI**: `.github/workflows/server-ci.yml`
  - Trigger: push/PR on `dev` and `main` for Server-related paths.
  - Flow: `security -> checkstyle -> tests + jacoco verification`.
- **CD**: `.github/workflows/server-cd.yml`
  - Trigger: manual `workflow_dispatch` with `version_number`.
  - Flow: `security -> package bootJar -> build/push container image + upload artifact`.

### Reusable Security Workflow

- **Workflow**: `.github/workflows/security.yml`
- Runs **Gitleaks** first, then **Snyk**.
- Scans are path-scoped (`android` or `server`) to avoid cross-project false failures.

## Setup & Installation

1. **Clone the repository**:

    ```bash
    git clone https://github.com/Schooleo/bif-mobile-app.git
    ```

2. **Android app setup**:
    - Launch Android Studio.
    - Open the `android` folder.
    - Configure local secrets and keys (`google-services.json`, maps/places API keys).
    - Build and run from Android Studio or CLI.

    ```bash
    cd android
    ./gradlew assembleDebug
    ```

3. **Server setup**:
    - Ensure Docker is available for local MongoDB compose setup.
    - Run the Spring Boot server from the `server` folder.

    ```bash
    cd server
    ./gradlew bootRun
    ```

4. **Run local infrastructure stack (recommended)**:

- Use root `Makefile` helpers for a consistent local workflow.

  ```bash
  make env
  make network-create
  make up
  ```

- Optional profiles can be enabled from the same command:

  ```bash
  make up PROFILES="typesense ollama tailscale"
  ```

- Debug tooling profiles are available via:

  ```bash
  make up-debug PROFILES="db ai logs"
  ```

- Useful lifecycle shortcuts:

  ```bash
  make down          # stop and remove core services only
  make down-all      # stop and remove all containers
  make restart       # restart all non-debug containers
  make restart-debug # restart all debug containers
  ```

1. **Initialize routing/place map data (OSRM + Overture)**:

  ```bash
  make init-maps
  ```

- This generates/updates data in `map-data/`:
  - Overture places GeoJSON (`places.geojson`)
  - OSM routing source (`merged.osm.pbf`)
  - OSRM graph artifacts (`merged.osrm*`)

- City-level extraction is also available:

  ```bash
  make init-city-map LAT=10.7769 LON=106.7009 RADIUS_KM=20
  ```

1. **Build BRouter offline cache for Android (optional)**:

  ```bash
  make init-brouter-cache
  ```

1. **Run server image compose (registry-based)**:

  ```bash
  docker compose -f docker-compose.image.yml pull
  docker compose -f docker-compose.image.yml up -d
  ```

1. **Run tests locally**:

    ```bash
    cd android
    ./gradlew test

    cd ../server
    ./gradlew test jacocoTestReport jacocoTestCoverageVerification
    ```

## AI Local Verification

Use the AI smoke path only when local AI/search infrastructure is intentionally running.

### Recommended setup

```bash
make up PROFILES="ollama typesense"
```

### Optional smoke verification

```bash
make ai-smoke
```

### Notes

- The AI smoke path is **opt-in** and is not part of the default server unit-test flow.
- Default smoke credentials come from seeded dev bootstrap data or a freshly registered user during manual testing.
- Smaller local models can be used for device-constrained development, but final integration should still be validated separately against the production-target model before release.

## Credits & Attribution

Bring In Friends is built on top of open-source libraries and open geospatial datasets.

### Open-source projects

- **Spring Boot**, **Spring GraphQL**, **Spring Security** for backend APIs and auth.
- **MongoDB** for persistence.
- **AndroidX**, **Material Components**, **Room**, **WorkManager**, **Navigation**, **Hilt** for Android architecture and UI.
- **Retrofit** and **Apollo Java** for REST/GraphQL client networking.
- **MapLibre Android SDK** for map rendering.
- **OSRM** (`osrm/osrm-backend`) for route graph preprocessing and routing service.
- **BRouter** for Android offline routing cache generation.
- **Typesense** for optional full-text place search.
- **Ollama** for optional local AI profile features.
- **Firebase Analytics** for app usage analytics.

### Data sources and map data usage

- **OpenStreetMap** data is used as routing source material (downloaded via Geofabrik extracts).
- **Geofabrik** provides the country/regional `.osm.pbf` files used in setup scripts.
- **Overture Maps Foundation** place data is used for local place seed/export (`places.geojson`).

Please comply with upstream attribution and license terms when redistributing builds, datasets, or derived artifacts.

### Contributors

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/KwanTheAsian">
        <img src="https://avatars.githubusercontent.com/KwanTheAsian" width="100px;" alt="KwanTheAsian"/><br />
        <sub><b>23127020 - Biện Xuân An</b></sub>
      </a><br />
      📝 Business Analyst / Developer
    </td>
    <td align="center">
      <a href="https://github.com/PaoPao1406">
        <img src="https://avatars.githubusercontent.com/PaoPao1406" width="100px;" alt="PaoPao1406"/><br />
        <sub><b>23127025 - Đoàn Lê Gia Bảo</b></sub>
      </a><br />
      🎨 UI/UX Designer / Developer
    </td>
    <td align="center">
      <a href="https://github.com/VNQuy94">
        <img src="https://avatars.githubusercontent.com/VNQuy94" width="100px;" alt="VNQuy94"/><br />
        <sub><b>23127114 - Văn Ngọc Quý</b></sub>
      </a><br />
      ⚙️ System Designer / Developer
    </td>
    <td align="center">
      <a href="https://github.com/Schooleo">
        <img src="https://avatars.githubusercontent.com/Schooleo" width="100px;" alt="Schooleo"/><br />
        <sub><b>23127136 - Lê Nguyễn Nhật Trường</b></sub>
      </a><br />
      💻 Project Manager / Developer
    </td>
  </tr>
</table>

# Bring In Friends

![Android CI](https://github.com/Schooleo/bif-mobile-locator/actions/workflows/android-ci.yml/badge.svg)
![Android CD](https://github.com/Schooleo/bif-mobile-locator/actions/workflows/android-cd.yml/badge.svg)
![Server CI](https://github.com/Schooleo/bif-mobile-locator/actions/workflows/server-ci.yml/badge.svg)
![Server CD](https://github.com/Schooleo/bif-mobile-locator/actions/workflows/server-cd.yml/badge.svg)
![Security](https://github.com/Schooleo/bif-mobile-locator/actions/workflows/security.yml/badge.svg)
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
- **Build**: Gradle
- **Data Store**: MongoDB
- **APIs**:
  - Spring Web (REST)
  - Spring GraphQL
- **Containerization**: Docker + GitHub Container Registry (GHCR)
- **Quality/Security**: Checkstyle, JaCoCo (70% gate), Gitleaks, Snyk

## Project Structure

```text
BIF-Mobile-App/
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
├── server/
│   ├── src/main/java/com/bif/server/
│   │   ├── common/           # Shared config/models (sync metadata, mongo config)
│   │   └── features/
│   │       ├── user/
│   │       ├── group/
│   │       ├── place/
│   │       ├── favorite/
│   │       ├── chat/
│   │       ├── trip/
│   │       └── sync/
│   ├── src/main/resources/graphql/
│   ├── src/test/             # Service and controller unit tests
│   └── Dockerfile
└── .github/workflows/
    ├── android-ci.yml
    ├── android-cd.yml
    ├── server-ci.yml
    ├── server-cd.yml
    └── security.yml
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

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/Schooleo/bif-mobile-locator.git
    ```
2.  **Android app setup**:
    - Launch Android Studio.
    - Open the `android` folder.
    - Configure local secrets and keys (`google-services.json`, maps/places API keys).
    - Build and run from Android Studio or CLI.

    ```bash
    cd android
    ./gradlew assembleDebug
    ```

3.  **Server setup**:
    - Ensure Docker is available for local MongoDB compose setup.
    - Run the Spring Boot server from the `server` folder.

    ```bash
    cd server
    ./gradlew bootRun
    ```

4.  **Run server container from registry (root compose)**:
    - Use the root `docker-compose.yml` to pull and run the published server image.
    - Optional: set `SERVER_IMAGE_TAG` (default: `latest`) before running.

    ```bash
    docker compose pull
    docker compose up -d
    ```

5.  **Run tests locally**:

    ```bash
    cd android
    ./gradlew test

    cd ../server
    ./gradlew test jacocoTestReport jacocoTestCoverageVerification
    ```

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

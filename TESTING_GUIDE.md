# Testing Guide

This guide reflects the current Bring In Friends test surfaces across the Android app, backend server, and optional live AI stack.

## 1. Test Surfaces in This Repo

### Android local unit tests (`src/test/java`)

Use these for repository logic, mappers, sync handlers, utilities, ViewModels, and routing helpers.

Current examples in the repo include:

- `android/core/src/test/.../DistanceUtilsTest.java`
- `android/data/src/test/.../EmbeddedBRouterEngineTest.java`
- `android/data/src/test/.../SyncManagerTest.java`
- `android/feature/map/src/test/.../MapViewModelTest.java`
- `android/feature/social/src/test/.../TripDetailViewModelTest.java`

### Android instrumented tests (`src/androidTest/java`)

Use these for Room/database integration, fragment/UI behavior, and Android framework-dependent flows.

Current examples in the repo include:

- `android/data/src/androidTest/.../MapRepositoryInstrumentedTest.java`
- `android/data/src/androidTest/.../FavoriteDaoInstrumentedTest.java`
- `android/feature/favorites/src/androidTest/.../FavoritesFragmentInstrumentedTest.java`
- `android/feature/map/src/androidTest/.../MapViewModelInstrumentedTest.java`

### Server tests (`server/src/test/java`)

The server test suite covers controllers, services, validators, search providers, sync handlers, and AI orchestration.

Current examples in the repo include:

- `server/src/test/.../features/auth/services/AuthServiceTest.java`
- `server/src/test/.../features/place/services/PlaceServiceTest.java`
- `server/src/test/.../features/search/services/TypesensePlaceSearchProviderTest.java`
- `server/src/test/.../features/sync/services/SyncServiceTest.java`
- `server/src/test/.../features/ai/services/AiOrchestratorServiceTest.java`

### Optional live AI smoke test

This is an end-to-end verification path for the running server + Ollama + search provider setup.

- Test class: `server/src/test/java/com/bif/server/features/ai/integration/AiLiveSmokeTest.java`
- Helper command: `make ai-smoke`

## 2. Common Commands

### Android

Run all Android JVM tests:

```bash
cd android
./gradlew test
```

Run Android lint:

```bash
./gradlew lint
```

Run app checkstyle:

```bash
./gradlew :app:checkstyleMain :app:checkstyleTest
```

Run all connected/instrumented tests:

```bash
./gradlew connectedAndroidTest
```

Run a single Android test class:

```bash
./gradlew :feature:map:testDebugUnitTest --tests "com.bif.app.feature.map.MapViewModelTest"
```

Run a single instrumented test class:

```bash
./gradlew :feature:favorites:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bif.app.feature.favorites.FavoritesFragmentInstrumentedTest
```

### Server

Run the full server verification path used locally most often:

```bash
cd server
./gradlew clean test checkstyleMain checkstyleTest jacocoTestReport jacocoTestCoverageVerification
```

Run only one server test class:

```bash
./gradlew test --tests "com.bif.server.features.ai.services.AiOrchestratorServiceTest"
```

Build the server JAR after tests:

```bash
./gradlew clean bootJar
```

## 3. Optional Live AI Smoke Workflow

Use this only when you intentionally want runtime proof for the AI integration.

### Prerequisites

1. Copy and edit `.env` if needed.
2. Ensure the server can reach Ollama.
3. Start the required services:

```bash
make up PROFILES="ollama typesense"
```

4. Common environment values for this path:

- `PLACE_SEARCH_PROVIDER=typesense`
- `TYPESENSE_ENABLED=true`
- `TYPESENSE_BOOTSTRAP_REINDEX_ON_STARTUP=true`
- `AI_LIVE_SMOKE_ENABLED=true` is set by `make ai-smoke`

### Run the smoke path

```bash
make ai-smoke
```

### What it proves

- unauthorized AI access is rejected
- authenticated `suggestPlacesFromQuery` works end-to-end
- authenticated `draftTripFromQuery` works end-to-end
- the running server can talk to the configured AI/search stack

## 4. Reports and Outputs

### Android reports

Typical Gradle HTML reports live under module-specific build folders, for example:

- `android/app/build/reports/lint-results-debug.html`
- `android/app/build/reports/tests/`
- `android/feature/map/build/reports/tests/`

### Server reports

- JaCoCo HTML: `server/build/reports/jacoco/test/html/index.html`
- JUnit XML: `server/build/test-results/test/`
- Checkstyle: `server/build/reports/checkstyle/`

## 5. CI/CD Coverage

### Android CI

Workflow: `.github/workflows/android-ci.yml`

Current pipeline:

1. shared security workflow
2. lint
3. unit tests
4. app checkstyle
5. debug APK build

### Server CI

Workflow: `.github/workflows/server-ci.yml`

Current pipeline:

1. shared security workflow
2. checkstyle
3. tests + JaCoCo coverage verification
4. bootJar build

### Shared security workflow

Workflow: `.github/workflows/security.yml`

Current checks:

- Gitleaks
- Snyk

## 6. Test Naming and Scope Guidance

Use descriptive names in the format:

`unitOfWork_stateUnderTest_expectedBehavior`

Examples from this codebase style:

- `calculateDistance_samePoint_returnsZero`
- `draftTripFromQuery_invalidQuery_returnsFailure`
- `updateMyProfile_invalidAvatarUrl_throwsException`

Keep tests focused:

- one behavior per test when practical
- prefer local unit tests first
- use instrumented tests only when Android runtime behavior matters
- use live smoke tests only for integration proof, not day-to-day development

## 7. Recommended Local Verification Before Merging

### Android-only changes

```bash
cd android
./gradlew test lint :app:checkstyleMain :app:checkstyleTest
```

Add `connectedAndroidTest` when the change touches Android framework behavior, Room, or fragment/UI flows.

### Server-only changes

```bash
cd server
./gradlew clean test checkstyleMain checkstyleTest jacocoTestReport jacocoTestCoverageVerification
```

### Cross-stack changes

Run both suites above. Add `make ai-smoke` when the change affects AI contracts or the live AI integration path.

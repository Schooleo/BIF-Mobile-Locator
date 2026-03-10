# Bring In Friends

![Android CI](https://github.com/Schooleo/bif-mobile-app/actions/workflows/android-dev.yml/badge.svg)
![Android Publish](https://github.com/Schooleo/bif-mobile-app/actions/workflows/android-publish.yml/badge.svg)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java-orange.svg)](https://www.java.com)
[![API](https://img.shields.io/badge/API-29%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=29)

**Bring In Friends** is a native Android application designed to provide robust location-based services. Built entirely in Java, this project demonstrates modern Android development practices including Edge-to-Edge UI.

## Key Features

- **Native Android Development**: Built using the official Android SDK for optimal performance and integration.
- **Pure Java Implementation**: 100% Java codebase, leveraging established patterns and libraries.
- **Modular Clean Architecture**: Organized into distinct structural layers (`app`, `core`, `data`, `domain`) and feature modules (`auth`, `favorites`, `map`, `profile`, `social`) for high scalability and separation of concerns.
- **Interactive Mapping & Places**: Real-time location tracking and place search functionality utilizing repositories and device sensors.
- **Dependency Injection**: Powered by Dagger Hilt for robust and testable dependency management.
- **Edge-to-Edge Modern UI**: Implements edge-to-edge display support for an immersive user experience.

## Technical Stack

- **Language**: Java 11
- **Minimum SDK**: API 29 (Android 10)
- **Target SDK**: API 36 (Android 16)
- **Build System**: Gradle with Version Catalogs.
- **Namespace**: `com.bif.app`

## CI/CD Pipeline

This project uses [GitHub Actions](.github/workflows/android.yml) for Continuous Integration.

- **Platform**: GitHub Actions
- **Triggers**:
  - Push to `main` and `dev` branches.
  - Pull Requests to `main` and `dev` branches.
- **Workflow Steps**:
  1.  **Setup**: Configures JDK 17.
  2.  **Lint**: Runs static code analysis (`./gradlew lint`).
  3.  **Test**: Executes local unit tests (`./gradlew test`).
  4.  **Build**: Assembles the debug APK (`./gradlew assembleDebug`).
- **Artifacts**: A debug APK (`BIF-Mobile-App.apk`) is uploaded and distributed via Firebase.

## Setup & Installation

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/Schooleo/bif-mobile-app.git
    ```
2.  **Open in Android Studio**:
    - Launch Android Studio.
    - Select "Open" and navigate to the cloned directory.
3.  **Build the project**:
    - Android Studio will automatically sync with Gradle.
    - Click the "Run" button (Green Arrow) to deploy to an emulator or physical device.

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

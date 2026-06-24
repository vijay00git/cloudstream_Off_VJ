# CloudStream Project Structure and Frameworks

This document provides an overview of the CloudStream project's architecture, directory structure, and the key frameworks and libraries used to build it.

## 📁 Project Structure

The project follows a modular structure utilizing Gradle, heavily leveraging Kotlin Multiplatform (KMP) to share code across different targets (e.g., Android and JVM).

### Root Directory
- `settings.gradle.kts` & `build.gradle.kts`: Root Gradle configuration files defining modules, plugins, and repository settings.
- `gradle/libs.versions.toml`: Centralized version catalog for managing dependencies and plugin versions across all modules.

### Modules

#### 1. `:app` (Android Application Module)
This is the main Android application module that contains the UI, Android-specific components, and application wiring.
- **Location**: `app/`
- **Key Directories** (`app/src/main/java/com/lagradost/cloudstream3/`):
  - `ui/`: Contains UI components like Fragments, Activities, and custom views.
  - `mvvm/`: ViewModels following the MVVM architecture pattern.
  - `services/`: Android Services (e.g., download services, background tasks).
  - `receivers/`: Broadcast receivers.
  - `widget/`: App widgets and UI related utilities.
  - `syncproviders/`: Integrations for syncing data (e.g., MAL, Anilist, OpenSubtitles).

#### 2. `:library` (Kotlin Multiplatform Module)
This module encapsulates the core business logic, web scraping extractors, and utility functions. By being a KMP module, this logic can potentially be shared beyond just the Android app (e.g., desktop/JVM applications).
- **Location**: `library/`
- **Structure** (`library/src/`):
  - `commonMain/`: Shared Kotlin code that is agnostic of the platform.
    - `extractors/`: Video and stream extractors.
    - `plugins/`: Core logic for the extension/plugin system.
    - `network/`: Platform-agnostic networking logic.
    - `utils/`: Shared utilities.
  - `androidMain/`: Android-specific implementations of expected common interfaces.
  - `jvmMain/`: JVM-specific implementations.
  - `commonTest/`: Shared unit tests.

#### 3. `:docs`
An auxiliary module used for generating project documentation (likely using Dokka).

---

## 🛠️ Frameworks and Technologies

The project is built using modern Android development practices and relies on a wide array of libraries for media playback, networking, and UI.

### Core Languages & Architecture
- **Kotlin (v2.3.20)**: The primary language used throughout the project.
- **Kotlin Multiplatform (KMP)**: Used in the `:library` module to separate core logic from the Android UI layer.
- **MVVM (Model-View-ViewModel)**: The primary UI architectural pattern used in the `:app` module.
- **Coroutines & Flow**: Used extensively for asynchronous programming and reactive data streams (`kotlinx-coroutines-core`).
- **ViewBinding**: Used for safe and null-free view referencing in the Android UI layer.

### Media & Playback
- **Media3 (ExoPlayer)**: The core video playback engine for handling diverse stream formats (HLS, DASH, etc.) (`androidx.media3:*`).
- **Cast Support**: For casting media to Chromecast devices (`media3-cast`).
- **TorrentServer**: Integrated for streaming torrent content directly within the app (`com.github.recloudstream:torrentserver`).
- **NewPipeExtractor**: Leveraged for extracting streams from YouTube and similar platforms (`com.github.teamnewpipe:NewPipeExtractor`).

### Networking & Data Serialization
- **Ktor & OkHttp**: Used for network requests (`io.ktor:ktor-http`, `media3-datasource-okhttp`).
- **Jsoup**: A crucial tool for the web scraping nature of the app, used to parse HTML from provider sites (`org.jsoup:jsoup`).
- **Kotlinx Serialization & Jackson**: Used for parsing and serializing JSON data (`kotlinx-serialization-json`, `jackson-module-kotlin`).
- **Coil**: Modern image loading library for fetching and caching posters and thumbnails (`io.coil-kt.coil3:coil`).

### UI & Styling
- **Material Design**: Standard UI components and styling (`com.google.android.material:material`).
- **Navigation Component**: For managing app navigation and passing arguments between screens (`androidx.navigation:*`).
- **Shimmer**: For displaying loading skeletons (`com.facebook.shimmer:shimmer`).
- **OverlappingPanels**: Used for specialized UI layouts like side drawers/panels (`com.github.discord:OverlappingPanels`).

### Extensions & Scripting
- **Zipline**: Used for downloading and running Kotlin/JS code dynamically, which is likely part of the app's powerful plugin/extension system (`app.cash.zipline:zipline-android`).
- **Rhino**: A JavaScript engine for Android, also supporting the evaluation of external plugin scripts (`org.mozilla:rhino`).

### Android System Integration
- **WorkManager**: For robust background processing and scheduling (`androidx.work:work-runtime-ktx`).
- **Biometric**: For locking the app or specific profiles using fingerprint/face unlock (`androidx.biometric:biometric`).
- **TV Provider**: Support for Android TV integration, likely to update the "Play Next" row on the TV launcher (`androidx.tvprovider:tvprovider`).

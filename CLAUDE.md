# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **Calmify**, an Android application built with modern Android development practices. It's a wellness/mental health app that combines mood tracking, journaling, AI-powered chat interactions, and Unity-based avatar integration.

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material3
- **Architecture**: Multi-module MVVM with Clean Architecture
- **Dependency Injection**: Hilt/Dagger
- **Navigation**: Jetpack Navigation Compose
- **Database**: Room (local) + Realm MongoDB (cloud sync)
- **Backend Services**: Firebase (Auth, Storage, Analytics)
- **AI Integration**: Google Gemini API for chat and voice features
- **3D Avatar**: Unity integration (unityLibrary module)
- **Build System**: Gradle with Version Catalogs (libs.versions.toml)

## Project Structure

```
app/                     # Main application module
├── core/
│   ├── ui/             # Shared UI components and theme
│   └── util/           # Common utilities and models
├── data/
│   └── mongo/          # Database layer (Room + Realm)
├── features/           # Feature modules (single activity, multiple features)
│   ├── auth/          # Authentication screens
│   ├── avatar/        # Unity avatar integration
│   ├── chat/          # AI chat functionality with Gemini
│   ├── home/          # Home screen and navigation
│   └── write/         # Journal/diary writing
├── functions/         # Cloud functions (Node.js/Express)
└── buildSrc/         # Build configuration (ProjectConfig.kt)
```

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK  
./gradlew assembleRelease

# Run all tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Check dependencies
./gradlew app:dependencies
```

## Development Configuration

### Project Constants (buildSrc/src/main/java/ProjectConfig.kt)
- **compileSdk**: 34
- **minSdk**: 26  
- **targetSdk**: 34
- **Java Version**: 17
- **Kotlin**: 1.9.22
- **Compose Compiler**: 1.5.8

### Key Dependencies Managed by BOMs
- Firebase BOM: 32.7.1
- Compose BOM: 2024.02.00

## Architecture Patterns

### Module Communication
- Feature modules are independent and communicate through navigation
- Shared resources live in `core:ui` and `core:util`
- Database operations go through `data:mongo` module

### State Management
- ViewModels with StateFlow for UI state
- Hilt for dependency injection across all modules
- Room for local caching, Realm for cloud sync

### Navigation
- Single Activity architecture with Compose Navigation
- Navigation graph defined in `app/.../navigation/NavGraph.kt`
- Each feature module has its own navigation extension

## API Keys and Configuration

**IMPORTANT**: The Gemini API key is currently hardcoded in MainActivity.kt:106. For production:
1. Move to secure configuration (build config, environment variables, or remote config)
2. Never commit API keys to version control
3. Use Firebase Remote Config or similar for runtime configuration

## Firebase Integration

- **Authentication**: Firebase Auth with Google Sign-In
- **Storage**: Firebase Storage for images and media
- **Analytics**: Firebase Analytics for user tracking
- **Cloud Functions**: Located in `/functions` directory

## Unity Avatar Integration

The app includes a Unity-based 3D avatar system:
- Unity output is in `unityOutput/` and integrated as a library
- Avatar features are in `features/avatar/` module
- Supports emotion detection and memory systems

## Testing

```bash
# Run unit tests for specific module
./gradlew :features:chat:test

# Run all unit tests
./gradlew test

# Generate test coverage report
./gradlew createDebugCoverageReport
```

## Important Files

- `MainActivity.kt`: App entry point, handles initialization and cleanup
- `CalmifyApp.kt`: Main composable with global navigation
- `libs.versions.toml`: Central dependency version management
- `ProjectConfig.kt`: Build configuration constants

## Code Style Guidelines

- Follow Kotlin coding conventions
- Use Compose for all new UI
- Prefer coroutines over callbacks
- Use Hilt for dependency injection
- Keep feature modules independent
- Use sealed classes for state management
- Implement proper error handling with Result types

## Common Tasks

### Adding a New Feature Module
1. Create module in `features/` directory
2. Add to `settings.gradle`
3. Configure build.gradle with necessary dependencies
4. Add navigation extension in the module
5. Wire up in main navigation graph

### Updating Dependencies
All versions are centralized in `gradle/libs.versions.toml`. Update versions there and sync project.

### Building for Release
1. Update versionCode and versionName in `ProjectConfig.kt`
2. Ensure ProGuard rules are configured
3. Build with `./gradlew assembleRelease`
4. Sign APK with release keystore

## Cloud Functions Deployment

```bash
cd functions
# Deploy to Google Cloud Run (see functions/readme.md)
docker build -t audio-api .
docker tag audio-api gcr.io/calmify-388723/audio-streaming-api:latest
docker push gcr.io/calmify-388723/audio-streaming-api:latest
gcloud run deploy audio-streaming-api --image=gcr.io/calmify-388723/audio-streaming-api:latest --region=europe-west1
```
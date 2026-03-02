# KMP Module Conversion Template

> How to convert an Android-only module to Kotlin Multiplatform.
> Use this template when `ProjectConfig.KMP_READY = true`.

## Pre-requisites (completed in Phases 1-14)

- [x] Zero `android.util.Log` (replaced with `println`)
- [x] Zero `android.net.Uri` in domain/shared code (replaced with `String`)
- [x] Zero `FirebaseAuth.getInstance()` in class methods (injected via Hilt or parameter)
- [x] Zero `@RequiresApi` annotations (minSdk=26 covers all)
- [x] Repository interfaces in `core/util/repository/` (separate from implementations)
- [x] Domain models pure Kotlin (no Compose Color, no Android R.drawable)
- [x] AR code removed (ARCore was experimental)

## Step 1: Update build.gradle

Replace:
```groovy
plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android { ... }
```

With:
```groovy
plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.com.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = ProjectConfig.jvmTarget
            }
        }
    }

    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Pure Kotlin dependencies
            implementation(libs.coroutines.core)
            // implementation(libs.kotlinx.datetime)
            // implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // Android-specific dependencies
            implementation(libs.coroutines.android)
        }

        // iosMain.dependencies { }
    }
}

android {
    namespace = "com.lifo.modulename"
    compileSdk = ProjectConfig.compileSdk
    defaultConfig {
        minSdk = ProjectConfig.minSdk
    }
}
```

## Step 2: Reorganize Source Sets

```
module/
├── src/
│   ├── commonMain/kotlin/    # Pure Kotlin (models, interfaces, use cases)
│   ├── androidMain/kotlin/   # Android implementations (Room, Firebase, etc.)
│   └── iosMain/kotlin/       # iOS implementations (future)
```

Move files:
- **commonMain**: Interfaces, data classes, enums, pure business logic
- **androidMain**: Anything with `android.*`, `androidx.*`, Firebase, Room, Hilt

## Step 3: expect/actual Declarations

For platform-specific functionality, use expect/actual:

```kotlin
// commonMain
expect class PlatformLogger() {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

// androidMain
actual class PlatformLogger {
    actual fun d(tag: String, message: String) = println("[$tag] $message")
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        println("[$tag] ERROR: $message")
        throwable?.printStackTrace()
    }
}
```

## Step 4: DI Migration (Hilt -> Koin)

Replace:
```kotlin
// Hilt
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository
) : ViewModel()
```

With:
```kotlin
// Koin (KMP-compatible)
class MyViewModel(
    private val repository: MyRepository
) : ViewModel()

// In DI module
val myModule = module {
    viewModel { MyViewModel(get()) }
}
```

## Step 5: Database Migration (Room -> SQLDelight)

Room is Android-only. For KMP, migrate to SQLDelight:

```kotlin
// SQLDelight schema (src/commonMain/sqldelight/com/lifo/db/Chat.sq)
CREATE TABLE ChatSession (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);

selectAll:
SELECT * FROM ChatSession WHERE userId = ? ORDER BY updatedAt DESC;
```

## Module Conversion Priority

| Priority | Module | Complexity | Notes |
|----------|--------|------------|-------|
| 1 | core/util | LOW | Pure Kotlin models + interfaces, almost ready |
| 2 | core/ui | MEDIUM | Compose MP needed, theme system |
| 3 | data/mongo | HIGH | Room -> SQLDelight, Firebase -> expect/actual |
| 4 | features/auth | MEDIUM | Firebase Auth -> expect/actual |
| 5 | features/insight | LOW | Mostly UI, simple ViewModel |
| 6 | features/history | LOW | Mostly UI |
| 7 | features/settings | LOW | Simple CRUD |
| 8 | features/onboarding | LOW | UI + Lottie -> Skottie |
| 9 | features/home | MEDIUM | Dashboard + charts |
| 10 | features/write | MEDIUM | Gallery + Firebase Storage |
| 11 | features/profile | MEDIUM | Vico charts -> KMP alternative |
| 12 | features/chat | HIGH | Audio pipeline, WebSocket, Camera, VAD |
| 13 | features/humanoid | HIGH | Filament 3D (C++/JNI, cross-platform capable) |
| 14 | app | N/A | Stays Android-only (entry point) |

## KMP Library Mapping

| Current (Android) | KMP Alternative | Version Catalog Key |
|--------------------|-----------------|---------------------|
| Room | SQLDelight | `sqldelight-*` |
| Hilt | Koin | `koin-*` |
| Navigation Compose | Voyager | `voyager-*` |
| java.time | kotlinx-datetime | `kotlinx-datetime` |
| Gson | kotlinx-serialization | `kotlinx-serialization-json` |
| SharedPreferences | multiplatform-settings | `multiplatform-settings` |
| android.util.Log | println (already done!) | N/A |
| android.net.Uri | String (already done!) | N/A |
| Compose (Jetpack) | Compose Multiplatform | `org-jetbrains-compose` plugin |

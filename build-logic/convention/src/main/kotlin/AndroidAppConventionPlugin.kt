import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Convention plugin for the :app (Android Application) module.
 *
 * Applies:
 *  - com.android.application
 *  - org.jetbrains.kotlin.android
 *  - org.jetbrains.kotlin.plugin.compose
 *
 * Configures:
 *  - compileSdk, minSdk, targetSdk (from ProjectConfig / defaults)
 *  - Java 17 source/target compatibility
 *  - Kotlin JVM target 17 with common opt-in annotations
 *  - Compose build feature enabled
 *  - ProGuard for release builds (minify + shrinkResources)
 *  - APK splitting by ABI (armeabi-v7a, arm64-v8a, x86_64)
 *  - Common packaging excludes
 *  - Lint configuration
 *
 * Usage in app/build.gradle.kts:
 * ```
 * plugins {
 *     id("calmify.android.app")
 * }
 * ```
 */
class AndroidAppConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        // --- Apply plugins ---
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        // --- Configure Android application extension ---
        extensions.configure<ApplicationExtension> {
            compileSdk = findProjectConfigInt("compileSdk") ?: 36

            defaultConfig {
                minSdk = findProjectConfigInt("minSdk") ?: 26
                targetSdk = findProjectConfigInt("targetSdk") ?: 35

                testInstrumentationRunner = findProjectConfigString("testRunner")
                    ?: "androidx.test.runner.AndroidJUnitRunner"

                vectorDrawables {
                    useSupportLibrary = true
                }
            }

            // --- Build types ---
            buildTypes {
                getByName("debug") {
                    isDebuggable = true
                    isMinifyEnabled = false
                    isShrinkResources = false
                }

                getByName("release") {
                    isDebuggable = false
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )

                    ndk {
                        debugSymbolLevel = "NONE"
                    }

                    // APK splitting by ABI — smaller downloads per architecture
                    splits {
                        abi {
                            isEnable = true
                            reset()
                            include("armeabi-v7a", "arm64-v8a", "x86_64")
                            isUniversalApk = false
                        }
                    }
                }
            }

            // --- Java compatibility ---
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            // --- Build features ---
            buildFeatures {
                compose = true
                buildConfig = true

                // Disable unused features for faster builds
                aidl = false
                renderScript = false
                shaders = false
                resValues = false
            }

            // --- Packaging ---
            packaging {
                resources {
                    excludes += setOf(
                        "/META-INF/{AL2.0,LGPL2.1}",
                        "/META-INF/DEPENDENCIES",
                        "/META-INF/INDEX.LIST",
                        "/META-INF/LICENSE*",
                        "/META-INF/NOTICE*",
                        "/META-INF/*.kotlin_module",
                    )
                }
            }

            // --- Lint ---
            lint {
                checkDependencies = true
                checkReleaseBuilds = false
                abortOnError = false
                warningsAsErrors = false
            }
        }

        // --- Configure Kotlin ---
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.addAll(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                    "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
                )
            }
        }
    }
}

/**
 * Attempts to read an Int constant from the buildSrc ProjectConfig object via reflection.
 * Returns null if ProjectConfig is not on the classpath.
 */
private fun Project.findProjectConfigInt(fieldName: String): Int? {
    return try {
        val clazz = Class.forName("ProjectConfig")
        val field = clazz.getDeclaredField(fieldName)
        field.getInt(null)
    } catch (_: Exception) {
        null
    }
}

/**
 * Attempts to read a String constant from the buildSrc ProjectConfig object via reflection.
 * Returns null if ProjectConfig is not on the classpath.
 */
private fun Project.findProjectConfigString(fieldName: String): String? {
    return try {
        val clazz = Class.forName("ProjectConfig")
        val field = clazz.getDeclaredField(fieldName)
        field.get(null) as? String
    } catch (_: Exception) {
        null
    }
}

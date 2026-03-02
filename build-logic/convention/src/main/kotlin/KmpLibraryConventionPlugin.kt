import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for Kotlin Multiplatform library modules.
 *
 * Applies:
 *  - org.jetbrains.kotlin.multiplatform
 *  - com.android.library
 *
 * Configures:
 *  - Android target (compileSdk, minSdk from version catalog / ProjectConfig)
 *  - JVM target (for desktop)
 *  - iOS targets (iosX64, iosArm64, iosSimulatorArm64)
 *  - Common source set with coroutines-core
 *  - Java 17 toolchain across all JVM-based targets
 */
class KmpLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        // --- Apply plugins ---
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.library")

        // --- Configure Android library extension ---
        extensions.configure<LibraryExtension> {
            // Use ProjectConfig values if available, otherwise fall back to sensible defaults
            // that match the project's current configuration.
            compileSdk = findProjectConfigInt("compileSdk") ?: 36

            defaultConfig {
                minSdk = findProjectConfigInt("minSdk") ?: 26
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            // Common packaging options to avoid merge conflicts
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
        }

        // --- Configure Kotlin Multiplatform ---
        extensions.configure<KotlinMultiplatformExtension> {

            // Android target
            androidTarget {
                compilations.all {
                    compileTaskProvider.configure {
                        compilerOptions {
                            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                        }
                    }
                }
            }

            // JVM target (for desktop / server-side tests)
            jvm("desktop") {
                compilations.all {
                    compileTaskProvider.configure {
                        compilerOptions {
                            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                        }
                    }
                }
            }

            // iOS targets
            iosX64()
            iosArm64()
            iosSimulatorArm64()

            // --- Source set dependencies ---
            sourceSets.apply {
                val commonMain = getByName("commonMain") {
                    dependencies {
                        // Coroutines — the foundation for all async KMP code
                        val coroutinesVersion = libs.findVersion("kotlin-coroutines").get().requiredVersion
                        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                    }
                }

                val commonTest = getByName("commonTest") {
                    dependencies {
                        implementation(kotlin("test"))
                    }
                }

                val androidMain = getByName("androidMain") {
                    dependsOn(commonMain)
                }

                val desktopMain = getByName("desktopMain") {
                    dependsOn(commonMain)
                }

                // iOS source set hierarchy: iosMain -> ios{X64,Arm64,SimulatorArm64}Main
                val iosX64Main = getByName("iosX64Main")
                val iosArm64Main = getByName("iosArm64Main")
                val iosSimulatorArm64Main = getByName("iosSimulatorArm64Main")

                val iosMain = create("iosMain") {
                    dependsOn(commonMain)
                }
                iosX64Main.dependsOn(iosMain)
                iosArm64Main.dependsOn(iosMain)
                iosSimulatorArm64Main.dependsOn(iosMain)

                val iosX64Test = getByName("iosX64Test")
                val iosArm64Test = getByName("iosArm64Test")
                val iosSimulatorArm64Test = getByName("iosSimulatorArm64Test")

                val iosTest = create("iosTest") {
                    dependsOn(commonTest)
                }
                iosX64Test.dependsOn(iosTest)
                iosArm64Test.dependsOn(iosTest)
                iosSimulatorArm64Test.dependsOn(iosTest)
            }
        }
    }
}

/**
 * Attempts to read an Int constant from the buildSrc ProjectConfig object via reflection.
 * Returns null if ProjectConfig is not on the classpath (e.g., during included-build resolution).
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

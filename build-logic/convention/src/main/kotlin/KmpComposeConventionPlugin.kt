import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for Kotlin Multiplatform modules that use Compose Multiplatform UI.
 *
 * Extends [KmpLibraryConventionPlugin] by additionally applying:
 *  - org.jetbrains.compose          (Compose Multiplatform)
 *  - org.jetbrains.kotlin.plugin.compose  (Compose compiler plugin)
 *
 * Configures:
 *  - Compose dependencies in commonMain (runtime, foundation, material3, components)
 *  - Android-specific compose dependencies in androidMain
 *
 * Usage in a module's build.gradle.kts:
 * ```
 * plugins {
 *     id("calmify.kmp.compose")
 * }
 * ```
 */
class KmpComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        // --- Apply the base KMP library convention first ---
        pluginManager.apply("calmify.kmp.library")

        // --- Apply Compose plugins ---
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        // --- Configure Compose Multiplatform dependencies ---
        val compose = extensions.getByType<ComposeExtension>().dependencies

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.apply {
                getByName("commonMain") {
                    dependencies {
                        // Compose Multiplatform — core runtime and UI toolkit
                        implementation(compose.runtime)
                        implementation(compose.foundation)
                        implementation(compose.material3)
                        implementation(compose.ui)
                        implementation(compose.components.resources)
                        implementation(compose.components.uiToolingPreview)
                    }
                }

                getByName("commonTest") {
                    dependencies {
                        // Compose test utilities for multiplatform
                        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                        implementation(compose.uiTest)
                    }
                }

                getByName("androidMain") {
                    dependencies {
                        // Android-specific Compose integration
                        val activityComposeVersion = libs
                            .findVersion("activity-compose").get().requiredVersion
                        implementation("androidx.activity:activity-compose:$activityComposeVersion")

                        // Compose UI tooling for Android debug builds
                        implementation(compose.preview)
                    }
                }

                getByName("desktopMain") {
                    dependencies {
                        implementation(compose.desktop.currentOs)
                    }
                }
            }
        }
    }
}

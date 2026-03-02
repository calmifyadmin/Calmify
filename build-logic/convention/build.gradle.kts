import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.lifo.calmify.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Versions aligned with gradle/libs.versions.toml
    val agpVersion = "8.13.0"
    val kotlinVersion = "2.1.0"
    val composeMultiplatformVersion = "1.7.1"

    // Android Gradle Plugin (AGP)
    compileOnly("com.android.tools.build:gradle:$agpVersion")

    // Kotlin Gradle Plugin
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")

    // Compose Multiplatform Gradle Plugin
    compileOnly("org.jetbrains.compose:compose-gradle-plugin:$composeMultiplatformVersion")

    // Kotlin Compose Compiler Plugin (for Android-only Compose support)
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "calmify.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "calmify.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("androidApp") {
            id = "calmify.android.app"
            implementationClass = "AndroidAppConventionPlugin"
        }
    }
}

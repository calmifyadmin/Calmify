plugins {
    id("calmify.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.lifo.network"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:util"))
            implementation(project(":shared:models"))

            // Ktor HTTP client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            // Serialization
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)

            // DI
            implementation(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}

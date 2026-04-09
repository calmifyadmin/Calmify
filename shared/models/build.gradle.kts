plugins {
    id("calmify.kmp.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.lifo.shared.models"
}

kotlin {
    // Suppress ExperimentalSerializationApi warnings for @ProtoNumber
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

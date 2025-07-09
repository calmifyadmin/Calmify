object ProjectConfig {
    const val compileSdk = 34
    const val minSdk = 26  // Minimo per evitare desugaring
    const val targetSdk = 34
    const val extensionVersion = "1.5.8"  // Compose compiler per Kotlin 1.9.22

    // Versioni Java
    const val javaVersion = "17"
    const val jvmTarget = "17"

    // App version
    const val versionCode = 1
    const val versionName = "1.0.0"

    // Build config
    const val applicationId = "com.lifo.calmifyapp"
    const val testRunner = "androidx.test.runner.AndroidJUnitRunner"
}
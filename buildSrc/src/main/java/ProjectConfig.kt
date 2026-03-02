object ProjectConfig {
    const val compileSdk = 36
    const val minSdk = 26  // Minimo per evitare desugaring
    const val targetSdk = 35

    // Versioni Java
    const val javaVersion = "17"
    const val jvmTarget = "17"

    // App version
    const val versionCode = 1
    const val versionName = "1.0.0"

    // Build config
    const val applicationId = "com.lifo.calmifyapp"
    const val testRunner = "androidx.test.runner.AndroidJUnitRunner"

    // KMP Migration — Set to true after full conversion to Kotlin Multiplatform
    const val KMP_READY = false

    // Nexus Social Features — Feature flags for gradual rollout
    const val SOCIAL_ENABLED = false           // Master toggle for all social features
    const val FEED_ENABLED = false             // Feed/Threads
    const val MESSAGING_ENABLED = false        // DM between users
    const val FEDERATION_ENABLED = false       // ActivityPub bridge
    const val SEMANTIC_SEARCH_ENABLED = false  // Vertex AI Vector Search
    const val MEDIA_PIPELINE_ENABLED = false   // Cloud transcoding + CDN
}
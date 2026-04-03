package com.lifo.util

object Constants {
    const val APP_ID = "calmifyapp-uomoz"
    const val CLIENT_ID = "23546263069-vdutt98mlgpeokvs2122r0rop466mfth.apps.googleusercontent.com"

    // Deprecated: API keys are no longer stored here.
    // Gemini API key is managed by ApiConfigManager (features/chat) and read from BuildConfig.
    // Google Cloud API key is similarly managed via secure runtime configuration.
    // These empty constants remain only for source compatibility during migration.
    @Deprecated("Use ApiConfigManager in features/chat instead")
    const val GEMINI_API_KEY: String = ""
    const val WRITE_SCREEN_ARGUMENT_KEY = "diaryId"
    @Deprecated("Use ApiConfigManager in features/chat instead")
    const val GOOGLE_CLOUD_API_KEY = ""
    const val IMAGES_DATABASE = "images_db"
    const val IMAGE_TO_UPLOAD_TABLE = "image_to_upload_table"
    const val IMAGE_TO_DELETE_TABLE = "image_to_delete_table"

    // Database
    const val DATABASE_NAME = "calmify_database"
    const val CHAT_SESSION_TABLE = "chat_sessions"
    const val CHAT_MESSAGE_TABLE = "chat_messages"

}
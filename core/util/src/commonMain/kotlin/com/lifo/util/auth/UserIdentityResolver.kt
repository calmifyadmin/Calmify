package com.lifo.util.auth

import com.lifo.util.repository.SocialGraphRepository.SocialUser

/**
 * Unified identity resolution for user display across the app.
 *
 * Priority chain:
 * 1. SocialUser.displayName (user-chosen, from social profile)
 * 2. ProfileSettings.displayName (user-chosen, from onboarding/settings)
 * 3. AuthProvider.currentUserDisplayName (Google/Firebase name — fallback)
 * 4. AuthProvider.currentUserEmail prefix (last resort)
 * 5. "Utente" (ultimate fallback)
 */
object UserIdentityResolver {

    /**
     * Resolve the best display name for the current user.
     */
    fun resolveDisplayName(
        socialProfile: SocialUser? = null,
        profileDisplayName: String? = null,
        authDisplayName: String? = null,
        authEmail: String? = null,
    ): String {
        return socialProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: profileDisplayName?.takeIf { it.isNotBlank() }
            ?: authDisplayName?.takeIf { it.isNotBlank() }
            ?: authEmail?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Utente"
    }

    /**
     * Resolve the first name only (for greetings, hero sections).
     */
    fun resolveFirstName(
        socialProfile: SocialUser? = null,
        profileDisplayName: String? = null,
        authDisplayName: String? = null,
        authEmail: String? = null,
    ): String {
        val fullName = resolveDisplayName(socialProfile, profileDisplayName, authDisplayName, authEmail)
        return fullName.split(" ").firstOrNull() ?: fullName
    }

    /**
     * Resolve the @username handle for a user.
     * Falls back to userId if no username is set.
     */
    fun resolveUsername(socialProfile: SocialUser?): String {
        return socialProfile?.username?.takeIf { it.isNotBlank() }
            ?: socialProfile?.userId.orEmpty()
    }

    /**
     * Validate username format:
     * - 3-20 characters
     * - Only lowercase letters, numbers, underscores, dots
     * - Must start with a letter
     */
    fun isValidUsername(username: String): Boolean {
        if (username.length < 3 || username.length > 20) return false
        val regex = Regex("^[a-z][a-z0-9._]{2,19}$")
        return regex.matches(username)
    }

    /**
     * Get a username validation error message, or null if valid.
     */
    fun getUsernameError(username: String): String? {
        return when {
            username.isBlank() -> "Username richiesto"
            username.length < 3 -> "Minimo 3 caratteri"
            username.length > 20 -> "Massimo 20 caratteri"
            !username.first().isLetter() -> "Deve iniziare con una lettera"
            username != username.lowercase() -> "Solo lettere minuscole"
            !Regex("^[a-z0-9._]+$").matches(username) -> "Solo lettere, numeri, . e _"
            else -> null
        }
    }
}

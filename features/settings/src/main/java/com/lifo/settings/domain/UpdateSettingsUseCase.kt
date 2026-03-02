package com.lifo.settings.domain

import com.lifo.util.model.ProfileSettings
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ProfileSettingsRepository
import com.lifo.util.usecase.UseCase
/**
 * Saves updated profile settings to Firestore.
 */
class UpdateSettingsUseCase(
    private val profileSettingsRepository: ProfileSettingsRepository
) : UseCase<ProfileSettings, RequestState<Boolean>> {

    override suspend fun invoke(params: ProfileSettings): RequestState<Boolean> {
        return profileSettingsRepository.saveProfileSettings(params)
    }
}

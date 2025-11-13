package com.lifo.humanoid.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.data.vrm.VrmExtensions
import com.lifo.humanoid.data.vrm.VrmLoader
import com.lifo.humanoid.domain.model.AvatarState
import com.lifo.humanoid.domain.model.Emotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * ViewModel for the Humanoid avatar screen.
 * Manages avatar state, loading, and updates.
 */
@HiltViewModel
class HumanoidViewModel @Inject constructor(
    private val vrmLoader: VrmLoader,
    private val blendShapeController: VrmBlendShapeController
) : ViewModel() {

    private val _uiState = MutableStateFlow(HumanoidUiState())
    val uiState: StateFlow<HumanoidUiState> = _uiState.asStateFlow()

    private val _avatarState = MutableStateFlow(AvatarState.Default)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()

    // VRM model data (ByteBuffer and extensions)
    private val _vrmModelData = MutableStateFlow<Pair<ByteBuffer, VrmExtensions>?>(null)
    val vrmModelData: StateFlow<Pair<ByteBuffer, VrmExtensions>?> = _vrmModelData.asStateFlow()

    // VRM extensions (blend shapes, etc.)
    private val _vrmExtensions = MutableStateFlow<VrmExtensions?>(null)
    val vrmExtensions: StateFlow<VrmExtensions?> = _vrmExtensions.asStateFlow()

    // Blend shape weights from controller
    val blendShapeWeights: StateFlow<Map<String, Float>> = blendShapeController.currentWeights

    init {
        // Auto-load default avatar on initialization
        loadDefaultAvatar()

        // Observe avatar state changes and update blend shapes
        viewModelScope.launch {
            avatarState.collect { state ->
                updateBlendShapesForState(state)
            }
        }
    }

    /**
     * Load the default VRM avatar from assets
     */
    fun loadDefaultAvatar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // TODO: Add actual default avatar to assets
                val modelData = vrmLoader.loadVrmFromAssets("models/default_avatar.vrm")

                if (modelData != null) {
                    _vrmModelData.value = modelData
                    _vrmExtensions.value = modelData.second
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        avatarLoaded = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load default avatar"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading avatar: ${e.message}"
                )
            }
        }
    }

    /**
     * Set avatar emotion.
     * The blend shape controller will automatically fade out previous emotion.
     */
    fun setEmotion(emotion: Emotion) {
        // Immediately update emotion state
        // VrmBlendShapeController.update() will fade out old weights automatically
        _avatarState.value = _avatarState.value.copy(emotion = emotion)
    }

    /**
     * Set avatar to speaking state
     */
    fun setSpeaking(isSpeaking: Boolean) {
        _avatarState.value = _avatarState.value.copy(isSpeaking = isSpeaking)
    }

    /**
     * Set avatar to listening state
     */
    fun setListening(isListening: Boolean) {
        _avatarState.value = if (isListening) {
            AvatarState.listening()
        } else {
            _avatarState.value.copy(isListening = false)
        }
    }

    /**
     * Enable/disable vision
     */
    fun setVisionEnabled(enabled: Boolean) {
        _avatarState.value = _avatarState.value.copy(visionEnabled = enabled)
    }

    /**
     * Update blend shapes based on current avatar state.
     * Maps emotions to VRM standard blend shape presets.
     * VRM Spec: https://github.com/vrm-c/vrm-specification/blob/master/specification/0.0/schema/vrmBlendShape.schema.json
     */
    private fun updateBlendShapesForState(state: AvatarState) {
        val weights = mutableMapOf<String, Float>()

        // Map emotion to VRM standard blend shape presets
        // VRM presets: neutral, joy, angry, sorrow, fun, surprised, blink, blink_l, blink_r, a, i, u, e, o
        when (state.emotion) {
            is Emotion.Happy -> {
                weights["joy"] = state.emotion.intensity
                // Also try common variations
                weights["happy"] = state.emotion.intensity
                weights["smile"] = state.emotion.intensity
            }
            is Emotion.Sad -> {
                weights["sorrow"] = state.emotion.intensity
                weights["sad"] = state.emotion.intensity
            }
            is Emotion.Angry -> {
                weights["angry"] = state.emotion.intensity
            }
            is Emotion.Surprised -> {
                weights["surprised"] = state.emotion.intensity
            }
            is Emotion.Thinking -> {
                // Thinking might not be a standard VRM preset, try common names
                weights["thinking"] = state.emotion.intensity
                weights["serious"] = state.emotion.intensity * 0.5f
            }
            is Emotion.Calm, is Emotion.Neutral -> {
                weights["neutral"] = state.emotion.intensity
            }
            is Emotion.Excited -> {
                weights["fun"] = state.emotion.intensity
                weights["excited"] = state.emotion.intensity
            }
            is Emotion.Confused -> {
                weights["confused"] = state.emotion.intensity
            }
            is Emotion.Worried -> {
                weights["worried"] = state.emotion.intensity
            }
            is Emotion.Disappointed -> {
                weights["sorrow"] = state.emotion.intensity * 0.7f
                weights["disappointed"] = state.emotion.intensity
            }
            else -> {
                weights["neutral"] = 1.0f
            }
        }


        blendShapeController.setTargetWeights(weights)
    }

    /**
     * Update blend shapes - call every frame
     */
    fun updateBlendShapes(deltaTime: Float) {
        blendShapeController.update(deltaTime)
    }

    /**
     * Reset avatar to default state
     */
    fun resetAvatar() {
        _avatarState.value = AvatarState.Default
        blendShapeController.reset()
    }
}

/**
 * UI State for Humanoid screen
 */
data class HumanoidUiState(
    val isLoading: Boolean = false,
    val avatarLoaded: Boolean = false,
    val error: String? = null,
    val debugMode: Boolean = false
)

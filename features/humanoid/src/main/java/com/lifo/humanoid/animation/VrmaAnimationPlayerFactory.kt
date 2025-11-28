package com.lifo.humanoid.animation

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating VrmaAnimationPlayer instances.
 *
 * Since VrmaAnimationPlayer requires a Filament Engine which is only available
 * after the renderer is initialized, this factory provides deferred instantiation.
 */
@Singleton
class VrmaAnimationPlayerFactory @Inject constructor(
    private val boneMapper: VrmHumanoidBoneMapper
) {
    companion object {
        private const val TAG = "VrmaAnimationPlayerFactory"
    }

    private var currentPlayer: VrmaAnimationPlayer? = null
    private var currentEngine: Engine? = null

    /**
     * Create or get the animation player for the given engine.
     * If the engine has changed, a new player will be created.
     *
     * @param engine The Filament Engine instance
     * @return VrmaAnimationPlayer instance
     */
    fun getOrCreate(engine: Engine): VrmaAnimationPlayer {
        if (currentEngine !== engine || currentPlayer == null) {
            Log.d(TAG, "Creating new VrmaAnimationPlayer for engine")
            currentPlayer = VrmaAnimationPlayer(engine, boneMapper)
            currentEngine = engine
        }
        return currentPlayer!!
    }

    /**
     * Initialize the player with a loaded asset and node names.
     * Must be called after model is loaded.
     *
     * @param engine The Filament Engine instance
     * @param asset The loaded FilamentAsset
     * @param nodeNames List of node names from the asset
     */
    fun initializeWithAsset(
        engine: Engine,
        asset: FilamentAsset,
        nodeNames: List<String>
    ): VrmaAnimationPlayer {
        val player = getOrCreate(engine)
        player.initialize(asset, nodeNames)
        Log.d(TAG, "VrmaAnimationPlayer initialized with ${nodeNames.size} nodes")
        return player
    }

    /**
     * Get the current player if available.
     */
    fun getCurrentPlayer(): VrmaAnimationPlayer? = currentPlayer

    /**
     * Clear the current player (call on cleanup).
     */
    fun clear() {
        currentPlayer?.stop(blendOut = false)
        currentPlayer = null
        currentEngine = null
        Log.d(TAG, "VrmaAnimationPlayer cleared")
    }
}

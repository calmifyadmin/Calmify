package com.lifo.humanoid.animation

import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
/**
 * Factory for creating VrmaAnimationPlayer instances.
 *
 * Since VrmaAnimationPlayer requires a Filament Engine which is only available
 * after the renderer is initialized, this factory provides deferred instantiation.
 */
class VrmaAnimationPlayerFactory(
    private val boneMapper: VrmHumanoidBoneMapper
) {
    private var currentPlayer: VrmaAnimationPlayer? = null
    private var currentEngine: Engine? = null

    /**
     * Create or get the animation player for the given engine.
     * If the engine has changed, a new player will be created.
     * CRITICAL: The old player is destroyed before creating a new one to prevent
     * leaked idle-loop coroutines from accessing a destroyed Filament engine.
     *
     * @param engine The Filament Engine instance
     * @return VrmaAnimationPlayer instance
     */
    fun getOrCreate(engine: Engine): VrmaAnimationPlayer {
        if (currentEngine !== engine || currentPlayer == null) {
            // CRITICAL: Destroy the old player BEFORE creating a new one.
            // Without this, the old player's idle-loop coroutine (running on viewModelScope)
            // keeps executing applyBlendedAnimation() with a reference to the destroyed engine,
            // causing SIGSEGV in TransformManager.getInstance() or Animator.updateBoneMatrices().
            currentPlayer?.let { oldPlayer ->
                println("[VrmaAnimationPlayerFactory] Destroying previous player before creating new one")
                oldPlayer.stop(blendOut = false, destroy = true)
            }
            println("[VrmaAnimationPlayerFactory] Creating new VrmaAnimationPlayer for engine")
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
        println("[VrmaAnimationPlayerFactory] VrmaAnimationPlayer initialized with ${nodeNames.size} nodes")
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
        currentPlayer?.stop(blendOut = false, destroy = true)
        currentPlayer = null
        currentEngine = null
        println("[VrmaAnimationPlayerFactory] VrmaAnimationPlayer cleared")
    }
}

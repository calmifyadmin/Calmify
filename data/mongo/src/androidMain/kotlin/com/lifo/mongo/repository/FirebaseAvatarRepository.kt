package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.lifo.util.model.*
import com.lifo.util.repository.AvatarRepository
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "AvatarRepo"

class FirebaseAvatarRepository(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : AvatarRepository {

    private fun avatarsCollection(userId: String) =
        firestore.collection("users").document(userId).collection("avatars")

    override suspend fun createAvatar(userId: String, form: AvatarCreationForm): String {
        // Create Firestore document with PENDING status
        val docRef = avatarsCollection(userId).document()
        val avatarId = docRef.id

        val avatarData = hashMapOf(
            "id" to avatarId,
            "name" to form.name,
            "status" to AvatarStatus.PENDING.name,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),

            // Voice config
            "voiceId" to form.voiceId,
            "voiceConfig" to hashMapOf(
                "speakingRate" to form.speakingRate,
                "volumeGainDb" to form.volumeGain,
                "languageCode" to (form.languages.firstOrNull() ?: "it-IT"),
            ),

            // Character profile from form
            "characterProfile" to hashMapOf(
                "coreIdentity" to form.name,
                "traits" to form.traits,
                "values" to form.values.split(",").map { it.trim() }.filter { it.isNotBlank() },
                "biases" to form.biases,
                "coreWound" to form.coreWound,
                "coreStrength" to form.coreStrength,
                "primaryNeed" to form.primaryNeed.name,
                "goals" to form.goals,
                "avoidTopics" to form.avoidTopics.split(",").map { it.trim() }.filter { it.isNotBlank() },
                "culturalReference" to form.culturalReference,
                "communicationStyle" to hashMapOf(
                    "directness" to form.directness,
                    "humor" to form.humor,
                    "speakingRhythm" to if (form.pauseStyle) "with_pauses" else "continuous",
                ),
                "emotionalProfile" to hashMapOf(
                    "attachmentStyle" to form.attachmentStyle.name,
                    "stressResponse" to form.stressResponse.name,
                    "affectionStyle" to form.affectionStyle,
                    "vulnerabilityTriggers" to form.vulnerabilityTriggers.split(",").map { it.trim() }.filter { it.isNotBlank() },
                ),
            ),

            // VRM params
            "vrmParams" to hashMapOf(
                "bodyType" to form.appearance.bodyType,
                "height" to form.appearance.height,
                "skinTone" to form.appearance.skinTone,
                "hairStyle" to form.appearance.hairStyle,
                "hairColor" to form.appearance.hairColor,
                "outfitType" to form.appearance.outfitType,
                "extras" to form.appearance.extras,
            ),

            // Raw form answers — never delete
            "formAnswers" to hashMapOf(
                "v1" to hashMapOf(
                    "name" to form.name,
                    "perceivedAge" to form.perceivedAge,
                    "gender" to form.gender.name,
                    "languages" to form.languages,
                    "traits" to form.traits,
                    "stressResponse" to form.stressResponse.name,
                    "directness" to form.directness,
                    "humor" to form.humor,
                    "decisionStyle" to form.decisionStyle.name,
                    "authorityRelation" to form.authorityRelation,
                    "values" to form.values,
                    "biases" to form.biases,
                    "coreWound" to form.coreWound,
                    "coreStrength" to form.coreStrength,
                    "culturalBackground" to form.culturalBackground,
                    "culturalReference" to form.culturalReference,
                    "primaryNeed" to form.primaryNeed.name,
                    "goals" to form.goals,
                    "avoidTopics" to form.avoidTopics,
                    "engagementFrequency" to form.engagementFrequency.name,
                    "voiceId" to form.voiceId,
                    "speakingRate" to form.speakingRate,
                    "voiceTone" to form.voiceTone.name,
                    "pauseStyle" to form.pauseStyle,
                    "volumeGain" to form.volumeGain,
                    "attachmentStyle" to form.attachmentStyle.name,
                    "jealousyLevel" to form.jealousyLevel,
                    "affectionStyle" to form.affectionStyle,
                    "vulnerabilityTriggers" to form.vulnerabilityTriggers,
                    "emotionalBarrier" to form.emotionalBarrier,
                ),
            ),
        )

        Log.d(TAG, "Creating avatar doc: $avatarId for user: $userId")
        docRef.set(avatarData).await()
        Log.d(TAG, "Firestore doc created OK")

        // === Pipeline Step 1: Generate system prompt via Cloud Function + Gemini ===
        try {
            docRef.update("status", "GENERATING").await()

            // Build a serializable copy without FieldValue sentinels for the HTTP call
            val callableData = HashMap(avatarData).apply {
                remove("createdAt")
                remove("updatedAt")
            }

            Log.d(TAG, "Calling createAvatarPipeline Cloud Function...")
            val result = functions.getHttpsCallable("createAvatarPipeline")
                .call(hashMapOf("avatarData" to callableData))
                .await()
            Log.d(TAG, "Cloud Function returned OK")

            @Suppress("UNCHECKED_CAST")
            val resultData = result.getData() as? Map<String, Any?> ?: emptyMap()
            Log.d(TAG, "Result keys: ${resultData.keys}")
            val systemPrompt = resultData["systemPrompt"] as? String

            if (systemPrompt != null) {
                docRef.update(
                    mapOf(
                        "systemPrompt" to hashMapOf(
                            "raw" to systemPrompt,
                            "version" to 1,
                            "generatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        ),
                        "status" to AvatarStatus.PROMPT_READY.name,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )
                ).await()
            }

            // === Pipeline Step 2: Generate VRM via Cloud Run ===
            try {
                val vrmResult = functions.getHttpsCallable("generateVrmAvatar")
                    .call(hashMapOf(
                        "userId" to userId,
                        "avatarId" to avatarId,
                        "gender" to form.gender.name,
                        "vrmParams" to hashMapOf(
                            "bodyType" to form.appearance.bodyType,
                            "hairStyle" to form.appearance.hairStyle,
                            "hairColor" to form.appearance.hairColor,
                            "skinTone" to form.appearance.skinTone,
                            "outfitType" to form.appearance.outfitType,
                            "extras" to form.appearance.extras,
                        ),
                    ))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val vrmData = vrmResult.getData() as? Map<String, Any?> ?: emptyMap()
                val vrmUrl = vrmData["vrmUrl"] as? String

                if (vrmUrl != null) {
                    docRef.update(
                        mapOf(
                            "vrmUrl" to vrmUrl,
                            "status" to AvatarStatus.READY.name,
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            } catch (vrmError: Exception) {
                // VRM generation failed but prompt is ready — keep PROMPT_READY status
                Log.w(TAG, "VRM generation failed: ${vrmError.message}", vrmError)
            }

        } catch (e: Exception) {
            // Pipeline failed — update status to ERROR
            Log.e(TAG, "Pipeline failed: ${e.message}", e)
            try {
                docRef.update(
                    mapOf(
                        "status" to AvatarStatus.ERROR.name,
                        "errorMessage" to (e.message ?: "Errore generazione prompt"),
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )
                ).await()
            } catch (_: Exception) { /* ignore secondary failure */ }
        }

        return avatarId
    }

    override fun observeAvatar(userId: String, avatarId: String): Flow<Avatar?> = callbackFlow {
        val listener = avatarsCollection(userId).document(avatarId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val avatar = snapshot?.let { parseAvatar(it) }
                trySend(avatar)
            }
        awaitClose { listener.remove() }
    }

    override fun observeUserAvatars(userId: String): Flow<List<Avatar>> = callbackFlow {
        val listener = avatarsCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val avatars = snapshots?.documents?.mapNotNull { parseAvatar(it) } ?: emptyList()
                trySend(avatars)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getAvatar(userId: String, avatarId: String): Avatar? {
        val doc = avatarsCollection(userId).document(avatarId).get().await()
        return parseAvatar(doc)
    }

    override suspend fun getUserAvatars(userId: String): List<Avatar> {
        val docs = avatarsCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        return docs.documents.mapNotNull { parseAvatar(it) }
    }

    override suspend fun deleteAvatar(userId: String, avatarId: String) {
        avatarsCollection(userId).document(avatarId).delete().await()
    }

    override suspend fun updateAvatarStatus(userId: String, avatarId: String, status: AvatarStatus) {
        avatarsCollection(userId).document(avatarId).update(
            mapOf(
                "status" to status.name,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            )
        ).await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAvatar(doc: com.google.firebase.firestore.DocumentSnapshot): Avatar? {
        if (!doc.exists()) return null

        val voiceConfigMap = doc.get("voiceConfig") as? Map<String, Any?> ?: emptyMap()
        val systemPromptMap = doc.get("systemPrompt") as? Map<String, Any?> ?: emptyMap()
        val charProfileMap = doc.get("characterProfile") as? Map<String, Any?> ?: emptyMap()
        val commStyleMap = charProfileMap["communicationStyle"] as? Map<String, Any?> ?: emptyMap()
        val emotionalMap = charProfileMap["emotionalProfile"] as? Map<String, Any?> ?: emptyMap()
        val vrmMap = doc.get("vrmParams") as? Map<String, Any?> ?: emptyMap()

        return Avatar(
            id = doc.getString("id") ?: doc.id,
            name = doc.getString("name") ?: "",
            status = try { AvatarStatus.valueOf(doc.getString("status") ?: "PENDING") } catch (_: Exception) { AvatarStatus.PENDING },
            createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            vrmUrl = doc.getString("vrmUrl") ?: "",
            thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
            voiceId = doc.getString("voiceId") ?: "",
            voiceConfig = VoiceConfig(
                speakingRate = (voiceConfigMap["speakingRate"] as? Number)?.toFloat() ?: 1.0f,
                volumeGainDb = (voiceConfigMap["volumeGainDb"] as? Number)?.toFloat() ?: 0.0f,
                languageCode = voiceConfigMap["languageCode"] as? String ?: "it-IT",
            ),
            systemPrompt = AvatarSystemPrompt(
                raw = systemPromptMap["raw"] as? String ?: "",
                version = (systemPromptMap["version"] as? Number)?.toInt() ?: 1,
                generatedAt = (systemPromptMap["generatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L,
            ),
            characterProfile = CharacterProfile(
                coreIdentity = charProfileMap["coreIdentity"] as? String ?: "",
                traits = charProfileMap["traits"] as? List<String> ?: emptyList(),
                values = charProfileMap["values"] as? List<String> ?: emptyList(),
                biases = charProfileMap["biases"] as? List<String> ?: emptyList(),
                coreWound = charProfileMap["coreWound"] as? String ?: "",
                coreStrength = charProfileMap["coreStrength"] as? String ?: "",
                primaryNeed = charProfileMap["primaryNeed"] as? String ?: "",
                goals = charProfileMap["goals"] as? String ?: "",
                avoidTopics = charProfileMap["avoidTopics"] as? List<String> ?: emptyList(),
                culturalReference = charProfileMap["culturalReference"] as? String ?: "",
                communicationStyle = CommunicationStyle(
                    directness = (commStyleMap["directness"] as? Number)?.toFloat() ?: 0.5f,
                    humor = (commStyleMap["humor"] as? Number)?.toFloat() ?: 0.5f,
                    speakingRhythm = commStyleMap["speakingRhythm"] as? String ?: "",
                ),
                emotionalProfile = EmotionalProfile(
                    attachmentStyle = emotionalMap["attachmentStyle"] as? String ?: "",
                    stressResponse = emotionalMap["stressResponse"] as? String ?: "",
                    affectionStyle = emotionalMap["affectionStyle"] as? String ?: "",
                    vulnerabilityTriggers = emotionalMap["vulnerabilityTriggers"] as? List<String> ?: emptyList(),
                ),
            ),
            vrmParams = VrmParams(
                bodyType = vrmMap["bodyType"] as? String ?: "",
                height = (vrmMap["height"] as? Number)?.toFloat() ?: 170f,
                skinTone = vrmMap["skinTone"] as? String ?: "",
                hairStyle = vrmMap["hairStyle"] as? String ?: "",
                hairColor = vrmMap["hairColor"] as? String ?: "",
                outfitType = vrmMap["outfitType"] as? String ?: "",
                extras = vrmMap["extras"] as? List<String> ?: emptyList(),
            ),
        )
    }
}

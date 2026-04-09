package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.model.ProfileSettingsProto
import com.lifo.shared.model.PsychologicalProfileProto
import com.lifo.shared.model.StressPeakProto
import org.slf4j.LoggerFactory

class ProfileService(private val db: Firestore?) {
    private val logger = LoggerFactory.getLogger(ProfileService::class.java)
    private val collection = "profileSettings"
    private val psychCollection = "psychologicalProfiles"

    suspend fun getProfile(userId: String): ProfileSettingsProto? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val doc = firestore.collection(collection).document(userId).get().get()
        if (!doc.exists()) return null

        return ProfileSettingsProto(
            id = doc.id,
            ownerId = userId,
            createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
            updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L,
            isOnboardingCompleted = doc.getBoolean("isOnboardingCompleted") ?: false,
            displayName = doc.getString("displayName") ?: "",
            fullName = doc.getString("fullName") ?: "",
            dateOfBirth = doc.getString("dateOfBirth") ?: "",
            gender = doc.getString("gender") ?: "PREFER_NOT_TO_SAY",
            height = doc.getLong("height")?.toInt() ?: 0,
            weight = doc.getDouble("weight")?.toFloat() ?: 0f,
            location = doc.getString("location") ?: "",
            primaryConcerns = (doc.get("primaryConcerns") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            mentalHealthHistory = doc.getString("mentalHealthHistory") ?: "NO_DIAGNOSIS",
            currentTherapy = doc.getBoolean("currentTherapy") ?: false,
            medication = doc.getBoolean("medication") ?: false,
            occupation = doc.getString("occupation") ?: "",
            sleepHoursAvg = doc.getDouble("sleepHoursAvg")?.toFloat() ?: 7f,
            exerciseFrequency = doc.getString("exerciseFrequency") ?: "MODERATE",
            socialSupport = doc.getString("socialSupport") ?: "MODERATE",
            primaryGoals = (doc.get("primaryGoals") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            preferredCopingStrategies = (doc.get("preferredCopingStrategies") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            aiTone = doc.getString("aiTone") ?: "FRIENDLY",
            reminderFrequency = doc.getString("reminderFrequency") ?: "DAILY",
            topicsToAvoid = (doc.get("topicsToAvoid") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            shareDataForResearch = doc.getBoolean("shareDataForResearch") ?: false,
            enableAdvancedInsights = doc.getBoolean("enableAdvancedInsights") ?: true,
        )
    }

    suspend fun updateProfile(userId: String, profile: ProfileSettingsProto): ProfileSettingsProto {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val now = System.currentTimeMillis()

        val data = hashMapOf<String, Any>(
            "ownerId" to userId,
            "createdAtMillis" to (profile.createdAtMillis.takeIf { it > 0 } ?: now),
            "updatedAtMillis" to now,
            "isOnboardingCompleted" to profile.isOnboardingCompleted,
            "displayName" to profile.displayName,
            "fullName" to profile.fullName,
            "dateOfBirth" to profile.dateOfBirth,
            "gender" to profile.gender,
            "height" to profile.height,
            "weight" to profile.weight.toDouble(),
            "location" to profile.location,
            "primaryConcerns" to profile.primaryConcerns,
            "mentalHealthHistory" to profile.mentalHealthHistory,
            "currentTherapy" to profile.currentTherapy,
            "medication" to profile.medication,
            "occupation" to profile.occupation,
            "sleepHoursAvg" to profile.sleepHoursAvg.toDouble(),
            "exerciseFrequency" to profile.exerciseFrequency,
            "socialSupport" to profile.socialSupport,
            "primaryGoals" to profile.primaryGoals,
            "preferredCopingStrategies" to profile.preferredCopingStrategies,
            "aiTone" to profile.aiTone,
            "reminderFrequency" to profile.reminderFrequency,
            "topicsToAvoid" to profile.topicsToAvoid,
            "shareDataForResearch" to profile.shareDataForResearch,
            "enableAdvancedInsights" to profile.enableAdvancedInsights,
        )

        firestore.collection(collection).document(userId).set(data).get()
        logger.info("Updated profile for user $userId")
        return profile.copy(id = userId, ownerId = userId, updatedAtMillis = now)
    }

    suspend fun getPsychologicalProfiles(userId: String, weeks: Int): List<PsychologicalProfileProto> {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val docs = firestore.collection(psychCollection)
            .whereEqualTo("ownerId", userId)
            .orderBy("computedAtMillis", Query.Direction.DESCENDING)
            .limit(weeks)
            .get().get().documents

        return docs.map { doc -> mapPsychProfile(doc) }
    }

    suspend fun getLatestPsychologicalProfile(userId: String): PsychologicalProfileProto? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val docs = firestore.collection(psychCollection)
            .whereEqualTo("ownerId", userId)
            .orderBy("computedAtMillis", Query.Direction.DESCENDING)
            .limit(1)
            .get().get().documents

        return docs.firstOrNull()?.let { mapPsychProfile(it) }
    }

    suspend fun getPsychologicalProfileByWeek(userId: String, weekNumber: Int, year: Int): PsychologicalProfileProto? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val docs = firestore.collection(psychCollection)
            .whereEqualTo("ownerId", userId)
            .whereEqualTo("weekNumber", weekNumber)
            .whereEqualTo("year", year)
            .limit(1)
            .get().get().documents

        return docs.firstOrNull()?.let { mapPsychProfile(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapPsychProfile(doc: com.google.cloud.firestore.DocumentSnapshot): PsychologicalProfileProto {
        val peaks = (doc.get("stressPeaks") as? List<Map<String, Any>>)?.map { peak ->
            StressPeakProto(
                timestampMillis = (peak["timestampMillis"] as? Long) ?: 0L,
                level = (peak["level"] as? Long)?.toInt() ?: 0,
                trigger = (peak["trigger"] as? String) ?: "",
                resolved = (peak["resolved"] as? Boolean) ?: false,
            )
        } ?: emptyList()

        return PsychologicalProfileProto(
            id = doc.id,
            ownerId = doc.getString("ownerId") ?: "",
            weekNumber = doc.getLong("weekNumber")?.toInt() ?: 0,
            year = doc.getLong("year")?.toInt() ?: 2025,
            weekKey = doc.getString("weekKey") ?: "",
            sourceTimezone = doc.getString("sourceTimezone") ?: "Europe/Rome",
            computedAtMillis = doc.getLong("computedAtMillis") ?: 0L,
            stressBaseline = doc.getDouble("stressBaseline")?.toFloat() ?: 5f,
            stressVolatility = doc.getDouble("stressVolatility")?.toFloat() ?: 0f,
            stressPeaks = peaks,
            moodBaseline = doc.getDouble("moodBaseline")?.toFloat() ?: 5f,
            moodVolatility = doc.getDouble("moodVolatility")?.toFloat() ?: 0f,
            moodTrend = doc.getString("moodTrend") ?: "STABLE",
            resilienceIndex = doc.getDouble("resilienceIndex")?.toFloat() ?: 0.5f,
            recoverySpeed = doc.getDouble("recoverySpeed")?.toFloat() ?: 0f,
            diaryCount = doc.getLong("diaryCount")?.toInt() ?: 0,
            snapshotCount = doc.getLong("snapshotCount")?.toInt() ?: 0,
            confidence = doc.getDouble("confidence")?.toFloat() ?: 0f,
        )
    }
}

package com.lifo.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ProfileSettingsProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val createdAtMillis: Long = 0L,
    @ProtoNumber(4) val updatedAtMillis: Long = 0L,
    @ProtoNumber(5) val isOnboardingCompleted: Boolean = false,
    @ProtoNumber(6) val displayName: String = "",
    @ProtoNumber(7) val fullName: String = "",
    @ProtoNumber(8) val dateOfBirth: String = "",
    @ProtoNumber(9) val gender: String = "PREFER_NOT_TO_SAY",
    @ProtoNumber(10) val height: Int = 0,
    @ProtoNumber(11) val weight: Float = 0f,
    @ProtoNumber(12) val location: String = "",
    @ProtoNumber(13) val primaryConcerns: List<String> = emptyList(),
    @ProtoNumber(14) val mentalHealthHistory: String = "NO_DIAGNOSIS",
    @ProtoNumber(15) val currentTherapy: Boolean = false,
    @ProtoNumber(16) val medication: Boolean = false,
    @ProtoNumber(17) val occupation: String = "",
    @ProtoNumber(18) val sleepHoursAvg: Float = 7f,
    @ProtoNumber(19) val exerciseFrequency: String = "MODERATE",
    @ProtoNumber(20) val socialSupport: String = "MODERATE",
    @ProtoNumber(21) val primaryGoals: List<String> = emptyList(),
    @ProtoNumber(22) val preferredCopingStrategies: List<String> = emptyList(),
    @ProtoNumber(23) val aiTone: String = "FRIENDLY",
    @ProtoNumber(24) val reminderFrequency: String = "DAILY",
    @ProtoNumber(25) val topicsToAvoid: List<String> = emptyList(),
    @ProtoNumber(26) val shareDataForResearch: Boolean = false,
    @ProtoNumber(27) val enableAdvancedInsights: Boolean = true,
)

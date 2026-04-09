package com.lifo.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class HabitProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val name: String = "",
    @ProtoNumber(4) val description: String = "",
    @ProtoNumber(5) val category: String = "CRESCITA",
    @ProtoNumber(6) val anchorHabit: String = "",
    @ProtoNumber(7) val minimumAction: String = "",
    @ProtoNumber(8) val targetFrequency: String = "DAILY",
    @ProtoNumber(9) val reminderTime: String = "",
    @ProtoNumber(10) val createdAtMillis: Long = 0L,
    @ProtoNumber(11) val isActive: Boolean = true,
)

@Serializable
data class HabitCompletionProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val habitId: String = "",
    @ProtoNumber(3) val ownerId: String = "",
    @ProtoNumber(4) val completedAtMillis: Long = 0L,
    @ProtoNumber(5) val dayKey: String = "",
    @ProtoNumber(6) val note: String = "",
)

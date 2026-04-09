package com.lifo.write

import com.lifo.util.model.ConnectionEntry
import com.lifo.util.model.ConnectionType
import com.lifo.util.model.RelationshipReflection
import com.lifo.util.mvi.MviContract

object ConnectionContract {

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
        data class SelectTab(val index: Int) : Intent
        data class UpdatePerson(val name: String) : Intent
        data class UpdateDescription(val text: String) : Intent
        data class SelectType(val type: ConnectionType) : Intent
        data class ToggleExpressed(val value: Boolean) : Intent
        data object SaveEntry : Intent
        data class DeleteEntry(val id: String) : Intent
        // Reflection
        data object ShowReflection : Intent
        data object DismissReflection : Intent
        data class AddNurturing(val name: String) : Intent
        data class RemoveNurturing(val index: Int) : Intent
        data class AddDraining(val name: String) : Intent
        data class RemoveDraining(val index: Int) : Intent
        data class UpdateIntention(val text: String) : Intent
        data object SaveReflection : Intent
    }

    data class State(
        val entries: List<ConnectionEntry> = emptyList(),
        val selectedTab: Int = 0,
        val personName: String = "",
        val description: String = "",
        val selectedType: ConnectionType = ConnectionType.GRATITUDE,
        val expressed: Boolean = false,
        val showReflection: Boolean = false,
        val reflection: RelationshipReflection? = null,
        val nurturingInput: List<String> = emptyList(),
        val drainingInput: List<String> = emptyList(),
        val intentionInput: String = "",
        val weeklyGratitudeCount: Int = 0,
        val weeklyServiceCount: Int = 0,
        val weeklyQualityTimeCount: Int = 0,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object EntrySaved : Effect
        data object ReflectionSaved : Effect
    }
}

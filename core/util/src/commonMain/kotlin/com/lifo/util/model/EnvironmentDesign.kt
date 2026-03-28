package com.lifo.util.model

/**
 * User's environment design checklist items and routines.
 */
data class EnvironmentChecklist(
    val id: String = "",
    val ownerId: String = "",
    val items: List<ChecklistItem> = defaultChecklist(),
    val morningRoutine: List<RoutineStep> = defaultMorningRoutine(),
    val eveningRoutine: List<RoutineStep> = defaultEveningRoutine(),
    val detoxTimerMinutes: Int = 60,
)

data class ChecklistItem(
    val id: String = "",
    val text: String = "",
    val isCompleted: Boolean = false,
    val category: ChecklistCategory = ChecklistCategory.GENERALE,
)

enum class ChecklistCategory(val displayName: String) {
    MATTINA("Mattina"),
    LAVORO("Lavoro"),
    SERA("Sera"),
    GENERALE("Generale"),
}

data class RoutineStep(
    val id: String = "",
    val text: String = "",
    val durationMinutes: Int = 5,
    val isCompleted: Boolean = false,
)

fun defaultChecklist(): List<ChecklistItem> = listOf(
    ChecklistItem(id = "c1", text = "Telefono lontano al mattino (primi 60 min)", category = ChecklistCategory.MATTINA),
    ChecklistItem(id = "c2", text = "Libro sul cuscino", category = ChecklistCategory.SERA),
    ChecklistItem(id = "c3", text = "Scrivania pulita prima di lavorare", category = ChecklistCategory.LAVORO),
    ChecklistItem(id = "c4", text = "Notifiche disattivate durante il focus", category = ChecklistCategory.LAVORO),
    ChecklistItem(id = "c5", text = "Niente schermi nell'ultima ora prima di dormire", category = ChecklistCategory.SERA),
    ChecklistItem(id = "c6", text = "Bicchiere d'acqua appena sveglio", category = ChecklistCategory.MATTINA),
)

fun defaultMorningRoutine(): List<RoutineStep> = listOf(
    RoutineStep(id = "m1", text = "Bicchiere d'acqua", durationMinutes = 1),
    RoutineStep(id = "m2", text = "Stretching leggero", durationMinutes = 5),
    RoutineStep(id = "m3", text = "Meditazione / Respiro", durationMinutes = 5),
    RoutineStep(id = "m4", text = "Scrivi 3 cose belle (gratitudine)", durationMinutes = 3),
    RoutineStep(id = "m5", text = "Colazione consapevole", durationMinutes = 15),
)

fun defaultEveningRoutine(): List<RoutineStep> = listOf(
    RoutineStep(id = "e1", text = "Spegni schermi", durationMinutes = 1),
    RoutineStep(id = "e2", text = "Prepara domani (vestiti, borsa)", durationMinutes = 5),
    RoutineStep(id = "e3", text = "Journaling / Brain dump serale", durationMinutes = 5),
    RoutineStep(id = "e4", text = "Lettura", durationMinutes = 15),
    RoutineStep(id = "e5", text = "Respiro 4-7-8 e luci spente", durationMinutes = 3),
)

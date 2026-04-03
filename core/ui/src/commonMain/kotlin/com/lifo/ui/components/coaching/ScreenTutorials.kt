package com.lifo.ui.components.coaching

/**
 * Centralised tutorial step definitions for each screen.
 * All text is in Italian, tone warm and welcoming.
 */
object ScreenTutorials {

    /** Unique key used to check/persist "already seen" state. */
    const val KEY_HOME      = "tutorial_home"
    const val KEY_PERCORSO  = "tutorial_percorso"
    const val KEY_CHAT      = "tutorial_chat"
    const val KEY_WRITE     = "tutorial_write"

    val home: List<CoachMarkStep> = listOf(
        CoachMarkStep(
            title       = "Bentornato, amico",
            description = "Questa è la tua home. Ogni giorno trovi un saluto personalizzato e un riepilogo di come stai andando.",
            buttonText  = "Avanti",
            targetKey   = CoachMarkKeys.HOME_GREETING,
        ),
        CoachMarkStep(
            title       = "Le tue attività rapide",
            description = "Da qui puoi accedere con un tocco a diario, meditazione, check-in energia e molto altro. Scegli cosa ti serve oggi.",
            buttonText  = "Avanti",
            targetKey   = CoachMarkKeys.HOME_QUICK_ACTIONS,
        ),
        CoachMarkStep(
            title       = "Come stai oggi?",
            description = "Il tuo umore del giorno è mostrato qui. Toccalo per aggiornarlo o per vedere come è cambiato nel tempo.",
            buttonText  = "Avanti",
            targetKey   = CoachMarkKeys.HOME_MOOD,
        ),
        CoachMarkStep(
            title       = "Il tuo compagno AI",
            description = "Il tuo avatar è sempre pronto ad ascoltarti. Toccalo per iniziare una conversazione vocale o testuale, in qualsiasi momento.",
            buttonText  = "Capito!",
            targetKey   = CoachMarkKeys.HOME_AVATAR,
        ),
    )

    val percorso: List<CoachMarkStep> = listOf(
        CoachMarkStep(
            title       = "I tuoi pattern mentali",
            description = "L'AI analizza i tuoi diari e individua schemi ricorrenti nel tuo modo di pensare. Riconoscerli è il primo passo per cambiarli.",
            buttonText  = "Avanti",
            targetKey   = CoachMarkKeys.PERCORSO_PATTERNS,
        ),
        CoachMarkStep(
            title       = "Il tuo trend di benessere",
            description = "Qui vedi come il tuo umore e la tua energia sono cambiati negli ultimi giorni. Le piccole variazioni quotidiane raccontano una storia.",
            buttonText  = "Avanti",
            targetKey   = CoachMarkKeys.PERCORSO_TREND,
        ),
        CoachMarkStep(
            title       = "Suggerimenti personalizzati",
            description = "Basandosi su ciò che scrivi, Calmify ti propone azioni concrete e domande di riflessione pensate per te.",
            buttonText  = "Capito!",
            targetKey   = CoachMarkKeys.PERCORSO_SUGGESTIONS,
        ),
    )

    val chat: List<CoachMarkStep> = listOf(
        CoachMarkStep(
            title       = "Parla con il tuo AI",
            description = "Puoi scrivere quello che hai in testa oppure semplicemente sfogare. L'AI ti risponde con empatia, senza giudicarti.",
            buttonText  = "Avanti",
            targetKey   = CoachMarkKeys.CHAT_INTRO,
        ),
        CoachMarkStep(
            title       = "Modalità voce",
            description = "Tieni premuto il microfono per parlare. La conversazione diventa a piena voce: più naturale, più umana.",
            buttonText  = "Capito!",
            targetKey   = CoachMarkKeys.CHAT_VOICE,
        ),
    )

    val write: List<CoachMarkStep> = listOf(
        CoachMarkStep(
            title       = "Scrivi liberamente",
            description = "Non esistono regole. Puoi scrivere tre parole o tre pagine. Calmify analizza quello che vuoi condividere, non quanto.",
            buttonText  = "Avanti",
            targetKey   = CoachMarkKeys.WRITE_EDITOR,
        ),
        CoachMarkStep(
            title       = "Tag e umore",
            description = "Aggiungere un umore o un tag aiuta l'AI a capire meglio il contesto e a offrirti insight più precisi nel tempo.",
            buttonText  = "Capito!",
            targetKey   = CoachMarkKeys.WRITE_MOOD,
        ),
    )
}

package com.lifo.write

import com.lifo.ui.i18n.Strings
import com.lifo.util.model.BodySensation
import com.lifo.util.model.Mood
import com.lifo.util.model.Trigger
import org.jetbrains.compose.resources.StringResource

/**
 * Local text analyzer for Smart Capture.
 * Infers mood, emotion intensity, stress, energy, trigger,
 * and body sensation from diary text using keyword matching.
 * Fast, offline, zero API cost.
 */
object TextAnalyzer {

    data class InferredMetrics(
        val mood: Mood,
        val emotionIntensity: Int,
        val stressLevel: Int,
        val energyLevel: Int,
        val calmAnxietyLevel: Int,
        val trigger: Trigger,
        val bodySensation: BodySensation
    )

    fun analyze(title: String, description: String): InferredMetrics {
        val text = "$title $description".lowercase()

        val mood = inferMood(text)
        val emotionIntensity = inferEmotionIntensity(text, mood)
        val stressLevel = inferStressLevel(text)
        val energyLevel = inferEnergyLevel(text)
        val calmAnxietyLevel = inferCalmAnxiety(text)
        val trigger = inferTrigger(text)
        val bodySensation = inferBodySensation(text)

        return InferredMetrics(
            mood = mood,
            emotionIntensity = emotionIntensity,
            stressLevel = stressLevel,
            energyLevel = energyLevel,
            calmAnxietyLevel = calmAnxietyLevel,
            trigger = trigger,
            bodySensation = bodySensation
        )
    }

    private fun inferMood(text: String): Mood {
        val scores = mutableMapOf<Mood, Int>()

        val moodKeywords = mapOf(
            Mood.Happy to listOf("felice", "contento", "gioia", "fantastico", "bellissim", "meraviglios", "ottimo", "sorriso", "ridere", "soddisfatt"),
            Mood.Romantic to listOf("amore", "innamorat", "romantico", "passione", "bacio", "cuore", "partner", "dolce"),
            Mood.Calm to listOf("calmo", "sereno", "tranquillo", "rilassat", "pace", "equilibrio", "armonia"),
            Mood.Angry to listOf("arrabbiato", "furioso", "rabbia", "incazzat", "irritat", "nervoso", "frustrat"),
            Mood.Depressed to listOf("triste", "malinconia", "piangere", "lacrime", "vuoto", "depresso", "abbattut", "disperato"),
            Mood.Disappointed to listOf("deluso", "delusione", "sconforto", "scoraggiat", "fallimento", "speranza persa"),
            Mood.Lonely to listOf("solo", "solitudine", "isolat", "abbandonat", "nessuno", "lontano"),
            Mood.Tense to listOf("teso", "ansioso", "ansia", "preoccupat", "agitat", "stress", "panico", "paura"),
            Mood.Surprised to listOf("sorpreso", "sorpresa", "incredibile", "inaspettat", "shock", "wow"),
            Mood.Mysterious to listOf("strano", "confuso", "misterioso", "incerto", "dubbio", "perpless"),
            Mood.Humorous to listOf("divertente", "ridere", "scherzo", "buffo", "comico", "ironico"),
            Mood.Suspicious to listOf("sospetto", "diffident", "non mi fido", "strano", "inquietant"),
            Mood.Bored to listOf("annoiato", "noia", "monotono", "piatto", "apatico"),
            Mood.Shameful to listOf("vergogna", "imbarazzo", "colpa", "pentit"),
            Mood.Awful to listOf("terribile", "orribile", "peggiore", "disastro", "schifoso", "male")
        )

        moodKeywords.forEach { (mood, keywords) ->
            val count = keywords.count { keyword -> text.contains(keyword) }
            if (count > 0) scores[mood] = count
        }

        return scores.maxByOrNull { it.value }?.key ?: Mood.Neutral
    }

    private fun inferEmotionIntensity(text: String, mood: Mood): Int {
        val intensifiers = listOf("molto", "tanto", "troppo", "estremamente", "incredibilmente", "davvero", "assolutamente")
        val diminishers = listOf("poco", "appena", "leggermente", "un po'", "quasi")

        val intensifierCount = intensifiers.count { text.contains(it) }
        val diminisherCount = diminishers.count { text.contains(it) }
        val exclamationCount = text.count { it == '!' }.coerceAtMost(3)

        var base = when (mood) {
            Mood.Happy, Mood.Angry, Mood.Awful -> 7
            Mood.Depressed, Mood.Tense, Mood.Romantic -> 6
            Mood.Surprised, Mood.Shameful, Mood.Disappointed -> 5
            Mood.Calm, Mood.Humorous -> 4
            else -> 5
        }

        base += intensifierCount
        base -= diminisherCount
        base += exclamationCount

        return base.coerceIn(1, 10)
    }

    private fun inferStressLevel(text: String): Int {
        val highStress = listOf("stress", "ansia", "preoccup", "panico", "scadenz", "urgent", "pressione", "sopraffat", "non ce la faccio", "troppo")
        val lowStress = listOf("relax", "tranquill", "sereno", "pace", "calmo", "riposat")

        val high = highStress.count { text.contains(it) }
        val low = lowStress.count { text.contains(it) }

        return (5 + high * 2 - low * 2).coerceIn(1, 10)
    }

    private fun inferEnergyLevel(text: String): Int {
        val highEnergy = listOf("energia", "attivo", "carico", "motivat", "produttiv", "corsa", "sport", "allenament", "sveglio")
        val lowEnergy = listOf("stanc", "esaust", "sfinit", "dormire", "letto", "pigr", "spossato", "affaticat")

        val high = highEnergy.count { text.contains(it) }
        val low = lowEnergy.count { text.contains(it) }

        return (5 + high * 2 - low * 2).coerceIn(1, 10)
    }

    private fun inferCalmAnxiety(text: String): Int {
        val calm = listOf("calmo", "sereno", "tranquill", "rilassat", "pace", "meditaz")
        val anxious = listOf("ansia", "ansios", "nervos", "agitat", "irrequiet", "paura", "panico", "preoccup")

        val calmCount = calm.count { text.contains(it) }
        val anxiousCount = anxious.count { text.contains(it) }

        // Higher = calmer, lower = more anxious
        return (5 + calmCount * 2 - anxiousCount * 2).coerceIn(1, 10)
    }

    private fun inferTrigger(text: String): Trigger {
        val triggerKeywords = mapOf(
            Trigger.WORK to listOf("lavoro", "ufficio", "capo", "collega", "riunione", "progetto", "deadline", "carriera"),
            Trigger.FAMILY to listOf("famiglia", "genitor", "madre", "padre", "figlio", "figlia", "fratello", "sorella", "casa"),
            Trigger.HEALTH to listOf("salute", "malattia", "dolore", "medico", "ospedal", "sintom"),
            Trigger.FINANCE to listOf("soldi", "denaro", "bolletta", "debito", "spesa", "conto", "stipendio"),
            Trigger.SOCIAL to listOf("amici", "social", "persona", "gente", "uscire", "festa", "evento"),
            Trigger.SELF to listOf("me stesso", "io", "autostima", "identita'", "crescita", "obiettivo")
        )

        val scores = triggerKeywords.mapValues { (_, keywords) ->
            keywords.count { text.contains(it) }
        }

        return scores.maxByOrNull { it.value }?.let { if (it.value > 0) it.key else Trigger.NONE } ?: Trigger.NONE
    }

    private fun inferBodySensation(text: String): BodySensation {
        val sensationKeywords = mapOf(
            BodySensation.TENSION to listOf("teso", "tensione", "rigido", "collo", "spalle", "muscol", "stretto"),
            BodySensation.LIGHTNESS to listOf("leggero", "leggerezza", "libero", "fluttuare", "sollevato"),
            BodySensation.FATIGUE to listOf("stanco", "affaticat", "sfinito", "esausto", "spossato"),
            BodySensation.HEAVINESS to listOf("pesante", "pesantezza", "appesantit", "schiacciato"),
            BodySensation.AGITATION to listOf("agitato", "irrequieto", "tremante", "cuore che batte"),
            BodySensation.RELAXATION to listOf("rilassato", "disteso", "comodo", "a mio agio")
        )

        val scores = sensationKeywords.mapValues { (_, keywords) ->
            keywords.count { text.contains(it) }
        }

        return scores.maxByOrNull { it.value }?.let { if (it.value > 0) it.key else BodySensation.NONE } ?: BodySensation.NONE
    }

    /**
     * Returns the localized StringResource for the inferred mood.
     * Caller resolves via `stringResource(...)` in a @Composable scope.
     */
    fun moodLabelRes(mood: Mood): StringResource = when (mood) {
        Mood.Neutral -> Strings.Mood.neutral
        Mood.Happy -> Strings.Mood.happy
        Mood.Angry -> Strings.Mood.angry
        Mood.Depressed -> Strings.Mood.depressed
        Mood.Disappointed -> Strings.Mood.disappointed
        Mood.Romantic -> Strings.Mood.romantic
        Mood.Calm -> Strings.Mood.calm
        Mood.Tense -> Strings.Mood.tense
        Mood.Lonely -> Strings.Mood.lonely
        Mood.Mysterious -> Strings.Mood.confused
        Mood.Shameful -> Strings.Mood.guilty
        Mood.Awful -> Strings.Mood.awful
        Mood.Surprised -> Strings.Mood.surprised
        Mood.Bored -> Strings.Mood.bored
        Mood.Humorous -> Strings.Mood.amused
        Mood.Suspicious -> Strings.Mood.suspicious
    }
}

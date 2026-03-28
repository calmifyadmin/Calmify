package com.lifo.util.analysis

import com.lifo.util.model.ThoughtType

/**
 * Detects recurring thought themes in text using keyword pattern matching.
 * Returns detected themes with their type (limiting/empowering/neutral).
 */
object ThemeDetector {

    data class DetectedTheme(
        val theme: String,
        val type: ThoughtType,
        val matchCount: Int,
    )

    private val limitingPatterns = mapOf(
        "non sono abbastanza" to "Non sono abbastanza",
        "non ce la faccio" to "Non ce la faccio",
        "non sono capace" to "Non sono capace",
        "non valgo" to "Non valgo",
        "non merito" to "Non merito",
        "sono un fallimento" to "Sono un fallimento",
        "non cambiera' mai" to "Non cambiera' mai",
        "e' colpa mia" to "E' colpa mia",
        "non posso" to "Non posso",
        "troppo tardi" to "Troppo tardi",
        "non ho tempo" to "Non ho tempo",
        "nessuno mi capisce" to "Nessuno mi capisce",
        "sono solo" to "Sono solo/a",
        "sono sbagliato" to "Sono sbagliato/a",
        "non ho scelta" to "Non ho scelta",
        "devo essere perfetto" to "Devo essere perfetto/a",
        "gli altri sono meglio" to "Gli altri sono meglio",
    )

    private val empoweringPatterns = mapOf(
        "ce la faccio" to "Ce la faccio",
        "sono grato" to "Sono grato/a",
        "mi sento bene" to "Mi sento bene",
        "ho imparato" to "Ho imparato",
        "posso farcela" to "Posso farcela",
        "sono cresciuto" to "Sono cresciuto/a",
        "mi accetto" to "Mi accetto",
        "sono abbastanza" to "Sono abbastanza",
        "mi merito" to "Mi merito",
        "sono forte" to "Sono forte",
        "ho fatto progressi" to "Ho fatto progressi",
    )

    fun detectThemes(text: String): List<DetectedTheme> {
        val lower = text.lowercase()
        val results = mutableListOf<DetectedTheme>()

        for ((pattern, theme) in limitingPatterns) {
            val count = countOccurrences(lower, pattern)
            if (count > 0) {
                results.add(DetectedTheme(theme, ThoughtType.LIMITING, count))
            }
        }

        for ((pattern, theme) in empoweringPatterns) {
            val count = countOccurrences(lower, pattern)
            if (count > 0) {
                results.add(DetectedTheme(theme, ThoughtType.EMPOWERING, count))
            }
        }

        return results.sortedByDescending { it.matchCount }
    }

    fun containsThemes(text: String): Boolean {
        val lower = text.lowercase()
        return limitingPatterns.keys.any { lower.contains(it) } ||
                empoweringPatterns.keys.any { lower.contains(it) }
    }

    private fun countOccurrences(text: String, pattern: String): Int {
        var count = 0
        var index = text.indexOf(pattern)
        while (index >= 0) {
            count++
            index = text.indexOf(pattern, index + pattern.length)
        }
        return count
    }
}

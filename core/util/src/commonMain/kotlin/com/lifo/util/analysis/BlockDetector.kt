package com.lifo.util.analysis

import com.lifo.util.model.BlockType

/**
 * Detects mental blocks in user text using keyword pattern matching.
 * Returns the most likely BlockType based on keyword frequency.
 */
object BlockDetector {

    private val fearPatterns = listOf(
        "paura", "fallimento", "fallire", "sbagliare", "perfetto", "perfezionismo",
        "non sono capace", "non ce la faccio", "non sono abbastanza", "giudicato",
        "vergogna", "ansia da prestazione", "timore", "rischio",
    )

    private val overloadPatterns = listOf(
        "troppo", "sovraccarico", "non ho tempo", "mille cose", "sopraffatto",
        "stress", "esausto", "stanco", "non riesco a pensare", "confuso",
        "caos", "casino", "overwhelm", "troppe cose",
    )

    private val limitingBeliefPatterns = listOf(
        "non sono capace", "non merito", "non posso", "impossibile",
        "non ce la faro' mai", "sono stupido", "non valgo", "inutile",
        "non cambiera' mai", "sempre cosi'", "e' colpa mia", "non sono fatto per",
    )

    private val creativeBlockPatterns = listOf(
        "bloccato", "blocco", "non mi viene", "pagina bianca",
        "non ho idee", "non so cosa", "ispirazione", "arenato",
        "stallo", "fermo", "impantanato",
    )

    /**
     * Returns true if the text contains block-related keywords.
     */
    fun containsBlockSignals(text: String): Boolean {
        val lower = text.lowercase()
        return (fearPatterns + overloadPatterns + limitingBeliefPatterns + creativeBlockPatterns)
            .any { lower.contains(it) }
    }

    /**
     * Detects the most likely block type from the text.
     * Returns null if no block patterns are detected.
     */
    fun detectBlockType(text: String): BlockType? {
        val lower = text.lowercase()

        val scores = mapOf(
            BlockType.FEAR_OF_FAILURE to fearPatterns.count { lower.contains(it) },
            BlockType.OVERLOAD to overloadPatterns.count { lower.contains(it) },
            BlockType.LIMITING_BELIEF to limitingBeliefPatterns.count { lower.contains(it) },
            BlockType.CREATIVE_BLOCK to creativeBlockPatterns.count { lower.contains(it) },
        )

        val maxEntry = scores.maxByOrNull { it.value } ?: return null
        return if (maxEntry.value > 0) maxEntry.key else null
    }

    /**
     * Returns all detected block types with their confidence scores.
     */
    fun detectAllBlockTypes(text: String): List<Pair<BlockType, Int>> {
        val lower = text.lowercase()
        return listOf(
            BlockType.FEAR_OF_FAILURE to fearPatterns.count { lower.contains(it) },
            BlockType.OVERLOAD to overloadPatterns.count { lower.contains(it) },
            BlockType.LIMITING_BELIEF to limitingBeliefPatterns.count { lower.contains(it) },
            BlockType.CREATIVE_BLOCK to creativeBlockPatterns.count { lower.contains(it) },
        ).filter { it.second > 0 }.sortedByDescending { it.second }
    }
}

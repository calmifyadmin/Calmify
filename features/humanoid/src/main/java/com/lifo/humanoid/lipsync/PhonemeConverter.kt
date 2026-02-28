package com.lifo.humanoid.lipsync

import android.content.Context

/**
 * Converts English text to a sequence of phonemes.
 * Based on the CMU Pronouncing Dictionary for known words,
 * with rule-based fallback for unknown words.
 *
 * This is a text-based lip-sync system that generates phonemes from text,
 * suitable for synchronizing lip movements with TTS output.
 */
class PhonemeConverter(context: Context) {

    // Dictionary: word -> list of ARPAbet phonemes
    private val dictionary = mutableMapOf<String, List<String>>()

    init {
        loadEssentialDictionary()
    }

    /**
     * Convert a string of text to a sequence of phonemes with timing
     *
     * @param text The text to convert
     * @param totalDurationMs Total duration for the speech in milliseconds
     * @return List of PhonemeTiming representing the phoneme sequence
     */
    fun textToPhonemes(text: String, totalDurationMs: Long): List<PhonemeTiming> {
        val words = text.lowercase()
            .replace(Regex("[^a-z\\s']"), " ") // Remove punctuation except apostrophe
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (words.isEmpty()) return emptyList()

        val allPhonemes = mutableListOf<String>()

        words.forEach { word ->
            val phonemes = wordToPhonemes(word)
            allPhonemes.addAll(phonemes)
            allPhonemes.add("SIL") // Short pause between words
        }

        // Remove trailing silence
        if (allPhonemes.lastOrNull() == "SIL") {
            allPhonemes.removeAt(allPhonemes.lastIndex)
        }

        if (allPhonemes.isEmpty()) return emptyList()

        // Calculate timing
        return calculateTiming(allPhonemes, totalDurationMs)
    }

    /**
     * Convert a single word to phonemes
     */
    fun wordToPhonemes(word: String): List<String> {
        val normalizedWord = word.lowercase().trim()

        // Check dictionary first
        dictionary[normalizedWord]?.let { return it }

        // Fallback to rule-based conversion
        return applyPhoneticRules(normalizedWord)
    }

    /**
     * Calculate timing for each phoneme
     */
    private fun calculateTiming(phonemes: List<String>, totalDurationMs: Long): List<PhonemeTiming> {
        if (phonemes.isEmpty()) return emptyList()

        // Calculate relative durations for each phoneme
        val weights = phonemes.map { getPhonemeWeight(it) }
        val totalWeight = weights.sum()

        var currentTime = 0L
        return phonemes.mapIndexed { index, phoneme ->
            val weight = weights[index]
            val duration = ((weight / totalWeight) * totalDurationMs).toLong()
                .coerceAtLeast(30L) // Minimum 30ms per phoneme

            val timing = PhonemeTiming(
                phoneme = phoneme,
                startTimeMs = currentTime,
                durationMs = duration
            )
            currentTime += duration
            timing
        }
    }

    /**
     * Get relative weight/duration for a phoneme type
     */
    private fun getPhonemeWeight(phoneme: String): Float {
        val cleanPhoneme = phoneme.replace(Regex("[0-2]"), "") // Remove stress markers
        return when {
            phoneme == "SIL" -> 0.3f // Short silence
            cleanPhoneme in listOf("AE", "AH", "AO", "EH", "IH", "UH") -> 1.0f // Short vowels
            cleanPhoneme in listOf("AA", "AW", "AY", "EY", "IY", "OW", "OY", "UW", "ER") -> 1.3f // Long vowels/diphthongs
            cleanPhoneme in listOf("P", "B", "T", "D", "K", "G") -> 0.6f // Plosives
            cleanPhoneme in listOf("M", "N", "NG") -> 0.9f // Nasals
            cleanPhoneme in listOf("F", "V", "TH", "DH", "S", "Z", "SH", "ZH", "HH") -> 0.8f // Fricatives
            cleanPhoneme in listOf("CH", "JH") -> 0.7f // Affricates
            cleanPhoneme in listOf("L", "R", "W", "Y") -> 0.8f // Approximants
            else -> 0.8f
        }
    }

    /**
     * Apply rule-based phonetic conversion for unknown words
     */
    private fun applyPhoneticRules(word: String): List<String> {
        val phonemes = mutableListOf<String>()
        var i = 0

        while (i < word.length) {
            val remaining = word.substring(i)
            val (phoneme, consumed) = matchPhonemePattern(remaining, word, i)

            if (phoneme.isNotEmpty()) {
                if (phoneme.contains(" ")) {
                    phonemes.addAll(phoneme.split(" "))
                } else {
                    phonemes.add(phoneme)
                }
            }
            i += consumed.coerceAtLeast(1)
        }

        return phonemes
    }

    /**
     * Pattern matching for grapheme to phoneme conversion
     */
    private fun matchPhonemePattern(remaining: String, fullWord: String, position: Int): Pair<String, Int> {
        val isWordStart = position == 0
        val isWordEnd = position == fullWord.length - 1

        // Multi-character patterns (check these first)
        val multiPatterns = mapOf(
            "tion" to ("SH AH N" to 4),
            "sion" to ("ZH AH N" to 4),
            "ough" to ("AO" to 4),
            "ight" to ("AY T" to 4),
            "ould" to ("UH D" to 4),
            "ture" to ("CH ER" to 4),
            "ious" to ("IY AH S" to 4),
            "tch" to ("CH" to 3),
            "dge" to ("JH" to 3),
            "sch" to ("SH" to 3),
            "thr" to ("TH R" to 3),
            "str" to ("S T R" to 3),
            "ing" to ("IH NG" to 3),
            "ght" to ("T" to 3),
            "nce" to ("N S" to 3),
            "que" to ("K" to 3),
            "th" to ("TH" to 2),
            "sh" to ("SH" to 2),
            "ch" to ("CH" to 2),
            "wh" to ("W" to 2),
            "ph" to ("F" to 2),
            "ng" to ("NG" to 2),
            "ck" to ("K" to 2),
            "gh" to ("" to 2), // Usually silent
            "kn" to ("N" to 2),
            "wr" to ("R" to 2),
            "ee" to ("IY" to 2),
            "ea" to ("IY" to 2),
            "oo" to ("UW" to 2),
            "ou" to ("AW" to 2),
            "ow" to ("OW" to 2),
            "ai" to ("EY" to 2),
            "ay" to ("EY" to 2),
            "oi" to ("OY" to 2),
            "oy" to ("OY" to 2),
            "au" to ("AO" to 2),
            "aw" to ("AO" to 2),
            "ie" to ("IY" to 2),
            "ei" to ("EY" to 2),
            "ue" to ("UW" to 2),
            "ew" to ("UW" to 2)
        )

        for ((pattern, result) in multiPatterns) {
            if (remaining.startsWith(pattern)) {
                return result
            }
        }

        // Single character patterns
        val char = remaining.first()
        val phoneme = when (char) {
            'a' -> if (isWordEnd || (remaining.length > 1 && remaining[1] == 'e')) "EY" else "AE"
            'e' -> if (isWordEnd) "" else "EH"
            'i' -> if (isWordEnd || (remaining.length > 1 && remaining[1] == 'e')) "AY" else "IH"
            'o' -> if (isWordEnd || (remaining.length > 1 && remaining[1] == 'e')) "OW" else "AA"
            'u' -> "AH"
            'y' -> if (isWordStart) "Y" else "IY"
            'b' -> "B"
            'c' -> if (remaining.length > 1 && remaining[1] in "eiy") "S" else "K"
            'd' -> "D"
            'f' -> "F"
            'g' -> if (remaining.length > 1 && remaining[1] in "eiy") "JH" else "G"
            'h' -> "HH"
            'j' -> "JH"
            'k' -> "K"
            'l' -> "L"
            'm' -> "M"
            'n' -> "N"
            'p' -> "P"
            'q' -> "K"
            'r' -> "R"
            's' -> "S"
            't' -> "T"
            'v' -> "V"
            'w' -> "W"
            'x' -> "K S"
            'z' -> "Z"
            '\'' -> ""
            else -> ""
        }

        return phoneme to 1
    }

    /**
     * Load essential dictionary with common words
     */
    private fun loadEssentialDictionary() {
        val essentialWords = mapOf(
            // Greetings
            "hello" to listOf("HH", "AH", "L", "OW"),
            "hi" to listOf("HH", "AY"),
            "hey" to listOf("HH", "EY"),
            "goodbye" to listOf("G", "UH", "D", "B", "AY"),
            "bye" to listOf("B", "AY"),

            // Common words
            "the" to listOf("DH", "AH"),
            "a" to listOf("AH"),
            "an" to listOf("AE", "N"),
            "and" to listOf("AE", "N", "D"),
            "or" to listOf("AO", "R"),
            "but" to listOf("B", "AH", "T"),
            "is" to listOf("IH", "Z"),
            "are" to listOf("AA", "R"),
            "was" to listOf("W", "AA", "Z"),
            "were" to listOf("W", "ER"),
            "be" to listOf("B", "IY"),
            "been" to listOf("B", "IH", "N"),
            "being" to listOf("B", "IY", "IH", "NG"),
            "have" to listOf("HH", "AE", "V"),
            "has" to listOf("HH", "AE", "Z"),
            "had" to listOf("HH", "AE", "D"),
            "do" to listOf("D", "UW"),
            "does" to listOf("D", "AH", "Z"),
            "did" to listOf("D", "IH", "D"),
            "will" to listOf("W", "IH", "L"),
            "would" to listOf("W", "UH", "D"),
            "could" to listOf("K", "UH", "D"),
            "should" to listOf("SH", "UH", "D"),
            "can" to listOf("K", "AE", "N"),
            "may" to listOf("M", "EY"),
            "might" to listOf("M", "AY", "T"),
            "must" to listOf("M", "AH", "S", "T"),

            // Pronouns
            "i" to listOf("AY"),
            "you" to listOf("Y", "UW"),
            "he" to listOf("HH", "IY"),
            "she" to listOf("SH", "IY"),
            "it" to listOf("IH", "T"),
            "we" to listOf("W", "IY"),
            "they" to listOf("DH", "EY"),
            "me" to listOf("M", "IY"),
            "him" to listOf("HH", "IH", "M"),
            "her" to listOf("HH", "ER"),
            "us" to listOf("AH", "S"),
            "them" to listOf("DH", "EH", "M"),
            "my" to listOf("M", "AY"),
            "your" to listOf("Y", "AO", "R"),
            "his" to listOf("HH", "IH", "Z"),
            "its" to listOf("IH", "T", "S"),
            "our" to listOf("AW", "ER"),
            "their" to listOf("DH", "EH", "R"),

            // Question words
            "what" to listOf("W", "AH", "T"),
            "where" to listOf("W", "EH", "R"),
            "when" to listOf("W", "EH", "N"),
            "why" to listOf("W", "AY"),
            "who" to listOf("HH", "UW"),
            "how" to listOf("HH", "AW"),

            // Common verbs
            "go" to listOf("G", "OW"),
            "come" to listOf("K", "AH", "M"),
            "get" to listOf("G", "EH", "T"),
            "make" to listOf("M", "EY", "K"),
            "know" to listOf("N", "OW"),
            "think" to listOf("TH", "IH", "NG", "K"),
            "take" to listOf("T", "EY", "K"),
            "see" to listOf("S", "IY"),
            "want" to listOf("W", "AA", "N", "T"),
            "look" to listOf("L", "UH", "K"),
            "use" to listOf("Y", "UW", "Z"),
            "find" to listOf("F", "AY", "N", "D"),
            "give" to listOf("G", "IH", "V"),
            "tell" to listOf("T", "EH", "L"),
            "work" to listOf("W", "ER", "K"),
            "feel" to listOf("F", "IY", "L"),
            "try" to listOf("T", "R", "AY"),
            "leave" to listOf("L", "IY", "V"),
            "call" to listOf("K", "AO", "L"),
            "keep" to listOf("K", "IY", "P"),
            "let" to listOf("L", "EH", "T"),
            "begin" to listOf("B", "IH", "G", "IH", "N"),
            "seem" to listOf("S", "IY", "M"),
            "help" to listOf("HH", "EH", "L", "P"),
            "show" to listOf("SH", "OW"),
            "hear" to listOf("HH", "IY", "R"),
            "play" to listOf("P", "L", "EY"),
            "run" to listOf("R", "AH", "N"),
            "move" to listOf("M", "UW", "V"),
            "live" to listOf("L", "IH", "V"),
            "believe" to listOf("B", "IH", "L", "IY", "V"),
            "bring" to listOf("B", "R", "IH", "NG"),
            "happen" to listOf("HH", "AE", "P", "AH", "N"),
            "write" to listOf("R", "AY", "T"),
            "provide" to listOf("P", "R", "AH", "V", "AY", "D"),
            "sit" to listOf("S", "IH", "T"),
            "stand" to listOf("S", "T", "AE", "N", "D"),
            "lose" to listOf("L", "UW", "Z"),
            "pay" to listOf("P", "EY"),
            "meet" to listOf("M", "IY", "T"),
            "include" to listOf("IH", "N", "K", "L", "UW", "D"),
            "continue" to listOf("K", "AH", "N", "T", "IH", "N", "Y", "UW"),
            "set" to listOf("S", "EH", "T"),
            "learn" to listOf("L", "ER", "N"),
            "change" to listOf("CH", "EY", "N", "JH"),
            "lead" to listOf("L", "IY", "D"),
            "understand" to listOf("AH", "N", "D", "ER", "S", "T", "AE", "N", "D"),
            "watch" to listOf("W", "AA", "CH"),
            "follow" to listOf("F", "AA", "L", "OW"),
            "stop" to listOf("S", "T", "AA", "P"),
            "create" to listOf("K", "R", "IY", "EY", "T"),
            "speak" to listOf("S", "P", "IY", "K"),
            "read" to listOf("R", "IY", "D"),
            "spend" to listOf("S", "P", "EH", "N", "D"),
            "grow" to listOf("G", "R", "OW"),
            "open" to listOf("OW", "P", "AH", "N"),
            "walk" to listOf("W", "AO", "K"),
            "win" to listOf("W", "IH", "N"),
            "offer" to listOf("AO", "F", "ER"),
            "remember" to listOf("R", "IH", "M", "EH", "M", "B", "ER"),
            "love" to listOf("L", "AH", "V"),
            "consider" to listOf("K", "AH", "N", "S", "IH", "D", "ER"),
            "appear" to listOf("AH", "P", "IY", "R"),
            "buy" to listOf("B", "AY"),
            "wait" to listOf("W", "EY", "T"),
            "serve" to listOf("S", "ER", "V"),
            "die" to listOf("D", "AY"),
            "send" to listOf("S", "EH", "N", "D"),
            "expect" to listOf("IH", "K", "S", "P", "EH", "K", "T"),
            "build" to listOf("B", "IH", "L", "D"),
            "stay" to listOf("S", "T", "EY"),
            "fall" to listOf("F", "AO", "L"),
            "cut" to listOf("K", "AH", "T"),
            "reach" to listOf("R", "IY", "CH"),
            "kill" to listOf("K", "IH", "L"),
            "remain" to listOf("R", "IH", "M", "EY", "N"),

            // Common adjectives
            "good" to listOf("G", "UH", "D"),
            "new" to listOf("N", "UW"),
            "first" to listOf("F", "ER", "S", "T"),
            "last" to listOf("L", "AE", "S", "T"),
            "long" to listOf("L", "AO", "NG"),
            "great" to listOf("G", "R", "EY", "T"),
            "little" to listOf("L", "IH", "T", "AH", "L"),
            "own" to listOf("OW", "N"),
            "other" to listOf("AH", "DH", "ER"),
            "old" to listOf("OW", "L", "D"),
            "right" to listOf("R", "AY", "T"),
            "big" to listOf("B", "IH", "G"),
            "high" to listOf("HH", "AY"),
            "different" to listOf("D", "IH", "F", "ER", "AH", "N", "T"),
            "small" to listOf("S", "M", "AO", "L"),
            "large" to listOf("L", "AA", "R", "JH"),
            "next" to listOf("N", "EH", "K", "S", "T"),
            "early" to listOf("ER", "L", "IY"),
            "young" to listOf("Y", "AH", "NG"),
            "important" to listOf("IH", "M", "P", "AO", "R", "T", "AH", "N", "T"),
            "few" to listOf("F", "Y", "UW"),
            "public" to listOf("P", "AH", "B", "L", "IH", "K"),
            "bad" to listOf("B", "AE", "D"),
            "same" to listOf("S", "EY", "M"),
            "able" to listOf("EY", "B", "AH", "L"),

            // Common nouns
            "time" to listOf("T", "AY", "M"),
            "year" to listOf("Y", "IY", "R"),
            "people" to listOf("P", "IY", "P", "AH", "L"),
            "way" to listOf("W", "EY"),
            "day" to listOf("D", "EY"),
            "man" to listOf("M", "AE", "N"),
            "thing" to listOf("TH", "IH", "NG"),
            "woman" to listOf("W", "UH", "M", "AH", "N"),
            "life" to listOf("L", "AY", "F"),
            "child" to listOf("CH", "AY", "L", "D"),
            "world" to listOf("W", "ER", "L", "D"),
            "school" to listOf("S", "K", "UW", "L"),
            "state" to listOf("S", "T", "EY", "T"),
            "family" to listOf("F", "AE", "M", "AH", "L", "IY"),
            "student" to listOf("S", "T", "UW", "D", "AH", "N", "T"),
            "group" to listOf("G", "R", "UW", "P"),
            "country" to listOf("K", "AH", "N", "T", "R", "IY"),
            "problem" to listOf("P", "R", "AA", "B", "L", "AH", "M"),
            "hand" to listOf("HH", "AE", "N", "D"),
            "part" to listOf("P", "AA", "R", "T"),
            "place" to listOf("P", "L", "EY", "S"),
            "case" to listOf("K", "EY", "S"),
            "week" to listOf("W", "IY", "K"),
            "company" to listOf("K", "AH", "M", "P", "AH", "N", "IY"),
            "system" to listOf("S", "IH", "S", "T", "AH", "M"),
            "program" to listOf("P", "R", "OW", "G", "R", "AE", "M"),
            "question" to listOf("K", "W", "EH", "S", "CH", "AH", "N"),
            "work" to listOf("W", "ER", "K"),
            "government" to listOf("G", "AH", "V", "ER", "N", "M", "AH", "N", "T"),
            "number" to listOf("N", "AH", "M", "B", "ER"),
            "night" to listOf("N", "AY", "T"),
            "point" to listOf("P", "OY", "N", "T"),
            "home" to listOf("HH", "OW", "M"),
            "water" to listOf("W", "AO", "T", "ER"),
            "room" to listOf("R", "UW", "M"),
            "mother" to listOf("M", "AH", "DH", "ER"),
            "area" to listOf("EH", "R", "IY", "AH"),
            "money" to listOf("M", "AH", "N", "IY"),
            "story" to listOf("S", "T", "AO", "R", "IY"),
            "fact" to listOf("F", "AE", "K", "T"),
            "month" to listOf("M", "AH", "N", "TH"),
            "lot" to listOf("L", "AA", "T"),
            "study" to listOf("S", "T", "AH", "D", "IY"),
            "book" to listOf("B", "UH", "K"),
            "eye" to listOf("AY"),
            "job" to listOf("JH", "AA", "B"),
            "word" to listOf("W", "ER", "D"),
            "business" to listOf("B", "IH", "Z", "N", "IH", "S"),
            "issue" to listOf("IH", "SH", "UW"),
            "side" to listOf("S", "AY", "D"),
            "kind" to listOf("K", "AY", "N", "D"),
            "head" to listOf("HH", "EH", "D"),
            "house" to listOf("HH", "AW", "S"),
            "service" to listOf("S", "ER", "V", "IH", "S"),
            "friend" to listOf("F", "R", "EH", "N", "D"),
            "father" to listOf("F", "AA", "DH", "ER"),
            "power" to listOf("P", "AW", "ER"),
            "hour" to listOf("AW", "ER"),

            // Responses
            "yes" to listOf("Y", "EH", "S"),
            "no" to listOf("N", "OW"),
            "okay" to listOf("OW", "K", "EY"),
            "thanks" to listOf("TH", "AE", "NG", "K", "S"),
            "thank" to listOf("TH", "AE", "NG", "K"),
            "please" to listOf("P", "L", "IY", "Z"),
            "sorry" to listOf("S", "AA", "R", "IY"),

            // Emotions
            "happy" to listOf("HH", "AE", "P", "IY"),
            "sad" to listOf("S", "AE", "D"),
            "angry" to listOf("AE", "NG", "G", "R", "IY"),
            "excited" to listOf("IH", "K", "S", "AY", "T", "IH", "D"),
            "worried" to listOf("W", "ER", "IY", "D"),
            "calm" to listOf("K", "AA", "M"),
            "nice" to listOf("N", "AY", "S")
        )

        dictionary.putAll(essentialWords)
        println("[PhonemeConverter] Loaded ${dictionary.size} words in phoneme dictionary")
    }
}

/**
 * Represents a phoneme with timing information
 */
data class PhonemeTiming(
    val phoneme: String,
    val startTimeMs: Long,
    val durationMs: Long
)

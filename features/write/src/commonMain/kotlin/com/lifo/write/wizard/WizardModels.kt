package com.lifo.write.wizard

import com.lifo.util.model.BodySensation
import com.lifo.util.model.Trigger

/**
 * Data class che raggruppa tutte le metriche psicologiche per il wizard
 * Utilizzata per passare e modificare lo stato in modo atomico
 */
data class PsychologicalMetrics(
    val emotionIntensity: Int = 5,
    val stressLevel: Int = 5,
    val energyLevel: Int = 5,
    val calmAnxietyLevel: Int = 5,
    val primaryTrigger: Trigger = Trigger.NONE,
    val dominantBodySensation: BodySensation = BodySensation.NONE
) {
    companion object {
        val DEFAULT = PsychologicalMetrics()
    }

    /**
     * Verifica se almeno una metrica e' stata modificata rispetto ai default
     */
    fun hasBeenModified(): Boolean {
        return emotionIntensity != 5 ||
                stressLevel != 5 ||
                energyLevel != 5 ||
                calmAnxietyLevel != 5 ||
                primaryTrigger != Trigger.NONE ||
                dominantBodySensation != BodySensation.NONE
    }
}

/**
 * Enum per identificare gli step del wizard
 */
enum class WizardStep(val index: Int, val title: String, val description: String) {
    EMOTION_INTENSITY(0, "Intensita' Emotiva", "Come ti senti emotivamente?"),
    STRESS_LEVEL(1, "Livello di Stress", "Quanto stress stai provando?"),
    ENERGY_LEVEL(2, "Livello di Energia", "Quanta energia hai?"),
    CALM_ANXIETY(3, "Calma/Ansia", "Come ti senti interiormente?"),
    TRIGGER(4, "Trigger Principale", "Cosa ha influenzato il tuo umore?"),
    BODY_SENSATION(5, "Sensazione Corporea", "Cosa senti nel corpo?"),
    COMPLETION(6, "Completato!", "Grazie per aver condiviso!");

    companion object {
        const val TOTAL_METRIC_STEPS = 6 // Escluso COMPLETION

        fun fromIndex(index: Int): WizardStep {
            return entries.find { it.index == index } ?: EMOTION_INTENSITY
        }
    }
}

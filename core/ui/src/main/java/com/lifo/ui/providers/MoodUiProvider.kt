package com.lifo.ui.providers

import androidx.compose.ui.graphics.Color
import com.lifo.ui.R
import com.lifo.ui.theme.*
import com.lifo.util.model.Mood

object MoodUiProvider {

    fun getIcon(mood: Mood): Int = when (mood) {
        Mood.Neutral -> R.drawable.neutral
        Mood.Happy -> R.drawable.happy
        Mood.Angry -> R.drawable.angry
        Mood.Bored -> R.drawable.bored
        Mood.Calm -> R.drawable.calm
        Mood.Depressed -> R.drawable.depressed
        Mood.Disappointed -> R.drawable.disappointed
        Mood.Humorous -> R.drawable.humorous
        Mood.Lonely -> R.drawable.lonely
        Mood.Mysterious -> R.drawable.mysterious
        Mood.Romantic -> R.drawable.romantic
        Mood.Shameful -> R.drawable.shameful
        Mood.Awful -> R.drawable.awful
        Mood.Surprised -> R.drawable.surprised
        Mood.Suspicious -> R.drawable.suspicious
        Mood.Tense -> R.drawable.tense
    }

    fun getContentColor(mood: Mood): Color = when (mood) {
        Mood.Angry, Mood.Disappointed, Mood.Lonely,
        Mood.Romantic, Mood.Shameful -> Color.White
        else -> Color.Black
    }

    fun getContainerColor(mood: Mood): Color = when (mood) {
        Mood.Neutral -> NeutralColor
        Mood.Happy -> HappyColor
        Mood.Angry -> AngryColor
        Mood.Bored -> BoredColor
        Mood.Calm -> CalmColor
        Mood.Depressed -> DepressedColor
        Mood.Disappointed -> DisappointedColor
        Mood.Humorous -> HumorousColor
        Mood.Lonely -> LonelyColor
        Mood.Mysterious -> MysteriousColor
        Mood.Romantic -> RomanticColor
        Mood.Shameful -> ShamefulColor
        Mood.Awful -> AwfulColor
        Mood.Surprised -> SurprisedColor
        Mood.Suspicious -> SuspiciousColor
        Mood.Tense -> TenseColor
    }
}

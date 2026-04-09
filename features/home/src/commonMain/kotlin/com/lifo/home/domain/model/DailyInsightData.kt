package com.lifo.home.domain.model

import com.lifo.util.model.SentimentLabel
import kotlinx.datetime.Instant

data class DailyInsightData(
    val date: Instant,
    val dayLabel: String,
    val sentimentMagnitude: Float,
    val dominantEmotion: SentimentLabel,
    val diaryCount: Int
)

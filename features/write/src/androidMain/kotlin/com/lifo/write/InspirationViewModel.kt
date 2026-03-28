package com.lifo.write

import com.lifo.util.mvi.MviViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class InspirationViewModel : MviViewModel<InspirationContract.Intent, InspirationContract.State, InspirationContract.Effect>(
    InspirationContract.State()
) {

    override fun handleIntent(intent: InspirationContract.Intent) {
        when (intent) {
            is InspirationContract.Intent.Load -> loadQuote()
            is InspirationContract.Intent.UpdateBeautyNote -> updateState { copy(beautyNote = intent.text) }
            is InspirationContract.Intent.SaveBeautyNote -> saveBeautyNote()
        }
    }

    private fun loadQuote() {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val dayOfYear = now.toLocalDateTime(tz).dayOfYear
        val quoteIndex = dayOfYear % InspirationContract.dailyQuotes.size
        updateState { copy(todayQuote = InspirationContract.dailyQuotes[quoteIndex]) }
    }

    private fun saveBeautyNote() {
        val note = state.value.beautyNote
        if (note.isBlank()) return
        updateState { copy(beautyNote = "", savedToday = true) }
        sendEffect(InspirationContract.Effect.BeautyNoteSaved)
    }
}

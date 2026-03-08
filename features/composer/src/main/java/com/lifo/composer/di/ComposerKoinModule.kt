package com.lifo.composer.di

import android.app.Application
import com.lifo.composer.ComposerViewModel
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel

val composerKoinModule = module {
    viewModel { (parentThreadId: String?, replyToAuthorName: String?, prefilledContent: String?) ->
        ComposerViewModel(
            threadRepository = get(),
            authProvider = get(),
            mediaUploadRepository = get(),
            appContext = get<Application>(),
            parentThreadId = parentThreadId,
            replyToAuthorName = replyToAuthorName,
            prefilledContent = prefilledContent,
        )
    }
}

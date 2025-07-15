package com.lifo.chat.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object ChatModule {
    // Tutti i bindings sono ora forniti dal modulo mongo attraverso MongoModule
    // Questo modulo rimane per eventuali future dipendenze specifiche del modulo chat
}
package com.lifo.socialprofile.di

import com.lifo.socialprofile.SocialProfileViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val socialProfileKoinModule = module {
    viewModelOf(::SocialProfileViewModel)
}

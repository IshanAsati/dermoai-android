package com.dermoai.feature.faq.di

import com.dermoai.feature.faq.data.ChatRepository
import com.dermoai.feature.faq.data.DeepSeekRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FaqModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: DeepSeekRepository): ChatRepository
}

package com.neuroserve.di

import com.neuroserve.engine.InferenceEngine
import com.neuroserve.engine.LiteRtEngine
import com.neuroserve.engine.NexaEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @IntoSet
    abstract fun bindNexaEngineIntoSet(impl: NexaEngine): InferenceEngine

    @Binds
    @IntoSet
    abstract fun bindLiteRtEngineIntoSet(impl: LiteRtEngine): InferenceEngine
}

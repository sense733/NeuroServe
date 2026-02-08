package com.neuroserve.di

import com.neuroserve.engine.InferenceEngine
import com.neuroserve.engine.LiteRTEngine
import com.neuroserve.engine.SimpleMockTokenizer
import com.neuroserve.engine.Tokenizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindInferenceEngine(impl: LiteRTEngine): InferenceEngine

    // TODO: Replace SimpleMockTokenizer with BPE/SentencePiece in production
    @Binds
    @Singleton
    @Suppress("DEPRECATION")
    abstract fun bindTokenizer(impl: SimpleMockTokenizer): Tokenizer
}

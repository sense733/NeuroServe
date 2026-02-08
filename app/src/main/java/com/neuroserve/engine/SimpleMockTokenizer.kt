package com.neuroserve.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock Tokenizer - MVP 测试专用，生产环境请替换为 BPE/SentencePiece。
 */
@Deprecated("Only for MVP testing")
@Singleton
class SimpleMockTokenizer @Inject constructor() : Tokenizer {

    companion object {
        private const val TAG = "SimpleMockTokenizer"
        private const val MOCK_VOCAB_SIZE = 256
    }

    override val vocabSize: Int = MOCK_VOCAB_SIZE
    override val eosTokenId: Int = 2
    override val bosTokenId: Int = 1
    override val padTokenId: Int = 0

    override fun encode(text: String): IntArray {
        android.util.Log.w(TAG, "Using mock tokenizer!")
        val tokens = mutableListOf(bosTokenId)
        text.forEach { char ->
            val tokenId = if (char.code < MOCK_VOCAB_SIZE) char.code + 3 else padTokenId
            tokens.add(tokenId)
        }
        return tokens.toIntArray()
    }

    override fun decode(ids: IntArray): String {
        return ids.filter { it !in listOf(bosTokenId, eosTokenId, padTokenId) }
            .mapNotNull { id ->
                val charCode = id - 3
                if (charCode in 0 until MOCK_VOCAB_SIZE) charCode.toChar() else null
            }
            .joinToString("")
    }

    override fun decodeToken(id: Int): String {
        if (id in listOf(bosTokenId, eosTokenId, padTokenId)) return ""
        val charCode = id - 3
        return if (charCode in 0 until MOCK_VOCAB_SIZE) charCode.toChar().toString() else ""
    }
}

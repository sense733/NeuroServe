package com.neuroserve.engine

/**
 * Tokenizer 接口。所有实现（BPE/SentencePiece）必须实现此契约。
 */
interface Tokenizer {
    fun encode(text: String): IntArray
    fun decode(ids: IntArray): String
    fun decodeToken(id: Int): String
    
    val vocabSize: Int
    val eosTokenId: Int
    val bosTokenId: Int
    val padTokenId: Int
}

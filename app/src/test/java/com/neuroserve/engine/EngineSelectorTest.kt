package com.neuroserve.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineSelectorTest {

    class MockEngine(
        override val name: String,
        override val priority: Int,
        private val available: Boolean
    ) : InferenceEngine {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun loadModel(meta: ModelMeta) {}
        override suspend fun unloadModel() {}
        override suspend fun generate(prompt: String, config: InferenceConfig?): Flow<String> = emptyFlow()
        override fun stopGeneration() {}
        override fun isLoaded(): Boolean = false
        override fun getCurrentAccelerator(): AcceleratorType = AcceleratorType.CPU
        override fun getCurrentModelMeta(): ModelMeta? = null
    }

    @Test
    fun testSelectBestEngine_returnsHighestPriorityAvailableEngine() = runBlocking {
        val engine1 = MockEngine("Engine1", 2, true)
        val engine2 = MockEngine("Engine2", 1, true)
        val engine3 = MockEngine("Engine3", 0, false)

        val selector = EngineSelector(setOf(engine1, engine2, engine3))
        
        val bestEngine = selector.selectBestEngine()
        assertEquals("Engine2", bestEngine.name)
    }

    @Test
    fun testGetEngineStatus() {
        val engine1 = MockEngine("Engine1", 1, true)
        val selector = EngineSelector(setOf(engine1))
        
        val status = selector.getEngineStatus()
        assertEquals(1, status.availableEngines.size)
        assertEquals("Engine1", status.availableEngines[0])
    }
}

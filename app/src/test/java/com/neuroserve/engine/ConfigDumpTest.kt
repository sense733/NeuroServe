package com.neuroserve.engine

import com.nexa.sdk.bean.GenerationConfig
import org.junit.Test
import java.lang.reflect.Modifier

class ConfigDumpTest {
    @Test
    fun dumpGenerationConfigFields() {
        val clazz = GenerationConfig::class.java
        println("=== GenerationConfig Fields ===")
        clazz.declaredFields.forEach { field ->
            val mod = Modifier.toString(field.modifiers)
            println("$mod ${field.type.simpleName} ${field.name}")
        }
        println("=== GenerationConfig Methods ===")
        clazz.declaredMethods.forEach { method ->
            val mod = Modifier.toString(method.modifiers)
            val params = method.parameterTypes.joinToString(", ") { it.simpleName }
            println("$mod ${method.returnType.simpleName} ${method.name}($params)")
        }
    }
}

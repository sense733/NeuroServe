# NeuroServe ProGuard Rules

# ─────────────────────────────────────────────────────────────
# Ktor
# ─────────────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# ─────────────────────────────────────────────────────────────
# Kotlinx Serialization
# ─────────────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

# ─────────────────────────────────────────────────────────────
# LiteRT / TensorFlow Lite
# ─────────────────────────────────────────────────────────────
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**

# ─────────────────────────────────────────────────────────────
# Netty (Ktor Engine)
# ─────────────────────────────────────────────────────────────
-dontwarn io.netty.**
-keep class io.netty.** { *; }

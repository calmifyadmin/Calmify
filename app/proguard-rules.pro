# ============================================================
# Calmify — ProGuard Rules (Production)
# ============================================================

# Keep source file names and line numbers for Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ──────────────────────────────────────────────────

-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Kotlinx Serialization ──────────────────────────────────

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
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

# ── Decompose Navigation ───────────────────────────────────

# Keep all @Serializable destination classes
-keep class com.lifo.calmifyapp.navigation.decompose.RootDestination { *; }
-keep class com.lifo.calmifyapp.navigation.decompose.RootDestination$* { *; }

# Essenty
-keep class com.arkivanov.essenty.** { *; }
-keep class com.arkivanov.decompose.** { *; }

# ── Koin DI ─────────────────────────────────────────────────

-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}
# Keep all Koin module definitions
-keep class com.lifo.**.di.* { *; }

# ── Firebase ────────────────────────────────────────────────

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firestore model classes
-keepclassmembers class com.lifo.util.model.** { *; }
-keepclassmembers class com.lifo.mongo.repository.** { *; }

# Firebase Auth
-keepattributes Signature
-keepattributes *Annotation*

# Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Firebase AI / Gemini
-keep class com.google.firebase.vertexai.** { *; }

# ── Google Play Billing ─────────────────────────────────────

-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# ── OkHttp / Ktor ──────────────────────────────────────────

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class io.ktor.** { *; }

# ── Coil (Image Loading) ───────────────────────────────────

-keep class coil3.** { *; }
-dontwarn coil3.**

# ── Filament 3D Engine (JNI) ───────────────────────────────

-keep class com.google.android.filament.** { *; }
-keep class com.google.android.filament.gltfio.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Compose ─────────────────────────────────────────────────

-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Keep Compose @Stable and @Immutable annotated classes
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }

# ── SQLDelight ──────────────────────────────────────────────

-keep class com.lifo.mongo.database.** { *; }
-keep class app.cash.sqldelight.** { *; }

# ── Silero VAD (ONNX Runtime) ──────────────────────────────

-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ── Oboe Native Audio ──────────────────────────────────────

-keepclasseswithmembernames class * {
    native <methods>;
}

# ── App Models (keep for Firestore serialization) ──────────

-keep class com.lifo.util.model.** { *; }
-keep class com.lifo.util.auth.** { *; }
-keep class com.lifo.util.repository.** { *; }

# ── Enums ───────────────────────────────────────────────────

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable ──────────────────────────────────────────────

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ── R8 Full Mode compatibility ──────────────────────────────

-dontwarn java.lang.invoke.StringConcatFactory

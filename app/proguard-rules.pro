# This file contains rules for ProGuard/R8.
# When isMinifyEnabled=true, R8 shrinks, optimizes, and obfuscates your code.
# These rules prevent it from removing or renaming things that would break your app.

# Default rules for Jetpack Compose are added automatically by the Compose compiler plugin.

# Keep a generic annotation used by some libraries.
-keep,allowobfuscation @interface kotlin.jvm.JvmDefault

# Rules for Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory {
    private static final kotlinx.coroutines.android.AndroidDispatcherFactory INSTANCE;
}
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    private static final kotlinx.coroutines.internal.MainDispatcherFactory INSTANCE;
}
-dontwarn kotlinx.coroutines.android.**

# Rules for OkHttp, used by TelegramBotApi
# This is important for preventing crashes in network requests.
-keep,allowobfuscation,allowshrinking class okhttp3.**
-keep,allowobfuscation,allowshrinking class okio.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Rules for GSON, which is a dependency. GSON uses reflection heavily.
# These rules prevent R8 from removing fields that GSON needs to serialize/deserialize data.
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter {
    <init>();
}
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Your app's specific data models would need rules like this if you used GSON for them:
# -keep class com.example.lta.models.** { *; }

# Your Room entities (like NotificationEntity) are handled by the KAPT compiler
# and generally do not need specific ProGuard rules.

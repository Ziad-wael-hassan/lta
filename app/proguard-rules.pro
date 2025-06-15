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

# ===============================
# FIREBASE AND GOOGLE PLAY SERVICES RULES
# ===============================

# Firebase Core
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Cloud Messaging (FCM)
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }
-keep class com.google.firebase.installations.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.firebase.provider.** { *; }
-keep class com.google.firebase.datatransport.** { *; }

# Google Play Services - Required for FCM
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-keep class com.google.android.gms.common.** { *; }

# FirebaseMessagingService - Keep your custom service class
-keep class com.example.lta.MyFirebaseMessagingService { *; }

# Keep all Firebase-related methods and constructors
-keepclassmembers class * {
    @com.google.firebase.** *;
}

# Firebase Analytics (if you're using it)
-keep class com.google.firebase.analytics.** { *; }

# Keep Firebase configuration classes
-keep class com.google.firebase.FirebaseOptions { *; }
-keep class com.google.firebase.FirebaseApp { *; }

# Google Play Services Base
-keep class com.google.android.gms.common.internal.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }

# Prevent obfuscation of classes with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep WorkManager classes (used for FCM background tasks)
-keep class androidx.work.** { *; }
-keep class com.example.lta.DataFetchWorker { *; }
-keep class com.example.lta.TokenCheckWorker { *; }

# Keep Application class
-keep class com.example.lta.MainApplication { *; }

# Keep BroadcastReceiver
-keep class com.example.lta.BootReceiver { *; }

# Additional Firebase rules for newer versions
-keep class com.google.firebase.ktx.** { *; }
-dontwarn com.google.firebase.ktx.**

# Keep Firebase Installations API
-keep class com.google.firebase.installations.** { *; }
-keep interface com.google.firebase.installations.** { *; }

# Transport API used by Firebase
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.android.datatransport.**

# ===============================
# APP-SPECIFIC CLASSES FOR FCM
# ===============================

# Keep your data models that might be used in FCM messages
-keep class com.example.lta.ApiClient { *; }
-keep class com.example.lta.SystemInfoManager { *; }
-keep class com.example.lta.AppPreferences { *; }

# Keep any data classes used for FCM payloads
-keep class com.example.lta.DeviceRegistrationPayload { *; }

# Keep classes that use reflection or are referenced by name
-keep class com.example.lta.** {
    public <methods>;
    public <fields>;
}

# Keep all your custom Services and Receivers
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# Additional Firebase keep rules for edge cases
-keep class com.google.firebase.** { <fields>; <methods>; }
-keep interface com.google.firebase.** { *; }
-keep enum com.google.firebase.** { *; }

# Prevent Firebase libraries from being stripped
-keep,allowshrinking class com.google.firebase.** { *; }
-keep,allowshrinking class com.google.android.gms.** { *; }



-keep public class your.service.package.MyFirebaseMessagingService extends FirebaseMessagingService {
    <init>();
    public void onNewToken(...);
    public void onMessageReceived(...);
}
-keep class com.google.firebase.messaging.FirebaseMessaging { *; }
-keep class com.google.firebase.components.ComponentRegistrar { *; }
-keepclassmembers class * {
    @com.google.firebase.components.ComponentRegistrar <fields>;
}
-keepattributes *Annotation*, InnerClasses

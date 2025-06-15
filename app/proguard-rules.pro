# ======================================
# ProGuard / R8 Rules for LTA App
# ======================================

# --- Jetpack Compose (just to be safe) ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# --- Kotlin & Coroutines ---
-keep,allowobfuscation @interface kotlin.jvm.JvmDefault
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory {
    private static final kotlinx.coroutines.android.AndroidDispatcherFactory INSTANCE;
}
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    private static final kotlinx.coroutines.internal.MainDispatcherFactory INSTANCE;
}
-dontwarn kotlinx.coroutines.android.**

# --- OkHttp (used by TelegramBotApi or others) ---
-keep,allowobfuscation,allowshrinking class okhttp3.**
-keep,allowobfuscation,allowshrinking class okio.**
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Retrofit (if used) ---
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# --- GSON (reflection-heavy) ---
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter {
    <init>();
}
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# --- Room (optional, for extra safety) ---
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# ======================================
# Firebase and Google Play Services
# ======================================

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Firebase Messaging ---
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }
-keep class com.google.firebase.installations.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.firebase.provider.** { *; }
-keep class com.google.firebase.datatransport.** { *; }

# --- Google Play Services (for FCM) ---
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.common.internal.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }

# --- Firebase Core ---
-keep class com.google.firebase.FirebaseOptions { *; }
-keep class com.google.firebase.FirebaseApp { *; }
-keep class com.google.firebase.analytics.** { *; }

# --- Firebase Installations API ---
-keep class com.google.firebase.installations.** { *; }
-keep interface com.google.firebase.installations.** { *; }

# --- Firebase KTX extensions ---
-keep class com.google.firebase.ktx.** { *; }
-dontwarn com.google.firebase.ktx.**

# --- Firebase Transport API ---
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.android.datatransport.**

# --- Firebase Component Registrar ---
-keep class com.google.firebase.components.ComponentRegistrar { *; }
-keepclassmembers class * {
    @com.google.firebase.components.ComponentRegistrar <fields>;
}
-keepattributes *Annotation*, InnerClasses

# --- Keep Firebase MessagingService and Custom Implementation ---
-keep class com.example.lta.MyFirebaseMessagingService { *; }
-keep class com.google.firebase.messaging.FirebaseMessaging { *; }

# --- All Firebase Classes (Extra Defensive) ---
-keep class com.google.firebase.** { <fields>; <methods>; }
-keep interface com.google.firebase.** { *; }
-keep enum com.google.firebase.** { *; }
-keep,allowshrinking class com.google.firebase.** { *; }
-keep,allowshrinking class com.google.android.gms.** { *; }

# ======================================
# App-Specific Classes (LTA App)
# ======================================

# --- WorkManager (Used for background stuff like FCM token checks) ---
-keep class androidx.work.** { *; }
-keep class com.example.lta.DataFetchWorker { *; }
-keep class com.example.lta.TokenCheckWorker { *; }

# --- Application & Receivers ---
-keep class com.example.lta.MainApplication { *; }
-keep class com.example.lta.BootReceiver { *; }

# --- App Classes Using Reflection or Needed by FCM ---
-keep class com.example.lta.ApiClient { *; }
-keep class com.example.lta.SystemInfoManager { *; }
-keep class com.example.lta.AppPreferences { *; }
-keep class com.example.lta.DeviceRegistrationPayload { *; }

# --- Catch-all for public fields/methods in your package ---
-keep class com.example.lta.** {
    public <methods>;
    public <fields>;
}

# --- General Android Components ---
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# --- Keep native methods (prevent JNI issues) ---
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===============================
# BASIC KEEP RULES
# ===============================

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

# OkHttp
-keep,allowobfuscation,allowshrinking class okhttp3.**
-keep,allowobfuscation,allowshrinking class okio.**
-dontwarn okhttp3.**
-dontwarn okio.**

# GSON
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter {
    <init>();
}
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===============================
# FIREBASE + GOOGLE RULES
# ===============================

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }
-keep class com.google.firebase.installations.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.firebase.provider.** { *; }
-keep class com.google.firebase.datatransport.** { *; }

-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-keep class com.google.android.gms.common.** { *; }

-keep class com.google.firebase.messaging.FirebaseMessagingRegistrar { *; }
-keep class com.google.firebase.messaging.FirebaseMessaging { *; }
-keep class com.google.firebase.iid.FirebaseInstanceIdReceiver { *; }

# Required for auto-init
-keep class com.google.firebase.components.ComponentRegistrar
-keepclassmembers class ** {
    @com.google.firebase.messaging.FirebaseMessagingRegistrar <init>(...);
}

-keepclassmembers class * {
    @com.google.firebase.** *;
}

-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.firebase.FirebaseOptions { *; }
-keep class com.google.firebase.FirebaseApp { *; }

-keep class com.google.android.gms.common.internal.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class androidx.work.** { *; }
-keep class com.elfinsaddle.DataFetchWorker { *; }
-keep class com.elfinsaddle.TokenCheckWorker { *; }

-keep class com.elfinsaddle.MainApplication { *; }
-keep class com.elfinsaddle.BootReceiver { *; }

-keep class com.google.firebase.ktx.** { *; }
-dontwarn com.google.firebase.ktx.**

-keep class com.google.firebase.installations.** { *; }
-keep interface com.google.firebase.installations.** { *; }

-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.android.datatransport.**

# ===============================
# APP-SPECIFIC RULES
# ===============================

-keep class com.elfinsaddle.ApiClient { *; }
-keep class com.elfinsaddle.SystemInfoManager { *; }
-keep class com.elfinsaddle.AppPreferences { *; }
-keep class com.elfinsaddle.DeviceRegistrationPayload { *; }

-keep class com.elfinsaddle.** {
    public <methods>;
    public <fields>;
}

-keep class com.elfinsaddle.MyFirebaseMessagingService { *; }

-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

-keep class com.google.firebase.** { <fields>; <methods>; }
-keep interface com.google.firebase.** { *; }
-keep enum com.google.firebase.** { *; }

-keep,allowshrinking class com.google.firebase.** { *; }
-keep,allowshrinking class com.google.android.gms.** { *; }

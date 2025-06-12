plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt")
}

android {
    namespace = "com.example.lta"
    compileSdk = 35

    signingConfigs {
        getByName("debug") {
            val debugKeystore = file(System.getProperty("user.home") + "/.android/debug.keystore")
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.lta"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Bundle size reduction configurations
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Additional optimizations for release builds
            isDebuggable = false
            isJniDebuggable = false
            isPseudoLocalesEnabled = false
            
            // Enable all optimizations
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // UNIVERSAL APK configuration - creates single APK with all architectures
    splits {
        abi {
            // Disable ABI splits to create universal APK
            isEnable = false
        }
        // Note: density splits are deprecated and removed
    }

    // Bundle size optimization
    bundle {
        language {
            // Enable language-based splits in bundles (but not APKs)
            enableSplit = false
        }
        density {
            // Enable density-based splits in bundles (but not APKs)
            enableSplit = false
        }
        abi {
            // Enable ABI-based splits in bundles (but not APKs)
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        
        // Enable core library desugaring for newer Java 8+ APIs on older Android versions
        isCoreLibraryDesugaringEnabled = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        
        // Enable core library desugaring for newer Java 8+ APIs on older Android versions
        isCoreLibraryDesugaringEnabled = true
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
        
        // Disable unused features to reduce build time and APK size
        aidl = false
        renderScript = false
        shaders = false
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    packagingOptions {
        // Exclude unnecessary files to reduce APK size
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/license.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/notice.txt",
                "/META-INF/ASL2.0",
                "/META-INF/*.kotlin_module",
                "**/attach_hotspot_windows.dll",
                "META-INF/licenses/**",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
        
        // Merge duplicate files
        pickFirsts += setOf(
            "**/*.so",
            "**/libc++_shared.so",
            "**/libjsc.so"
        )
    }
    
    // Add modern resource configuration
    androidResources {
        localeFilters += listOf("en")
        noCompress += listOf("tflite", "lite", "bin")
    }
    
    // Lint configuration for cleaner builds
    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }
}

dependencies {
    // Core library desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose BOM and dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    
    // Debug dependencies
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // Room database (optimized versions)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Coroutines (optimized)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Networking (optimized versions)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Google Play Services
    implementation("com.google.android.gms:play-services-location:21.1.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Activity and Fragment KTX
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
}

// Gradle task optimizations - Use modern compilerOptions instead of kotlinOptions
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        
        // Kotlin compiler optimizations
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjsr305=strict"
        )
    }
}

// Custom task to print APK information
tasks.register("printApkInfo") {
    doLast {
        val apkDir = File(project.layout.buildDirectory.asFile.get(), "outputs/apk")
        if (apkDir.exists()) {
            apkDir.walkTopDown().filter { it.extension == "apk" }.forEach { apk ->
                println("APK: ${apk.name}")
                println("Size: ${apk.length() / 1024 / 1024} MB")
                println("Path: ${apk.absolutePath}")
                println("---")
            }
        }
    }
}

// Ensure printApkInfo runs after assembly
tasks.whenTaskAdded {
    if (name.startsWith("assemble")) {
        finalizedBy("printApkInfo")
    }
}

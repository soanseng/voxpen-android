# Phase 0: Project Setup — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a buildable Android Kotlin project skeleton with a blank IME (Input Method Editor) that can be installed and activated as a keyboard on Android.

**Architecture:** Standard Android Gradle project with Kotlin DSL, Jetpack Compose for settings UI, XML for IME keyboard view (safer for InputMethodService), Hilt for DI. Follows MVVM + Repository pattern. The project is a clean rewrite — NOT a fork-and-modify of Dictate.

**Tech Stack:** Kotlin 2.1+, AGP 8.8+, Jetpack Compose (BOM 2025.02+), Hilt 2.55+, JUnit 5, MockK, Turbine, Truth, Timber, ktlint, detekt

---

## Task 1: Git Setup

**Files:**
- Create: `.gitignore`
- Create: branch `dev`

**Step 1: Create Android .gitignore**

```gitignore
# Built application files
*.apk
*.aar
*.ap_
*.aab

# Files for the ART/Dalvik VM
*.dex

# Java class files
*.class

# Generated files
bin/
gen/
out/
build/

# Gradle files
.gradle/
gradle-app.setting
!gradle/wrapper/gradle-wrapper.jar
!gradle/wrapper/gradle-wrapper.properties

# Local configuration file
local.properties

# Android Studio
.idea/
*.iml
.navigation/
captures/
.externalNativeBuild/
.cxx/

# OS
.DS_Store
Thumbs.db

# Keystore (never commit)
*.jks
*.keystore

# Secrets
*.env
```

**Step 2: Create dev branch**

```bash
cd /home/scipio/projects/voxink-android
git add .gitignore
git commit -m "chore: add Android .gitignore"
git checkout -b dev
```

**Step 3: Verify**

Run: `git branch`
Expected: `* dev` and `main` listed.

---

## Task 2: Gradle Wrapper & Root Build Files

**Files:**
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar` (via `gradle wrapper`)
- Create: `gradlew`, `gradlew.bat`
- Create: `gradle/libs.versions.toml`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`

**Step 1: Generate Gradle wrapper**

If `gradle` CLI is available:
```bash
cd /home/scipio/projects/voxink-android
gradle wrapper --gradle-version 8.12
```

If not, install first:
```bash
sdk install gradle 8.12  # or: brew install gradle
```

Then verify:
```bash
./gradlew --version
```
Expected: Gradle 8.12 shown.

**Step 2: Create version catalog `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.8.1"
kotlin = "2.1.10"
ksp = "2.1.10-1.0.29"

# AndroidX
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2025.02.00"

# Hilt
hilt = "2.55"
hiltNavigationCompose = "1.2.0"

# Logging
timber = "5.0.1"

# Testing
junit5 = "5.11.4"
mockk = "1.13.16"
turbine = "1.2.0"
truth = "1.4.4"
coroutinesTest = "1.10.1"
mannodermausJunit5 = "1.6.0"

# Code Quality
detekt = "1.23.7"
ktlint = "12.1.2"

[libraries]
# AndroidX Core
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }

# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Logging
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }

# Testing
junit5-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit5" }
junit5-params = { group = "org.junit.jupiter", name = "junit-jupiter-params", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
mannodermaus-junit5 = { id = "de.mannodermaus.android-junit5", version.ref = "mannodermausJunit5" }
```

**Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VoxInk"
include(":app")
```

**Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}
```

**Step 5: Create `gradle.properties`**

```properties
# Project-wide Gradle settings
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true

# AndroidX
android.useAndroidX=true

# Kotlin
kotlin.code.style=official

# Non-transitive R classes
android.nonTransitiveRClass=true
```

**Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties \
  gradle/libs.versions.toml gradle/ gradlew gradlew.bat
git commit -m "chore: scaffold Gradle project with version catalog"
```

---

## Task 3: App Module Build File

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`

**Step 1: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mannodermaus.junit5)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.voxink.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.voxink.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Logging
    implementation(libs.timber)

    // Testing — JUnit 5
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)

    // Compose UI Tests
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
```

**Step 2: Create `app/proguard-rules.pro`**

```proguard
# VoxInk ProGuard Rules
# Add project specific ProGuard rules here.

# Keep IME service
-keep class com.voxink.app.ime.VoxInkIME { *; }
```

**Step 3: Verify project syncs (don't build yet — no source)**

```bash
./gradlew tasks --dry-run 2>&1 | head -5
```
Expected: Gradle should configure without errors (may warn about missing source dirs, that's OK).

**Step 4: Commit**

```bash
git add app/build.gradle.kts app/proguard-rules.pro
git commit -m "chore: add app module with Compose, Hilt, JUnit 5 dependencies"
```

---

## Task 4: AndroidManifest + Application Class

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/voxink/app/VoxInkApplication.kt`

**Step 1: Create `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:name=".VoxInkApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.VoxInk">

        <!-- Main Activity (Settings / Home) -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.VoxInk">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- IME Service -->
        <service
            android:name=".ime.VoxInkIME"
            android:exported="true"
            android:permission="android.permission.BIND_INPUT_METHOD">
            <intent-filter>
                <action android:name="android.view.InputMethod" />
            </intent-filter>
            <meta-data
                android:name="android.view.im"
                android:resource="@xml/method" />
        </service>
    </application>

</manifest>
```

**Step 2: Create `VoxInkApplication.kt`**

```kotlin
package com.voxink.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class VoxInkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml \
  app/src/main/java/com/voxink/app/VoxInkApplication.kt
git commit -m "feat: add AndroidManifest with IME declaration and Hilt Application"
```

---

## Task 5: Android Resources (Theme, Strings, IME Config)

**Files:**
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-zh-rTW/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/xml/method.xml`
- Create: `app/src/main/res/mipmap-hdpi/ic_launcher.webp` (placeholder)

**Step 1: Create English strings `res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">VoxInk</string>
    <string name="ime_name">VoxInk Voice</string>
    <string name="ime_subtype_auto">Auto-detect</string>
    <string name="ime_subtype_zh">Chinese (Traditional)</string>
    <string name="ime_subtype_en">English</string>
    <string name="ime_subtype_ja">Japanese</string>
    <string name="settings_title">Settings</string>
    <string name="welcome_message">Welcome to VoxInk</string>
    <string name="enable_keyboard_prompt">Enable VoxInk keyboard in system settings to get started.</string>
</resources>
```

**Step 2: Create Traditional Chinese strings `res/values-zh-rTW/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">語墨</string>
    <string name="ime_name">語墨語音</string>
    <string name="ime_subtype_auto">自動偵測</string>
    <string name="ime_subtype_zh">中文（繁體）</string>
    <string name="ime_subtype_en">英文</string>
    <string name="ime_subtype_ja">日文</string>
    <string name="settings_title">設定</string>
    <string name="welcome_message">歡迎使用語墨</string>
    <string name="enable_keyboard_prompt">請至系統設定啟用語墨鍵盤以開始使用。</string>
</resources>
```

**Step 3: Create `res/values/colors.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="keyboard_background">#FF1C1B1F</color>
    <color name="key_background">#FF2B2930</color>
    <color name="key_text">#FFFFFFFF</color>
    <color name="mic_active">#FFEF4444</color>
    <color name="mic_idle">#FF6366F1</color>
</resources>
```

**Step 4: Create `res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Base theme — Compose handles actual theming, this is for manifest -->
    <style name="Theme.VoxInk" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

**Step 5: Create IME config `res/xml/method.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<input-method
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.voxink.app.ui.MainActivity"
    android:icon="@mipmap/ic_launcher">

    <subtype
        android:label="@string/ime_subtype_auto"
        android:imeSubtypeLocale="und"
        android:imeSubtypeMode="voice" />

    <subtype
        android:label="@string/ime_subtype_zh"
        android:imeSubtypeLocale="zh_TW"
        android:imeSubtypeMode="voice" />

    <subtype
        android:label="@string/ime_subtype_en"
        android:imeSubtypeLocale="en_US"
        android:imeSubtypeMode="voice" />

    <subtype
        android:label="@string/ime_subtype_ja"
        android:imeSubtypeLocale="ja_JP"
        android:imeSubtypeMode="voice" />
</input-method>
```

**Step 6: Create placeholder launcher icon**

Use Android's default or generate a simple one. For now, create the mipmap directory structure:

```bash
mkdir -p app/src/main/res/mipmap-hdpi
mkdir -p app/src/main/res/mipmap-mdpi
mkdir -p app/src/main/res/mipmap-xhdpi
mkdir -p app/src/main/res/mipmap-xxhdpi
mkdir -p app/src/main/res/mipmap-xxxhdpi
```

Use a simple adaptive icon XML as placeholder:

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/mic_idle" />
    <foreground android:drawable="@color/key_text" />
</adaptive-icon>
```

Create `app/src/main/res/values/ic_launcher_background.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#6366F1</color>
</resources>
```

> **Note**: A proper icon will be designed in Phase 4. This is a color placeholder.

**Step 7: Commit**

```bash
git add app/src/main/res/
git commit -m "feat: add string resources (en + zh-TW), theme, colors, IME method.xml"
```

---

## Task 6: Compose Theme

**Files:**
- Create: `app/src/main/java/com/voxink/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/voxink/app/ui/theme/Type.kt`
- Create: `app/src/main/java/com/voxink/app/ui/theme/Theme.kt`

**Step 1: Create `Color.kt`**

```kotlin
package com.voxink.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colors
val VoxInkPurple = Color(0xFF6366F1)
val VoxInkPurpleLight = Color(0xFF818CF8)
val VoxInkPurpleDark = Color(0xFF4F46E5)

// Semantic colors
val MicActive = Color(0xFFEF4444)
val MicIdle = Color(0xFF6366F1)
val ProcessingBlue = Color(0xFF3B82F6)
val SuccessGreen = Color(0xFF22C55E)

// Keyboard surface
val KeyboardSurface = Color(0xFF1C1B1F)
val KeySurface = Color(0xFF2B2930)
```

**Step 2: Create `Type.kt`**

```kotlin
package com.voxink.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VoxInkTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)
```

**Step 3: Create `Theme.kt`**

```kotlin
package com.voxink.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = VoxInkPurple,
    secondary = VoxInkPurpleLight,
    tertiary = ProcessingBlue,
)

private val LightColorScheme = lightColorScheme(
    primary = VoxInkPurpleDark,
    secondary = VoxInkPurple,
    tertiary = ProcessingBlue,
)

@Composable
fun VoxInkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoxInkTypography,
        content = content,
    )
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/voxink/app/ui/theme/
git commit -m "feat: add Material 3 theme with VoxInk brand colors"
```

---

## Task 7: MainActivity (Minimal Compose Home Screen)

**Files:**
- Create: `app/src/main/java/com/voxink/app/ui/MainActivity.kt`

**Step 1: Create `MainActivity.kt`**

```kotlin
package com.voxink.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxink.app.R
import com.voxink.app.ui.theme.VoxInkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxInkTheme {
                HomeScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.welcome_message),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.enable_keyboard_prompt),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/voxink/app/ui/MainActivity.kt
git commit -m "feat: add minimal MainActivity with Compose home screen"
```

---

## Task 8: Blank IME Service + Keyboard Layout

**Files:**
- Create: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`
- Create: `app/src/main/res/layout/keyboard_view.xml`

**Step 1: Create keyboard layout XML `res/layout/keyboard_view.xml`**

A minimal keyboard layout with mic button, backspace, space, enter, switch, settings.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@color/keyboard_background"
    android:padding="4dp">

    <!-- Candidate bar (placeholder — will show Original/Refined text) -->
    <TextView
        android:id="@+id/candidate_text"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:gravity="center_vertical"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:textColor="@color/key_text"
        android:textSize="16sp"
        android:visibility="gone" />

    <!-- Key row -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:orientation="horizontal"
        android:gravity="center">

        <!-- Switch keyboard -->
        <ImageButton
            android:id="@+id/btn_switch"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/key_background"
            android:contentDescription="Switch keyboard"
            android:src="@android:drawable/ic_menu_sort_by_size"
            android:layout_margin="2dp" />

        <!-- Backspace -->
        <ImageButton
            android:id="@+id/btn_backspace"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/key_background"
            android:contentDescription="Backspace"
            android:src="@android:drawable/ic_delete"
            android:layout_margin="2dp" />

        <!-- Mic (larger) -->
        <ImageButton
            android:id="@+id/btn_mic"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="2"
            android:background="@color/mic_idle"
            android:contentDescription="Record"
            android:src="@android:drawable/ic_btn_speak_now"
            android:layout_margin="2dp" />

        <!-- Enter -->
        <ImageButton
            android:id="@+id/btn_enter"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/key_background"
            android:contentDescription="Enter"
            android:src="@android:drawable/ic_menu_send"
            android:layout_margin="2dp" />

        <!-- Settings -->
        <ImageButton
            android:id="@+id/btn_settings"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/key_background"
            android:contentDescription="Settings"
            android:src="@android:drawable/ic_menu_preferences"
            android:layout_margin="2dp" />
    </LinearLayout>
</LinearLayout>
```

**Step 2: Create `VoxInkIME.kt`**

```kotlin
package com.voxink.app.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.ImageButton
import com.voxink.app.R
import com.voxink.app.ui.MainActivity
import timber.log.Timber

class VoxInkIME : InputMethodService() {

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        setupKeys(view)
        Timber.d("VoxInkIME input view created")
        return view
    }

    private fun setupKeys(view: View) {
        view.findViewById<ImageButton>(R.id.btn_backspace)?.setOnClickListener {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
        }

        view.findViewById<ImageButton>(R.id.btn_enter)?.setOnClickListener {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
        }

        view.findViewById<ImageButton>(R.id.btn_switch)?.setOnClickListener {
            switchToPreviousInputMethod()
        }

        view.findViewById<ImageButton>(R.id.btn_mic)?.setOnClickListener {
            // TODO: Phase 1 — audio recording
            Timber.d("Mic button tapped")
        }

        view.findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private fun switchToPreviousInputMethod() {
        switchToPreviousInputMethod(currentInputBinding?.connectionToken)
            .also { success ->
                if (!success) {
                    Timber.w("Failed to switch to previous input method")
                }
            }
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxink/app/ime/VoxInkIME.kt \
  app/src/main/res/layout/keyboard_view.xml
git commit -m "feat: add blank VoxInkIME service with minimal keyboard layout"
```

---

## Task 9: Verify Build

**Step 1: Run full build**

```bash
cd /home/scipio/projects/voxink-android
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

**Step 2: If build fails, fix errors iteratively**

Common issues to watch for:
- Missing Android SDK: set `ANDROID_HOME` in `local.properties`
- Hilt requires Java 17: already set in `build.gradle.kts`
- KSP version mismatch: align `ksp` version with Kotlin version in `libs.versions.toml`
- Missing launcher icon: ensure at least one `ic_launcher` resource exists

**Step 3: Run lint**

```bash
./gradlew lintDebug
```

Expected: No critical errors. Warnings are OK for Phase 0.

**Step 4: Commit (if any fixes were needed)**

```bash
git add -A
git commit -m "fix: resolve build issues from initial scaffold"
```

---

## Task 10: Test Infrastructure — Smoke Test

**Files:**
- Create: `app/src/test/java/com/voxink/app/SmokeTest.kt`

**Step 1: Write a trivial smoke test**

This confirms JUnit 5 + MockK pipeline works.

```kotlin
package com.voxink.app

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SmokeTest {

    @Test
    fun `should verify test infrastructure works`() {
        assertThat(true).isTrue()
    }

    @Test
    fun `should confirm app package exists`() {
        val className = VoxInkApplication::class.qualifiedName
        assertThat(className).isEqualTo("com.voxink.app.VoxInkApplication")
    }
}
```

**Step 2: Run tests**

```bash
./gradlew test
```

Expected: `2 tests passed`

**Step 3: Commit**

```bash
git add app/src/test/
git commit -m "test: add smoke tests for JUnit 5 + Truth pipeline"
```

---

## Task 11: Code Quality — ktlint, detekt, .editorconfig

**Files:**
- Create: `.editorconfig`
- Create: `config/detekt/detekt.yml`
- Modify: `app/build.gradle.kts` (add detekt configuration block)

**Step 1: Create `.editorconfig`**

```editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
indent_size = 4
indent_style = space
insert_final_newline = true
trim_trailing_whitespace = true

[*.{kt,kts}]
max_line_length = 120

[*.{xml,json,toml,yml,yaml}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
```

**Step 2: Create `config/detekt/detekt.yml`**

```yaml
build:
  maxIssues: 10

complexity:
  LongMethod:
    threshold: 60
  LongParameterList:
    functionThreshold: 8
    constructorThreshold: 10
  TooManyFunctions:
    thresholdInClasses: 20
    thresholdInFiles: 25

style:
  MaxLineLength:
    maxLineLength: 120
  WildcardImport:
    active: true
  MagicNumber:
    active: false
  ReturnCount:
    max: 4

naming:
  FunctionNaming:
    functionPattern: '[a-zA-Z][a-zA-Z0-9]*|`.*`'
```

> **Note**: The `FunctionNaming` pattern allows backtick test names like `` `should do something`() ``.

**Step 3: Add detekt config to `app/build.gradle.kts`**

Append after the existing plugins block area:

```kotlin
detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}
```

> Actually — apply the `detekt` plugin at the app level too. Add to the app `build.gradle.kts` plugins block:
```kotlin
alias(libs.plugins.detekt)
```

**Step 4: Run ktlint + detekt**

```bash
./gradlew ktlintCheck detekt
```

Expected: PASS (fix any formatting issues from scaffolded code).

**Step 5: Auto-format if needed**

```bash
./gradlew ktlintFormat
```

**Step 6: Commit**

```bash
git add .editorconfig config/ app/build.gradle.kts
git commit -m "chore: add ktlint, detekt, .editorconfig for code quality"
```

---

## Task 12: CI — GitHub Actions

**Files:**
- Create: `.github/workflows/ci.yml`

**Step 1: Create CI workflow**

```yaml
name: CI

on:
  push:
    branches: [main, dev]
  pull_request:
    branches: [main, dev]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run ktlint
        run: ./gradlew ktlintCheck

      - name: Run detekt
        run: ./gradlew detekt

      - name: Run unit tests
        run: ./gradlew test

      - name: Build debug APK
        run: ./gradlew assembleDebug

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

**Step 2: Commit**

```bash
git add .github/
git commit -m "ci: add GitHub Actions workflow for lint, test, build"
```

---

## Task 13: Final Verification & Tag

**Step 1: Run full pipeline locally**

```bash
./gradlew clean ktlintCheck detekt test assembleDebug
```

Expected: All 4 tasks pass, `BUILD SUCCESSFUL`.

**Step 2: Review git log**

```bash
git log --oneline
```

Expected: Clean commit history with conventional commit messages.

**Step 3: Merge to main and tag**

```bash
git checkout main
git merge dev --no-ff -m "feat: Phase 0 — project skeleton with blank IME"
git tag v0.0.1 -m "Phase 0 complete: buildable project skeleton"
git checkout dev
```

---

## Summary: Phase 0 File Tree

After all tasks complete, the project should look like:

```
voxink-android/
├── .editorconfig
├── .gitignore
├── .github/workflows/ci.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── config/detekt/detekt.yml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/voxink/app/
│       │   │   ├── VoxInkApplication.kt
│       │   │   ├── ime/
│       │   │   │   └── VoxInkIME.kt
│       │   │   └── ui/
│       │   │       ├── MainActivity.kt
│       │   │       └── theme/
│       │   │           ├── Color.kt
│       │   │           ├── Type.kt
│       │   │           └── Theme.kt
│       │   └── res/
│       │       ├── layout/keyboard_view.xml
│       │       ├── mipmap-anydpi-v26/ic_launcher.xml
│       │       ├── values/
│       │       │   ├── strings.xml
│       │       │   ├── colors.xml
│       │       │   ├── themes.xml
│       │       │   └── ic_launcher_background.xml
│       │       ├── values-zh-rTW/strings.xml
│       │       └── xml/method.xml
│       └── test/
│           └── java/com/voxink/app/SmokeTest.kt
├── CLAUDE.md
├── plan.md
└── docs/plans/
    └── 2026-02-23-phase-0-project-setup.md
```

## Acceptance Criteria

- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew test` passes (2 smoke tests)
- [ ] `./gradlew ktlintCheck detekt` passes
- [ ] APK installs on device/emulator
- [ ] VoxInk keyboard appears in system Settings > Languages > Keyboard
- [ ] Activating VoxInk shows the minimal key row (mic, backspace, enter, switch, settings)
- [ ] Backspace and Enter keys send correct key events
- [ ] Switch button returns to previous keyboard
- [ ] Settings button opens MainActivity
- [ ] App displays correct strings in English and Traditional Chinese locales

# VoxInk ProGuard Rules

# ── IME Service ──
-keep class com.voxink.app.ime.VoxInkIME { *; }

# ── Hilt ──
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Retrofit ──
-dontwarn retrofit2.**
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepattributes Signature
-keepattributes Exceptions

# ── OkHttp ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── kotlinx-serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.voxink.app.**$$serializer { *; }
-keepclassmembers class com.voxink.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.voxink.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Timber (strip logs in release) ──
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ── DataStore ──
-keep class androidx.datastore.** { *; }

# ── Google Play Billing ──
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# ── AdMob / Google Mobile Ads ──
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.gms.ads.**

# ── General ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

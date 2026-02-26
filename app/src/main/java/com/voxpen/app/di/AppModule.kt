package com.voxpen.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.voxpen.app.billing.BillingManager
import com.voxpen.app.billing.LicenseManager
import com.voxpen.app.billing.ProStatusResolver
import com.voxpen.app.data.local.AppDatabase
import com.voxpen.app.data.local.DictionaryDao
import com.voxpen.app.data.local.TranscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "voxink_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "voxink.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideTranscriptionDao(database: AppDatabase): TranscriptionDao = database.transcriptionDao()

    @Provides
    fun provideDictionaryDao(database: AppDatabase): DictionaryDao = database.dictionaryDao()

    @Provides
    @Singleton
    @Named("licenseInstanceName")
    fun provideLicenseInstanceName(
        @ApplicationContext context: Context,
    ): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        )
        return "android-$androidId"
    }

    @Provides
    @Singleton
    @Named("ioDispatcher")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideProStatusResolver(
        billingManager: BillingManager,
        licenseManager: LicenseManager,
    ): ProStatusResolver {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        return ProStatusResolver(
            billingStatusFlow = billingManager.proStatus,
            licenseStatusFlow = licenseManager.proStatus,
            scope = scope,
        )
    }
}

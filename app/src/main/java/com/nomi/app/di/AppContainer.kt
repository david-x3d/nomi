package com.nomi.app.di

import android.content.Context
import com.nomi.app.BuildConfig
import com.nomi.app.data.backup.NomiBackupService
import com.nomi.app.data.local.NomiDatabase
import com.nomi.app.data.preferences.DataStoreAppPreferencesStore
import com.nomi.app.data.remote.ai.OpenAiCompatibleClient
import com.nomi.app.data.remote.openfoodfacts.OpenFoodFactsClient
import com.nomi.app.data.repository.NomiRepository
import com.nomi.app.data.security.AndroidKeystoreSecureSecretStore
import com.nomi.app.data.security.SecureSecretStore
import com.nomi.app.integration.health.HealthConnectManager
import com.nomi.app.integration.notifications.ReminderScheduler

/** Small process-wide dependency graph. Nomi keeps construction explicit and testable. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: NomiDatabase by lazy { NomiDatabase.getInstance(appContext) }
    val preferencesStore by lazy { DataStoreAppPreferencesStore(appContext) }
    val repository: NomiRepository by lazy { NomiRepository(database, preferencesStore) }
    val secretStore: SecureSecretStore by lazy { AndroidKeystoreSecureSecretStore(appContext) }
    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(appContext) }
    val openAiClient: OpenAiCompatibleClient by lazy { OpenAiCompatibleClient() }
    val openFoodFacts: OpenFoodFactsClient by lazy { OpenFoodFactsClient() }
    val backupService: NomiBackupService by lazy { NomiBackupService(database, preferencesStore, BuildConfig.VERSION_NAME) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }
}

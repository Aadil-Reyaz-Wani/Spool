package com.aadil.spool.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aadil.spool.core.model.currentTimeMillis
import com.aadil.spool.core.model.preferences.NotificationPrefs
import com.aadil.spool.core.model.preferences.SpoolAlertConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

interface NotificationPreferencesRepository {
    val prefsFlow: Flow<NotificationPrefs>
    suspend fun loadPrefs(): NotificationPrefs
    suspend fun updatePrefs(transform: (NotificationPrefs) -> NotificationPrefs)
    fun spoolConfigs(): Flow<Map<Int, SpoolAlertConfig>>
    suspend fun loadSpoolConfig(spoolId: Int): SpoolAlertConfig
    suspend fun saveSpoolConfig(config: SpoolAlertConfig)

    /** Records that a notification was shown: bumps daily counter and per-spool cooldown stamps. */
    suspend fun markNotified(
        spoolIds: List<Int>,
        nowMillis: Long = currentTimeMillis(),
        nowEpochDay: Long,
        slots: Int = spoolIds.size,
    ) {
        updatePrefs { prefs ->
            if (prefs.todayEpochDay == nowEpochDay) {
                prefs.copy(todayCount = prefs.todayCount + slots)
            } else {
                prefs.copy(todayCount = slots, todayEpochDay = nowEpochDay)
            }
        }
        for (id in spoolIds) {
            val config = loadSpoolConfig(id)
            saveSpoolConfig(config.copy(lastNotifiedAt = nowMillis, alertedWhileLow = true))
        }
    }
}

// ponytail: configs as one JSON string per spool in DataStore; move to Room columns if
// alerts ever need querying/sorting by threshold.
class DefaultNotificationPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : NotificationPreferencesRepository {

    private object Keys {
        val PREFS_JSON = stringPreferencesKey("alert_prefs_json")
        fun config(spoolId: Int) = stringPreferencesKey("alert_config_$spoolId")
    }

    private val json = Json { ignoreUnknownKeys = true }

    override val prefsFlow: Flow<NotificationPrefs> = dataStore.data.map { prefs ->
        prefs[Keys.PREFS_JSON]?.let { decodePrefs(it) } ?: NotificationPrefs()
    }

    override suspend fun loadPrefs(): NotificationPrefs = prefsFlow.first()

    override suspend fun updatePrefs(transform: (NotificationPrefs) -> NotificationPrefs) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PREFS_JSON]?.let { decodePrefs(it) } ?: NotificationPrefs()
            prefs[Keys.PREFS_JSON] = json.encodeToString(transform(current))
        }
    }

    override fun spoolConfigs(): Flow<Map<Int, SpoolAlertConfig>> = dataStore.data.map { prefs ->
        prefs.asMap().keys.filter { it.name.startsWith("alert_config_") }.mapNotNull { key ->
            val spoolId = key.name.removePrefix("alert_config_").toIntOrNull() ?: return@mapNotNull null
            val value = (prefs[key] as? String)?.let { runCatching { json.decodeFromString<SpoolAlertConfig>(it) }.getOrNull() }
                ?: return@mapNotNull null
            spoolId to value
        }.toMap()
    }

    override suspend fun loadSpoolConfig(spoolId: Int): SpoolAlertConfig {
        val raw = dataStore.data.first()[Keys.config(spoolId)] ?: return SpoolAlertConfig(spoolId)
        return runCatching { json.decodeFromString<SpoolAlertConfig>(raw) }.getOrDefault(SpoolAlertConfig(spoolId))
    }

    override suspend fun saveSpoolConfig(config: SpoolAlertConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.config(config.spoolId)] = json.encodeToString(config)
        }
    }

    private fun decodePrefs(raw: String): NotificationPrefs =
        runCatching { json.decodeFromString<NotificationPrefs>(raw) }.getOrDefault(NotificationPrefs())
}

package com.aadil.spool.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aadil.spool.core.model.defaultCurrencyCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CurrencyPreferencesRepository {
    val currencyCodeFlow: Flow<String>
    suspend fun updateCurrencyCode(newCode: String)
}

class DefaultCurrencyPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : CurrencyPreferencesRepository {
    private object PreferencesKeys {
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
    }

    override val currencyCodeFlow: Flow<String> = dataStore.data.map { preferences ->
        if (preferences[PreferencesKeys.CURRENCY_CODE] == "Set Currency") {
            defaultCurrencyCode()
        } else {
            preferences[PreferencesKeys.CURRENCY_CODE] ?: defaultCurrencyCode()
        }
    }

    override suspend fun updateCurrencyCode(newCode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENCY_CODE] = newCode
        }
    }
}

package com.aadil.spool.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CurrencyPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
    }

    val currencyCodeFlow: Flow<String> = dataStore.data.map { preferences ->
        if (preferences[PreferencesKeys.CURRENCY_CODE] == "Set Currency") {
            java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode
        }else {
            preferences[PreferencesKeys.CURRENCY_CODE] ?: java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode
        }
    }

    suspend fun updateCurrencyCode(newCode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENCY_CODE] = newCode
        }
    }
}
package com.bee.thaiwrite.system

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SettingsState(
    val onboardingComplete: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
)

class AppSettings(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("thaiwrite.preferences_pb")
    }

    val settings: Flow<SettingsState> = dataStore.data.map { prefs ->
        SettingsState(
            onboardingComplete = prefs[ONBOARDING_COMPLETE] ?: false,
            reminderHour = prefs[REMINDER_HOUR] ?: 19,
            reminderMinute = prefs[REMINDER_MINUTE] ?: 0,
        )
    }

    suspend fun updateOnboardingComplete(complete: Boolean) {
        dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETE] = complete
        }
    }

    suspend fun updateReminder(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[REMINDER_HOUR] = hour
            prefs[REMINDER_MINUTE] = minute
        }
    }

    companion object {
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        private val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }
}

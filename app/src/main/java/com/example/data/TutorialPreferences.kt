package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.adVmDataStore: DataStore<Preferences> by preferencesDataStore(name = "advm_preferences")

class TutorialPreferences(private val context: Context) {
    private val hasSeenTutorialKey = booleanPreferencesKey("has_seen_tutorial")

    val hasSeenTutorial: Flow<Boolean> = context.adVmDataStore.data.map { prefs ->
        prefs[hasSeenTutorialKey] ?: false
    }

    suspend fun setTutorialCompleted(completed: Boolean) {
        context.adVmDataStore.edit { prefs ->
            prefs[hasSeenTutorialKey] = completed
        }
    }
}

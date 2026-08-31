package com.example.vplayer

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "vplayer_prefs")

class PlaybackDataStore(private val context: Context) {

    private val favoritesKey = stringSetPreferencesKey("favorites")

    val favoriteIds: Flow<Set<String>> = context.dataStore.data.map {
        it[favoritesKey] ?: emptySet()
    }

    suspend fun toggleFavorite(videoId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: emptySet()
            prefs[favoritesKey] = if (videoId.toString() in current)
                current - videoId.toString()
            else
                current + videoId.toString()
        }
    }

    // Resume position: one key per video, storing "positionMs"
    suspend fun savePosition(videoId: Long, positionMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[longPreferencesKey("pos_$videoId")] = positionMs
        }
    }

    suspend fun getPosition(videoId: Long): Long {
        val prefs = context.dataStore.data.map { it[longPreferencesKey("pos_$videoId")] ?: 0L }
        var result = 0L
        prefs.collect { result = it; return@collect }
        return result
    }

    // Recently played: ordered list of video IDs, most recent first, capped at 20
    private val historyKey = stringPreferencesKey("history")

    suspend fun addToHistory(videoId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[historyKey]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(videoId.toString()) + current.filter { it != videoId.toString() }).take(20)
            prefs[historyKey] = updated.joinToString(",")
        }
    }

    val historyIds: Flow<List<Long>> = context.dataStore.data.map { prefs ->
        prefs[historyKey]?.split(",")?.filter { it.isNotBlank() }?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    }
}
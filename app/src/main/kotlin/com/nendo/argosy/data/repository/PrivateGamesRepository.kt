package com.nendo.argosy.data.repository

import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The IGDB ids of games the user marked private. Private games stay out of play stats and
 * monthly recaps.
 */
@Singleton
class PrivateGamesRepository @Inject constructor(
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "PrivateGamesRepository")

    val privateIgdbIds: StateFlow<Set<Int>> = syncPreferencesRepository.hiddenGameIds()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    suspend fun toggle(igdbGameId: Int) {
        val current = syncPreferencesRepository.hiddenGameIds().first()
        val updated = if (igdbGameId in current) current - igdbGameId else current + igdbGameId
        syncPreferencesRepository.setHiddenGameIds(updated)
    }
}

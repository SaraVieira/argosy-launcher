package com.nendo.argosy.domain.usecase.achievement

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "VerifyRAGameIdUseCase"

class VerifyRAGameIdUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val raRepository: RetroAchievementsRepository
) {
    suspend operator fun invoke(gameId: Long, forceRehash: Boolean = false): Long? =
        withContext(Dispatchers.IO) { verify(gameId, forceRehash) }

    private suspend fun verify(gameId: Long, forceRehash: Boolean): Long? {
        val game = gameDao.getById(gameId) ?: return null

        if (game.raIdVerified && !forceRehash) {
            return game.effectiveRaId
        }

        val hash = game.romHash
        if (hash == null) {
            gameDao.updateVerifiedRaId(gameId, game.raId)
            return game.raId
        }

        val resolvedId = try {
            raRepository.resolveGameId(hash)
        } catch (e: Exception) {
            Logger.warn(TAG, "Network error resolving game ID for hash $hash: ${e.message}")
            return game.effectiveRaId ?: game.raId
        }

        if (resolvedId != null) {
            gameDao.updateVerifiedRaId(gameId, resolvedId)
            if (resolvedId != game.raId) {
                Logger.info(TAG, "Verified RA ID differs from RomM: verified=$resolvedId, romm=${game.raId} (game $gameId)")
            }
            return resolvedId
        }

        Logger.warn(TAG, "RA hash lookup returned no match for game $gameId (hash=$hash); not persisting verified=null, falling back to romm raId=${game.raId}")
        return game.raId
    }
}

package com.nendo.argosy.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.util.LruCache
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.remote.romm.PhysicalMediaImage
import com.nendo.argosy.data.remote.romm.RomMApiClient
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.physicalMediaImages
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class PhysicalMediaArtRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val games: GameDao,
    private val api: RomMApiClient,
    private val imageCache: ImageCacheManager,
    private val launchBox: LaunchBoxPhysicalMediaIndex,
    private val pico8: Pico8CartArtwork
) {
    private val locks = Array(4) { Mutex() }
    private val retryAfter = LruCache<String, Long>(512)

    suspend fun artwork(gameId: Long, model: String): String? = withContext(Dispatchers.IO) {
        locks[gameId.mod(locks.size)].withLock {
            val game = games.getById(gameId) ?: return@withLock null
            val platform = PlatformDefinitions.getCanonicalSlug(game.platformSlug)
            if (model == "pico8") return@withLock pico8.artwork(game, imageCache.getCurrentCachePath())
            if (!isPhysicalScanModel(model)) return@withLock null
            val nameKey = (game.rommFileName ?: game.title).hashCode().toUInt().toString(16)
            val folder = when (model) {
                "gba" -> "gba/cartridge-labels"
                "msx" -> "physical-media/msx/wrapped-labels"
                "gbc" -> "physical-media/gbc/fronts"
                else -> "physical-media/$model"
            }
            val cache = File(imageCache.getCurrentCachePath(), folder)
            val file = File(cache, "${game.id}-${game.rommId ?: 0}-$nameKey.png")
            if (file.isFile && hasImageBounds(file)) return@withLock file.absolutePath
            val key = file.absolutePath
            if (SystemClock.elapsedRealtime() < (retryAfter.get(key) ?: 0L)) return@withLock null
            val rom = game.rommId?.let { api.getRom(it) }
            currentCoroutineContext().ensureActive()
            val indexed = launchBox.lookup(platform, model, game.title)
            val candidates = api.physicalMediaImages((rom as? RomMResult.Success)?.data, model, indexed)
            for (candidate in candidates) {
                if (cacheCandidate(candidate, model, file)) return@withLock key
            }
            val unavailable = candidates.isEmpty() && rom !is RomMResult.Error
            val wait = if (unavailable) MISSING_RETRY_MS else NETWORK_RETRY_MS
            retryAfter.put(key, SystemClock.elapsedRealtime() + wait)
            null
        }
    }

    private suspend fun cacheCandidate(image: PhysicalMediaImage, model: String, file: File): Boolean {
        val label = fetchLabel(image, model) ?: return false
        return try {
            writeLabel(file, label)
        } finally {
            label.recycle()
        }
    }

    private suspend fun fetchLabel(image: PhysicalMediaImage, model: String): Bitmap? {
        val request = ImageRequest.Builder(context).data(image.url)
            .size(960, 960)
            .scale(Scale.FIT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
        return cropPhysicalMedia(bitmap, model, image.region)
    }

    private fun writeLabel(file: File, bitmap: Bitmap): Boolean {
        val directory = file.parentFile ?: return false
        var temp: File? = null
        return try {
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) return false
            val pending = File.createTempFile("label-", ".tmp", directory)
            temp = pending
            pending.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } && pending.renameTo(file)
        } catch (_: IOException) {
            false
        } finally {
            temp?.delete()
        }
    }

    private fun hasImageBounds(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private companion object {
        const val NETWORK_RETRY_MS = 60_000L
        const val MISSING_RETRY_MS = 6 * 60 * 60_000L
    }
}

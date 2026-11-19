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
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.remote.romm.RomMApiClient
import com.nendo.argosy.data.remote.romm.RomMResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ArcadeCabinetArtRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val games: GameDao,
    private val screenshots: GameScreenshotDao,
    private val api: RomMApiClient,
    private val cache: ImageCacheManager,
    private val index: ArcadeCabinetArtIndex
) {
    private val locks = Array(8) { Mutex() }
    private data class Retry(val after: Long, val screenPath: String?)

    private val retry = LruCache<String, Retry>(384)

    suspend fun artwork(gameId: Long): ArcadeCabinetArt = withContext(Dispatchers.IO) {
        locks[gameId.mod(locks.size)].withLock {
            val game = games.getById(gameId) ?: return@withLock ArcadeCabinetArt()
            val platform = PlatformDefinitions.getCanonicalSlug(game.platformSlug)
            if (platform !in platforms) return@withLock ArcadeCabinetArt()
            val signature = listOf(game.rommId, game.launchboxId, game.rommFileName, game.title)
                .hashCode().toUInt().toString(16)
            val folder = File(cache.getCurrentCachePath(), "arcade/cabinets/$gameId-$signature")
            fun cached(slot: String) = File(folder, "$slot.png").validPath()
            val existing = ArcadeCabinetArt(cached("marquee"), cached("screen"), cached("side"))
            if (existing.marquee != null && existing.screenshot != null && existing.sideArt != null) {
                return@withLock existing
            }
            val identity = folder.absolutePath
            val official = screenshots.getForGame(gameId)
            val personal = screenshots.getGalleryForGame(gameId)
            val localShots = (personal + official).mapNotNull { shot ->
                shot.cachedPath?.let { File(it).validPath() }?.let { shot.sourceUrl to it }
            }.toMap()
            val catalog = index.lookup(game)
            val localScreen = existing.screenshot ?: localShots[catalog.screenshot]
                ?: personal.firstNotNullOfOrNull { localShots[it.sourceUrl] }
            val previous = retry.get(identity)
            if (previous != null && SystemClock.elapsedRealtime() < previous.after) {
                return@withLock existing.copy(screenshot = localScreen ?: previous.screenPath?.let { File(it).validPath() })
            }
            // LaunchBox has no cabinet side artwork category. A complete indexed marquee/screen
            // pair and the locally indexed side panel can render without a RomM round trip.
            val initial = coroutineScope {
                val marquee = async { existing.marquee ?: fetch(folder, "marquee", listOfNotNull(catalog.marquee)) }
                val screen = async { localScreen ?: fetch(folder, "screen", listOfNotNull(catalog.screenshot)) }
                val side = async { existing.sideArt ?: fetch(folder, "side", listOfNotNull(catalog.sideArt)) }
                ArcadeCabinetArt(marquee.await(), screen.await(), side.await())
            }
            if (initial.marquee != null && initial.screenshot != null && (catalog.sideArt == null || initial.sideArt != null)) {
                return@withLock initial
            }
            val rom = game.rommId?.let { (api.getRom(it) as? RomMResult.Success)?.data }
            currentCoroutineContext().ensureActive()
            val entries = rom?.launchboxMetadata?.images.orEmpty()
            fun urls(type: String) = entries.filter { it.type.equals(type, ignoreCase = true) }.map { it.url }
            val otherScreens = entries.filter {
                it.type.contains("Screenshot", ignoreCase = true) && !it.type.equals("Screenshot - Gameplay", true)
            }.map { it.url }.toSet()
            val screenSources = (urls("Screenshot - Gameplay") + listOfNotNull(catalog.screenshot) +
                rom?.screenshotPaths.orEmpty().mapNotNull(api::buildMediaUrl) + official.map { it.sourceUrl })
                .distinct().filterNot(otherScreens::contains)
            val result = coroutineScope {
                val marquee = async { initial.marquee ?: fetch(folder, "marquee", urls("Arcade - Marquee").filterNot { it == catalog.marquee }) }
                val screen = async {
                    initial.screenshot ?: screenSources.firstNotNullOfOrNull(localShots::get)
                        ?: fetch(folder, "screen", screenSources.filterNot { it == catalog.screenshot })
                }
                val side = async { initial.sideArt ?: fetch(folder, "side", urls("Arcade - Side Art")) }
                ArcadeCabinetArt(marquee.await(), screen.await(), side.await())
            }
            if (result.marquee == null || result.screenshot == null || result.sideArt == null) {
                val delay = if (rom == null) 60_000L else 10 * 60_000L
                retry.put(identity, Retry(SystemClock.elapsedRealtime() + delay, result.screenshot))
            }
            result
        }
    }

    private suspend fun fetch(folder: File, slot: String, urls: List<String>): String? {
        val file = File(folder, "$slot.png")
        for (url in urls.distinct()) {
            val request = ImageRequest.Builder(context).data(url).size(800, 800).scale(Scale.FIT)
                .allowHardware(false).memoryCachePolicy(CachePolicy.DISABLED).build()
            val success = context.imageLoader.execute(request) as? SuccessResult ?: continue
            val bitmap = (success.drawable as? BitmapDrawable)?.bitmap ?: continue
            val saved = try {
                if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) return null
                val pending = File.createTempFile(slot, ".tmp", folder)
                try {
                    pending.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } &&
                        pending.renameTo(file)
                } finally {
                    pending.delete()
                }
            } catch (_: IOException) {
                false
            }
            if (saved) return file.absolutePath
        }
        return null
    }

    private fun File.validPath(): String? {
        if (!isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(absolutePath, bounds)
        return absolutePath.takeIf { bounds.outWidth > 0 && bounds.outHeight > 0 }
    }

    private companion object {
        val platforms = setOf("arcade", "cps1", "cps2", "cps3")
    }
}

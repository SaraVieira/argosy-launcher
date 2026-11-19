package com.nendo.argosy.data.repository

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.remote.romm.DownloadResponse
import com.nendo.argosy.data.remote.romm.RomMApiClient
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.scanner.Pico8CartFiles
import com.nendo.argosy.data.storage.FileAccessLayer
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Pico8CartArtwork @Inject constructor(
    private val files: FileAccessLayer,
    private val api: RomMApiClient
) {
    private val retryAfter = LruCache<String, Long>(256)

    internal suspend fun artwork(game: GameEntity, cacheRoot: String): String? {
        val identity = listOf(game.rommFileName, game.sha1Hash, game.md5Hash, game.crcHash, game.title)
            .hashCode().toUInt().toString(16)
        val cache = File(cacheRoot, "pico8/cartridges/${game.id}-${game.rommId ?: 0}-$identity.png")
        val local = game.localPath?.let { path ->
            when {
                isPng(path) -> path
                files.isDirectory(path) -> {
                    val entries = files.listFiles(path).orEmpty().filter { it.isFile && isPng(it.name) }
                    chooseCart(entries, { it.name }, game)?.path
                }
                else -> null
            }
        }
        val stamp = local?.let(files::lastModified) ?: 0L
        val cached = cache.isFile && cache.length() in 1L..MAX_BYTES && readCart { cache.inputStream() } != null
        if (cached && (local == null || (stamp > 0 && cache.lastModified() == stamp))) return cache.absolutePath
        if (local != null) {
            val image = readCart { files.getInputStream(local) }
            if (image != null) {
                if (publish(cache, image)) {
                    if (stamp > 0) cache.setLastModified(stamp)
                    return cache.absolutePath
                }
                if (File(local).canRead()) return local
            }
        }
        if (cached) return cache.absolutePath
        val romId = game.rommId ?: return null
        val key = cache.absolutePath
        if (SystemClock.elapsedRealtime() < (retryAfter.get(key) ?: 0L)) return null
        val image = remoteCart(game, romId)
        if (image != null && publish(cache, image)) return key
        retryAfter.put(key, SystemClock.elapsedRealtime() + 60_000L)
        return null
    }

    private suspend fun remoteCart(game: GameEntity, romId: Long): ByteArray? {
        val name = game.rommFileName
        if (name != null && isPng(name)) {
            val image = download(api.downloadRom(romId, Uri.encode(name.substringAfterLast('/'))))
            if (image != null) return image
        }
        if (name?.endsWith(".p8", ignoreCase = true) == true) return null
        val rom = (api.getRom(romId) as? RomMResult.Success)?.data ?: return null
        val entries = rom.files.orEmpty().filter { isPng(it.fileName) }
        val selected = chooseCart(entries, { it.fileName }, game)
        if (selected != null) {
            return download(api.downloadRomFile(selected.id, Uri.encode(selected.fileName.substringAfterLast('/'))))
        }
        val actual = rom.fileName?.takeIf { it != name && isPng(it) } ?: return null
        return download(api.downloadRom(romId, Uri.encode(actual.substringAfterLast('/'))))
    }

    private fun <T> chooseCart(entries: List<T>, name: (T) -> String, game: GameEntity): T? {
        val namedCarts = entries.filter { name(it).endsWith(".p8.png", ignoreCase = true) }
        val candidates = namedCarts.ifEmpty { entries }
        return candidates.firstOrNull { name(it).equals(game.rommFileName, ignoreCase = true) }
            ?: candidates.singleOrNull { Pico8CartFiles.titleStem(name(it)).equals(game.title, ignoreCase = true) }
            ?: candidates.singleOrNull()
    }

    private fun download(result: RomMResult<DownloadResponse>): ByteArray? =
        (result as? RomMResult.Success)?.data?.body?.use { body ->
            if (body.contentLength() > MAX_BYTES) null else readCart { body.byteStream() }
        }

    private fun readCart(open: () -> InputStream?): ByteArray? = try {
        open()?.source()?.buffer()?.use { input ->
            if (!input.request(24)) return@use null
            val header = ByteBuffer.wrap(input.peek().readByteArray(24))
            if (header.long != PNG_MAGIC || header.int != 13 || header.int != IHDR ||
                header.int != 160 || header.int != 205 || input.request(MAX_BYTES + 1)) return@use null
            val bytes = input.readByteArray()
            if (bytes.size < 45 || ByteBuffer.wrap(bytes, bytes.size - 12, 8).long != IEND_CHUNK) return@use null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
            decoded.recycle()
            bytes
        }
    } catch (_: IOException) {
        null
    }

    private fun publish(file: File, image: ByteArray): Boolean {
        val dir = file.parentFile ?: return false
        var pending: File? = null
        return try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return false
            val temp = File.createTempFile("pico8-", ".tmp", dir)
            pending = temp
            temp.writeBytes(image)
            temp.renameTo(file)
        } catch (_: IOException) {
            false
        } finally {
            pending?.delete()
        }
    }

    private companion object {
        const val MAX_BYTES = 2 * 1024 * 1024L
        const val PNG_MAGIC = -8552249625308161526L
        const val IHDR = 0x49484452
        const val IEND_CHUNK = 0x49454e44L
        fun isPng(name: String) = name.endsWith(".png", ignoreCase = true)
    }
}

package com.nendo.argosy.data.repository

import android.content.Context
import android.util.LruCache
import com.nendo.argosy.data.remote.romm.PhysicalMediaImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchBoxPhysicalMediaIndex @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val platforms by lazy {
        context.assets.list(DIRECTORY).orEmpty().map { it.removeSuffix(".tsv") }.toSet()
    }
    private val indexes = LruCache<String, Map<String, PhysicalMediaImage>>(3)

    internal fun lookup(platform: String, model: String, title: String): PhysicalMediaImage? {
        val index = when {
            model == "floppy35" || model == "floppy525" -> "floppy-$platform"
            (model == "msx" || model == "cassette") && platform in setOf("msx", "msx2") -> "front-$platform"
            model == "cassette" -> "cassette-$platform"
            model == "famicom_cart" -> "famicom"
            platform == "snes" && model == "snes-pal" -> "snes-pal"
            platform == "dsi" -> "nds"
            else -> platform
        }
        if (index !in platforms) return null
        return read(index)[titleKey(title)]
    }

    @Synchronized
    private fun read(index: String): Map<String, PhysicalMediaImage> =
        indexes.get(index) ?: context.assets.open("$DIRECTORY/$index.tsv").bufferedReader()
            .useLines { lines ->
                lines.associate { line ->
                    val (title, region, filename) = line.split('\t', limit = 3)
                    title to PhysicalMediaImage("https://images.launchbox-app.com/$filename", region)
                }
            }.also { indexes.put(index, it) }

    private fun titleKey(title: String): String =
        Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(combiningMarks, "")
            .replace("&", " and ")
            .replace("'", "")
            .replace("’", "")
            .replace(separators, " ")
            .trim()

    private companion object {
        const val DIRECTORY = "physical-media-index"
        val combiningMarks = Regex("\\p{M}+")
        val separators = Regex("[^\\p{L}\\p{N}]+")
    }
}

package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.entity.GameEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ArcadeCabinetArt(
    val marquee: String? = null,
    val screenshot: String? = null,
    val sideArt: String? = null
)

@Singleton
class ArcadeCabinetArtIndex @Inject constructor(@ApplicationContext private val context: Context) {
    private val images by lazy {
        read("images") { fields ->
            fun launchBox(index: Int) = fields[index].takeIf { it.isNotEmpty() }
                ?.let { "https://images.launchbox-app.com/$it" }
            ArcadeCabinetArt(launchBox(1), launchBox(2), fields[3].ifEmpty { null })
        }
    }
    private val roms by lazy { read("roms") { it[1] } }
    private val titles by lazy { read("titles") { it[1] } }

    internal fun lookup(game: GameEntity): ArcadeCabinetArt {
        val shortname = (game.rommFileName ?: game.localPath)?.let { File(it).nameWithoutExtension }
        val id = game.launchboxId?.toString()
            ?: shortname?.let { roms[it.lowercase(Locale.ROOT)] }
            ?: titles[titleKey(game.title)]
        return images[id] ?: ArcadeCabinetArt()
    }

    private fun <T> read(name: String, value: (List<String>) -> T): Map<String, T> =
        context.assets.open("arcade-art/$name.tsv").bufferedReader().useLines { lines ->
            lines.associate { line -> line.split('\t').let { it[0] to value(it) } }
        }

    private fun titleKey(title: String): String =
        Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(marks, "").replace("&", " and ").replace("'", "").replace("’", "")
            .replace(separators, " ").trim()

    private companion object {
        val marks = Regex("\\p{M}+")
        val separators = Regex("[^\\p{L}\\p{N}]+")
    }
}

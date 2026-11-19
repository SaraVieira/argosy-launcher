package com.nendo.argosy.ui.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.ui.common.PhysicalMediaArtEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
internal fun ArcadeCabinet(gameId: Long?, title: String, frame: Int, carouselPreview: Boolean, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        EntryPointAccessors.fromApplication(context, PhysicalMediaArtEntryPoint::class.java).arcadeCabinetArtRepository()
    }
    var panels by remember(gameId, title) { mutableStateOf<CabinetPanels?>(null) }
    var image by remember(gameId, title) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(gameId, title, repository) {
        val fallback = withContext(Dispatchers.Default) { cabinetMarquee(title) }
        panels = CabinetPanels(fallback, null, null)
        val art = gameId?.let { repository.artwork(it) } ?: return@LaunchedEffect
        panels = withContext(Dispatchers.IO) {
            CabinetPanels(
                panelPixels(art.marquee, 2.58f) ?: fallback,
                panelPixels(art.screenshot, .835f),
                panelPixels(art.sideArt, .526f)
            )
        }
    }
    LaunchedEffect(frame, panels, context, carouselPreview) {
        val textures = panels ?: return@LaunchedEffect
        image = withContext(Dispatchers.IO) { CabinetSheets.render(context.assets, frame, textures, carouselPreview) }
    }
    image?.let { Image(remember(it) { it.asImageBitmap() }, title, modifier, contentScale = ContentScale.Fit) }
}

private const val UV_SIZE = 256
private const val CABINET_REST_FRAME = 57

private class CabinetPanels(val marquee: IntArray, val screen: IntArray?, val side: IntArray?)

private fun Bitmap.panelPixels() = IntArray(UV_SIZE * UV_SIZE).also {
    getPixels(it, 0, UV_SIZE, 0, 0, UV_SIZE, UV_SIZE)
}

private fun panelPixels(path: String?, aspect: Float): IntArray? {
    if (path == null) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val options = BitmapFactory.Options()
    while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > 1536) {
        options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
    }
    val art = BitmapFactory.decodeFile(path, options) ?: return null
    val target = Bitmap.createBitmap(UV_SIZE, UV_SIZE, Bitmap.Config.ARGB_8888)
    val scale = min(UV_SIZE * aspect / art.width, UV_SIZE.toFloat() / art.height)
    val w = art.width * scale / aspect
    val h = art.height * scale
    val rect = RectF((UV_SIZE - w) / 2, (UV_SIZE - h) / 2, (UV_SIZE + w) / 2, (UV_SIZE + h) / 2)
    Canvas(target).drawBitmap(art, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
    art.recycle()
    return target.panelPixels().also { target.recycle() }
}

private fun cabinetMarquee(title: String): IntArray {
    val target = Bitmap.createBitmap(UV_SIZE, UV_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(target)
    val width = (UV_SIZE * 2.58f).toInt()
    canvas.scale(UV_SIZE.toFloat() / width, 1f)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = UV_SIZE * .36f
    }
    val layout = StaticLayout.Builder.obtain(title, 0, title.length, paint, width - 36)
        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2)
        .setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
    canvas.translate(18f, (UV_SIZE - layout.height) / 2f)
    layout.draw(canvas)
    return target.panelPixels().also { target.recycle() }
}

private object CabinetSheets {
    private class Sheet(val shell: Bitmap, val coordinates: Bitmap)

    private val cache = object : LruCache<Int, Sheet>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Sheet) = value.shell.allocationByteCount + value.coordinates.allocationByteCount
    }

    @Synchronized
    private fun sheet(assets: AssetManager, group: Int): Sheet {
        cache.get(group)?.let { return it }
        val prefix = "physical-media/arcade/" + when (group) {
            -2 -> "carousel"
            -1 -> "front"
            else -> group.toString()
        }
        fun decode(suffix: String) = assets.open(prefix + suffix).use { requireNotNull(BitmapFactory.decodeStream(it)) }
        return Sheet(decode(".webp"), decode("-uv.png")).also { cache.put(group, it) }
    }

    fun render(assets: AssetManager, frame: Int, panels: CabinetPanels, carouselPreview: Boolean): Bitmap {
        val group = when {
            carouselPreview -> -2
            frame == CABINET_REST_FRAME -> -1
            else -> frame / 10
        }
        val sheet = sheet(assets, group)
        val cellWidth = if (group < 0) sheet.shell.width else sheet.shell.width / 5
        val height = if (group < 0) sheet.shell.height else sheet.shell.height / 2
        val width = if (carouselPreview) cellWidth else min(cellWidth, height)
        val x = (if (group < 0) 0 else frame % 5 * cellWidth) + (cellWidth - width) / 2
        val y = if (group < 0) 0 else frame % 10 / 5 * height
        val pixels = IntArray(width * height)
        val coords = IntArray(pixels.size)
        sheet.shell.getPixels(pixels, 0, width, x, y, width, height)
        sheet.coordinates.getPixels(coords, 0, width, x, y, width, height)
        for (i in pixels.indices) {
            val uv = coords[i]
            val texture = when (uv and 255) {
                1 -> panels.marquee
                2 -> panels.screen
                3 -> panels.side
                else -> null
            } ?: continue
            val color = texture[(uv and 0xff00) or ((uv ushr 16) and 255)]
            when (val alpha = Color.alpha(color)) {
                0 -> Unit
                255 -> pixels[i] = color
                else -> {
                    val base = pixels[i]
                    val keep = 255 - alpha
                    pixels[i] = Color.rgb(
                        (Color.red(color) * alpha + Color.red(base) * keep) / 255,
                        (Color.green(color) * alpha + Color.green(base) * keep) / 255,
                        (Color.blue(color) * alpha + Color.blue(base) * keep) / 255
                    )
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}

package com.nendo.argosy.ui.components

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import org.json.JSONArray
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.rememberPhysicalMediaLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val FRAME_WIDTH = 420
private const val FRAME_HEIGHT = 272
internal const val PHYSICAL_MEDIA_FRAME_ASPECT_RATIO = 1f * FRAME_WIDTH / FRAME_HEIGHT
private const val FRAME_COUNT = 60
private const val REST_FRAME = 57
private const val LABEL_WIDTH = 406
private val frameBounds = RectF(0f, 0f, FRAME_WIDTH.toFloat(), FRAME_HEIGHT.toFloat())
private val sheets = intArrayOf(
    R.drawable.gba_cartridge_angles_0, R.drawable.gba_cartridge_angles_1,
    R.drawable.gba_cartridge_angles_2, R.drawable.gba_cartridge_angles_3,
    R.drawable.gba_cartridge_angles_4, R.drawable.gba_cartridge_angles_5
)

@Composable
internal fun PhysicalMediaModel(
    model: String,
    artPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    carouselPreview: Boolean = false,
    onArtLoaded: ((Bitmap) -> Unit)? = null,
    onArtLoadFailed: (() -> Unit)? = null,
    rotationSteps: Int = 0,
    gameId: Long? = null
) {
    val context = LocalContext.current
    val artModel = rememberFileImageModel(artPath)
    val reportLoaded by rememberUpdatedState(onArtLoaded)
    val reportFailed by rememberUpdatedState(onArtLoadFailed)
    var art by remember(artModel) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(artModel) {
        if (artModel == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(artModel)
            .size(LABEL_WIDTH * 2, LABEL_WIDTH * 2)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult
        val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
        art = bitmap
        if (bitmap == null) reportFailed?.invoke() else reportLoaded?.invoke(bitmap)
    }

    if (model == "pico8") {
        Pico8Cartridge(rememberPhysicalMediaLabel(gameId, model), art, title, modifier, interactive, rotationSteps, gameId)
        return
    }

    var dragTurns by remember(model, gameId, title, artPath) { mutableFloatStateOf(0f) }
    val frame = (REST_FRAME + (dragTurns * FRAME_COUNT).roundToInt() + rotationSteps.mod(20) * 3).mod(FRAME_COUNT)
    val transform = if (interactive) {
        val sway = rememberInfiniteTransition(label = "cartridge sway").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3300), RepeatMode.Reverse),
            label = "cartridge tilt"
        )
        Modifier.graphicsLayer {
            rotationY = sway.value * 4f
            rotationZ = sway.value * .7f
        }.pointerInput(title, artPath) {
            detectHorizontalDragGestures { change, distance ->
                change.consume()
                dragTurns = (dragTurns + distance / size.width.coerceAtLeast(1)) % 1f
            }
        }
    } else Modifier

    if (model == "arcade") {
        ArcadeCabinet(gameId, title, frame, carouselPreview, modifier.then(transform))
        return
    }
    val label = rememberPhysicalMediaLabel(gameId, model)

    val group = if (frame == REST_FRAME) -1 else frame / 10
    var sheet by remember(model) { mutableStateOf<MediaSheet?>(null) }
    LaunchedEffect(model, group, context) {
        sheet = withContext(Dispatchers.IO) { MediaSheet.load(context, model, group) }
    }
    val scanPlane = sheet?.frontPlane?.takeIf { label != null }
    val plane = scanPlane ?: sheet?.plane ?: gbaPlane
    val labelHeight = LABEL_WIDTH * plane.height / plane.width
    val labelBounds = remember(labelHeight) { RectF(0f, 0f, LABEL_WIDTH.toFloat(), labelHeight) }

    val labelColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val titleColor = MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
    val titleLayout = remember(title, titleColor, labelHeight) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = titleColor
            textSize = min(labelHeight / 5f, LABEL_WIDTH / 7f)
            typeface = Typeface.DEFAULT_BOLD
        }
        StaticLayout.Builder.obtain(title, 0, title.length, paint, (LABEL_WIDTH * .9f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }
    val source = remember(label, art, labelHeight) {
        if (label != null) Rect(0, 0, label.width, label.height) else art?.let { croppedLabel(it, labelHeight) }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val loaded = sheet
    val wrap = loaded?.wrap
    val printedWrap = remember(wrap, label, art, source, titleLayout, title, labelColor) {
        wrap?.print(label ?: art, source, title, titleLayout, labelColor)
    }
    val shownFrame = loaded?.nearestFrame(frame) ?: REST_FRAME
    val labelTransform = remember(shownFrame, plane) { plane.projection(shownFrame, labelHeight) }
    val sprite = remember(loaded, shownFrame) { loaded?.sprite(shownFrame) }

    val maskPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) } }
    Canvas(modifier.then(transform).semantics { contentDescription = title }) {
        if (loaded == null || sprite == null) return@Canvas
        val canvas = drawContext.canvas.nativeCanvas
        val scale = min(size.width / FRAME_WIDTH, size.height / FRAME_HEIGHT)
        canvas.save()
        canvas.translate((size.width - FRAME_WIDTH * scale) / 2, (size.height - FRAME_HEIGHT * scale) / 2)
        canvas.scale(scale, scale)
        val mask = if (scanPlane != null) loaded.bitmap else loaded.mask
        if (mask != null) canvas.drawBitmap(loaded.bitmap, sprite, frameBounds, paint)
        if (labelTransform != null || printedWrap != null) {
            if (mask != null) canvas.saveLayer(frameBounds, null)
            if (wrap != null && printedWrap != null) {
                wrap.draw(canvas, printedWrap, Math.toRadians(shownFrame * 360.0 / FRAME_COUNT), Math.toRadians(9.0),
                    frameBounds.centerX(), frameBounds.centerY(), paint)
            } else if (labelTransform != null) {
                canvas.drawLabel(labelTransform, label ?: art, source, labelBounds, titleLayout, labelColor, paint)
            }
            if (mask != null) {
                canvas.drawBitmap(mask, sprite, frameBounds, maskPaint)
                canvas.restore()
            }
        }
        if (mask == null) canvas.drawBitmap(loaded.bitmap, sprite, frameBounds, paint)
        canvas.restore()
    }
}

private data class LabelPlane(val left: Float, val top: Float, val width: Float, val height: Float, val depth: Float) {
    fun projection(frame: Int, labelHeight: Float): Matrix? {
        val yaw = Math.toRadians(frame * 360.0 / FRAME_COUNT)
        if (cos(yaw) < .001) return null
        val pitch = Math.toRadians(9.0)
        val x = FRAME_WIDTH / 2 + left * cos(yaw) - depth * sin(yaw)
        val y = FRAME_HEIGHT / 2 + left * sin(yaw) * sin(pitch) - top * cos(pitch) + depth * cos(yaw) * sin(pitch)
        return Matrix().apply {
            setValues(floatArrayOf(
                (width * cos(yaw) / LABEL_WIDTH).toFloat(), 0f, x.toFloat(),
                (width * sin(yaw) * sin(pitch) / LABEL_WIDTH).toFloat(), (height * cos(pitch) / labelHeight).toFloat(), y.toFloat(),
                0f, 0f, 1f
            ))
        }
    }
}

private const val GBA_SCALE = FRAME_WIDTH * .14f
private val gbaPlane = LabelPlane(-2.29f * GBA_SCALE, (3.055f - 2.0798f) * GBA_SCALE,
    4.58f * GBA_SCALE, 2.425f * GBA_SCALE, (.51574f - .30553f) * GBA_SCALE)

private class MediaSheet(
    val group: Int,
    val bitmap: Bitmap,
    val mask: Bitmap?,
    val plane: LabelPlane,
    val frontPlane: LabelPlane? = null,
    val wrap: WrappedCartridgeLabel? = null
) {
    fun nearestFrame(frame: Int): Int {
        if (group < 0) return REST_FRAME
        val first = group * 10
        if (frame in first until first + 10) return frame
        return if ((frame - first).mod(FRAME_COUNT) <= FRAME_COUNT / 2 + 4) first + 9 else first
    }

    fun sprite(frame: Int): Rect {
        if (group < 0) return Rect(0, 0, bitmap.width, bitmap.height)
        val left = frame % 5 * FRAME_WIDTH
        val top = frame % 10 / 5 * FRAME_HEIGHT
        return Rect(left, top, left + FRAME_WIDTH, top + FRAME_HEIGHT)
    }

    companion object {
        private val cache = object : LruCache<String, MediaSheet>(32 * 1024 * 1024) {
            override fun sizeOf(key: String, value: MediaSheet) =
                value.bitmap.allocationByteCount + (value.mask?.allocationByteCount ?: 0)
        }
        private val wraps = mutableMapOf<String, WrappedCartridgeLabel>()
        private val planes = mutableMapOf<String, LabelPlane>()

        @Synchronized
        fun load(context: Context, model: String, group: Int): MediaSheet {
            val key = "$model/$group"
            cache.get(key)?.let { return it }
            val result = if (model == "gba") {
                val resource = if (group < 0) R.drawable.gba_cartridge_front else sheets[group]
                MediaSheet(group, BitmapFactory.decodeResource(context.resources, resource), null, gbaPlane)
            } else {
                val dir = "physical-media/$model"
                val file = if (group < 0) "front" else group.toString()
                fun plane(name: String) = planes.getOrPut("$model/$name") {
                    val values = context.assets.open("$dir/$name.json").bufferedReader().use { JSONArray(it.readText()) }
                    fun value(index: Int) = values.getDouble(index).toFloat()
                    LabelPlane(value(0), value(1), value(2), value(3), value(4))
                }
                fun bitmap(path: String) = context.assets.open("$dir/$path").use { requireNotNull(BitmapFactory.decodeStream(it)) }
                MediaSheet(group, bitmap("$file.webp"), bitmap("$file-mask.png"), plane("label"),
                    if (model == "gbc") plane("face") else null,
                    if (model == "msx") wraps.getOrPut(model) { WrappedCartridgeLabel(context.assets, dir) } else null)
            }
            cache.put(key, result)
            return result
        }
    }
}

private fun croppedLabel(bitmap: Bitmap, labelHeight: Float): Rect {
    val scale = min(bitmap.width.toFloat() / LABEL_WIDTH, bitmap.height / labelHeight)
    val width = (LABEL_WIDTH * scale).roundToInt().coerceIn(1, bitmap.width)
    val height = (labelHeight * scale).roundToInt().coerceIn(1, bitmap.height)
    val left = (bitmap.width - width) / 2
    val top = (bitmap.height - height) / 2
    return Rect(left, top, left + width, top + height)
}

private fun NativeCanvas.drawLabel(
    transform: Matrix, bitmap: Bitmap?, source: Rect?, labelBounds: RectF, layout: StaticLayout, labelColor: Int, paint: Paint
) {
    save()
    concat(transform)
    if (bitmap != null && source != null) {
        paint.color = android.graphics.Color.WHITE
        drawBitmap(bitmap, source, labelBounds, paint)
    } else {
        drawRect(labelBounds, paint.apply { color = labelColor })
        translate((LABEL_WIDTH - layout.width) / 2f, (labelBounds.height() - layout.height) / 2f)
        layout.draw(this)
    }
    restore()
}

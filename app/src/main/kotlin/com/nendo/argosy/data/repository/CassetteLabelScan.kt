package com.nendo.argosy.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val SAMPLE_WIDTH = 200
private const val SAMPLE_HEIGHT = 128
private val spokes = List(18) { angle -> cos(angle * PI / 9) to sin(angle * PI / 9) }

internal fun cropCassetteLabel(scan: Bitmap, bounds: Rect): Bitmap? {
    val width = bounds.width()
    val height = bounds.height()
    if (width.toFloat() / height !in 1.44f..1.72f || width < 100) return null
    val sample = Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888)
    Canvas(sample).drawBitmap(scan, bounds, Rect(0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT), Paint(Paint.FILTER_BITMAP_FLAG))
    val grid = IntArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
    sample.getPixels(grid, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
    sample.recycle()
    val center = cassetteReels(grid) ?: return null
    val inverted = center > .51
    if (inverted) grid.reverse()
    val label = cassetteSticker(grid)
    val left = bounds.left + (label.left * width / SAMPLE_WIDTH)
    val top = bounds.top + (label.top * height / SAMPLE_HEIGHT)
    val stickerWidth = label.width() * width / SAMPLE_WIDTH
    val stickerHeight = label.height() * height / SAMPLE_HEIGHT
    val pixels = IntArray(stickerWidth * stickerHeight)
    val sourceX = if (inverted) bounds.right - (left - bounds.left) - stickerWidth else left
    val sourceY = if (inverted) bounds.bottom - (top - bounds.top) - stickerHeight else top
    scan.getPixels(pixels, 0, stickerWidth, sourceX, sourceY, stickerWidth, stickerHeight)
    if (inverted) pixels.reverse()
    fillCassetteWindow(pixels, stickerWidth, stickerHeight, width, height, left - bounds.left, top - bounds.top)
    return Bitmap.createBitmap(pixels, stickerWidth, stickerHeight, Bitmap.Config.ARGB_8888)
}

private fun difference(a: Int, b: Int) = maxOf(
    abs(Color.red(a) - Color.red(b)), abs(Color.green(a) - Color.green(b)),
    abs(Color.blue(a) - Color.blue(b)), abs(Color.alpha(a) - Color.alpha(b))
)

private fun cassetteReels(grid: IntArray): Double? {
    fun sample(x: Double, y: Double) =
        grid[(y * SAMPLE_HEIGHT).roundToInt() * SAMPLE_WIDTH + (x * SAMPLE_WIDTH).roundToInt()]

    fun ringMatches(x: Double, y: Double, radius: Double): Int = spokes.count { (dx, dy) ->
        val sy = dy * SAMPLE_WIDTH / SAMPLE_HEIGHT
        difference(sample(x + dx * (radius - .006), y + sy * (radius - .006)),
            sample(x + dx * (radius + .006), y + sy * (radius + .006))) > 22
    }

    var best = 10
    var center: Double? = null
    for (step in 0..19) {
        val y = .422 + step * .008
        val matches = (0..9).maxOf { index ->
            val radius = .040 + index * .004
            minOf(ringMatches(.285, y, radius), ringMatches(.715, y, radius))
        }
        if (matches <= best) continue
        val aligned = listOf(.0, .012, .022, .032, .040).sumOf { radius ->
            spokes.count { (dx, dy) ->
                val sampleY = y + radius * dy * SAMPLE_WIDTH / SAMPLE_HEIGHT
                difference(sample(.285 + dx * radius, sampleY), sample(.715 + dx * radius, sampleY)) < 46
            }
        }
        if (aligned >= 59) {
            best = matches
            center = y
        }
    }
    return center
}

private fun cassetteSticker(grid: IntArray): Rect {
    fun edge(range: IntRange, baseline: Int, across: IntRange, horizontal: Boolean): Int {
        fun strength(line: Int) = across.count { pos ->
            val middle = if (horizontal) line * SAMPLE_WIDTH + pos else pos * SAMPLE_WIDTH + line
            val stride = if (horizontal) SAMPLE_WIDTH else 1
            difference(grid[middle - stride], grid[middle + stride]) > 17
        }
        val best = range.maxWith(compareBy<Int>({ strength(it) }, { -abs(it - baseline) }))
        return if (strength(best) > across.count() * .34) best else baseline
    }
    return Rect(
        edge(3..19, 9, 15..79, false) + 1,
        edge(3..16, 8, 18..181, true) + 1,
        edge(180..196, 190, 15..79, false) - 1,
        edge(83..101, 93, 18..181, true) - 1
    )
}

private fun fillCassetteWindow(
    pixels: IntArray, width: Int, height: Int, shellWidth: Int, shellHeight: Int, left: Int, top: Int
) {
    for (y in 0 until height) {
        val row = y * width
        val inset = ((1 - minOf(y / (height * .075), 1.0)) * width * .055).roundToInt()
        for (x in 0 until inset) {
            pixels[row + x] = pixels[row + inset]
            pixels[row + width - 1 - x] = pixels[row + width - 1 - inset]
        }
        val dy = ((top + y).toDouble() / shellHeight - .455) / .159
        if (abs(dy) >= 1) continue
        val dx = .115 * sqrt(1 - dy * dy)
        val lo = ((.285 - dx) * shellWidth - left).roundToInt().coerceIn(1, width - 2)
        val hi = ((.715 + dx) * shellWidth - left).roundToInt().coerceIn(lo + 1, width - 1)
        val before = paperColor(pixels, width, height, lo - 4, y)
        val after = paperColor(pixels, width, height, hi + 4, y)
        for (x in lo..hi) {
            val t = (x - lo).toFloat() / (hi - lo)
            fun mix(a: Int, b: Int) = (a + t * (b - a)).roundToInt()
            pixels[row + x] = Color.rgb(mix(Color.red(before), Color.red(after)),
                mix(Color.green(before), Color.green(after)), mix(Color.blue(before), Color.blue(after)))
        }
    }
}

private fun paperColor(pixels: IntArray, width: Int, height: Int, x: Int, y: Int): Int {
    val patch = IntArray(25) { i ->
        pixels[(y + (i / 5 - 2) * 2).coerceIn(0, height - 1) * width + (x + (i % 5 - 2) * 2).coerceIn(0, width - 1)]
    }
    return patch.sortedBy { Color.red(it) + Color.green(it) + Color.blue(it) }[patch.size / 2]
}

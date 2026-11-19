package com.nendo.argosy.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val GRID = 96

internal fun cropFloppyLabel(scan: Bitmap, bounds: Rect, model: String): Bitmap? {
    val aspect = bounds.width().toFloat() / bounds.height()
    val isSmallDisk = model == "floppy35"
    if (aspect !in (if (isSmallDisk) .90f..1.05f else .97f..1.04f)) return null
    val grid = Bitmap.createBitmap(GRID, GRID, Bitmap.Config.ARGB_8888)
    Canvas(grid).drawBitmap(scan, bounds, Rect(0, 0, GRID, GRID), Paint(Paint.FILTER_BITMAP_FLAG))
    val pixels = IntArray(GRID * GRID)
    grid.getPixels(pixels, 0, GRID, 0, 0, GRID, GRID)
    grid.recycle()
    val label = FloppyFace(pixels).label(isSmallDisk) ?: return null
    val left = bounds.left + label.left * bounds.width() / GRID
    val top = bounds.top + label.top * bounds.height() / GRID
    val width = label.width() * bounds.width() / GRID
    val height = label.height() * bounds.height() / GRID
    if (width < 36 || height < 16) return null
    return Bitmap.createBitmap(scan, left, top, width, height)
}

private class FloppyFace(private val colors: IntArray) {
    private fun pixel(x: Int, y: Int): Int = colors[y * GRID + x]

    fun label(smallDisk: Boolean): Rect? {
        val corners = listOf(pixel(6, 6), pixel(89, 6), pixel(6, 89), pixel(89, 89))
        val whiteMatte = isWhite(pixel(0, 0))
        if (corners.count { Color.alpha(it) > 170 && !(whiteMatte && isWhite(it)) } < 3) return null
        if (smallDisk) {
            val top = shutterStrength(false)
            val bottom = shutterStrength(true)
            if (maxOf(top, bottom) < .76) return null
            return if (top > bottom) sticker(34 until GRID) else sticker(0 until 62)
        }
        if (!hasSpindle()) return null
        return sticker(0 until 35) ?: sticker(61 until GRID)
    }

    private fun shutterStrength(inverted: Boolean): Double {
        fun row(y: Int) = if (inverted) GRID - 1 - y else y
        fun vertical(range: IntRange): Double = range.maxOf { x ->
            (4..24).count { y -> contrast(pixel(x - 1, row(y)), pixel(x + 1, row(y))) > 22 } / 21.0
        }
        val base = (25..38).maxOf { y ->
            (22..71).count { x -> contrast(pixel(x, row(y - 1)), pixel(x, row(y + 1))) > 20 } / 50.0
        }
        return minOf(vertical(15..36), vertical(40..66), vertical(69..84), base * 1.5)
    }

    private fun hasSpindle(): Boolean {
        val shell = pixel(17, 67)
        val similarShell = listOf(9, 19, 71, 84).sumOf { x ->
            listOf(41, 52, 63, 73, 84).count { y -> contrast(shell, pixel(x, y)) < 20 }
        }
        if (similarShell <= 10) return false
        return (0 until 16).count { direction ->
            val radians = direction * PI / 8
            fun radial(radius: Double) = pixel(
                ((.5 + radius * cos(radians)) * (GRID - 1)).toInt(),
                ((.5 + radius * sin(radians)) * (GRID - 1)).toInt()
            )
            val center = radial(.055)
            listOf(.113, .125, .135, .145, .153, .166).any { contrast(center, radial(it)) > 28 }
        } >= 14
    }

    private fun sticker(band: IntRange): Rect? {
        val sides = (32 until 76 step 4).flatMap { y -> listOf(pixel(4, y), pixel(91, y)) }
        val shell = Color.rgb(
            sides.map(Color::red).median(), sides.map(Color::green).median(), sides.map(Color::blue).median()
        )
        val groups = mutableListOf<MutableList<Rect>>()
        for (y in band) {
            val ink = (3 until GRID - 3).filter { x ->
                val value = pixel(x, y)
                Color.alpha(value) > 150 && contrast(value, shell) > 34
            }
            if (ink.size < 24 || ink.size < .55 * (ink.last() - ink.first() + 1)) continue
            val run = groups.lastOrNull()?.takeIf { y - it.last().top <= 2 }
                ?: mutableListOf<Rect>().also { groups.add(it) }
            run.add(Rect(ink.first(), y, ink.last() + 1, y + 1))
        }
        val label = groups.maxByOrNull { it.size } ?: return null
        val height = label.last().bottom - label.first().top
        if (height < 12) return null
        return Rect(label.map { it.left }.median(), label.first().top + 1, label.map { it.right }.median(), label.last().bottom - 1)
    }
}

private fun isWhite(pixel: Int) = Color.alpha(pixel) > 240 && minOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) > 240

private fun contrast(a: Int, b: Int) = maxOf(
    abs(Color.alpha(a) - Color.alpha(b)), abs(Color.red(a) - Color.red(b)),
    abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b))
)

private fun List<Int>.median(): Int {
    val sorted = sorted()
    return (sorted[(size - 1) / 2] + sorted[size / 2]) / 2
}

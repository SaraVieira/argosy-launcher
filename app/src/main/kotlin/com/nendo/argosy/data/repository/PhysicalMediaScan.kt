package com.nendo.argosy.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.nendo.argosy.data.model.isOpticalDiscModel
import kotlin.math.abs
import kotlin.math.roundToInt

private data class Sticker(val left: Float, val top: Float, val right: Float, val bottom: Float)

private val stickers = mapOf(
    "vita" to Sticker(.103f, .095f, .895f, .852f),
    "coleco" to Sticker(.063f, .042f, .937f, .753f),
    "atari2600" to Sticker(.06f, .05f, .94f, .95f),
    "gba" to Sticker(.15f, .235f, .86f, .86f),
    "gb" to Sticker(.12f, .28f, .865f, .87f),
    "nes" to Sticker(.407f, .008f, .864f, .67f),
    "snes" to Sticker(.193f, .006f, .807f, .435f),
    "snes-pal" to Sticker(.07f, .055f, .925f, .46f),
    "n64" to Sticker(.265f, .105f, .733f, .902f),
    "ds" to Sticker(.08f, .035f, .917f, .874f),
    "3ds" to Sticker(.089f, .09f, .857f, .90f),
    "md" to Sticker(.148f, .024f, .855f, .889f),
    "sms" to Sticker(.018f, .015f, .983f, .226f),
    "gg" to Sticker(.088f, .193f, .913f, .766f),
    "tg16" to Sticker(.049f, .427f, .952f, .907f),
    "switch" to Sticker(.078f, .083f, .918f, .858f)
)
private val famicom = Sticker(.075f, .112f, .918f, .746f)
private val umd = Sticker(.054f, .047f, .95f, .925f)
private val fullFace = Sticker(0f, 0f, 1f, 1f)

internal fun isPhysicalScanModel(model: String): Boolean = model in stickers || model == "msx" || model == "famicom_cart" || isOpticalDiscModel(model) || model == "gbc" ||
    model == "floppy35" || model == "floppy525" || model == "cassette"

internal fun cropPhysicalMedia(scan: Bitmap, model: String, region: String?): Bitmap? {
    val shell = cartridgeBounds(scan) ?: return null
    val aspect = shell.width().toFloat() / shell.height()
    if (model == "floppy35" || model == "floppy525") return cropFloppyLabel(scan, shell, model)
    if (model == "cassette") return cropCassetteLabel(scan, shell)
    if (model == "msx") {
        if (aspect !in 1.15f..1.95f) return null
        cropCassetteLabel(scan, shell)?.let { it.recycle(); return null }
    }
    if (model == "gbc") {
        if (aspect !in .72f..1.04f || shell.width() < 64 || shell.height() < 64) return null
        return cartridgeFront(scan, shell)
    }
    val sticker = when {
        model == "msx" -> msxSticker(scan, shell)
        isOpticalDiscModel(model) -> {
            if (aspect !in .80f..1.20f) return null
            if (model == "psp" && hasUmdShell(scan, shell)) umd else fullFace
        }
        model == "snes" || model == "snes-pal" -> {
            val style = when (region?.lowercase()) {
                "us", "usa", "united states", "north america", "canada" -> "snes"
                "japan", "jp", "europe", "eu", "united kingdom", "australia", "france", "germany" -> "snes-pal"
                else -> model
            }
            stickers.getValue(style)
        }
        model == "nes" || model == "famicom_cart" -> if (aspect > 1.15f) famicom else stickers.getValue("nes")
        else -> stickers[model] ?: return null
    }
    if (model == "gba" && aspect !in 1.3f..2.15f) return null
    val left = shell.left + (shell.width() * sticker.left).roundToInt()
    val top = shell.top + (shell.height() * sticker.top).roundToInt()
    val right = shell.left + (shell.width() * sticker.right).roundToInt()
    val bottom = shell.top + (shell.height() * sticker.bottom).roundToInt()
    if (right - left < 36 || bottom - top < 20) return null
    if (isOpticalDiscModel(model)) {
        return discFace(scan, RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()))
    }
    val crop = Bitmap.createBitmap(scan, left, top, right - left, bottom - top)
    return if (crop === scan) scan.copy(Bitmap.Config.ARGB_8888, false) else crop
}

private fun msxSticker(scan: Bitmap, shell: Rect): Sticker {
    fun edge(horizontal: Boolean, range: IntRange, fallback: Int): Float {
        var strongest = 0
        var best = fallback
        for (percent in range) {
            var contrast = 0
            for (sample in 20..75 step 2) {
                fun colorAt(offset: Int): Int {
                    val x = if (horizontal) percent + offset else sample
                    val y = if (horizontal) sample else percent + offset
                    return scan.getPixel(shell.left + x * shell.width() / 100, shell.top + y * shell.height() / 100)
                }
                val a = colorAt(-1)
                val b = colorAt(1)
                contrast += abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
            }
            if (contrast > strongest) {
                strongest = contrast
                best = percent
            }
        }
        return (if (strongest >= 28 * 3 * 20) best else fallback) / 100f
    }
    return Sticker(edge(true, 7..20, 14), .008f, edge(true, 82..96, 91), edge(false, 77..96, 89))
}

private fun cartridgeFront(scan: Bitmap, bounds: Rect): Bitmap {
    val width = bounds.width()
    val height = bounds.height()
    val pixels = IntArray(width * height)
    scan.getPixels(pixels, 0, width, bounds.left, bounds.top, width, height)
    val matte = scan.getPixel(0, 0)
    if (Color.alpha(matte) > 240 && minOf(Color.red(matte), Color.green(matte), Color.blue(matte)) > 170) {
        // Remove only the outside matte. White lettering and labels are inside the shell.
        for (y in 0 until height) {
            val start = y * width
            val end = start + width - 1
            var left = start
            while (left <= end && similarColor(pixels[left], matte)) pixels[left++] = Color.TRANSPARENT
            var right = end
            while (right >= left && similarColor(pixels[right], matte)) pixels[right--] = Color.TRANSPARENT
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun discFace(scan: Bitmap, source: RectF): Bitmap {
    val size = minOf(source.width(), source.height()).roundToInt()
    val destination = RectF(0f, 0f, size.toFloat(), size.toFloat())
    val mapping = Matrix().apply { setRectToRect(source, destination, Matrix.ScaleToFit.FILL) }
    val image = BitmapShader(scan, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(mapping) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = image }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { face ->
        Canvas(face).drawOval(destination, paint)
    }
}

private fun hasUmdShell(scan: Bitmap, bounds: Rect): Boolean {
    val x = bounds.left + (bounds.width() * .045f).toInt()
    val y = bounds.top + (bounds.height() * .80f).toInt()
    val pixel = scan.getPixel(x, y)
    val background = scan.getPixel(0, 0)
    if (Color.alpha(background) > 240 && similarColor(pixel, background)) return false
    return Color.alpha(pixel) > 150 && minOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) > 110
}

private fun cartridgeBounds(scan: Bitmap): Rect? {
    val width = scan.width
    val height = scan.height
    val pixels = IntArray(width * height)
    scan.getPixels(pixels, 0, width, 0, 0, width, height)
    val corner = pixels.first()
    val corners = listOf(pixels[width - 1], pixels[width * (height - 1)], pixels.last())
    val uniformMatte = Color.alpha(corner) > 240 && corners.all { similarColor(it, corner) } &&
        minOf(Color.red(corner), Color.green(corner), Color.blue(corner)) > 170
    var left = width
    var right = -1
    var top = height
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            if (Color.alpha(pixel) < 64 || (uniformMatte && similarColor(pixel, corner))) continue
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
    }
    return if (right > left && bottom > top) Rect(left, top, right + 1, bottom + 1) else null
}

private fun similarColor(first: Int, second: Int): Boolean =
    abs(Color.red(first) - Color.red(second)) < 22 &&
        abs(Color.green(first) - Color.green(second)) < 22 &&
        abs(Color.blue(first) - Color.blue(second)) < 22

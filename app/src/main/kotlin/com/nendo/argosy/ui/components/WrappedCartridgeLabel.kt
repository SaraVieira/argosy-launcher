package com.nendo.argosy.ui.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import org.json.JSONArray
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

internal class WrappedCartridgeLabel(assets: AssetManager, directory: String) {
    private val profile = assets.open("$directory/wrap.json").bufferedReader().use { JSONArray(it.readText()) }
    private val left = profile.getDouble(0).toFloat()
    private val width = profile.getDouble(1).toFloat() - left
    private val points = profile.getJSONArray(2)
    private val height = FloatArray(points.length()) { points.getJSONArray(it).getDouble(0).toFloat() }
    private val depth = FloatArray(points.length()) { points.getJSONArray(it).getDouble(1).toFloat() }
    private val distance = FloatArray(points.length()).apply {
        for (i in 1 until size) this[i] = this[i - 1] + hypot(height[i] - height[i - 1], depth[i] - depth[i - 1])
    }

    fun print(art: Bitmap?, source: Rect?, title: String, layout: StaticLayout, fallbackColor: Int): Bitmap {
        val scale = 512f / width
        val faceStart = scale * distance[distance.lastIndex - 1]
        val end = scale * distance.last()
        val print = Bitmap.createBitmap(512, end.roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(print)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val paper = if (art != null && source != null) art.getPixel(source.centerX(), source.top) else fallbackColor
        canvas.drawColor(paper or Color.BLACK)
        if (art != null && source != null) {
            canvas.drawBitmap(art, source, RectF(0f, faceStart, 512f, end), paint)
        } else {
            canvas.save()
            canvas.translate((512f - layout.width) / 2, (faceStart + end - layout.height) / 2)
            layout.draw(canvas)
            canvas.restore()
        }
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (Color.luminance(paper) > .35f) Color.BLACK else Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textSize = scale * (depth.max() - depth.min()) * .49f
            textAlign = Paint.Align.CENTER
        }
        val spineCenter = (faceStart + distance[1] * scale) / 2
        canvas.drawText(TextUtils.ellipsize(title, text, 490f, TextUtils.TruncateAt.END).toString(),
            256f, spineCenter - (text.ascent() + text.descent()) / 2, text)
        return print
    }

    fun draw(canvas: Canvas, bitmap: Bitmap, yaw: Double, pitch: Double, centerX: Float, centerY: Float, paint: Paint) {
        val cy = cos(yaw).toFloat()
        val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()
        val dx = width * cy / bitmap.width
        val dy = width * sy * sp / bitmap.width
        val matrix = Matrix()
        for (i in 1 until distance.size) {
            val start = i - 1
            val top = bitmap.height * distance[start] / distance.last()
            val bottom = bitmap.height * distance[i] / distance.last()
            val dz = depth[i] - depth[start]
            val ux = -dz * sy / (bottom - top)
            val uy = ((height[start] - height[i]) * cp + dz * cy * sp) / (bottom - top)
            if (dx * uy - dy * ux <= 0f) continue
            val x = centerX + left * cy - depth[start] * sy
            val y = centerY + left * sy * sp - height[start] * cp + depth[start] * cy * sp
            matrix.setValues(floatArrayOf(dx, ux, x - top * ux, dy, uy, y - top * uy, 0f, 0f, 1f))
            canvas.save()
            canvas.concat(matrix)
            canvas.clipRect(0f, top, bitmap.width.toFloat(), bottom)
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), paint)
            canvas.restore()
        }
    }
}

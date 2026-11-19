package com.nendo.argosy.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.sin

@Composable
internal fun Pico8Cartridge(
    cart: Bitmap?,
    cover: Bitmap?,
    title: String,
    modifier: Modifier,
    interactive: Boolean,
    rotationSteps: Int,
    gameId: Long?
) {
    var drag by remember(gameId, title) { mutableFloatStateOf(0f) }
    val image = cart ?: cover
    val touch = if (interactive) {
        Modifier.pointerInput(gameId, title) {
            detectHorizontalDragGestures { change, delta ->
                change.consume()
                drag = (drag + delta / size.width.coerceAtLeast(1)) % 1f
            }
        }
    } else Modifier
    Box(modifier.then(touch), contentAlignment = Alignment.Center) {
        if (image == null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Image(
                bitmap = remember(image) { image.asImageBitmap() },
                contentDescription = title,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val turn = (drag + rotationSteps.mod(20) / 20f) * 2 * Math.PI
                    rotationY = 30f * sin(turn).toFloat()
                },
                contentScale = ContentScale.Fit,
                filterQuality = if (cart != null) FilterQuality.None else FilterQuality.Low
            )
        }
    }
}

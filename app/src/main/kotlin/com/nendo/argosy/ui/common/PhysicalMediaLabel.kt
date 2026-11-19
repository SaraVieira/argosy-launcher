package com.nendo.argosy.ui.common

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nendo.argosy.data.repository.ArcadeCabinetArtRepository
import com.nendo.argosy.data.repository.PhysicalMediaArtRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhysicalMediaArtEntryPoint {
    fun arcadeCabinetArtRepository(): ArcadeCabinetArtRepository
    fun physicalMediaArtRepository(): PhysicalMediaArtRepository
}

@Composable
internal fun rememberPhysicalMediaLabel(gameId: Long?, model: String): Bitmap? {
    if (gameId == null) return null
    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        EntryPointAccessors.fromApplication(context, PhysicalMediaArtEntryPoint::class.java)
            .physicalMediaArtRepository()
    }
    var bitmap by remember(repository, gameId, model) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(repository, gameId, model) {
        val label = repository.artwork(gameId, model) ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(File(label)).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult
        bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
    }
    return bitmap
}

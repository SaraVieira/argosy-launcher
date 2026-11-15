package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.emulator.DriverFetcherRepository
import com.nendo.argosy.data.emulator.EmulatorDownloadManager
import com.nendo.argosy.data.repository.DatabaseAdminRepository
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.screens.settings.CachesClearTarget
import com.nendo.argosy.ui.screens.settings.StorageCachesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StorageCachesDelegate @Inject constructor(
    private val databaseAdminRepository: DatabaseAdminRepository,
    private val downloadManager: com.nendo.argosy.data.download.DownloadManager,
    private val emulatorDownloadManager: EmulatorDownloadManager,
    private val driverFetcherRepository: DriverFetcherRepository,
    private val soundFeedbackManager: SoundFeedbackManager,
    private val attributionRepository: StorageAttributionRepository,
    private val notificationManager: NotificationManager
) {
    private val _state = MutableStateFlow(StorageCachesState())
    val state: StateFlow<StorageCachesState> = _state.asStateFlow()

    fun requestClear(target: CachesClearTarget) {
        if (target in _state.value.busyClears) return
        val blockedReason: NotificationText? = when (target) {
            CachesClearTarget.EMULATOR_APKS ->
                NotificationText.Res(R.string.notif_storage_caches_emulator_busy_precheck)
                    .takeIf { emulatorDownloadManager.hasActiveDownload() }
            else -> null
        }
        if (blockedReason != null) {
            notificationManager.showError(blockedReason)
            return
        }
        _state.update { it.copy(pendingClear = target) }
    }

    fun cancelClear() {
        _state.update { it.copy(pendingClear = null) }
    }

    /** Runs the pending clear on IO; [onCleared] fires on success with the completed target. */
    fun confirmClear(scope: CoroutineScope, onCleared: (CachesClearTarget) -> Unit = {}) {
        val target = _state.value.pendingClear ?: return
        _state.update { it.copy(pendingClear = null, busyClears = it.busyClears + target) }
        scope.launch {
            val succeeded = withContext(Dispatchers.IO) { runClear(target) }
            _state.update { it.copy(busyClears = it.busyClears - target) }
            if (succeeded) {
                attributionRepository.refreshOnOpen()
                onCleared(target)
            }
        }
    }

    private suspend fun runClear(target: CachesClearTarget): Boolean = when (target) {
        CachesClearTarget.IMAGE_CACHE -> {
            databaseAdminRepository.clearImageCache()
            true
        }
        CachesClearTarget.ROM_EXTRACTION -> {
            val performed = databaseAdminRepository.clearRomExtractionCache()
            if (!performed) {
                notificationManager.showError(NotificationText.Res(R.string.notif_storage_caches_rom_extraction_busy))
            }
            performed
        }
        CachesClearTarget.ROM_STAGING -> {
            downloadManager.cleanAbandonedStaging()
            true
        }
        CachesClearTarget.SFX_CACHE -> {
            soundFeedbackManager.clearSfxCache()
            true
        }
        CachesClearTarget.EMULATOR_APKS -> {
            val performed = emulatorDownloadManager.clearApkCache()
            if (!performed) {
                notificationManager.showError(NotificationText.Res(R.string.notif_storage_caches_emulator_busy_runclear))
            }
            performed
        }
        CachesClearTarget.MISC_DOWNLOADS -> {
            driverFetcherRepository.clearDownloadedDrivers()
            true
        }
    }
}

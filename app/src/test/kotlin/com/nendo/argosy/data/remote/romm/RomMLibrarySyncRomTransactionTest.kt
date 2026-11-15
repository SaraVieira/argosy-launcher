package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.InlineRoomTransactions
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RomMLibrarySyncRomTransactionTest {

    private val romId = 42L
    private val romName = "Chrono Trigger"
    private val coverUrl = "https://romm/covers/42.png"

    private val transactions = InlineRoomTransactions()
    private lateinit var apiClient: RomMApiClient
    private lateinit var gameDao: GameDao
    private lateinit var gameArtDao: GameArtDao
    private lateinit var gameScreenshotDao: GameScreenshotDao
    private lateinit var imageCacheManager: ImageCacheManager
    private lateinit var platformDao: PlatformDao
    private lateinit var overlayWriter: GameUserOverlayWriter
    private lateinit var service: RomMLibrarySyncService

    private fun rom() = RomMRom(
        id = romId,
        platformId = 1L,
        platformSlug = "snes",
        name = romName,
        slug = "chrono-trigger",
        fileName = "ct.sfc",
        filePath = "/roms/snes/ct.sfc",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = null,
        languages = null,
        revision = null,
        crcHash = null,
        md5Hash = null,
        sha1Hash = null
    )

    private fun existing() = GameEntity(
        id = 9L,
        platformId = 1L,
        platformSlug = "snes",
        title = romName,
        sortTitle = "chrono trigger",
        localPath = null,
        rommId = romId,
        igdbId = null,
        source = GameSource.ROMM_REMOTE
    )

    @Before
    fun setup() {
        transactions.install()
        apiClient = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        gameArtDao = mockk(relaxed = true)
        gameScreenshotDao = mockk(relaxed = true)
        imageCacheManager = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        overlayWriter = mockk(relaxed = true)
        val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
        val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val preferences = mockk<UserPreferences>(relaxed = true)

        every { connectionManager.getApi() } returns mockk(relaxed = true)
        every { preferences.boxArtCacheEnabled } returns false
        every { preferencesRepository.preferences } returns flowOf(preferences)
        coEvery { apiClient.getRom(romId) } returns RomMResult.Success(rom())
        every { apiClient.buildCoverUrls(any()) } returns listOf(coverUrl)
        every { apiClient.buildLogoUrls(any()) } returns emptyList()
        every { apiClient.buildBackgroundUrls(any()) } returns emptyList()
        every { apiClient.buildBox3dUrls(any()) } returns emptyList()
        every { apiClient.buildMediaUrl(any()) } returns null
        every { apiClient.buildResourceUrl(any()) } returns null
        coEvery { platformDao.getById(1L) } returns mockk<PlatformEntity>(relaxed = true) {
            every { slug } returns "snes"
        }
        coEvery { overlayWriter.activeOwnerId() } returns null
        coEvery { gameDao.getByRommId(romId) } returns existing()

        service = RomMLibrarySyncService(
            apiClient = apiClient,
            connectionManager = connectionManager,
            userPreferencesRepository = preferencesRepository,
            database = mockk(relaxed = true),
            gameDao = gameDao,
            homeTileDao = mockk(relaxed = true),
            gameDiscDao = mockk(relaxed = true),
            gameFileDao = mockk(relaxed = true),
            saveSyncDao = mockk(relaxed = true),
            saveCacheDao = mockk(relaxed = true),
            localRomFiles = mockk(relaxed = true),
            platformDao = platformDao,
            emulatorConfigDao = mockk(relaxed = true),
            platformLibretroSettingsDao = mockk(relaxed = true),
            playSessionDao = mockk(relaxed = true),
            firmwareDao = mockk(relaxed = true),
            controllerMappingDao = mockk(relaxed = true),
            collectionDao = mockk(relaxed = true),
            imageCacheManager = imageCacheManager,
            musicDirectoryManager = mockk(relaxed = true),
            gameFileSync = mockk(relaxed = true),
            biosRepository = mockk(relaxed = true),
            installedAppResolver = mockk(relaxed = true),
            gameRepository = mockk(relaxed = true),
            overlayWriter = overlayWriter,
            overlayDao = mockk(relaxed = true),
            visibilityService = mockk(relaxed = true),
            syncVirtualCollectionsUseCase = mockk(relaxed = true),
            fileAccessLayer = mockk(relaxed = true),
            androidGameScanner = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            userRomsHiddenDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            siblingSplitRepair = mockk(relaxed = true),
            siblingConfigCarryOver = mockk(relaxed = true),
            siblingGroupRepository = mockk(relaxed = true),
            variantFileCleanup = mockk(relaxed = true),
            gameArtDao = gameArtDao,
            gameScreenshotDao = gameScreenshotDao
        )
    }

    @After
    fun tearDown() {
        transactions.release()
    }

    @Test
    fun `a rom's game, art and screenshot writes all land in one transaction`() = runTest {
        val openAt = mutableListOf<Boolean>()
        coEvery { gameDao.insert(any()) } answers { openAt += transactions.isOpen; 9L }
        coEvery { gameArtDao.setSourceUrl(any(), any(), any()) } answers { openAt += transactions.isOpen }
        coEvery { gameScreenshotDao.replaceSources(any(), any()) } answers {
            openAt += transactions.isOpen
            emptyList()
        }

        service.syncSingleRom(romId)

        assertEquals(1, transactions.opened)
        assertTrue(openAt.isNotEmpty())
        assertTrue(openAt.all { it })
    }

    @Test
    fun `art is queued only after the rom's transaction has committed`() = runTest {
        val openAt = mutableListOf<Boolean>()
        coEvery { imageCacheManager.queueArtIfStale(any(), any(), any(), any(), any(), any()) } answers {
            openAt += transactions.isOpen
        }

        service.syncSingleRom(romId)

        coVerify(exactly = 1) {
            imageCacheManager.queueArtIfStale(9L, ArtSlot.COVER, listOf(coverUrl), romId, null, romName)
        }
        assertEquals(listOf(false), openAt)
    }

    @Test
    fun `a rom whose write fails queues no art`() = runTest {
        coEvery { gameScreenshotDao.replaceSources(any(), any()) } throws IllegalStateException("disk full")

        val result = runCatching { service.syncSingleRom(romId) }

        assertTrue(result.isFailure)
        assertFalse(transactions.isOpen)
        coVerify(exactly = 0) { imageCacheManager.queueArtIfStale(any(), any(), any(), any(), any(), any()) }
    }
}

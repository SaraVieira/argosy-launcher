package com.nendo.argosy.ui.screens.setup

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.setup.SetupFolderProbe
import com.nendo.argosy.data.setup.SetupStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupDelegatesTest {

    private val romM = mockk<RomMRepository>(relaxed = true)
    private val platformRepository = mockk<PlatformRepository>(relaxed = true)
    private val probe = mockk<SetupFolderProbe>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val setupState = mockk<SetupStateRepository>(relaxed = true)

    private fun platform(id: Long, slug: String, enabled: Boolean) = PlatformEntity(
        id = id,
        slug = slug,
        name = slug,
        shortName = slug,
        romExtensions = "bin",
        syncEnabled = enabled
    )

    @Test
    fun `a small library syncs every platform and skips the page`() = runTest {
        coEvery { romM.fetchAndStorePlatforms(true) } returns RomMResult.Success(listOf(platform(1, "snes", true)))
        val delegate = SetupPlatformsDelegate(romM, platformRepository).also { it.bind(backgroundScope) }

        assertFalse(delegate.load(platformCount = 3))
        coVerify(exactly = 1) { romM.fetchAndStorePlatforms(true) }
    }

    @Test
    fun `a large library starts with every platform off and offers the page`() = runTest {
        val platforms = (1L..11L).map { platform(it, "p$it", false) }
        coEvery { romM.fetchAndStorePlatforms(false) } returns RomMResult.Success(platforms)
        val delegate = SetupPlatformsDelegate(romM, platformRepository).also { it.bind(backgroundScope) }

        assertTrue(delegate.load(platformCount = 11))
        coVerify(exactly = 1) { romM.fetchAndStorePlatforms(false) }
    }

    @Test
    fun `an unwritable folder is marked and nothing is confirmed`() = runTest {
        every { probe.prepareWritable("/storage/emulated/0/Games") } returns false
        val delegate = SetupFoldersDelegate(probe, preferences, setupState).also { it.bind(backgroundScope) }
        delegate.setCustomPath("/storage/emulated/0/Games")

        assertFalse(delegate.confirm())
        assertEquals(RomFolderKind.CUSTOM, delegate.state.value.unwritable)
        coVerify(exactly = 0) { setupState.confirmRomFolder(any()) }
        coVerify(exactly = 0) { preferences.setImageCachePath(any()) }
    }

    @Test
    fun `a writable folder is confirmed with the cover choice`() = runTest {
        every { probe.prepareWritable("/storage/emulated/0/Games") } returns true
        val delegate = SetupFoldersDelegate(probe, preferences, setupState).also { it.bind(backgroundScope) }
        delegate.setCustomPath("/storage/emulated/0/Games")

        assertTrue(delegate.confirm())
        assertNull(delegate.state.value.unwritable)
        coVerify(exactly = 1) { setupState.confirmRomFolder("/storage/emulated/0/Games") }
        coVerify(exactly = 1) { preferences.setImageCachePath(null) }
    }
}

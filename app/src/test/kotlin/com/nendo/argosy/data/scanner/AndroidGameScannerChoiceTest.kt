package com.nendo.argosy.data.scanner

import com.nendo.argosy.data.local.dao.AppCategoryDao
import com.nendo.argosy.data.local.entity.AppCategoryEntity
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidGameScannerChoiceTest {

    private val appCategoryDao: AppCategoryDao = mockk(relaxed = true)

    private val scanner = AndroidGameScanner(
        appsRepository = mockk(relaxed = true),
        appCategoryDao = appCategoryDao,
        gameDao = mockk(relaxed = true),
        platformDao = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        metadataFetcher = mockk(relaxed = true),
        gameArtDao = mockk(relaxed = true)
    )

    @Test
    fun `marking an app nobody has categorised records a manual game flag`() = runBlocking {
        coEvery { appCategoryDao.getByPackageName("com.example.notes") } returns null
        val written = slot<AppCategoryEntity>()
        coEvery { appCategoryDao.insert(capture(written)) } returns Unit

        scanner.rememberGameChoice("com.example.notes", isGame = true)

        assertEquals("com.example.notes", written.captured.packageName)
        assertTrue(written.captured.isGame)
        assertTrue(written.captured.isManualOverride)
        assertNull(written.captured.category)
        assertEquals(0L, written.captured.id)
    }

    @Test
    fun `marking a categorised game as not a game keeps its row and category`() = runBlocking {
        coEvery { appCategoryDao.getByPackageName("com.example.puzzle") } returns AppCategoryEntity(
            id = 7,
            packageName = "com.example.puzzle",
            category = "GAME_PUZZLE",
            isGame = true,
            isManualOverride = false,
            fetchedAt = 1L
        )
        val written = slot<AppCategoryEntity>()
        coEvery { appCategoryDao.insert(capture(written)) } returns Unit

        scanner.rememberGameChoice("com.example.puzzle", isGame = false)

        assertEquals(7L, written.captured.id)
        assertEquals("GAME_PUZZLE", written.captured.category)
        assertFalse(written.captured.isGame)
        assertTrue(written.captured.isManualOverride)
    }
}

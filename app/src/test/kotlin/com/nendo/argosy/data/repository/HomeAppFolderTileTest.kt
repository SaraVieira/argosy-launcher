package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.data.local.entity.HomeTileTarget
import com.nendo.argosy.domain.model.HomeGridKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.appfolder.toggled
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAppFolderTileTest {

    private val dao = mockk<HomeTileDao>(relaxed = true)
    private val repository = HomeTileRepository(dao)

    private suspend fun roundTrip(target: HomeTileTargetRef): Pair<HomeTileEntity, HomeTileTargetRef> {
        val written = slot<HomeTileEntity>()
        coEvery { dao.insert(capture(written)) } returns 7L
        repository.place(
            ownerUserId = 1L,
            kind = HomeGridKind.PAGED,
            pageIndex = 0,
            rect = TileRect(0, 0),
            target = target
        )
        val row = written.captured.copy(id = 7L)
        every { dao.observeTiles(1L, "PAGED") } returns flowOf(listOf(row))
        every { dao.observeAllEpisodes() } returns flowOf(emptyList())
        return row to repository.observeTiles(1L, HomeGridKind.PAGED).first().single().target
    }

    @Test
    fun `a folder keeps its name and app order across a write and a read`() = runTest {
        val target = HomeTileTargetRef.AppFolder("Tools", listOf("org.notes", "com.twinlore.app", "org.calc"))

        val (row, read) = roundTrip(target)

        assertEquals(HomeTileTarget.APP_FOLDER.name, row.targetType)
        assertEquals("Tools", row.virtualName)
        assertEquals("org.notes,com.twinlore.app,org.calc", row.featureConfig)
        assertEquals(target, read)
    }

    @Test
    fun `an empty folder reads back as a folder, not a gap`() = runTest {
        val (_, read) = roundTrip(HomeTileTargetRef.AppFolder("", emptyList()))

        assertEquals(HomeTileTargetRef.AppFolder("", emptyList()), read)
    }

    @Test
    fun `picking apps keeps the order they were picked in and unpicking removes them`() {
        val picked = emptyList<String>().toggled("org.b").toggled("org.a").toggled("org.c").toggled("org.a")

        assertEquals(listOf("org.b", "org.c"), picked)
    }
}

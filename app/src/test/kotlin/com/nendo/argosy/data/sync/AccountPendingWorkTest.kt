package com.nendo.argosy.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPendingWorkTest {

    private fun work(
        sync: Int = 0,
        saves: Int = 0,
        downloads: Int = 0
    ) = AccountPendingWork(
        queuedSyncOperations = sync,
        savesAwaitingUpload = saves,
        unfinishedDownloads = downloads
    )

    @Test
    fun `unsent saves block removal`() {
        assertFalse(work(saves = 1).isEmpty)
    }

    @Test
    fun `queued sync operations block removal`() {
        assertFalse(work(sync = 1).isEmpty)
    }

    @Test
    fun `unfinished downloads block removal`() {
        assertFalse(work(downloads = 1).isEmpty)
    }

    @Test
    fun `nothing queued blocks nothing`() {
        assertTrue(work().isEmpty)
    }
}

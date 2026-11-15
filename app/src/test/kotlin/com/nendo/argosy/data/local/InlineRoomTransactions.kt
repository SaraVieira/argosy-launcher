package com.nendo.argosy.data.local

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

/**
 * Runs every `RoomDatabase.withTransaction` block inline on the caller. [opened] counts the
 * blocks entered and [isOpen] is true while one is running.
 */
class InlineRoomTransactions {
    var opened = 0
        private set
    var isOpen = false
        private set

    fun install() {
        mockkStatic(ROOM_DATABASE_KT)
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            opened++
            isOpen = true
            try {
                secondArg<suspend () -> Any?>().invoke()
            } finally {
                isOpen = false
            }
        }
    }

    fun release() {
        unmockkStatic(ROOM_DATABASE_KT)
    }

    private companion object {
        const val ROOM_DATABASE_KT = "androidx.room.RoomDatabaseKt"
    }
}

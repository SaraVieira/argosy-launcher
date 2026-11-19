package com.nendo.argosy.ui.screens.settings.search

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.ui.common.DisplayText
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.EmulatorState
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.sections.PlatformDetailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchLauncherSectionsTest {

    @Test
    fun `presentation rows need a usable second screen`() {
        val single = SettingsUiState(display = DisplayState())
        val dual = SettingsUiState(display = DisplayState(hasSecondaryDisplay = true))

        assertTrue(buildSettingsSearchIndex(single).rowKeysIn(SettingsSection.PRESENTATION).isEmpty())
        assertTrue(buildSettingsSearchIndex(dual).rowKeysIn(SettingsSection.PRESENTATION).isNotEmpty())
    }

    @Test
    fun `platform rows are indexed for enabled platforms under the platform name`() {
        val ui = SettingsUiState(
            emulators = EmulatorState(
                platforms = listOf(platform(1, "SNES", enabled = true), platform(2, "Saturn", enabled = false))
            )
        )

        val rows = enabledPlatformRowEntries(ui)
        val emulatorRow = rows.single {
            it.target == SettingsSearchTarget.Platform(1, PlatformDetailItem.Emulator.key)
        }

        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { (it.target as SettingsSearchTarget.Platform).platformId == 1L })
        assertEquals(DisplayText.Raw("SNES"), emulatorRow.path.last())
        assertTrue(buildSettingsSearchIndex(ui).any { it.target == emulatorRow.target })
    }

    private fun List<SettingsSearchEntry>.rowKeysIn(section: SettingsSection): List<String> =
        mapNotNull { (it.target as? SettingsSearchTarget.Row)?.takeIf { row -> row.section == section }?.key }

    private fun platform(id: Long, name: String, enabled: Boolean) = PlatformEmulatorConfig(
        platform = PlatformEntity(
            id = id,
            slug = name.lowercase(),
            name = name,
            shortName = name,
            romExtensions = "",
            syncEnabled = enabled
        ),
        selectedEmulator = null,
        isUserConfigured = false,
        availableEmulators = emptyList()
    )
}

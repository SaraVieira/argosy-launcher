package com.nendo.argosy.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsPlatformScopeTest {

    @Test
    fun `a platform's storage games scope to it, and none selected scopes to nothing`() {
        val selected = SettingsUiState(
            currentSection = SettingsSection.STORAGE_PLATFORM_GAMES,
            storagePlatformGames = StoragePlatformGamesState(selectedPlatformId = 9, platformName = "Wii")
        )
        val none = selected.copy(storagePlatformGames = StoragePlatformGamesState())
        assertEquals(SettingsPlatformScope(9, "Wii"), settingsPlatformScope(selected))
        assertNull(settingsPlatformScope(none))
    }

    @Test
    fun `sections about the whole app scope to nothing`() {
        assertNull(settingsPlatformScope(SettingsUiState(currentSection = SettingsSection.THEME)))
    }
}

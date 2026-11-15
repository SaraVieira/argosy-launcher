package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.screens.settings.sections.AudioItem
import com.nendo.argosy.ui.screens.settings.sections.audioFocusIndexOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackStackTest {

    private fun at(section: SettingsSection, focus: Int) =
        SettingsUiState(currentSection = section, focusedIndex = focus)

    @Test
    fun `back returns to the row the section was entered from`() {
        val entered = at(SettingsSection.AUDIO, audioFocusIndexOf(AudioItem.Music))
            .pushedSection(SettingsSection.THEME_MUSIC)

        assertEquals(SettingsSection.THEME_MUSIC, entered.currentSection)
        assertEquals(0, entered.focusedIndex)

        val back = entered.poppedSection()!!
        assertEquals(SettingsSection.AUDIO, back.currentSection)
        assertEquals(audioFocusIndexOf(AudioItem.Music), back.focusedIndex)
        assertEquals(emptyList<SettingsNavEntry>(), back.backStack)
    }

    @Test
    fun `the same section backs out to whichever parent opened it`() {
        val fromMain = at(SettingsSection.MAIN, 4)
            .pushedSection(SettingsSection.STEAM_SETTINGS)
        val fromStorage = at(SettingsSection.STORAGE, 7)
            .pushedSection(SettingsSection.STEAM_SETTINGS)

        assertEquals(SettingsSection.MAIN, fromMain.poppedSection()!!.currentSection)
        assertEquals(SettingsSection.STORAGE, fromStorage.poppedSection()!!.currentSection)
        assertEquals(7, fromStorage.poppedSection()!!.focusedIndex)
    }

    @Test
    fun `nesting unwinds one level at a time`() {
        val deep = at(SettingsSection.MAIN, 3)
            .pushedSection(SettingsSection.PLATFORMS)
            .let { it.copy(focusedIndex = 5) }
            .pushedSection(SettingsSection.PLATFORM_DETAIL)
            .let { it.copy(focusedIndex = 2) }
            .pushedSection(SettingsSection.BIOS)

        val first = deep.poppedSection()!!
        assertEquals(SettingsSection.PLATFORM_DETAIL, first.currentSection)
        assertEquals(2, first.focusedIndex)

        val second = first.poppedSection()!!
        assertEquals(SettingsSection.PLATFORMS, second.currentSection)
        assertEquals(5, second.focusedIndex)

        val third = second.poppedSection()!!
        assertEquals(SettingsSection.MAIN, third.currentSection)
        assertEquals(3, third.focusedIndex)
    }

    @Test
    fun `a section with no parent leaves settings rather than popping`() {
        assertNull(at(SettingsSection.MAIN, 0).poppedSection())
        assertNull(at(SettingsSection.ACCOUNTS, 0).poppedSection())
    }

    @Test
    fun `an explicitly resolved focus wins over the remembered index`() {
        val entered = at(SettingsSection.PLATFORMS, 4).pushedSection(SettingsSection.PLATFORM_DETAIL)
        assertEquals(9, entered.poppedSection(restoredFocus = 9)!!.focusedIndex)
    }
}

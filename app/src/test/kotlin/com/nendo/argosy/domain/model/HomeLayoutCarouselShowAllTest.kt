package com.nendo.argosy.domain.model

import com.nendo.argosy.ui.components.HomeLayoutSettingField
import com.nendo.argosy.ui.components.adjustHomeLayoutField
import com.nendo.argosy.ui.components.homeLayoutFieldsFor
import com.nendo.argosy.ui.components.isHomeLayoutFieldShown
import com.nendo.argosy.ui.components.toggleHomeLayoutField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutCarouselShowAllTest {

    private val carousel = HomeLayoutSettings(selected = HomeLayoutKind.CAROUSEL)

    @Test
    fun `a carousel shows every game only when its own option is on`() {
        assertFalse(carousel.showsEveryGame)
        assertTrue(carousel.copy(carousel = carousel.carousel.copy(showAllGames = true)).showsEveryGame)
    }

    @Test
    fun `each layout reads its own show-all option`() {
        val gridOn = HomeLayoutSettings(
            selected = HomeLayoutKind.CAROUSEL,
            autoGrid = AutoGridConfig(showAllGames = true)
        )
        assertFalse(gridOn.showsEveryGame)
        assertTrue(gridOn.copy(selected = HomeLayoutKind.AUTO_GRID).showsEveryGame)

        val carouselOn = HomeLayoutSettings(
            selected = HomeLayoutKind.AUTO_GRID,
            carousel = CarouselConfig(showAllGames = true)
        )
        assertFalse(carouselOn.showsEveryGame)
        assertFalse(carouselOn.copy(selected = HomeLayoutKind.CUSTOM_GRID).showsEveryGame)
    }

    @Test
    fun `the carousel option survives a save and a load`() {
        val on = carousel.copy(carousel = carousel.carousel.copy(showAllGames = true))

        val read = HomeLayoutSettings.fromJson(on.toJson())

        assertTrue(read.carousel.showAllGames)
        assertFalse(read.autoGrid.showAllGames)
        assertEquals(on, read)
    }

    @Test
    fun `settings saved before the option existed keep the carousel capped`() {
        val legacy = """{"selected":"CAROUSEL","carousel":{"rowAlignment":"BOTTOM","showPlatformBadge":true}}"""

        assertFalse(HomeLayoutSettings.fromJson(legacy).carousel.showAllGames)
    }

    @Test
    fun `the carousel settings offer the option and both inputs flip it`() {
        assertTrue(HomeLayoutSettingField.CAROUSEL_SHOW_ALL in homeLayoutFieldsFor(HomeLayoutKind.CAROUSEL))
        assertTrue(isHomeLayoutFieldShown(carousel, HomeLayoutSettingField.CAROUSEL_SHOW_ALL))
        assertFalse(
            isHomeLayoutFieldShown(
                carousel.copy(selected = HomeLayoutKind.AUTO_GRID),
                HomeLayoutSettingField.CAROUSEL_SHOW_ALL
            )
        )

        val toggled = toggleHomeLayoutField(carousel, HomeLayoutSettingField.CAROUSEL_SHOW_ALL)
        assertTrue(toggled.carousel.showAllGames)
        assertFalse(toggleHomeLayoutField(toggled, HomeLayoutSettingField.CAROUSEL_SHOW_ALL).carousel.showAllGames)

        assertTrue(adjustHomeLayoutField(carousel, HomeLayoutSettingField.CAROUSEL_SHOW_ALL, 1, CustomGridShape(3, 2)).carousel.showAllGames)
        assertFalse(adjustHomeLayoutField(toggled, HomeLayoutSettingField.CAROUSEL_SHOW_ALL, -1, CustomGridShape(3, 2)).carousel.showAllGames)
    }
}

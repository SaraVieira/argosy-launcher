package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.model.ArtProvider
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.ServerArt
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class RomMServerArtTest {

    private val base = "https://romm.test"
    private val connection = mockk<RomMConnectionManager>(relaxed = true).also {
        every { it.getBaseUrl() } returns base
    }
    private val client = RomMApiClient(connection, mockk(relaxed = true))

    private fun lb(type: String, url: String) = RomMLaunchboxImage(url = url, type = type)

    private val rom = RomMRom(
        id = 7L,
        platformId = 1L,
        platformSlug = "snes",
        name = "ActRaiser",
        slug = "actraiser",
        fileName = "actraiser.sfc",
        filePath = "/roms/snes/actraiser.sfc",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = "/assets/romm/resources/roms/1/7/cover/big.png",
        regions = null,
        languages = null,
        revision = null,
        crcHash = null,
        md5Hash = null,
        sha1Hash = null,
        launchboxMetadata = RomMLaunchboxMetadata(
            images = listOf(
                lb("Fanart - Background", "https://lb.test/fanart-1.jpg"),
                lb("Fanart - Background", "https://lb.test/fanart-2.jpg"),
                lb("Box - Front", "https://lb.test/front-us.jpg"),
                lb("Box - Front - Reconstructed", "https://lb.test/front-recon.jpg"),
                lb("Fanart - Box - Front", "https://lb.test/front-fan.jpg"),
                lb("Box - Back", "https://lb.test/back.jpg"),
                lb("Clear Logo", "https://lb.test/logo.png"),
                lb("Screenshot - Gameplay", "https://lb.test/shot-1.jpg"),
                lb("Screenshot - Game Title", "https://lb.test/shot-2.jpg")
            )
        ),
        ssMetadata = RomMSsMetadata(
            box2dPath = "roms/1/7/box2d/box2d.png",
            logoPath = "roms/1/7/logo/logo.png",
            fanartPath = "roms/1/7/fanart/fanart.png"
        )
    )

    @Test
    fun `cartridge art prefers stored scans and regional cart fronts before the index and external ScreenScraper`() {
        val cart = rom.copy(
            regions = listOf("USA"),
            ssMetadata = RomMSsMetadata(physicalPath = "a.png", physicalUrl = "https://ss.test/cart"),
            gamelistMetadata = RomMGamelistMetadata(physicalPath = "a.png"),
            launchboxMetadata = RomMLaunchboxMetadata(images = listOf(
                RomMLaunchboxImage("https://lb.test/jp", "Cart - Front", "Japan"),
                RomMLaunchboxImage("file:///local", "Cart - Front", "USA"),
                RomMLaunchboxImage("https://lb.test/box", "Box - Front", "USA"),
                RomMLaunchboxImage("https://lb.test/us", "Cart - Front", "North America")
            ))
        )

        assertEquals(
            listOf("$base/assets/romm/resources/a.png", "https://lb.test/us", "https://lb.test/jp",
                "https://lb.test/index", "https://ss.test/cart"),
            client.physicalMediaImages(cart, "gba", PhysicalMediaImage("https://lb.test/index")).map { it.url }
        )
        assertEquals(listOf("https://lb.test/index"), client.physicalMediaImages(null, "gba", PhysicalMediaImage("https://lb.test/index")).map { it.url })
    }

    @Test
    fun `discs prefer the matching stored SS disc and exclude cartridge scans`() {
        val second = rom.copy(
            tags = listOf("Disc 2"),
            ssMetadata = RomMSsMetadata(
                physicalDisc = 1, physicalPath = "first.png", physicalUrl = "https://ss.test/1",
                physicalExtraDiscs = listOf(RomMPhysicalDisc(2, "second.png", "https://ss.test/2"))
            ),
            launchboxMetadata = RomMLaunchboxMetadata(images = listOf(
                lb("Cart - Front", "https://lb.test/wrong"), lb("Disc", "https://lb.test/disc")
            ))
        )
        for (model in listOf("disc", "gc_mini_dvd", "dreamcast_gd")) {
            assertEquals(
                listOf("$base/assets/romm/resources/second.png", "https://lb.test/disc", "https://ss.test/2"),
                client.physicalMediaImages(second, model, null).map { it.url }
            )
        }
    }

    @Test
    fun `floppy candidates prefer physical disk scans and ignore cart scans on mixed platforms`() {
        val disk = rom.copy(launchboxMetadata = RomMLaunchboxMetadata(images = listOf(
            lb("Cart - Front", "https://lb.test/cassette"),
            lb("Disc", "https://lb.test/media"),
            lb("Box - Front", "https://lb.test/box")
        )))
        for (model in listOf("floppy35", "floppy525")) {
            assertEquals(
                listOf("https://lb.test/media"),
                client.physicalMediaImages(disk, model, null).map { it.url }
            )
        }
    }

    @Test
    fun `cassette sources use physical media and LaunchBox front scans on tape platforms`() {
        val tape = rom.copy(
            ssMetadata = RomMSsMetadata(physicalPath = "tape.png"),
            launchboxMetadata = RomMLaunchboxMetadata(images = listOf(
                lb("Disc", "https://lb.test/disk"), lb("Cart - Front", "https://lb.test/tape")
            ))
        )
        assertEquals(
            listOf("$base/assets/romm/resources/tape.png", "https://lb.test/tape"),
            client.physicalMediaImages(tape, "cassette", null).map { it.url }
        )
    }

    @Test
    fun `background lists ScreenScraper fanart, then every LaunchBox fanart, then screenshots`() {
        assertEquals(
            listOf(
                ServerArt("$base/assets/romm/resources/roms/1/7/fanart/fanart.png", ArtProvider.SCREENSCRAPER),
                ServerArt("https://lb.test/fanart-1.jpg", ArtProvider.LAUNCHBOX),
                ServerArt("https://lb.test/fanart-2.jpg", ArtProvider.LAUNCHBOX),
                ServerArt("https://lb.test/shot-2.jpg", ArtProvider.ROMM),
                ServerArt("https://lb.test/shot-1.jpg", ArtProvider.ROMM)
            ),
            client.serverArt(rom, ArtSlot.BACKGROUND)
        )
    }

    @Test
    fun `sync background order matches the picker`() {
        assertEquals(
            client.serverArt(rom, ArtSlot.BACKGROUND).map { it.url },
            client.buildBackgroundUrls(rom)
        )
    }

    @Test
    fun `the picker falls back to ScreenScraper's fanart url but sync never fetches it`() {
        val ssUrl = "https://neoclone.screenscraper.fr/api2/mediaJeu.php?jeuid=1&media=fanart"
        val unstored = rom.copy(ssMetadata = rom.ssMetadata?.copy(fanartPath = null, fanartUrl = ssUrl))

        assertEquals(
            ServerArt(ssUrl, ArtProvider.SCREENSCRAPER),
            client.serverArt(unstored, ArtSlot.BACKGROUND).first()
        )
        assertEquals("https://lb.test/fanart-1.jpg", client.buildBackgroundUrls(unstored).first())
    }

    @Test
    fun `a stored ScreenScraper fanart wins over its url`() {
        val both = rom.copy(ssMetadata = rom.ssMetadata?.copy(fanartUrl = "https://ss.test/fanart"))

        assertEquals(
            "$base/assets/romm/resources/roms/1/7/fanart/fanart.png",
            client.serverArt(both, ArtSlot.BACKGROUND).first().url
        )
        assertEquals(
            "$base/assets/romm/resources/roms/1/7/fanart/fanart.png",
            client.buildBackgroundUrls(both).first()
        )
    }

    @Test
    fun `cover lists RomM's cover, the ScreenScraper box, then every LaunchBox front`() {
        assertEquals(
            listOf(
                ServerArt("$base/assets/romm/resources/roms/1/7/cover/big.png", ArtProvider.ROMM),
                ServerArt("$base/assets/romm/resources/roms/1/7/box2d/box2d.png", ArtProvider.SCREENSCRAPER),
                ServerArt("https://lb.test/front-us.jpg", ArtProvider.LAUNCHBOX),
                ServerArt("https://lb.test/front-recon.jpg", ArtProvider.LAUNCHBOX),
                ServerArt("https://lb.test/front-fan.jpg", ArtProvider.LAUNCHBOX)
            ),
            client.serverArt(rom, ArtSlot.COVER)
        )
    }

    @Test
    fun `logo lists ScreenScraper then LaunchBox`() {
        assertEquals(
            listOf(
                ServerArt("$base/assets/romm/resources/roms/1/7/logo/logo.png", ArtProvider.SCREENSCRAPER),
                ServerArt("https://lb.test/logo.png", ArtProvider.LAUNCHBOX)
            ),
            client.serverArt(rom, ArtSlot.LOGO)
        )
    }

    @Test
    fun `3d box lists ScreenScraper, LaunchBox and gamelist files, then remote LaunchBox images`() {
        val boxed = rom.copy(
            ssMetadata = rom.ssMetadata?.copy(box3dPath = "roms/1/7/box3d/ss.png"),
            launchboxMetadata = RomMLaunchboxMetadata(
                images = listOf(
                    lb("Box - 3D", "https://lb.test/box3d.png"),
                    lb("Box - 3D", "launchbox-file:///box3d.png")
                ),
                box3dPath = "roms/1/7/box3d/lb.png"
            ),
            gamelistMetadata = RomMGamelistMetadata(box3dPath = "roms/1/7/box3d/gl.png")
        )

        assertEquals(
            listOf(
                ServerArt("$base/assets/romm/resources/roms/1/7/box3d/ss.png", ArtProvider.SCREENSCRAPER),
                ServerArt("$base/assets/romm/resources/roms/1/7/box3d/lb.png", ArtProvider.LAUNCHBOX),
                ServerArt("$base/assets/romm/resources/roms/1/7/box3d/gl.png", ArtProvider.ROMM),
                ServerArt("https://lb.test/box3d.png", ArtProvider.LAUNCHBOX)
            ),
            client.serverArt(boxed, ArtSlot.BOX_3D)
        )
        assertEquals(client.serverArt(boxed, ArtSlot.BOX_3D).map { it.url }, client.buildBox3dUrls(boxed))
    }

    @Test
    fun `a rom with no 3d box offers none`() {
        assertEquals(emptyList<ServerArt>(), client.serverArt(rom, ArtSlot.BOX_3D))
    }

    @Test
    fun `a rom without metadata offers nothing`() {
        val bare = rom.copy(coverLarge = null, launchboxMetadata = null, ssMetadata = null)

        ArtSlot.entries.forEach { assertEquals(emptyList<ServerArt>(), client.serverArt(bare, it)) }
    }
}

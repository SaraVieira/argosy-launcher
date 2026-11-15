package com.nendo.argosy.data.remote.github

import com.nendo.argosy.data.remote.github.EmulatorUpdateRepository.Companion.extractVersionFromRelease
import com.nendo.argosy.data.remote.github.EmulatorUpdateRepository.Companion.isUpdateAvailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DottedPrereleaseVersionTest {

    private fun v(version: String) = VersionInfo.parse(version)!!

    private fun release(tag: String, name: String, asset: String? = null) = GitHubRelease(
        tagName = tag,
        name = name,
        body = null,
        prerelease = false,
        draft = false,
        htmlUrl = "https://github.com/SapphireRhodonite/WatermelonDS/releases/tag/$tag",
        targetCommitish = null,
        assets = listOfNotNull(asset?.let { GitHubAsset(name = it, downloadUrl = "https://example.invalid/$it", size = 1L) })
    )

    private fun updates(installed: String, latest: String) =
        isUpdateAvailable(installed, latest, installedAt = null, releasePublishedAt = null)

    @Test
    fun `a dotted release candidate is a prerelease of its version`() {
        assertEquals(VersionInfo(0, 8, 0, "rc.3"), v("0.8.0.rc3"))
        assertTrue(v("0.8.0.rc3").isPrerelease)
        assertEquals(VersionInfo(0, 7, 0, "rc.3.fix.2"), v("0.7.0.rc3.fix2"))
    }

    @Test
    fun `dotted release candidates order by number and hotfix`() {
        assertTrue(v("0.8.0.rc2") < v("0.8.0.rc3"))
        assertTrue(v("0.8.0.rc9") < v("0.8.0.rc10"))
        assertTrue(v("0.7.0.rc3") < v("0.7.0.rc3.fix"))
        assertTrue(v("0.7.0.rc3.fix") < v("0.7.0.rc3.fix2"))
        assertTrue(v("0.7.0.rc5") < v("0.7.0"))
        assertTrue(v("0.7.0") < v("0.8.0.rc2"))
    }

    @Test
    fun `numeric and unknown fourth segments keep their old reading`() {
        assertEquals(VersionInfo(1, 2, 3), v("1.2.3.4"))
        assertEquals(VersionInfo(1, 2, 3), v("1.2.3.final"))
        assertEquals(VersionInfo(3, 0, 0, "beta.5.fork.2"), v("3.0.0-beta.5.fork.2"))
    }

    @Test
    fun `WatermelonDS release names keep their candidate suffix`() {
        assertEquals("0.8.0.rc3", extractVersionFromRelease(release("0.8.0.rc3", "WatermelonDS 0.8.0.rc3")))
        assertEquals(
            "0.7.0.rc4",
            extractVersionFromRelease(release("0.7.0.rc4", "melonDualDS v0.7.0.rc4: Final Release Candidate"))
        )
        assertEquals("0.7.0", extractVersionFromRelease(release("0.7.0", "WatermelonDS 0.7.0 is finally here!")))
        assertEquals("0.8.0.rc3", extractVersionFromRelease(release("build", "Latest", "WatermelonDS-0.8.0.rc3.apk")))
    }

    @Test
    fun `candidate users are offered the next candidate and the final release`() {
        assertTrue(updates(installed = "0.8.0.rc2", latest = "0.8.0.rc3"))
        assertTrue(updates(installed = "0.8.0.rc3", latest = "0.8.0"))
        assertTrue(updates(installed = "0.7.0", latest = "0.8.0.rc3"))
        assertFalse(updates(installed = "0.8.0.rc3", latest = "0.8.0.rc3"))
        assertFalse(updates(installed = "0.8.0", latest = "0.8.0.rc3"))
    }
}

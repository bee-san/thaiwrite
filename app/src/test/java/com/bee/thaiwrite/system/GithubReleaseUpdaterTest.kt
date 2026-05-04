package com.bee.thaiwrite.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseUpdaterTest {
    @Test
    fun `normalizeVersionName removes leading v`() {
        assertEquals("1.2.3", GithubReleaseUpdater.normalizeVersionName(" v1.2.3 "))
    }

    @Test
    fun `isNewerVersion compares numeric segments`() {
        assertTrue(GithubReleaseUpdater.isNewerVersion("0.2.0", "0.1.9"))
        assertTrue(GithubReleaseUpdater.isNewerVersion("1.0.10", "1.0.2"))
        assertFalse(GithubReleaseUpdater.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(GithubReleaseUpdater.isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun `compareVersionNames orders semantic versions`() {
        assertTrue(GithubReleaseUpdater.compareVersionNames("1.2.0", "1.1.9") > 0)
        assertTrue(GithubReleaseUpdater.compareVersionNames("2.0.0", "1.99.99") > 0)
        assertEquals(0, GithubReleaseUpdater.compareVersionNames("1.0.0", "1.0.0"))
    }

    @Test
    fun `compareVersionNames returns zero when semantic comparison is unavailable`() {
        assertEquals(0, GithubReleaseUpdater.compareVersionNames("release-candidate", "debug-build"))
    }

    @Test
    fun `extractSha256Digest reads digest from checksum sidecar text`() {
        val digest = GithubReleaseUpdater.extractSha256Digest(
            "9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08  app-release.apk",
        )

        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            digest,
        )
    }

    @Test
    fun `extractSha256Digest returns null when text has no digest`() {
        assertEquals(null, GithubReleaseUpdater.extractSha256Digest("no checksum here"))
    }
}

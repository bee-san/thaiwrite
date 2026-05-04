package com.bee.thaiwrite.system

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun `extractNumericParts ignores surrounding text`() {
        assertEquals(listOf(1, 10, 3), GithubReleaseUpdater.extractNumericParts("release-v1.10.3-beta"))
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

    @Test
    fun `selectInstallableApkAsset prefers exact release apk and ignores debug assets`() {
        val asset = selectInstallableApkAsset(
            listOf(
                asset("app-debug.apk"),
                asset("thaiwrite-universal.apk"),
                asset("app-release.apk"),
            ),
        )

        assertEquals("app-release.apk", asset?.name)
    }

    @Test
    fun `selectInstallableApkAsset throws when release assets are ambiguous`() {
        try {
            selectInstallableApkAsset(
                listOf(
                    asset("phone-release.apk"),
                    asset("tablet-release.apk"),
                ),
            )
            fail("Expected ambiguous release APKs to throw.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("multiple release APK assets") == true)
        }
    }

    @Test
    fun `parseReleaseCandidates skips draft prerelease and missing apk releases`() {
        val releases = listOf(
            release(tag = "v0.2.0", assets = listOf(asset("app-release.apk"))),
            release(tag = "v0.3.0", draft = true, assets = listOf(asset("app-release.apk"))),
            release(tag = "v0.4.0", prerelease = true, assets = listOf(asset("app-release.apk"))),
            release(tag = "v0.5.0", assets = listOf(asset("notes.txt"))),
        )

        val parsed = parseReleaseCandidates(releases)

        assertEquals(1, parsed.size)
        assertEquals("0.2.0", parsed.single().versionName)
        assertEquals("app-release.apk", parsed.single().assetName)
    }

    @Test
    fun `parseReleaseCandidates reads checksum sidecar and inline digest`() {
        val parsed = parseReleaseCandidates(
            listOf(
                release(
                    tag = "v0.2.0",
                    assets = listOf(
                        asset("app-release.apk", digest = "sha256:abcdef"),
                        asset("app-release.apk.sha256", contentType = "text/plain", downloadUrl = "https://example.com/checksum"),
                    ),
                ),
            ),
        )

        assertEquals("abcdef", parsed.single().sha256Digest)
        assertEquals("https://example.com/checksum", parsed.single().checksumAssetDownloadUrl)
    }

    @Test
    fun `selectNewestUpdate prefers highest semantic version over publish date`() {
        val update = selectNewestUpdate(
            candidates = listOf(
                candidate(version = "1.2.0", publishedAt = "2026-05-01T10:00:00Z"),
                candidate(version = "1.10.0", publishedAt = "2026-04-01T10:00:00Z"),
            ),
            currentVersion = "1.1.0",
        )

        assertEquals("1.10.0", update?.versionName)
    }

    @Test
    fun `selectNewestUpdate falls back to newest published non current release when semver compare is unavailable`() {
        val update = selectNewestUpdate(
            candidates = listOf(
                candidate(version = "debug-build", publishedAt = "2026-05-02T10:00:00Z"),
                candidate(version = "release-candidate", publishedAt = "2026-05-04T10:00:00Z"),
                candidate(version = "1.0.0", publishedAt = "2026-05-01T10:00:00Z"),
            ),
            currentVersion = "1.0.0",
        )

        assertEquals("release-candidate", update?.versionName)
    }

    @Test
    fun `selectNewestUpdate returns null when every candidate matches current version`() {
        val update = selectNewestUpdate(
            candidates = listOf(
                candidate(version = "1.0.0", publishedAt = "2026-05-01T10:00:00Z"),
                candidate(version = "v1.0.0", publishedAt = "2026-05-02T10:00:00Z"),
            ),
            currentVersion = "1.0.0",
        )

        assertNull(update)
    }

    private fun asset(
        name: String,
        contentType: String = "application/octet-stream",
        downloadUrl: String = "https://example.com/$name",
        digest: String? = null,
    ): ReleaseAssetMetadata = ReleaseAssetMetadata(
        name = name,
        contentType = contentType,
        browserDownloadUrl = downloadUrl,
        sizeBytes = 42L,
        digest = digest,
    )

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<ReleaseAssetMetadata> = emptyList(),
    ): ReleaseMetadata = ReleaseMetadata(
        tagName = tag,
        name = tag,
        draft = draft,
        prerelease = prerelease,
        publishedAt = Instant.parse("2026-05-01T10:00:00Z"),
        releaseUrl = "https://example.com/releases/$tag",
        releaseNotes = "notes for $tag",
        assets = assets,
    )

    private fun candidate(version: String, publishedAt: String): ReleaseCandidate = ReleaseCandidate(
        versionName = version,
        publishedAt = Instant.parse(publishedAt),
        releaseUrl = "https://example.com/releases/$version",
        releaseNotes = "",
        assetName = "app-release.apk",
        assetDownloadUrl = "https://example.com/$version/app-release.apk",
        assetSizeBytes = 42L,
        sha256Digest = null,
        checksumAssetDownloadUrl = null,
    )
}

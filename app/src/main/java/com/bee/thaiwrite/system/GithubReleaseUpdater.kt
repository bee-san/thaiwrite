package com.bee.thaiwrite.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bee.thaiwrite.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class GithubReleaseUpdate(
    val currentVersionName: String,
    val latestVersionName: String,
    val releaseUrl: String,
    val releaseNotes: String,
    val assetName: String,
    val assetDownloadUrl: String,
    val assetSizeBytes: Long,
    val sha256Digest: String?,
    val checksumAssetDownloadUrl: String?,
)

class GithubReleaseUpdater(context: Context) {
    private val appContext = context.applicationContext
    private val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }

    fun isSupported(): Boolean =
        BuildConfig.GITHUB_UPDATER_ENABLED &&
            BuildConfig.GITHUB_OWNER.isNotBlank() &&
            BuildConfig.GITHUB_REPO.isNotBlank()

    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun openReleasePage(releaseUrl: String? = null) {
        val targetUrl = releaseUrl?.takeIf { it.isNotBlank() }
            ?: "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun launchInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(intent)
    }

    suspend fun checkForUpdate(): GithubReleaseUpdate? = withContext(Dispatchers.IO) {
        if (!isSupported()) {
            return@withContext null
        }

        val connection = openConnection(
            url = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases?per_page=20",
            acceptJson = true,
        )
        connection.useChecked { checked ->
            val currentVersion = normalizeVersionName(BuildConfig.VERSION_NAME)
            val releases = JSONArray(checked.readText())
            val candidates = parseReleaseCandidates(releases)
            val update = selectNewestUpdate(candidates, currentVersion) ?: return@withContext null
            return@withContext GithubReleaseUpdate(
                currentVersionName = currentVersion,
                latestVersionName = update.versionName,
                releaseUrl = update.releaseUrl,
                releaseNotes = update.releaseNotes,
                assetName = update.assetName,
                assetDownloadUrl = update.assetDownloadUrl,
                assetSizeBytes = update.assetSizeBytes,
                sha256Digest = update.sha256Digest,
                checksumAssetDownloadUrl = update.checksumAssetDownloadUrl,
            )
        }
    }

    suspend fun downloadUpdateApk(
        update: GithubReleaseUpdate,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        updatesDir.mkdirs()
        val targetFile = File(updatesDir, sanitizeAssetName(update.assetName))
        updatesDir.listFiles()?.forEach { existing ->
            if (existing.name != targetFile.name) {
                existing.delete()
            }
        }
        val connection = openConnection(update.assetDownloadUrl, acceptJson = false)
        connection.useChecked { checked ->
            val totalBytes = checked.contentLengthLong.takeIf { it > 0 } ?: update.assetSizeBytes
            checked.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress(((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100))
                        }
                    }
                    output.flush()
                }
            }
        }

        val expectedDigest = update.sha256Digest ?: update.checksumAssetDownloadUrl?.let(::downloadChecksumDigest)
        expectedDigest?.let { expected ->
            val actual = sha256(targetFile)
            if (!actual.equals(expected, ignoreCase = true)) {
                targetFile.delete()
                throw IllegalStateException("Downloaded APK digest did not match the expected release checksum.")
            }
        }

        onProgress(100)
        return@withContext targetFile
    }

    private fun openConnection(url: String, acceptJson: Boolean): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ThaiWrite/${BuildConfig.VERSION_NAME}")
            if (acceptJson) {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            }
        }

    private fun parseReleaseCandidates(releases: JSONArray): List<ReleaseCandidate> {
        val parsed = mutableListOf<ReleaseCandidate>()
        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                continue
            }
            val versionName = normalizeVersionName(
                release.optString("tag_name").ifBlank { release.optString("name") },
            )
            if (versionName.isBlank()) {
                continue
            }
            val assets = release.optJSONArray("assets")
            val asset = findApkAsset(assets) ?: continue
            parsed += ReleaseCandidate(
                versionName = versionName,
                publishedAt = release.optString("published_at")
                    .takeIf { it.isNotBlank() }
                    ?.let(Instant::parse),
                releaseUrl = release.optString("html_url"),
                releaseNotes = release.optString("body"),
                assetName = asset.optString("name"),
                assetDownloadUrl = asset.optString("browser_download_url"),
                assetSizeBytes = asset.optLong("size"),
                sha256Digest = asset.optString("digest")
                    .takeIf { it.startsWith("sha256:") }
                    ?.removePrefix("sha256:"),
                checksumAssetDownloadUrl = findChecksumAsset(assets, asset.optString("name"))
                    ?.optString("browser_download_url")
                    ?.takeIf { it.isNotBlank() },
            )
        }
        return parsed
    }

    private fun selectNewestUpdate(
        candidates: List<ReleaseCandidate>,
        currentVersion: String,
    ): ReleaseCandidate? {
        val newerByVersion = candidates
            .filter { compareVersionNames(it.versionName, currentVersion) > 0 }
            .maxWithOrNull(
                Comparator { left, right ->
                    val versionComparison = compareVersionNames(left.versionName, right.versionName)
                    if (versionComparison != 0) {
                        versionComparison
                    } else {
                        (left.publishedAt ?: Instant.EPOCH).compareTo(right.publishedAt ?: Instant.EPOCH)
                    }
                },
            )
        if (newerByVersion != null) {
            return newerByVersion
        }

        return candidates
            .filter { normalizeVersionName(it.versionName) != currentVersion }
            .maxByOrNull { it.publishedAt ?: Instant.EPOCH }
    }

    private fun findApkAsset(assets: JSONArray?): JSONObject? {
        if (assets == null) {
            return null
        }
        val apkAssets = mutableListOf<JSONObject>()
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            val contentType = asset.optString("content_type")
            val isApk = name.endsWith(".apk", ignoreCase = true) || contentType == "application/vnd.android.package-archive"
            if (!isApk) {
                continue
            }
            if (name.contains("debug", ignoreCase = true) || name.contains("unsigned", ignoreCase = true)) {
                continue
            }
            apkAssets += asset
        }

        val exact = apkAssets.firstOrNull { it.optString("name").equals("app-release.apk", ignoreCase = true) }
        if (exact != null) {
            return exact
        }

        if (apkAssets.size == 1) {
            return apkAssets.first()
        }

        val releaseNamed = apkAssets.filter { it.optString("name").contains("release", ignoreCase = true) }
        if (releaseNamed.size == 1) {
            return releaseNamed.first()
        }
        if (releaseNamed.size > 1) {
            throw IllegalStateException("GitHub release contains multiple release APK assets. Expected exactly one.")
        }
        if (apkAssets.isEmpty()) {
            return null
        }
        throw IllegalStateException("GitHub release contains multiple APK assets. Expected exactly one installable release APK.")
    }

    private fun findChecksumAsset(assets: JSONArray?, apkAssetName: String): JSONObject? {
        if (assets == null || apkAssetName.isBlank()) {
            return null
        }
        val expectedNames = setOf(
            "$apkAssetName.sha256",
            "$apkAssetName.sha256.txt",
        )
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            if (name in expectedNames) {
                return asset
            }
        }
        return null
    }

    private fun sanitizeAssetName(assetName: String): String =
        assetName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun downloadChecksumDigest(url: String): String? {
        val connection = openConnection(url, acceptJson = false)
        connection.useChecked { checked ->
            val body = checked.readText()
            return extractSha256Digest(body)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        fun normalizeVersionName(raw: String): String = raw.trim().removePrefix("v")

        fun isNewerVersion(candidate: String, current: String): Boolean {
            return compareVersionNames(candidate, current) > 0
        }

        fun compareVersionNames(left: String, right: String): Int {
            val leftParts = extractNumericParts(left)
            val rightParts = extractNumericParts(right)
            if (leftParts.isEmpty() || rightParts.isEmpty()) {
                return 0
            }
            val maxLength = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until maxLength) {
                val leftPart = leftParts.getOrElse(index) { 0 }
                val rightPart = rightParts.getOrElse(index) { 0 }
                if (leftPart != rightPart) {
                    return leftPart.compareTo(rightPart)
                }
            }
            return 0
        }

        internal fun extractNumericParts(value: String): List<Int> =
            Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()

        internal fun extractSha256Digest(value: String): String? =
            Regex("([A-Fa-f0-9]{64})").find(value)?.groupValues?.get(1)?.lowercase()
    }
}

private data class ReleaseCandidate(
    val versionName: String,
    val publishedAt: Instant?,
    val releaseUrl: String,
    val releaseNotes: String,
    val assetName: String,
    val assetDownloadUrl: String,
    val assetSizeBytes: Long,
    val sha256Digest: String?,
    val checksumAssetDownloadUrl: String?,
)

private inline fun <T> HttpURLConnection.useChecked(block: (HttpURLConnection) -> T): T {
    try {
        val responseCode = responseCode
        if (responseCode !in 200..299) {
            val errorBody = errorStream?.bufferedReader()?.use { it.readText() }
            throw IllegalStateException(
                "GitHub request failed with HTTP $responseCode${errorBody?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}",
            )
        }
        return block(this)
    } finally {
        disconnect()
    }
}

private fun HttpURLConnection.readText(): String =
    inputStream.bufferedReader().use { it.readText() }

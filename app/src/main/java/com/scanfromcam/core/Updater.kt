package com.scanfromcam.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One published APK, as the update sheet needs it. */
data class AppRelease(
    val version: String,
    val notes: String,
    val sizeBytes: Long,
    val assetName: String,
    val downloadUrl: String,
    val digest: String?,
)

data class DownloadProgress(val bytes: Long, val total: Long, val bytesPerSecond: Long) {
    val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: DownloadProgress) : UpdateState
    data class Ready(val release: AppRelease, val file: File) : UpdateState
    data class NeedsPermission(val release: AppRelease, val file: File) : UpdateState
    /** [manual] failures are shown; automatic ones stay silent. */
    data class Failed(val kind: Failure, val manual: Boolean = false) : UpdateState
}

enum class Failure { Network, RateLimited, NoAsset, Integrity, Storage }

/**
 * Checks the GitHub releases of the project, downloads the APK that matches this
 * device and hands it to the system installer. Every byte of network the app uses
 * goes through [fetch], and [check] returns before touching it unless the user has
 * said yes to updates.
 */
class Updater(
    private val context: Context,
    private val settings: AppSettings,
    private val fetch: (String, String?) -> HttpResponse = ::httpGet,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    /** Called at launch: silent, rate limited and a no-op without consent. */
    fun checkAutomatic() {
        if (settings.updateConsent != true) return
        if (System.currentTimeMillis() - settings.lastCheckAt < CHECK_INTERVAL_MS) return
        scope.launch { check(manual = false) }
    }

    /** Called from Settings; the caller has already got consent. */
    fun checkManually() {
        if (settings.updateConsent != true) return
        scope.launch { check(manual = true) }
    }

    fun startDownload() {
        val release = (_state.value as? UpdateState.Available)?.release ?: return
        job?.cancel()
        job = scope.launch { download(release) }
    }

    fun cancelDownload() {
        val downloading = _state.value as? UpdateState.Downloading ?: return
        job?.cancel()
        job = null
        File(updateDir(), downloading.release.assetName).delete()
        _state.value = UpdateState.Available(downloading.release)
    }

    fun deleteDownload() {
        val ready = _state.value
        val file = when (ready) {
            is UpdateState.Ready -> ready.file
            is UpdateState.NeedsPermission -> ready.file
            else -> null
        }
        file?.delete()
        settings.pendingVersionCode = null
        _state.value = UpdateState.Idle
    }

    /** "Not now": this version stops asking by itself. */
    fun dismiss() {
        val current = _state.value
        if (current is UpdateState.Available) settings.skippedVersion = current.release.version
        _state.value = UpdateState.Idle
    }

    fun install(activity: Activity) {
        when (val current = _state.value) {
            is UpdateState.Ready -> installNow(activity, current.release, current.file)
            is UpdateState.NeedsPermission -> installNow(activity, current.release, current.file)
            else -> Unit
        }
    }

    private fun installNow(activity: Activity, release: AppRelease, file: File) {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(release, file)
            return
        }
        installApk(activity, file)
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the per-app "install unknown apps" screen (API 26+ has one). */
    fun openInstallSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${activity.packageName}"),
        )
        runCatching { activity.startActivity(intent) }
    }

    /** Back from the system settings: the permission may have been granted. */
    fun refreshInstallPermission() {
        val pending = _state.value as? UpdateState.NeedsPermission ?: return
        if (canInstall()) _state.value = UpdateState.Ready(pending.release, pending.file)
    }

    /** A downloaded APK without a live flow behind it is dead weight. */
    fun cleanCache() {
        updateDir().listFiles()?.forEach { it.delete() }
        settings.pendingVersionCode = null
    }

    // ---- network -------------------------------------------------------

    private suspend fun check(manual: Boolean) {
        if (settings.updateConsent != true) return
        _state.value = UpdateState.Checking
        val response = try {
            fetch(API_URL, settings.updateEtag)
        } catch (_: Exception) {
            null
        }
        when {
            response == null -> fail(manual, Failure.Network)
            response.code == HttpURLConnection.HTTP_NOT_MODIFIED ->
                resolve(settings.lastReleaseJson, manual)
            response.code == HttpURLConnection.HTTP_OK && response.body != null -> {
                settings.lastReleaseJson = response.body
                settings.updateEtag = response.etag
                resolve(response.body, manual)
            }
            response.code == 403 || response.code == 429 -> fail(manual, Failure.RateLimited)
            response.code == HttpURLConnection.HTTP_NOT_FOUND -> _state.value = UpdateState.UpToDate
            else -> fail(manual, Failure.Network)
        }
        if (response != null) settings.lastCheckAt = System.currentTimeMillis()
    }

    private fun resolve(json: String?, manual: Boolean) {
        val release = json?.let(::parseRelease)
        if (release == null) {
            _state.value = UpdateState.UpToDate
            return
        }
        if (!isNewerVersion(release.version, installedVersionName())) {
            _state.value = UpdateState.UpToDate
            return
        }
        if (!manual && release.version == settings.skippedVersion) {
            _state.value = UpdateState.UpToDate
            return
        }
        _state.value = UpdateState.Available(release)
        if (settings.autoDownload) startDownload()
    }

    private fun fail(manual: Boolean, kind: Failure) {
        _state.value = if (manual) UpdateState.Failed(kind, manual = true) else UpdateState.UpToDate
    }

    private fun parseRelease(json: String): AppRelease? = runCatching {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val version = root.getString("tag_name").removePrefix("v").removePrefix("V")
        val assets = root.optJSONArray("assets") ?: return null
        val rows = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
        val names = rows.map { it.optString("name") }
        val assetName = pickAssetName(version, Build.SUPPORTED_ABIS.toList(), names) ?: return null
        val asset = rows.firstOrNull { it.optString("name") == assetName } ?: return null
        AppRelease(
            version = version,
            notes = root.optString("body").orEmpty(),
            sizeBytes = asset.optLong("size"),
            assetName = assetName,
            downloadUrl = asset.getString("browser_download_url"),
            digest = asset.optString("digest").takeIf { it.startsWith("sha256:") },
        )
    }.getOrNull()

    // ---- download ------------------------------------------------------

    private suspend fun download(release: AppRelease) {
        val dir = updateDir().apply { mkdirs() }
        val target = File(dir, release.assetName)
        _state.value = UpdateState.Downloading(release, DownloadProgress(0, release.sizeBytes, 0))
        val connection = runCatching {
            (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", userAgent())
            }
        }.getOrNull() ?: run {
            _state.value = UpdateState.Failed(Failure.Network)
            return
        }
        try {
            if (connection.responseCode !in 200..299) {
                _state.value = UpdateState.Failed(Failure.Network)
                return
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            if (total > 0 && freeBytes(dir) < total * 12 / 10) {
                _state.value = UpdateState.Failed(Failure.Storage)
                return
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val started = System.currentTimeMillis()
            var done = 0L
            var pushed = 0L
            target.outputStream().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - pushed >= 80) {
                            pushed = now
                            pushProgress(release, done, total, now - started)
                        }
                    }
                }
            }
            pushProgress(release, done, total, System.currentTimeMillis() - started)
            val expected = release.digest?.removePrefix("sha256:")?.lowercase()
            val actual = hex(digest.digest())
            if (expected != null && expected != actual) {
                target.delete()
                _state.value = UpdateState.Failed(Failure.Integrity)
                return
            }
            if (release.sizeBytes > 0 && target.length() != release.sizeBytes) {
                target.delete()
                _state.value = UpdateState.Failed(Failure.Integrity)
                return
            }
            settings.pendingVersionCode = apkVersionCode(target)
            _state.value = if (canInstall()) {
                UpdateState.Ready(release, target)
            } else {
                UpdateState.NeedsPermission(release, target)
            }
        } catch (_: Exception) {
            target.delete()
            _state.value = UpdateState.Failed(Failure.Network)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun pushProgress(release: AppRelease, done: Long, total: Long, elapsedMs: Long) {
        val perSecond = if (elapsedMs > 0) done * 1000 / elapsedMs else 0
        _state.value = UpdateState.Downloading(release, DownloadProgress(done, total, perSecond))
    }

    // ---- device --------------------------------------------------------

    private fun updateDir(): File = File(context.cacheDir, UPDATE_DIR)

    private fun freeBytes(dir: File): Long = runCatching {
        StatFs(dir.absolutePath).availableBytes
    }.getOrDefault(0L)

    private fun installedVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private fun apkVersionCode(file: File): Long? = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrNull()

    private fun userAgent(): String = "ScanFromCam/${installedVersionName()}"

    companion object {
        private const val API_URL = "https://api.github.com/repos/JaMoLpE88/ScanFromCam/releases/latest"
        private const val UPDATE_DIR = "updates"
        private const val TIMEOUT_MS = 8_000
        private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L
    }
}

/** Status line and body of one GitHub API call. */
class HttpResponse(val code: Int, val body: String?, val etag: String?)

internal val SUPPORTED_ABIS = listOf("arm64-v8a", "x86_64")

internal fun httpGet(url: String, etag: String?): HttpResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "ScanFromCam")
        if (etag != null) connection.setRequestProperty("If-None-Match", etag)
        val code = connection.responseCode
        val body = if (code == HttpURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
        return HttpResponse(code, body, connection.getHeaderField("ETag"))
    } finally {
        connection.disconnect()
    }
}

/** Name of the APK published for this version and one of [abis], or null. */
internal fun pickAssetName(version: String, abis: List<String>, available: List<String>): String? {
    val abi = abis.firstOrNull { it in SUPPORTED_ABIS } ?: return null
    return "ScanFromCam-$version-$abi.apk".takeIf { it in available }
}

/** A remote download is only offered when it is strictly ahead of [local]. */
internal fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = versionNumbers(remote) ?: return false
    val localParts = versionNumbers(local) ?: return false
    for (index in 0 until maxOf(remoteParts.size, localParts.size)) {
        val left = remoteParts.getOrElse(index) { 0 }
        val right = localParts.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun versionNumbers(value: String): List<Int>? {
    val core = value.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
    val parts = core.split('.')
    if (parts.isEmpty() || parts.size > 4) return null
    return parts.map { it.toIntOrNull() ?: return null }
}

private val LINK = Regex("\\[([^]]+)]\\([^)]*\\)")
private val HEADING = Regex("^#+\\s*")

/**
 * The release body is a whole page. The sheet only wants the notes: the section
 * for the current language, then the other one, then the first readable lines.
 */
internal fun extractNotes(body: String, spanish: Boolean): String {
    val preferred = if (spanish) "## novedades" else "## what's new"
    val fallback = if (spanish) "## what's new" else "## novedades"
    val section = sectionAfter(body, preferred) ?: sectionAfter(body, fallback)
    return cleanNotes(section ?: body.lines())
}

private fun sectionAfter(body: String, heading: String): List<String>? {
    val lines = body.lines()
    val start = lines.indexOfFirst { it.trim().lowercase().startsWith(heading) }
    if (start < 0) return null
    val section = lines.drop(start + 1).takeWhile { !it.trimStart().startsWith("## ") }
    return section.takeIf { it.any { line -> line.isNotBlank() } }
}

private fun cleanNotes(lines: List<String>): String = lines.asSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("|") && it != "---" && it != "***" }
    .map { line ->
        val text = line.replace(HEADING, "").replace("**", "").replace("`", "").replace(LINK, "$1")
        if (text.startsWith("- ") || text.startsWith("* ")) "• " + text.drop(2) else text
    }
    .take(40)
    .joinToString("\n")

internal fun hex(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(it) }

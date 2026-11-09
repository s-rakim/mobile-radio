package com.srakim.spitify.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.srakim.spitify.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Over-the-air updates from the repo's GitHub Releases. CI publishes every build there as
 * v1.0.<run number> with the APK attached, and the run number is also the version code, so
 * comparing the two says whether a newer build exists.
 *
 * Android still shows its own install screen; an app can't update itself silently. Builds only
 * install over each other when they're signed with the same key (see docs/BUILDING.md).
 */
class AppUpdater(private val context: Context, client: OkHttpClient) {

    data class Release(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Available(val release: Release) : Status
        data class Downloading(val release: Release, val progress: Float) : Status
        /** Downloaded, waiting for the listener to allow installs from Spitify. */
        data class NeedsPermission(val release: Release, val apk: File) : Status
        data class Failed(val message: String) : Status
    }

    private val http = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val dir = File(context.cacheDir, "updates")

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Local debug builds have their own package name and can't take a release APK. */
    val supported: Boolean get() = !BuildConfig.APPLICATION_ID.endsWith(".debug")

    /** Release the listener said "Later" to. The automatic check stays quiet about it. */
    val skippedVersion: Int get() = prefs.getInt(KEY_SKIPPED, 0)

    /** Checks at most every few hours, quietly. Called when the app opens. */
    fun checkIfDue() {
        if (!supported) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return
        check(manual = false)
    }

    /** A manual check from Settings also brings back a release that was put off. */
    fun check(manual: Boolean = true) {
        if (!supported || _status.value is Status.Checking || _status.value is Status.Downloading) return
        if (manual) prefs.edit().remove(KEY_SKIPPED).apply()
        _status.value = Status.Checking
        io.launch {
            _status.value = runCatching { latest() }.fold(
                onSuccess = { release ->
                    prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                    when {
                        release == null || release.versionCode <= BuildConfig.VERSION_CODE -> Status.UpToDate
                        !manual && release.versionCode == skippedVersion -> Status.Idle
                        else -> Status.Available(release)
                    }
                },
                onFailure = { if (manual) Status.Failed(it.message ?: "Couldn't check for updates") else Status.Idle },
            )
        }
    }

    fun later(release: Release) {
        prefs.edit().putInt(KEY_SKIPPED, release.versionCode).apply()
        _status.value = Status.Idle
    }

    fun dismiss() {
        _status.value = Status.Idle
    }

    /** Downloads the APK and hands it to Android's installer. */
    fun install(release: Release) {
        val ready = File(dir, apkName(release))
        if (ready.exists() && ready.length() > 0) {
            launchInstaller(release, ready)
            return
        }
        _status.value = Status.Downloading(release, 0f)
        io.launch {
            runCatching { download(release) }
                .onSuccess { launchInstaller(release, it) }
                .onFailure { _status.value = Status.Failed(it.message ?: "Couldn't download the update") }
        }
    }

    private fun latest(): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("GitHub answered with ${response.code}")
            response.body?.string().orEmpty()
        }
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        val tag = root.text("tag_name") ?: return null
        val code = tag.substringAfterLast('.').toIntOrNull() ?: return null
        val apk = root.array("assets").firstOrNull { it.text("name").orEmpty().endsWith(".apk") }
            ?.text("browser_download_url") ?: return null
        return Release(code, tag.removePrefix("v"), apk, root.text("body").orEmpty())
    }

    private fun download(release: Release): File {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // old updates aren't needed any more
        val target = File(dir, apkName(release))
        val part = File(dir, target.name + ".part")
        http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
            val body = response.body ?: throw IOException("Download failed")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = 0f
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total != null) {
                            val progress = done.toFloat() / total
                            if (progress - lastShown >= 0.02f) {
                                lastShown = progress
                                _status.value = Status.Downloading(release, progress)
                            }
                        }
                    }
                }
            }
        }
        if (!part.renameTo(target)) throw IOException("Couldn't save the update")
        return target
    }

    private fun launchInstaller(release: Release, apk: File) {
        // Android 8+ asks once whether Spitify may install apps. Send the listener there first.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _status.value = Status.NeedsPermission(release, apk)
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        _status.value = runCatching { context.startActivity(intent) }
            .fold({ Status.Idle }, { Status.Failed("Couldn't open the installer") })
    }

    private fun apkName(release: Release) = "Spitify-${release.versionName}.apk"

    private companion object {
        const val REPO = "s-rakim/mobile-radio"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_SKIPPED = "skipped_version"
        const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    }
}

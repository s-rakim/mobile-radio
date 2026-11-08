package com.srakim.spitify.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Opens a primary address and, if that fails, a backup carried in the URI fragment
 * (`#fallback=<url>`). The fragment never reaches the network: it's stripped before the
 * request goes out.
 */
@OptIn(UnstableApi::class)
class FallbackDataSource(private val upstream: DataSource) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val fragment = dataSpec.uri.fragment
        if (fragment == null || !fragment.startsWith(KEY)) return upstream.open(dataSpec)
        val backup = Uri.parse(fragment.removePrefix(KEY))
        val primary = dataSpec.uri.buildUpon().fragment(null).build()
        return try {
            upstream.open(dataSpec.withUri(primary))
        } catch (e: IOException) {
            runCatching { upstream.close() }
            upstream.open(dataSpec.withUri(backup))
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = FallbackDataSource(upstream.createDataSource())
    }

    companion object {
        private const val KEY = "fallback="

        fun withFallback(primary: Uri, backup: Uri): Uri =
            primary.buildUpon().fragment(KEY + backup.toString()).build()
    }
}

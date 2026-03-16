package com.ehviewer.core.files

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import java.util.concurrent.ConcurrentHashMap
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import okio.Path
import okio.Path.Companion.toPath

const val CIFS_DOCUMENT_AUTHORITY = "com.wa2c.android.cifsdocumentsprovider.documents"
private const val WEBP_HEADER_SCAN_BYTES = 32 * 1024
private const val WEBP_ANIMATION_FLAG = 0x02
private val RIFF_TAG = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
private val WEBP_TAG = byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())
private val VP8X_TAG = byteArrayOf('V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'X'.code.toByte())
private val ANIM_TAG = byteArrayOf('A'.code.toByte(), 'N'.code.toByte(), 'I'.code.toByte(), 'M'.code.toByte())

fun Path.openFileDescriptor(mode: String) = PlatformSystemFileSystem.openFileDescriptor(this, mode)

actual inline fun <T> Path.read(f: Source.() -> T) = PlatformSystemFileSystem.rawSource(this).buffered().use(f)

actual inline fun <T> Path.write(f: Sink.() -> T) = PlatformSystemFileSystem.rawSink(this).buffered().use(f)

fun Path.isCifsDocumentPath() = !toString().startsWith('/') && toUri().authority == CIFS_DOCUMENT_AUTHORITY

fun Path.isAnimatedForReader(typeHint: String?): Boolean {
    val extension = (typeHint ?: name.substringAfterLast('.', "")).lowercase()
    return when (extension) {
        "gif" -> true
        "webp" -> hasAnimatedWebpHeader()
        else -> false
    }
}

fun Path.toUri(): Uri {
    val str = toString()
    if (str.startsWith('/')) {
        return toFile().toUri()
    }

    val uri = str.replaceFirst("content:/", "content://").toUri()
    val path = requireNotNull(uri.encodedPath) { "Invalid path: $str" }
    val paths = path.split('/').dropWhile { it.isEmpty() }
    return if (paths.size > 4 && paths[0] == "tree") {
        uri.buildUpon().apply {
            path(null)
            repeat(3) { i ->
                appendEncodedPath(paths[i])
            }
            val root = Uri.decode(paths[3])
            val prefix = if (root.endsWith(':')) root else "$root/"
            val suffix = uri.encodedFragment?.let { "#$it" }.orEmpty()
            appendPath(paths.subList(4, paths.size).joinToString("/", prefix, suffix))
        }.build()
    } else {
        uri
    }
}

fun Uri.toOkioPath() = if (scheme == ContentResolver.SCHEME_FILE) {
    requireNotNull(path) { "Invalid URI: $this" }
} else {
    toString()
}.toPath()

private val animatedWebpCache = ConcurrentHashMap<String, Boolean>()

private fun Path.hasAnimatedWebpHeader(): Boolean {
    val key = toString()
    animatedWebpCache[key]?.let { return it }
    return runCatching { hasAnimatedWebpHeaderUncached() }.getOrDefault(false)
        .also { animatedWebpCache[key] = it }
}

private fun Path.hasAnimatedWebpHeaderUncached() =
    ParcelFileDescriptor.AutoCloseInputStream(openFileDescriptor("r")).use { input ->
        val header = ByteArray(WEBP_HEADER_SCAN_BYTES)
        var total = 0
        while (total < header.size) {
            val read = input.read(header, total, header.size - total)
            if (read <= 0) break
            total += read
        }
        if (total < 21 || !header.matchesTag(0, RIFF_TAG) || !header.matchesTag(8, WEBP_TAG)) {
            return@use false
        }

        // Extended WebP uses a bitfield where bit 1 means animation.
        if (header.matchesTag(12, VP8X_TAG) && header[20].toInt() and WEBP_ANIMATION_FLAG != 0) {
            return@use true
        }
        header.indexOfTag(ANIM_TAG, total, start = 12) >= 0
    }

private fun ByteArray.matchesTag(offset: Int, tag: ByteArray): Boolean {
    if (offset < 0 || offset + tag.size > size) return false
    for (i in tag.indices) {
        if (this[offset + i] != tag[i]) return false
    }
    return true
}

private fun ByteArray.indexOfTag(tag: ByteArray, count: Int, start: Int): Int {
    if (count < tag.size || start > count - tag.size) return -1
    for (idx in start..count - tag.size) {
        if (matchesTag(idx, tag)) return idx
    }
    return -1
}

package com.hippo.ehviewer.library.thumbs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import coil3.BitmapImage
import coil3.DrawableImage
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.isAnimatedForReader
import com.ehviewer.core.files.isFile
import com.ehviewer.core.files.list
import com.ehviewer.core.files.metadataOrNull
import com.ehviewer.core.files.openFileDescriptor
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.download.archiveFile
import com.hippo.ehviewer.download.downloadDir
import com.hippo.ehviewer.gallery.useArchivePageLoader
import com.hippo.ehviewer.image.Image
import com.hippo.ehviewer.image.PathSource
import com.hippo.ehviewer.library.content.LocalGalleryContent
import com.hippo.ehviewer.util.AppConfig
import com.hippo.ehviewer.util.FileUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

private val archiveThumbMutex = Mutex()
private const val MAX_THUMBNAIL_CANDIDATE_PAGES = 5
private const val LOCAL_THUMB_FILE_PREFIX = "thumb-"
private const val STATIC_THUMB_EXTENSION = "png"

private val Path.isPhysicalPath get() = toString().startsWith('/')

private fun Path.extension() = name.substringAfterLast('.', "").lowercase()

private fun Path.thumbCandidates() = list().filter { it.name.startsWith("thumb.") && it.isFile }

private fun shouldStaticize(path: Path): Boolean {
    if (!path.isFile) return true
    val extension = path.extension()
    return extension == "gif" || (extension == "webp" && path.isAnimatedForReader(extension))
}

private fun Path.findAnySidecarThumbFile() = thumbCandidates().firstOrNull()

private fun Path.findImmediateSidecarThumbFile() = thumbCandidates().firstOrNull { !shouldStaticize(it) }

private fun localThumbFilePrefix(gid: Long) = "$LOCAL_THUMB_FILE_PREFIX$gid."

private fun findCachedThumbFile(gid: Long) = AppConfig.downloadThumbCacheDir.list().firstOrNull {
    it.name.startsWith(localThumbFilePrefix(gid)) && it.isFile
}

private fun buildCachedThumbPath(gid: Long, extension: String) = AppConfig.downloadThumbCacheDir / "$LOCAL_THUMB_FILE_PREFIX$gid.${extension.lowercase()}"

private fun pruneCachedThumbVariants(gid: Long, keep: Path) {
    AppConfig.downloadThumbCacheDir.list().forEach { path ->
        if (path != keep && path.name.startsWith(localThumbFilePrefix(gid)) && path.isFile) {
            path.delete()
        }
    }
}

private fun pruneSidecarThumbVariants(dir: Path, keep: Path) {
    dir.list().forEach { path ->
        if (path != keep && path.name.startsWith("thumb.") && path.isFile) {
            path.delete()
        }
    }
}

fun findLocalThumbFileInternal(info: DownloadInfo): Path? = info.downloadDir?.let { dir ->
    dir.findImmediateSidecarThumbFile()?.let { return it }
    if (dir.findAnySidecarThumbFile() != null) {
        null
    } else {
        findCachedThumbFile(info.gid)
    }
} ?: findCachedThumbFile(info.gid)

fun localThumbCacheKeyInternal(info: DownloadInfo, path: Path): String {
    val lastModified = path.metadataOrNull()?.lastModifiedAtMillis ?: 0L
    return "download-thumb:${info.gid}:${path.name}:$lastModified"
}

private fun buildThumbPath(info: DownloadInfo, dir: Path, extension: String) = if (dir.isPhysicalPath) {
    dir / "thumb.${extension.lowercase()}"
} else {
    buildCachedThumbPath(info.gid, extension)
}

private fun pruneThumbVariants(gid: Long, dir: Path, keep: Path) {
    if (dir.isPhysicalPath) {
        pruneSidecarThumbVariants(dir, keep)
    } else {
        pruneCachedThumbVariants(gid, keep)
    }
}

private fun shouldStaticize(extension: String) = extension.lowercase() in setOf("gif", "webp")

private fun isOutdated(target: Path, source: Path): Boolean {
    if (!target.isFile) return true
    val sourceModified = source.metadataOrNull()?.lastModifiedAtMillis
    val targetModified = target.metadataOrNull()?.lastModifiedAtMillis
    return sourceModified != null && targetModified != null && sourceModified > targetModified
}

private suspend fun writeStaticThumb(source: PathSource, target: Path): Path? = runCatching {
    val image = Image.decode(source)
    try {
        val bitmap = when (val coilImage = image.innerImage) {
            is BitmapImage -> coilImage.bitmap
            is DrawableImage -> {
                val width = coilImage.width.coerceAtLeast(1)
                val height = coilImage.height.coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    val canvas = Canvas(this)
                    coilImage.drawable.setBounds(0, 0, width, height)
                    coilImage.drawable.draw(canvas)
                }
            }
            else -> return@runCatching null
        }
        ParcelFileDescriptor.AutoCloseOutputStream(target.openFileDescriptor("wt")).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Failed to encode thumbnail to $target" }
        }
        target.takeIf { it.isFile }
    } finally {
        image.unpin()
    }
}.onFailure {
    logcat("DownloadThumbs", it)
}.getOrNull()

private suspend fun writeStaticThumbFromLocalContent(content: LocalGalleryContent, index: Int, target: Path) = runCatching {
    content.getImageSource(index).use { source ->
        writeStaticThumb(source, target)
    }
}.onFailure {
    logcat("DownloadThumbs", it)
}.getOrNull()

private suspend fun writeStaticThumbFromArchive(loader: com.hippo.ehviewer.gallery.PageLoader, gid: Long, index: Int, extension: String, target: Path): Path? {
    val source = AppConfig.tempDir / "thumb-source-$gid-$index.${extension.lowercase()}"
    return runCatching {
        if (source.isFile) {
            source.delete()
        }
        if (!loader.save(index, source)) {
            return@runCatching null
        }
        val pathSource = object : PathSource {
            override val source = source
            override val type = extension
            override fun close() = Unit
        }
        writeStaticThumb(pathSource, target)
    }.onFailure {
        logcat("DownloadThumbs", it)
    }.also {
        if (source.isFile) {
            source.delete()
        }
    }.getOrNull()
}

private suspend fun staticizeSidecarThumb(source: Path, target: Path): Path? {
    val pathSource = object : PathSource {
        override val source = source
        override val type = source.extension()
        override fun close() = Unit
    }
    return writeStaticThumb(pathSource, target)
}

private fun DownloadInfo.localGalleryInfo(): GalleryInfo = galleryInfo

private fun DownloadInfo.thumbnailCandidateIndices(): IntRange {
    val size = pages.takeIf { it > 0 } ?: MAX_THUMBNAIL_CANDIDATE_PAGES
    return 0..<minOf(size, MAX_THUMBNAIL_CANDIDATE_PAGES)
}

private fun thumbnailCandidateIndices(size: Int): IntRange = 0..<minOf(size, MAX_THUMBNAIL_CANDIDATE_PAGES)

suspend fun ensureLocalThumbFileInternal(info: DownloadInfo): Path? {
    val dir = info.downloadDir ?: return null
    findLocalThumbFileInternal(info)?.let { return it }
    val localDirname = info.dirname ?: return null

    dir.findAnySidecarThumbFile()?.let { sidecar ->
        if (!shouldStaticize(sidecar)) {
            return sidecar
        }

        val target = buildThumbPath(info, dir, STATIC_THUMB_EXTENSION)
        if (!isOutdated(target, sidecar)) {
            return target.takeIf { it.isFile }
        }

        val staticized = staticizeSidecarThumb(sidecar, target)
        if (staticized != null) {
            if (!dir.isPhysicalPath) pruneCachedThumbVariants(info.gid, staticized)
            return staticized
        }
    }

    findCachedThumbFile(info.gid)?.let { return it }

    val content = LocalGalleryContent(info.localGalleryInfo(), localDirname)
    content.initDownloadDirIfExist()
    val localThumb = runCatching {
        info.thumbnailCandidateIndices().firstNotNullOfOrNull { index ->
            content.getExtension(index)?.takeIf(String::isNotBlank)?.let { extension ->
                val targetExtension = if (shouldStaticize(extension)) STATIC_THUMB_EXTENSION else extension
                val target = buildThumbPath(info, dir, targetExtension)
                if (target.isFile) {
                    pruneThumbVariants(info.gid, dir, target)
                    target
                } else {
                    val saved = if (shouldStaticize(extension)) {
                        writeStaticThumbFromLocalContent(content, index, target)
                    } else if (content.saveToPath(index, target)) {
                        target.takeIf { it.isFile }
                    } else {
                        null
                    }
                    saved?.also { pruneThumbVariants(info.gid, dir, it) }
                }
            }
        }
    }.onFailure {
        logcat("DownloadThumbs", it)
    }.getOrNull()
    if (localThumb != null) return localThumb

    val archive = info.archiveFile ?: return null
    return runCatching {
        archiveThumbMutex.withLock {
            findLocalThumbFileInternal(info)?.let { return@withLock it }
            useArchivePageLoader(
                file = archive,
                info = info.localGalleryInfo(),
                passwdProvider = { error("Managed archive thumb generation should not require a password") },
            ) { loader ->
                thumbnailCandidateIndices(loader.size).firstNotNullOfOrNull { index ->
                    val extension = loader.getImageFilename(index)
                        ?.let(FileUtils::getExtensionFromFilename)
                        ?.takeIf(String::isNotBlank)
                        ?: "jpg"
                    val targetExtension = if (shouldStaticize(extension)) STATIC_THUMB_EXTENSION else extension
                    val target = buildThumbPath(info, dir, targetExtension)
                    if (target.isFile) {
                        pruneThumbVariants(info.gid, dir, target)
                        target
                    } else {
                        val saved = if (shouldStaticize(extension)) {
                            writeStaticThumbFromArchive(loader, info.gid, index, extension, target)
                        } else if (loader.save(index, target)) {
                            target.takeIf { it.isFile }
                        } else {
                            null
                        }
                        saved?.also { pruneThumbVariants(info.gid, dir, it) }
                    }
                }
            }
        }
    }.onFailure {
        logcat("DownloadThumbs", it)
    }.getOrNull()
}

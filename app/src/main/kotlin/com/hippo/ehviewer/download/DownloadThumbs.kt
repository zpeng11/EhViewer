package com.hippo.ehviewer.download

import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.files.isFile
import com.ehviewer.core.files.list
import com.ehviewer.core.files.metadataOrNull
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.gallery.useArchivePageLoader
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.util.FileUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

private val archiveThumbMutex = Mutex()

private fun Path.findLocalThumbFile() = list().firstOrNull { it.name.startsWith("thumb.") && it.isFile }

fun DownloadInfo.findLocalThumbFile(): Path? = downloadDir?.findLocalThumbFile()

fun DownloadInfo.localThumbCacheKey(path: Path): String {
    val lastModified = path.metadataOrNull()?.lastModifiedAtMillis ?: 0L
    return "download-thumb:$gid:${path.name}:$lastModified"
}

private fun buildThumbPath(dir: Path, extension: String) = dir / "thumb.${extension.lowercase()}"

private fun DownloadInfo.localGalleryInfo(): GalleryInfo = galleryInfo

suspend fun DownloadInfo.ensureLocalThumbFile(): Path? {
    val dir = downloadDir ?: return null
    findLocalThumbFile()?.let { return it }
    val localDirname = dirname ?: return null

    val spiderDen = SpiderDen(localGalleryInfo(), localDirname)
    val localThumb = runCatching {
        spiderDen.getExtension(0)?.takeIf(String::isNotBlank)?.let { extension ->
            val target = buildThumbPath(dir, extension)
            if (target.isFile || spiderDen.saveToPath(0, target)) {
                target.takeIf { it.isFile }
            } else {
                null
            }
        }
    }.onFailure {
        logcat(it)
    }.getOrNull()
    if (localThumb != null) return localThumb

    val archive = archiveFile ?: return null
    return runCatching {
        archiveThumbMutex.withLock {
            findLocalThumbFile()?.let { return@withLock it }
            useArchivePageLoader(
                file = archive,
                info = localGalleryInfo(),
                passwdProvider = { error("Managed archive thumb generation should not require a password") },
            ) { loader ->
                val extension = loader.getImageFilename(0)
                    ?.let(FileUtils::getExtensionFromFilename)
                    ?.takeIf(String::isNotBlank)
                    ?: "jpg"
                val target = buildThumbPath(dir, extension)
                if (target.isFile || loader.save(0, target)) {
                    target.takeIf { it.isFile }
                } else {
                    null
                }
            }
        }
    }.onFailure {
        logcat(it)
    }.getOrNull()
}

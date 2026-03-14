package com.hippo.ehviewer.ui.screen

import android.os.ParcelFileDescriptor
import com.ehviewer.core.files.find
import com.ehviewer.core.files.openFileDescriptor
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.GalleryTagGroup
import com.hippo.ehviewer.client.data.simpleTagsToTagGroups
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.archiveFile
import com.hippo.ehviewer.download.downloadDir
import com.hippo.ehviewer.spider.COMIC_INFO_FILE
import com.hippo.ehviewer.spider.ComicInfo
import com.hippo.ehviewer.spider.readComicInfo
import com.hippo.ehviewer.spider.toTagGroups
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream
import kotlinx.io.Buffer
import net.devrieze.xmlutil.serialization.kxio.decodeFromSource
import nl.adaptivity.xmlutil.serialization.XML
import okio.Path

data class LocalDetailTagInfo(
    val fallbackInfo: GalleryInfo,
    val tagGroups: List<GalleryTagGroup>,
)

private val localComicInfoXml = XML {
    recommended {
        ignoreUnknownChildren()
    }
}

internal suspend fun resolveLocalDetailTagInfo(
    gid: Long,
    fallbackInfo: GalleryInfo?,
): LocalDetailTagInfo? {
    val downloadInfo = DownloadManager.getDownloadInfo(gid) ?: return null
    val detailInfo = fallbackInfo ?: downloadInfo.galleryInfo
    val comicInfo = downloadInfo.downloadDir?.find(COMIC_INFO_FILE)?.let(::readComicInfo)
        ?: downloadInfo.archiveFile?.let(::readComicInfoFromArchive)
    val tagGroups = comicInfo?.toTagGroups() ?: simpleTagsToTagGroups(
        simpleTags = detailInfo.simpleTags ?: downloadInfo.simpleTags,
        simpleLanguage = detailInfo.simpleLanguage ?: downloadInfo.simpleLanguage,
    )
    return LocalDetailTagInfo(
        fallbackInfo = detailInfo,
        tagGroups = tagGroups,
    )
}

private fun readComicInfoFromArchive(file: Path): ComicInfo? = runCatching {
    ParcelFileDescriptor.AutoCloseInputStream(file.openFileDescriptor("r")).use { input ->
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var result: ComicInfo? = null
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (!name.equals(COMIC_INFO_FILE, ignoreCase = true)) continue
                result = Buffer().apply {
                    write(zip.readBytes())
                }.use {
                    localComicInfoXml.decodeFromSource<ComicInfo>(it)
                }
                break
            }
            result
        }
    }
}.getOrNull()

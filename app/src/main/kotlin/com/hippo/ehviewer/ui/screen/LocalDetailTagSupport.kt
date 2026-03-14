package com.hippo.ehviewer.ui.screen

import com.ehviewer.core.files.find
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.GalleryTagGroup
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.client.data.simpleTagsToTagGroups
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.archiveFile
import com.hippo.ehviewer.download.downloadDir
import com.hippo.ehviewer.spider.COMIC_INFO_FILE
import com.hippo.ehviewer.spider.readComicInfo
import com.hippo.ehviewer.spider.readComicInfoFromArchive
import com.hippo.ehviewer.spider.toTagGroups

data class LocalDetailTagInfo(
    val fallbackInfo: GalleryInfo,
    val tagGroups: List<GalleryTagGroup>,
)

internal suspend fun resolveLocalDetailTagInfo(
    gid: Long,
    fallbackInfo: GalleryInfo?,
): LocalDetailTagInfo? {
    val downloadInfo = DownloadManager.getDownloadInfo(gid)
    val dbInfo = EhDB.getGalleryInfoMap(listOf(gid))[gid]
    val detailInfo = fallbackInfo ?: downloadInfo?.galleryInfo ?: dbInfo ?: return null
    val simpleTags = detailInfo.simpleTags ?: downloadInfo?.simpleTags ?: dbInfo?.simpleTags
    val simpleLanguage = detailInfo.simpleLanguage ?: downloadInfo?.simpleLanguage ?: dbInfo?.simpleLanguage
    if (detailInfo.simpleTags == null) {
        detailInfo.simpleTags = simpleTags
    }
    if (detailInfo.simpleLanguage == null) {
        detailInfo.simpleLanguage = simpleLanguage
    }
    val comicInfo = downloadInfo?.downloadDir?.find(COMIC_INFO_FILE)?.let(::readComicInfo)
        ?: downloadInfo?.archiveFile?.let(::readComicInfoFromArchive)
    val tagGroups = comicInfo?.toTagGroups() ?: simpleTagsToTagGroups(
        simpleTags = simpleTags,
        simpleLanguage = simpleLanguage,
    )
    if (downloadInfo == null && tagGroups.isEmpty()) return null
    return LocalDetailTagInfo(
        fallbackInfo = detailInfo,
        tagGroups = tagGroups,
    )
}

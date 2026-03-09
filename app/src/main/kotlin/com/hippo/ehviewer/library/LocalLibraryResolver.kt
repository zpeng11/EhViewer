package com.hippo.ehviewer.library

import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.files.find
import com.ehviewer.core.files.isDirectory
import com.hippo.ehviewer.download.archiveFile
import com.hippo.ehviewer.download.downloadDir
import com.hippo.ehviewer.library.content.LocalGalleryContent
import com.hippo.ehviewer.spider.SPIDER_INFO_FILENAME
import com.hippo.ehviewer.spider.readCompatFromPath
import com.hippo.ehviewer.spider.readFromCache

object LocalLibraryResolver {
    fun resolveReadableEntry(downloadInfo: DownloadInfo?): DownloadInfo? {
        val info = downloadInfo ?: return null
        if (info.archiveFile != null) return info
        val dirname = info.dirname ?: return null
        val downloadDir = info.downloadDir ?: return null
        downloadDir.find(SPIDER_INFO_FILENAME)?.let { spiderInfo ->
            if (readCompatFromPath(spiderInfo)?.let { it.gid == info.gid && it.token == info.token } == true) return info
        }
        val content = LocalGalleryContent(info, dirname)
        if (content.getLocalPageCount() > 0) return info
        return readFromCache(info.gid)?.takeIf { it.gid == info.gid && it.token == info.token }?.let { info }
    }

    fun canRead(downloadInfo: DownloadInfo?) = resolveReadableEntry(downloadInfo) != null

    fun canExport(downloadInfo: DownloadInfo?) = downloadInfo?.archiveFile != null || downloadInfo?.downloadDir?.isDirectory == true
}

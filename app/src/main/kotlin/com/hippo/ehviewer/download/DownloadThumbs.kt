package com.hippo.ehviewer.download

import com.ehviewer.core.database.model.DownloadInfo
import com.hippo.ehviewer.library.thumbs.ensureLocalThumbFileInternal
import com.hippo.ehviewer.library.thumbs.findLocalThumbFileInternal
import com.hippo.ehviewer.library.thumbs.localThumbCacheKeyInternal
import okio.Path

fun DownloadInfo.findLocalThumbFile(): Path? = findLocalThumbFileInternal(this)

fun DownloadInfo.localThumbCacheKey(path: Path): String = localThumbCacheKeyInternal(this, path)

suspend fun DownloadInfo.ensureLocalThumbFile(): Path? = ensureLocalThumbFileInternal(this)

package com.hippo.ehviewer.library.reader

import arrow.autoCloseScope
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.i18n.R
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.gallery.PageLoader
import com.hippo.ehviewer.library.content.LocalGalleryContent
import com.hippo.ehviewer.util.hasAds
import kotlinx.coroutines.coroutineScope
import moe.tarsin.kt.install
import okio.Path
import splitties.init.appCtx

suspend inline fun <T> useLocalGalleryPageLoader(
    info: DownloadInfo,
    startPage: Int,
    crossinline block: suspend (PageLoader) -> T,
) = autoCloseScope {
    coroutineScope {
        val dirname = checkNotNull(info.dirname) {
            appCtx.getString(R.string.local_content_unavailable)
        }
        val content = LocalGalleryContent(info, dirname)
        content.initDownloadDirIfExist()
        val size = content.getLocalPageCount()
        check(size > 0) {
            appCtx.getString(R.string.local_content_unavailable)
        }
        val localReadFailureMessage = appCtx.getString(R.string.error_reading_failed)
        val loader = install(
            object : PageLoader(
                this,
                info,
                startPage,
                size,
                info.hasAds,
                onClose = { content.clearReaderIsolationCache() },
            ) {
                override val title by lazy { EhUtils.getSuitableTitle(info) }

                override fun getImageExtension(index: Int) = content.getExtension(index)

                override fun save(index: Int, file: Path) = content.saveToPath(index, file)

                override suspend fun openSource(index: Int) = content.getImageSourceForReader(index)

                override fun prefetchPages(pages: List<Int>, bounds: IntRange) = Unit

                override fun onRequest(index: Int, force: Boolean, orgImg: Boolean) {
                    if (index in content) {
                        notifySourceReady(index)
                    } else {
                        notifyPageFailed(index, localReadFailureMessage)
                    }
                }
            },
        )
        block(loader)
    }
}

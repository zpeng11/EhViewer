package com.hippo.ehviewer.gallery

import com.ehviewer.core.i18n.R
import arrow.autoCloseScope
import com.ehviewer.core.model.GalleryInfo
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.ehviewer.spider.SpiderQueen.Companion.obtainSpiderQueen
import com.hippo.ehviewer.spider.SpiderQueen.Companion.releaseSpiderQueen
import com.hippo.ehviewer.spider.SpiderQueen.OnSpiderListener
import com.hippo.ehviewer.util.hasAds
import kotlinx.coroutines.coroutineScope
import moe.tarsin.kt.install
import okio.Path
import splitties.init.appCtx

@PublishedApi
internal const val BLOCK_READER_REMOTE_FETCH = true

suspend inline fun <T> useEhPageLoader(
    info: GalleryInfo,
    startPage: Int,
    crossinline block: suspend (PageLoader) -> T,
) = autoCloseScope {
    coroutineScope {
        val queen = install(
            { obtainSpiderQueen(info) },
            { queen, _ -> releaseSpiderQueen(queen) },
        )
        queen.awaitReady()
        val localReadFailureMessage = appCtx.getString(R.string.error_reading_failed)
        val loader = install(
            object : PageLoader(
                this,
                info,
                startPage,
                queen.size,
                info.hasAds,
                onClose = { queen.spiderDen.clearReaderIsolationCache() },
            ) {
                override val title by lazy { EhUtils.getSuitableTitle(info) }

                override fun getImageExtension(index: Int) = queen.getExtension(index)

                override fun save(index: Int, file: Path) = queen.save(index, file)

                override fun openSource(index: Int) = queen.spiderDen.getImageSourceForReader(index)

                override fun prefetchPages(pages: List<Int>, bounds: IntRange) = queen.preloadPages(
                    pages = pages,
                    pair = bounds,
                    localOnly = BLOCK_READER_REMOTE_FETCH,
                    remoteFetchAllowed = !BLOCK_READER_REMOTE_FETCH,
                )

                override fun onRequest(index: Int, force: Boolean, orgImg: Boolean) =
                    queen.request(
                        index = index,
                        force = force,
                        orgImg = orgImg,
                        localOnly = BLOCK_READER_REMOTE_FETCH || (force && !orgImg),
                        remoteFetchAllowed = !BLOCK_READER_REMOTE_FETCH,
                    )
            },
        ).apply {
            val listener = object : OnSpiderListener {
                override fun onPageDownload(index: Int, contentLength: Long, receivedSize: Long, bytesRead: Int) {
                    if (contentLength > 0) {
                        notifyPagePercent(index, receivedSize.toFloat() / contentLength)
                    }
                }

                override fun onPageReady(index: Int) = notifySourceReady(index)

                override fun onPageFailure(index: Int, error: String?, finished: Int, downloaded: Int, total: Int) {
                    notifyPageFailed(
                        index,
                        if (BLOCK_READER_REMOTE_FETCH) localReadFailureMessage else error,
                    )
                }
            }
            install(
                { queen.addOnSpiderListener(listener) },
                { _, _ -> queen.removeOnSpiderListener(listener) },
            )
        }
        block(loader)
    }
}

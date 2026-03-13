package com.hippo.ehviewer.ui.screen

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DownloadsSearchRequest(
    val id: Long,
    val keyword: String,
)

internal object DownloadsSearchRouter {
    private val nextId = AtomicLong()
    private val mutableRequestFlow = MutableStateFlow<DownloadsSearchRequest?>(null)

    val requestFlow = mutableRequestFlow.asStateFlow()

    fun search(keyword: String) {
        mutableRequestFlow.value = DownloadsSearchRequest(
            id = nextId.incrementAndGet(),
            keyword = keyword,
        )
    }

    fun consume(request: DownloadsSearchRequest) {
        if (mutableRequestFlow.value == request) {
            mutableRequestFlow.value = null
        }
    }
}

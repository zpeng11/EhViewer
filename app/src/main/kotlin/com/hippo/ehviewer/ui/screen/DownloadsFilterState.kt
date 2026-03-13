package com.hippo.ehviewer.ui.screen

import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.util.containsIgnoreCase
import com.hippo.ehviewer.download.DownloadsFilterMode
import kotlinx.serialization.Serializable

@Serializable
data class DownloadsFilterState(
    val mode: DownloadsFilterMode,
    val label: String?,
    val keyword: String = "",
)

fun DownloadsFilterState.take(info: DownloadInfo) = mode.take(info, label) && with(info) {
        title.containsIgnoreCase(keyword) ||
            titleJpn.containsIgnoreCase(keyword) ||
            uploader.containsIgnoreCase(keyword) ||
            simpleTags?.any { it.containsIgnoreCase(keyword) } == true
    }

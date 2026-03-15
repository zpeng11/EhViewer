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

private fun matchesTagKeyword(tag: String, keyword: String): Boolean {
    if (tag.containsIgnoreCase(keyword)) return true
    val normalizedTag = tag.removePrefix("_")
    val normalizedKeyword = keyword.removePrefix("_")
    if (normalizedTag.containsIgnoreCase(normalizedKeyword)) return true
    val tagValue = normalizedTag.substringAfter(':', normalizedTag)
    val keywordValue = normalizedKeyword.substringAfter(':', normalizedKeyword)
    return tagValue.containsIgnoreCase(keywordValue)
}

fun DownloadsFilterState.take(info: DownloadInfo) = mode.take(info, label) && with(info) {
    title.containsIgnoreCase(keyword) ||
        titleJpn.containsIgnoreCase(keyword) ||
        uploader.containsIgnoreCase(keyword) ||
        simpleTags?.any { matchesTagKeyword(it, keyword) } == true
}

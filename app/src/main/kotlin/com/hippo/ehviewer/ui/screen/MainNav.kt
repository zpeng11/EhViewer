package com.hippo.ehviewer.ui.screen

import androidx.annotation.MainThread
import com.ehviewer.core.model.BaseGalleryInfo
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.ui.destinations.DownloadsScreenDestination
import com.hippo.ehviewer.ui.destinations.ReaderScreenDestination
import com.hippo.ehviewer.ui.reader.ReaderScreenArgs
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@MainThread
context(_: DestinationsNavigator)
fun navWithUrl(url: String): Boolean = false

fun BaseGalleryInfo.asDst() = ReaderScreenDestination(ReaderScreenArgs.Gallery(this, page = -1))

infix fun Long.asDstWith(token: String) = ReaderScreenDestination(ReaderScreenArgs.Gallery(BaseGalleryInfo(gid = this, token = token), page = -1))

infix fun Long.asDstPageTo(page: Int) = this to page

infix fun Pair<Long, Int>.with(token: String) = ReaderScreenDestination(
    ReaderScreenArgs.Gallery(
        BaseGalleryInfo(gid = first, token = token),
        page = second,
    ),
)

fun ListUrlBuilder.asDst() = DownloadsScreenDestination

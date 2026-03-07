package com.hippo.ehviewer.coil

import android.content.ContentResolver
import android.net.Uri
import coil3.Extras
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.files.toUri
import com.hippo.ehviewer.R
import com.hippo.ehviewer.download.ensureLocalThumbFile
import com.hippo.ehviewer.download.findLocalThumbFile
import com.hippo.ehviewer.download.localThumbCacheKey

const val LOCAL_DOWNLOAD_THUMB_DATA_PREFIX = "ehviewer-download-thumb:"

fun localDownloadThumbData(gid: Long) = "$LOCAL_DOWNLOAD_THUMB_DATA_PREFIX$gid"

private val downloadInfoKey = Extras.Key<DownloadInfo?>(default = null)

fun ImageRequest.Builder.downloadInfo(info: DownloadInfo) = apply {
    extras[downloadInfoKey] = info
}

val ImageRequest.downloadInfo: DownloadInfo?
    get() = getExtra(downloadInfoKey)

private fun placeholderUri(request: ImageRequest) = Uri.Builder()
    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
    .authority(request.context.packageName)
    .appendPath(R.drawable.image_failed.toString())
    .build()

private fun isLocalDownloadThumbRequest(data: Any?) = data is String && data.startsWith(LOCAL_DOWNLOAD_THUMB_DATA_PREFIX)

object DownloadThumbInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        if (!isLocalDownloadThumbRequest(chain.request.data)) {
            return chain.proceed()
        }

        val info = chain.request.downloadInfo ?: return chain.withRequest(
            chain.request.newBuilder()
                .data(placeholderUri(chain.request))
                .memoryCacheKey("download-thumb-missing")
                .diskCachePolicy(CachePolicy.DISABLED)
                .build(),
        ).proceed()

        val localThumb = info.findLocalThumbFile() ?: info.ensureLocalThumbFile()
        if (localThumb != null) {
            val localRequest = chain.request.newBuilder()
                .data(localThumb.toUri())
                .memoryCacheKey(info.localThumbCacheKey(localThumb))
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            val result = chain.withRequest(localRequest).proceed()
            if (result is SuccessResult) {
                return result
            }
        }

        return chain.withRequest(
            chain.request.newBuilder()
                .data(placeholderUri(chain.request))
                .memoryCacheKey("download-thumb-missing:${info.gid}")
                .diskCachePolicy(CachePolicy.DISABLED)
                .build(),
        ).proceed()
    }
}

package com.hippo.ehviewer.coil

import android.net.Uri
import android.os.ParcelFileDescriptor
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.ehviewer.core.files.CIFS_DOCUMENT_AUTHORITY
import com.ehviewer.core.files.openFileDescriptor
import com.ehviewer.core.files.toOkioPath
import okio.FileSystem
import okio.buffer
import okio.source

/**
 * Coil [Fetcher] that intercepts CIFS `content://` URIs and reads them
 * through [openFileDescriptor] — which is protected by
 * the CIFS semaphore and stale-connection recovery logic.
 *
 * Without this, Coil's built-in `ContentUriFetcher` calls
 * `ContentResolver.openInputStream()` directly, bypassing all throttling
 * and causing the CIFS DocumentProvider to enter a broken state.
 */
class CifsContentFetcher(
    private val uri: Uri,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val path = uri.toOkioPath()
        val pfd = path.openFileDescriptor("r")
        val source = ParcelFileDescriptor.AutoCloseInputStream(pfd).source().buffer()
        return SourceFetchResult(
            source = ImageSource(source, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content" || data.authority != CIFS_DOCUMENT_AUTHORITY) {
                return null
            }
            return CifsContentFetcher(data)
        }
    }
}

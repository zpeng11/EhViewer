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
import okio.Buffer
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
 *
 * **Key behavior**: The CIFS FD is eagerly read into memory and closed
 * before returning the ImageSource. This prevents downstream Coil decode
 * operations from keeping the CIFS provider-backed FD alive.
 */
class CifsContentFetcher(
    private val uri: Uri,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val path = uri.toOkioPath()
        val pfd = path.openFileDescriptor("r")
        
        // Eagerly read entire content into memory and close the FD.
        // Use try-finally to ensure close on both success and failure.
        val buffer = Buffer()
        val source = ParcelFileDescriptor.AutoCloseInputStream(pfd).source().buffer()
        try {
            buffer.writeAll(source)
        } finally {
            source.close()
        }
        
        return SourceFetchResult(
            source = ImageSource(buffer, options.fileSystem),
            mimeType = null,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content" || data.authority != CIFS_DOCUMENT_AUTHORITY) {
                return null
            }
            return CifsContentFetcher(data, options)
        }
    }
}

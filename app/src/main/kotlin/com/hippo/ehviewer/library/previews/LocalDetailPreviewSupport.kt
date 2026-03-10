package com.hippo.ehviewer.library.previews

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import coil3.BitmapImage
import coil3.DrawableImage
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.files.isFile
import com.ehviewer.core.files.openFileDescriptor
import com.ehviewer.core.files.toUri
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.GalleryPreview
import com.ehviewer.core.model.V1GalleryPreview
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.archiveFile
import com.hippo.ehviewer.gallery.PageLoader
import com.hippo.ehviewer.gallery.useArchivePageLoader
import com.hippo.ehviewer.image.Image
import com.hippo.ehviewer.image.ImageSource
import com.hippo.ehviewer.library.reader.useLocalGalleryPageLoader
import com.hippo.ehviewer.util.AppConfig
import com.hippo.ehviewer.util.ensureDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import splitties.init.appCtx

private const val LOCAL_DETAIL_PREVIEW_DIR = "detail-previews"
private const val LOCAL_DETAIL_PREVIEW_EXTENSION = "png"

private val localDetailPreviewMutex = Mutex()

private val localDetailPreviewCacheDir: Path
    get() = (AppConfig.tempDir / LOCAL_DETAIL_PREVIEW_DIR).apply { check(ensureDirectory()) }

private fun buildLocalDetailPreviewPath(gid: Long, index: Int) = localDetailPreviewCacheDir / "preview-$gid-$index.$LOCAL_DETAIL_PREVIEW_EXTENSION"

private suspend inline fun <T> useLocalPreviewPageLoader(
    info: DownloadInfo,
    crossinline block: suspend (PageLoader) -> T,
) = info.archiveFile?.let { archive ->
    useArchivePageLoader(
        file = archive,
        info = info.galleryInfo,
        passwdProvider = { error("Managed archive preview generation should not require a password") },
        block = block,
    )
} ?: useLocalGalleryPageLoader(info, startPage = 0, block = block)

private suspend fun writeStaticPreview(source: ImageSource, target: Path): Path? = source.use {
    runCatching {
        val image = Image.decode(source)
        try {
            val bitmap = when (val coilImage = image.innerImage) {
                is BitmapImage -> coilImage.bitmap
                is DrawableImage -> {
                    val width = coilImage.width.coerceAtLeast(1)
                    val height = coilImage.height.coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                        val canvas = Canvas(this)
                        coilImage.drawable.setBounds(0, 0, width, height)
                        coilImage.drawable.draw(canvas)
                    }
                }
                else -> return@runCatching null
            }
            ParcelFileDescriptor.AutoCloseOutputStream(target.openFileDescriptor("wt")).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Failed to encode preview to $target" }
            }
            target.takeIf { it.isFile }
        } finally {
            image.unpin()
        }
    }.getOrNull()
}

private suspend fun ensureLocalDetailPreviewFile(loader: PageLoader, gid: Long, index: Int): Path? {
    val target = buildLocalDetailPreviewPath(gid, index)
    if (target.isFile) return target
    return localDetailPreviewMutex.withLock {
        if (target.isFile) return@withLock target
        writeStaticPreview(loader.openSource(index), target)
    }
}

private data class LocalDetailPreviewPage(
    val previews: List<GalleryPreview>,
    val total: Int,
)

suspend fun loadLocalDetailPreviewPage(gid: Long, start: Int, endInclusive: Int): Pair<List<GalleryPreview>, Int> {
    val info = checkNotNull(DownloadManager.getReadableDownloadInfo(gid)) {
        appCtx.getString(R.string.local_content_unavailable)
    }
    val page = useLocalPreviewPageLoader(info) { loader ->
        val total = loader.size
        check(total > 0) {
            appCtx.getString(R.string.local_content_unavailable)
        }
        val range = start..endInclusive.coerceAtMost(total - 1)
        LocalDetailPreviewPage(
            previews = range.map { index ->
                val path = ensureLocalDetailPreviewFile(loader, gid, index)
                V1GalleryPreview(
                    url = path?.toUri()?.toString().orEmpty(),
                    position = index,
                    pToken = "",
                )
            },
            total = total,
        )
    }
    return page.previews to page.total
}

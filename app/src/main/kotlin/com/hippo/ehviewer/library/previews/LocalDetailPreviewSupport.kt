package com.hippo.ehviewer.library.previews

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.unit.IntRect
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
import com.hippo.ehviewer.image.detectBorder
import com.hippo.ehviewer.image.Image
import com.hippo.ehviewer.image.ImageSource
import com.hippo.ehviewer.image.PathSource
import com.hippo.ehviewer.library.reader.useLocalGalleryPageLoader
import com.hippo.ehviewer.util.AppConfig
import com.hippo.ehviewer.util.ensureDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import splitties.init.appCtx

private const val LOCAL_DETAIL_PREVIEW_DIR = "detail-previews"
private const val LOCAL_DETAIL_PREVIEW_EXTENSION = "webp"
private const val LOCAL_DETAIL_PREVIEW_WEBP_QUALITY = 80
private const val CROP_THRESHOLD = 0.75f
private const val RATIO_THRESHOLD = 2

private val localDetailPreviewMutex = Mutex()
private val localDetailPreviewTargetSize by lazy {
    appCtx.resources.run {
        PreviewTargetSize(
            width = getDimensionPixelSize(com.hippo.ehviewer.R.dimen.gallery_detail_thumb_width),
            height = getDimensionPixelSize(com.hippo.ehviewer.R.dimen.gallery_detail_thumb_height),
        )
    }
}

private val localDetailPreviewCacheDir: Path
    get() = (AppConfig.tempDir / LOCAL_DETAIL_PREVIEW_DIR).apply { check(ensureDirectory()) }

private fun buildLocalDetailPreviewPath(gid: Long, index: Int) = localDetailPreviewCacheDir / "preview-$gid-$index.$LOCAL_DETAIL_PREVIEW_EXTENSION"

private data class PreviewTargetSize(
    val width: Int,
    val height: Int,
)

private data class PreviewBitmap(
    val bitmap: Bitmap,
    val releaseAfterWrite: () -> Unit,
)

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

private fun previewBitmapFromCoilImage(image: Image): PreviewBitmap? = when (val coilImage = image.innerImage) {
    is BitmapImage -> PreviewBitmap(coilImage.bitmap) {
        image.unpin()
    }
    is DrawableImage -> {
        val width = coilImage.width.coerceAtLeast(1)
        val height = coilImage.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            coilImage.drawable.setBounds(0, 0, width, height)
            coilImage.drawable.draw(canvas)
        }
        PreviewBitmap(bitmap) {
            bitmap.recycle()
            image.unpin()
        }
    }
    else -> null
}

private fun exifRotationDegrees(pathSource: PathSource): Float {
    val extension = pathSource.type.lowercase()
    if (extension !in setOf("jpg", "jpeg")) return 0f
    return runCatching {
        ParcelFileDescriptor.AutoCloseInputStream(pathSource.source.openFileDescriptor("r")).use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrDefault(0f)
}

private fun Bitmap.rotateIfNeeded(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)
    if (rotated != this) recycle()
    return rotated
}

private fun Bitmap.cropBordersIfNeeded(): Bitmap {
    if (!com.hippo.ehviewer.Settings.cropBorder.value) return this
    val ratio = if (height > width) height / width else width / height
    if (ratio >= RATIO_THRESHOLD) return this

    val border = detectBorder(this)
    val rect = IntRect(border[0], border[1], border[2], border[3])
    val minWidth = width * CROP_THRESHOLD
    val minHeight = height * CROP_THRESHOLD
    if (rect.width <= minWidth || rect.height <= minHeight) return this

    val cropped = Bitmap.createBitmap(this, rect.left, rect.top, rect.width, rect.height)
    if (cropped != this) recycle()
    return cropped
}

private fun calculateInSampleSize(
    srcWidth: Int,
    srcHeight: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    if (srcWidth <= 0 || srcHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
    val widthRatio = srcWidth / reqWidth
    val heightRatio = srcHeight / reqHeight
    val sample = minOf(widthRatio, heightRatio)
    return sample.takeIf { it > 1 }?.let(Integer::highestOneBit) ?: 1
}

private fun decodeSampledPathPreview(pathSource: PathSource): Bitmap? {
    val targetSize = localDetailPreviewTargetSize
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    ParcelFileDescriptor.AutoCloseInputStream(pathSource.source.openFileDescriptor("r")).use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetSize.width, targetSize.height)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = ParcelFileDescriptor.AutoCloseInputStream(pathSource.source.openFileDescriptor("r")).use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: return null
    return bitmap
        .rotateIfNeeded(exifRotationDegrees(pathSource))
        .cropBordersIfNeeded()
}

private fun detailPreviewCompressFormat() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Bitmap.CompressFormat.WEBP_LOSSY
} else {
    @Suppress("DEPRECATION")
    Bitmap.CompressFormat.WEBP
}

private suspend fun writeStaticPreview(source: ImageSource, target: Path): Path? = source.use {
    runCatching {
        val previewBitmap = if (source is PathSource) {
            decodeSampledPathPreview(source)?.let { bitmap ->
                PreviewBitmap(bitmap) {
                    bitmap.recycle()
                }
            }
        } else {
            null
        } ?: run {
            val image = Image.decode(source)
            previewBitmapFromCoilImage(image) ?: run {
                image.unpin()
                null
            }
        } ?: return@runCatching null
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(target.openFileDescriptor("wt")).use { out ->
                check(previewBitmap.bitmap.compress(detailPreviewCompressFormat(), LOCAL_DETAIL_PREVIEW_WEBP_QUALITY, out)) {
                    "Failed to encode preview to $target"
                }
            }
            target.takeIf { it.isFile }
        } finally {
            previewBitmap.releaseAfterWrite()
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

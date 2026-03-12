package com.hippo.ehviewer.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.compose.AsyncImagePainter.State
import coil3.compose.rememberAsyncImagePainter
import com.ehviewer.core.model.GalleryPreview
import com.ehviewer.core.model.V2GalleryPreview
import com.ehviewer.core.ui.component.CrystalCard
import com.hippo.ehviewer.ktbuilder.imageRequest

@Composable
@NonRestartableComposable
fun requestOf(model: GalleryPreview) = with(LocalContext.current) {
    remember(model) { imageRequest(model) }
}

@Composable
fun EhPreviewCard(
    model: GalleryPreview,
    placeholderIndex: Int = model.position,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = requestOf(model)
    val painter = rememberAsyncImagePainter(
        model = request,
        transform = {
            if (it is State.Success && model is V2GalleryPreview) {
                with(model) {
                    it.copy(
                        painter = BitmapPainter(
                            (it.result.image as BitmapImage).bitmap.asImageBitmap(),
                            IntOffset(offsetX, 0),
                            IntSize(clipWidth - 1, clipHeight - 1),
                        ),
                    )
                }
            } else {
                it
            }
        },
    )
    val state by painter.state.collectAsState()
    CrystalCard(
        onClick = onClick,
        onLongClick = {
            if (state is State.Error) {
                painter.restart()
            }
        },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (state.painter == null) {
                PreviewPlaceholder(index = placeholderIndex)
            }
        }
    }
}

@Composable
@NonRestartableComposable
fun EhPreviewItem(
    galleryPreview: GalleryPreview?,
    placeholderIndex: Int,
    onClick: () -> Unit,
) {
    if (galleryPreview != null) {
        EhPreviewCard(
            model = galleryPreview,
            placeholderIndex = placeholderIndex,
            onClick = onClick,
            modifier = Modifier.aspectRatio(DEFAULT_RATIO),
        )
    } else {
        CrystalCard(
            onClick = onClick,
            modifier = Modifier.aspectRatio(DEFAULT_RATIO),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                PreviewPlaceholder(index = placeholderIndex)
            }
        }
    }
}

@Composable
private fun PreviewPlaceholder(index: Int) {
    Text(
        text = (index + 1).toString(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    )
}

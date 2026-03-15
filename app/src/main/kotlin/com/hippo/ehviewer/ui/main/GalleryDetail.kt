package com.hippo.ehviewer.ui.main

import android.content.Context
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.GalleryDetail
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.GalleryTagGroup
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.big.SadAndroid
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.detailThumbGenerator

@Composable
private fun GalleryDetailHeaderInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseRoundText(
        text = stringResource(id = R.string.gallery_info),
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
context(_: Context, _: SharedTransitionScope, _: TransitionsVisibilityScope)
fun GalleryDetailHeaderCard(
    info: GalleryInfo,
    tagGroups: List<GalleryTagGroup>,
    onInfoCardClick: () -> Unit,
    onUploaderChipClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onTagLongClick: (String, String) -> Unit,
    localOnlyThumb: Boolean = false,
    modifier: Modifier = Modifier,
) = ElevatedCard(modifier = modifier) {
    Row {
        with(detailThumbGenerator) {
            EhThumbCard(
                key = remember(info.gid) { info },
                localOnly = localOnlyThumb,
                modifier = Modifier.size(
                    dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_thumb_width),
                    dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_thumb_height),
                ),
            )
        }
        Column(
            modifier = Modifier
                .height(dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_thumb_height))
                .weight(1f)
                .padding(horizontal = dimensionResource(id = com.hippo.ehviewer.R.dimen.keyline_margin)),
        ) {
            (info as? GalleryDetail)?.let {
                GalleryDetailHeaderInfoButton(
                    onClick = onInfoCardClick,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val uploaderText = info.uploader.orEmpty()
                if (uploaderText.isNotEmpty() && uploaderText != "(Disowned)") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BaseRoundText(text = stringResource(id = R.string.key_uploader), isGroup = true)
                        BaseRoundText(
                            text = uploaderText,
                            modifier = Modifier.clickable(onClick = onUploaderChipClick),
                        )
                    }
                }
                if (tagGroups.isNotEmpty()) {
                    GalleryTags(
                        tagGroups = tagGroups,
                        onTagClick = onTagClick,
                        onTagLongClick = onTagLongClick,
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryDetailErrorTip(error: String, onClick: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    Icon(
        imageVector = EhIcons.Big.Default.SadAndroid,
        contentDescription = null,
        modifier = Modifier.clickable(onClick = onClick),
    )
    Spacer(modifier = Modifier.size(8.dp))
    Text(
        text = error,
        modifier = Modifier.widthIn(max = 228.dp),
    )
}

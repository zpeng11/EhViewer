package com.hippo.ehviewer.ui.main

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material3.AssistChip
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
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.big.SadAndroid
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.detailThumbGenerator

@Composable
private fun GalleryDetailHeaderInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text = stringResource(id = R.string.gallery_info)) },
        modifier = modifier.width(IntrinsicSize.Max),
    )
}

@Composable
context(_: SharedTransitionScope, _: TransitionsVisibilityScope)
fun GalleryDetailHeaderCard(
    info: GalleryInfo,
    onInfoCardClick: () -> Unit,
    onUploaderChipClick: () -> Unit,
    onBlockUploaderIconClick: () -> Unit,
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
        Spacer(modifier = Modifier.weight(0.5F))
        Column(
            modifier = Modifier.height(dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_thumb_height)),
            horizontalAlignment = Alignment.End,
        ) {
            (info as? GalleryDetail)?.let {
                GalleryDetailHeaderInfoButton(
                    onClick = onInfoCardClick,
                    modifier = Modifier.padding(top = 8.dp, end = dimensionResource(id = com.hippo.ehviewer.R.dimen.keyline_margin)),
                )
            }
            Spacer(modifier = Modifier.weight(1F))
            val uploaderText = info.uploader.orEmpty()
            AssistChip(
                onClick = onUploaderChipClick,
                label = { Text(text = uploaderText, overflow = TextOverflow.Visible, softWrap = false, maxLines = 1) },
                modifier = Modifier.padding(horizontal = dimensionResource(id = com.hippo.ehviewer.R.dimen.keyline_margin)),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.NoAccounts,
                        contentDescription = null,
                        modifier = Modifier.clickable(onClick = onBlockUploaderIconClick),
                    )
                },
            )
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

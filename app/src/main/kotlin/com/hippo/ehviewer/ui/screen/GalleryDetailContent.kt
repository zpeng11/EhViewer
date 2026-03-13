package com.hippo.ehviewer.ui.screen

import android.content.Context
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import arrow.core.partially1
import arrow.fx.coroutines.parMap
import com.ehviewer.core.data.model.asGalleryDetail
import com.ehviewer.core.data.model.findBaseInfo
import com.ehviewer.core.database.model.Filter
import com.ehviewer.core.database.model.FilterMode
import com.ehviewer.core.database.model.LocalFavoriteFolder
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.GalleryComment
import com.ehviewer.core.model.GalleryDetail
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.GalleryInfo.Companion.NOT_FAVORITED
import com.ehviewer.core.model.GalleryPreview
import com.ehviewer.core.model.V2GalleryPreview
import com.ehviewer.core.model.VoteStatus
import com.ehviewer.core.ui.component.CrystalCard
import com.ehviewer.core.ui.component.FastScrollLazyVerticalGrid
import com.ehviewer.core.ui.util.LocalWindowSizeClass
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.flattenForEach
import com.ehviewer.core.ui.util.isExpanded
import com.ehviewer.core.ui.util.rememberInVM
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.launchIO
import com.ehviewer.core.util.withIOContext
import com.ehviewer.core.util.withUIContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhFilter.remember
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.coil.PrefetchAround
import com.hippo.ehviewer.coil.justDownload
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.ktbuilder.executeIn
import com.hippo.ehviewer.ktbuilder.imageRequest
import com.hippo.ehviewer.library.previews.LocalDetailPreviewSession
import com.hippo.ehviewer.library.previews.loadLocalDetailPreviewPage
import com.hippo.ehviewer.ui.GalleryInfoBottomSheet
import com.hippo.ehviewer.ui.rememberFavoriteNameResolver
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.addToFavorites
import com.hippo.ehviewer.ui.destinations.GalleryCommentsScreenDestination
import com.hippo.ehviewer.ui.getFavoriteIcon
import com.hippo.ehviewer.ui.jumpToReaderByPage
import com.hippo.ehviewer.ui.main.EhPreviewItem
import com.hippo.ehviewer.ui.main.GalleryCommentCard
import com.hippo.ehviewer.ui.main.GalleryDetailErrorTip
import com.hippo.ehviewer.ui.main.GalleryDetailHeaderCard
import com.hippo.ehviewer.ui.main.GalleryTags
import com.hippo.ehviewer.ui.modifyFavorites
import com.hippo.ehviewer.ui.navToReader
import com.hippo.ehviewer.ui.openBrowser
import com.hippo.ehviewer.ui.selectExtraFavoriteFolder
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitSelectAction
import com.hippo.ehviewer.ui.tools.awaitSelectItem
import com.hippo.ehviewer.ui.tools.dialog
import com.hippo.ehviewer.ui.tools.foldToLoadResult
import com.hippo.ehviewer.ui.tools.getClippedRefreshKey
import com.hippo.ehviewer.ui.tools.getLimit
import com.hippo.ehviewer.ui.tools.getOffset
import com.hippo.ehviewer.util.FavouriteStatusRouter
import com.hippo.ehviewer.util.addTextToClipboard
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CoroutineScope
import moe.tarsin.coroutines.runSuspendCatching
import moe.tarsin.navigate
import moe.tarsin.snackbar
import moe.tarsin.string

@Composable
context(_: CoroutineScope, _: DestinationsNavigator, _: DialogState, _: MainActivity, _: SnackbarHostState, _: SharedTransitionScope, _: TransitionsVisibilityScope)
fun GalleryDetailContent(
    galleryInfo: GalleryInfo,
    contentPadding: PaddingValues,
    getDetailError: String,
    onRetry: () -> Unit,
    voteTag: VoteTag,
    modifier: Modifier,
) {
    val keylineMargin = dimensionResource(com.hippo.ehviewer.R.dimen.keyline_margin)
    val galleryDetail = galleryInfo.asGalleryDetail()
    val windowSizeClass = LocalWindowSizeClass.current
    val thumbColumns by Settings.thumbColumns.collectAsState()
    val previewSpacing = dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_preview_spacing)
    val readText = stringResource(R.string.read)
    val startPage by rememberInVM {
        EhDB.getReadProgressFlow(galleryInfo.gid)
    }.collectAsState(0)
    val readButtonText = if (startPage == 0) {
        readText
    } else {
        stringResource(R.string.read_from, startPage + 1)
    }
    val canReadLocally by DownloadManager.collectCanReadGalleryLocally(galleryInfo.gid)
    val localContentUnavailable = stringResource(R.string.local_content_unavailable)
    fun onReadButtonClick() {
        if (canReadLocally) {
            navToReader(galleryInfo.findBaseInfo(), startPage)
        } else {
            launch { snackbar(localContentUnavailable) }
        }
    }
    fun onCategoryChipClick() {
        val category = galleryInfo.category
        if (category == EhUtils.NONE || category == EhUtils.PRIVATE || category == EhUtils.UNKNOWN) {
            return
        }
        navigate(ListUrlBuilder(category = category).asDst())
    }
    fun onUploaderChipClick(galleryInfo: GalleryInfo) {
        val uploader = galleryInfo.uploader
        val disowned = uploader == "(Disowned)"
        if (uploader.isNullOrEmpty() || disowned) {
            return
        }
        navigate(ListUrlBuilder(mode = ListUrlBuilder.MODE_UPLOADER, keyword = uploader).asDst())
    }

    fun onGalleryInfoCardClick() {
        galleryDetail ?: return
        launch {
            dialog { cont ->
                ModalBottomSheet(
                    onDismissRequest = { cont.cancel() },
                    contentWindowInsets = { WindowInsets() },
                ) {
                    GalleryInfoBottomSheet(galleryDetail)
                }
            }
        }
    }

    val filterAdded = stringResource(R.string.filter_added)
    fun showFilterUploaderDialog(galleryInfo: GalleryInfo) {
        val uploader = galleryInfo.uploader
        val disowned = uploader == "(Disowned)"
        if (uploader.isNullOrEmpty() || disowned) {
            return
        }
        launchIO {
            awaitConfirmationOrCancel {
                Text(text = stringResource(R.string.filter_the_uploader, uploader))
            }
            Filter(FilterMode.UPLOADER, uploader).remember()
            snackbar(filterAdded)
        }
    }
    val favSlot by FavouriteStatusRouter.collectAsState(galleryInfo) { it }
    val favoriteNameForSlot = rememberFavoriteNameResolver()
    val favoriteButtonText = if (favSlot != NOT_FAVORITED) {
        favoriteNameForSlot(favSlot) ?: stringResource(id = R.string.local_favorites)
    } else {
        stringResource(id = R.string.not_favorited)
    }
    val favoritesLock = remember { MutatorMutex() }
    val removeSucceed = stringResource(R.string.remove_from_favorite_success)
    val addSucceed = stringResource(R.string.add_to_favorite_success)
    val addFailed = stringResource(R.string.add_to_favorite_failure)
    val addExtraSucceed = stringResource(R.string.add_to_extra_favorite_success)
    val addExtraFailed = stringResource(R.string.add_to_extra_favorite_failure)
    fun onFavoriteButtonClick() {
        launchIO {
            favoritesLock.mutate {
                runSuspendCatching {
                    modifyFavorites(galleryInfo)
                }.onSuccess { add ->
                    snackbar(if (add) addSucceed else removeSucceed)
                }.onFailure {
                    snackbar(addFailed)
                }
            }
        }
    }
    fun onFavoriteButtonLongClick() {
        launchIO {
            favoritesLock.mutate {
                val folder = selectExtraFavoriteFolder(
                    favSlot.takeIf { it in LocalFavoriteFolder.VALID_SLOT_RANGE },
                ) ?: return@mutate
                runSuspendCatching {
                    addToFavorites(galleryInfo, folder.slot)
                }.onSuccess { added ->
                    snackbar(if (added) addExtraSucceed else addExtraFailed)
                }.onFailure {
                    snackbar(addExtraFailed)
                }
            }
        }
    }

    @Composable
    fun PrimaryActionButtons(modifier: Modifier = Modifier) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LongClickableFilledTonalButton(
                onClick = ::onFavoriteButtonClick,
                onLongClick = ::onFavoriteButtonLongClick,
                modifier = Modifier.weight(1F),
            ) {
                Icon(
                    imageVector = getFavoriteIcon(favSlot != NOT_FAVORITED),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = favoriteButtonText, overflow = TextOverflow.Ellipsis, maxLines = 1)
            }
            Button(
                onClick = ::onReadButtonClick,
                enabled = canReadLocally,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1F),
            ) {
                Text(text = readButtonText, overflow = TextOverflow.Ellipsis, maxLines = 1)
            }
        }
    }

    val previews = when {
        canReadLocally -> galleryInfo.collectLocalPreviewItems()
        galleryDetail != null -> galleryDetail.collectPreviewItems()
        else -> null
    }
    val showingLocalPreviewGrid = canReadLocally && previews != null
    when {
        !windowSizeClass.isExpanded -> FastScrollLazyVerticalGrid(
            columns = GridCells.Fixed(thumbColumns),
            contentPadding = contentPadding,
            modifier = modifier.padding(horizontal = keylineMargin),
            horizontalArrangement = Arrangement.spacedBy(previewSpacing),
            verticalArrangement = Arrangement.spacedBy(previewSpacing),
        ) {
            item(
                key = "header",
                span = { GridItemSpan(maxCurrentLineSpan) },
                contentType = "header",
            ) {
                GalleryDetailHeaderCard(
                    info = galleryInfo,
                    onInfoCardClick = ::onGalleryInfoCardClick,
                    onUploaderChipClick = ::onUploaderChipClick.partially1(galleryInfo),
                    onBlockUploaderIconClick = ::showFilterUploaderDialog.partially1(galleryInfo),
                    onCategoryChipClick = ::onCategoryChipClick,
                    localOnlyThumb = canReadLocally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = keylineMargin),
                )
            }
            item(
                key = "body",
                span = { GridItemSpan(maxCurrentLineSpan) },
                contentType = "body",
            ) {
                LocalPinnableContainer.current!!.run { remember { pin() } }
                Column {
                    PrimaryActionButtons(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                    if (galleryDetail != null) {
                        BelowHeader(galleryDetail, voteTag)
                    } else if (showingLocalPreviewGrid) {
                        Spacer(modifier = Modifier.height(1.dp))
                    } else if (getDetailError.isNotBlank()) {
                        GalleryDetailErrorTip(error = getDetailError, onClick = onRetry)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(keylineMargin),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularWavyProgressIndicator()
                        }
                    }
                }
            }
            if (previews != null) {
                galleryPreview(
                    data = previews,
                    isV2Thumb = galleryDetail?.previewList?.firstOrNull() is V2GalleryPreview,
                    enablePrefetch = !canReadLocally,
                ) {
                    if (canReadLocally) {
                        navToReader(galleryInfo.findBaseInfo(), it)
                    } else {
                        launch { snackbar(localContentUnavailable) }
                    }
                }
            }
        }
        else -> FastScrollLazyVerticalGrid(
            columns = GridCells.Fixed(thumbColumns),
            contentPadding = contentPadding,
            modifier = modifier.padding(horizontal = keylineMargin),
            horizontalArrangement = Arrangement.spacedBy(previewSpacing),
            verticalArrangement = Arrangement.spacedBy(previewSpacing),
        ) {
            item(
                key = "header",
                span = { GridItemSpan(maxCurrentLineSpan) },
                contentType = "header",
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    GalleryDetailHeaderCard(
                        info = galleryInfo,
                        onInfoCardClick = ::onGalleryInfoCardClick,
                        onUploaderChipClick = ::onUploaderChipClick.partially1(galleryInfo),
                        onBlockUploaderIconClick = ::showFilterUploaderDialog.partially1(galleryInfo),
                        onCategoryChipClick = ::onCategoryChipClick,
                        localOnlyThumb = canReadLocally,
                        modifier = Modifier.width(dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_card_landscape_width)).padding(vertical = keylineMargin),
                    )
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = modifier.height(16.dp))
                        PrimaryActionButtons(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            item(
                key = "body",
                span = { GridItemSpan(maxCurrentLineSpan) },
                contentType = "body",
            ) {
                LocalPinnableContainer.current!!.run { remember { pin() } }
                Column {
                    if (galleryDetail != null) {
                        BelowHeader(galleryDetail, voteTag)
                    } else if (showingLocalPreviewGrid) {
                        Spacer(modifier = Modifier.height(1.dp))
                    } else if (getDetailError.isNotBlank()) {
                        GalleryDetailErrorTip(error = getDetailError, onClick = onRetry)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(keylineMargin),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularWavyProgressIndicator()
                        }
                    }
                }
            }
            if (previews != null) {
                galleryPreview(
                    data = previews,
                    isV2Thumb = galleryDetail?.previewList?.firstOrNull() is V2GalleryPreview,
                    enablePrefetch = !canReadLocally,
                ) {
                    if (canReadLocally) {
                        navToReader(galleryInfo.findBaseInfo(), it)
                    } else {
                        launch { snackbar(localContentUnavailable) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LongClickableFilledTonalButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val colors = ButtonDefaults.filledTonalButtonColors()
    Surface(
        modifier = modifier,
        shape = ButtonDefaults.filledTonalShape,
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(
                        minWidth = ButtonDefaults.MinWidth,
                        minHeight = ButtonDefaults.MinHeight,
                    )
                    .combinedClickable(
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        },
                    )
                    .padding(ButtonDefaults.ContentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
context(ctx: Context, _: CoroutineScope, _: DestinationsNavigator, _: DialogState, _: SnackbarHostState)
fun BelowHeader(galleryDetail: GalleryDetail, voteTag: VoteTag) {
    @Composable
    fun GalleryDetailComment(commentsList: List<GalleryComment>) {
        val maxShowCount = 2
        val commentText = when {
            commentsList.isEmpty() -> stringResource(R.string.no_comments)
            commentsList.size <= maxShowCount -> stringResource(R.string.no_more_comments)
            else -> stringResource(R.string.more_comment)
        }
        fun navigateToCommentScreen() {
            navigate(GalleryCommentsScreenDestination(galleryDetail.gid))
        }
        CrystalCard {
            commentsList.take(maxShowCount).forEach { item ->
                GalleryCommentCard(
                    modifier = Modifier.padding(vertical = 4.dp),
                    comment = item,
                    onCardClick = ::navigateToCommentScreen,
                    onUserClick = ::navigateToCommentScreen,
                    onUrlClick = {
                        if (it.startsWith("#c")) {
                            navigateToCommentScreen()
                        } else {
                            if (!jumpToReaderByPage(it, galleryDetail)) if (!navWithUrl(it)) openBrowser(it)
                        }
                    },
                    maxLines = 5,
                    ellipsis = true,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dimensionResource(id = com.hippo.ehviewer.R.dimen.strip_item_padding_v))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = ::navigateToCommentScreen),
                contentAlignment = Alignment.Center,
            ) {
                Text(commentText)
            }
        }
    }
    suspend fun showNewerVersionDialog() {
        val items = galleryDetail.newerVersions.map {
            string(R.string.newer_version_title, it.title, it.posted)
        }
        val selected = awaitSelectItem(items)
        val info = galleryDetail.newerVersions[selected]
        withUIContext {
            // Can't use GalleryInfoArgs as thumbKey is null
            navigate(info.gid asDetailDstWith info.token)
        }
    }
    val keylineMargin = dimensionResource(com.hippo.ehviewer.R.dimen.keyline_margin)
    Spacer(modifier = Modifier.size(keylineMargin))
    if (galleryDetail.newerVersions.isNotEmpty()) {
        Box(contentAlignment = Alignment.Center) {
            CrystalCard(
                onClick = { launchIO { showNewerVersionDialog() } },
                modifier = Modifier.fillMaxWidth().height(32.dp),
            ) {
            }
            Text(text = stringResource(id = R.string.newer_version_available))
        }
        Spacer(modifier = Modifier.size(keylineMargin))
    }
    val tags = galleryDetail.tagGroups
    if (tags.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(id = R.string.no_tags))
        }
    } else {
        val copy = stringResource(android.R.string.copy)
        val copyTrans = stringResource(R.string.copy_trans)
        val showDefine = stringResource(R.string.show_definition)
        val addFilter = stringResource(R.string.add_filter)
        val filterAdded = stringResource(R.string.filter_added)
        val upTag = stringResource(R.string.tag_vote_up)
        val downTag = stringResource(R.string.tag_vote_down)
        val withDraw = stringResource(R.string.tag_vote_withdraw)
        fun search(tag: String) = navigate(ListUrlBuilder(mode = ListUrlBuilder.MODE_TAG, keyword = tag).asDst())
        GalleryTags(
            tagGroups = tags,
            onTagClick = ::search,
            onTagLongClick = { tag, translation, vote ->
                val rawValue = tag.substringAfter(':')
                launchIO {
                    awaitSelectAction {
                        onSelect(ctx.getString(R.string.search_bar_hint, tag)) {
                            withUIContext { search(tag) }
                        }
                        onSelect(copy) {
                            addTextToClipboard(tag)
                        }
                        if (rawValue != translation) {
                            onSelect(copyTrans) {
                                addTextToClipboard(translation)
                            }
                        }
                        onSelect(showDefine) {
                            openBrowser(EhUrl.getTagDefinitionUrl(rawValue))
                        }
                        onSelect(addFilter) {
                            awaitConfirmationOrCancel { Text(text = stringResource(R.string.filter_the_tag, tag)) }
                            Filter(FilterMode.TAG, tag).remember()
                            snackbar(filterAdded)
                        }
                        if (galleryDetail.apiUid >= 0) {
                            when (vote) {
                                VoteStatus.None -> {
                                    onSelect(upTag) { galleryDetail.voteTag(tag, 1) }
                                    onSelect(downTag) { galleryDetail.voteTag(tag, -1) }
                                }
                                VoteStatus.Up -> onSelect(withDraw) { galleryDetail.voteTag(tag, -1) }
                                VoteStatus.Down -> onSelect(withDraw) { galleryDetail.voteTag(tag, 1) }
                            }
                        }
                    }()
                }
            },
        )
    }
    Spacer(modifier = Modifier.size(keylineMargin))
    if (Settings.showComments.value) {
        GalleryDetailComment(galleryDetail.comments.comments)
        Spacer(modifier = Modifier.size(dimensionResource(id = com.hippo.ehviewer.R.dimen.strip_item_padding_v)))
    }
}

@Composable
context(_: Context)
private fun GalleryDetail.collectPreviewItems() = rememberInVM(previewList) {
    val pageSize = previewList.size
    val pages = pages
    val previewPagesMap = previewList.associateBy { it.position } as MutableMap
    Pager(
        PagingConfig(
            pageSize = pageSize,
            prefetchDistance = pageSize.coerceAtMost(100),
            initialLoadSize = pageSize,
            jumpThreshold = 2 * pageSize,
        ),
    ) {
        object : PagingSource<Int, GalleryPreview>() {
            override fun getRefreshKey(state: PagingState<Int, GalleryPreview>) = state.getClippedRefreshKey()
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GalleryPreview> = withIOContext {
                val key = params.key ?: 0
                val up = getOffset(params, key, pages)
                val end = (up + getLimit(params, key) - 1).coerceAtMost(pages - 1)
                runSuspendCatching {
                    (up..end).filterNot { it in previewPagesMap }.map { it / pageSize }.toSet()
                        .parMap(concurrency = Settings.multiThreadDownload.value) { page ->
                            val url = EhUrl.getGalleryDetailUrl(gid, token, page)
                            EhEngine.getPreviewList(url).previews
                        }.flattenForEach {
                            previewPagesMap[it.position] = it
                            if (Settings.preloadThumbAggressively.value) {
                                imageRequest(it) { justDownload() }.executeIn(viewModelScope)
                            }
                        }
                }.foldToLoadResult {
                    val r = (up..end).map { requireNotNull(previewPagesMap[it]) }
                    val prevK = if (up <= 0 || r.isEmpty()) null else up
                    val nextK = if (end == pages - 1) null else end + 1
                    LoadResult.Page(r, prevK, nextK, up, pages - end - 1)
                }
            }
            override val jumpingSupported = true
        }
    }.flow.cachedIn(viewModelScope)
}.collectAsLazyPagingItems()

@Composable
private fun GalleryInfo.collectLocalPreviewItems() = rememberInVM(gid) {
    val gid = gid
    val previewSession = LocalDetailPreviewSession(gid)
    Pager(
        PagingConfig(
            pageSize = 4,
            prefetchDistance = 4,
            initialLoadSize = 2,
        ),
    ) {
        object : PagingSource<Int, GalleryPreview>() {
            private var pageCount = pages

            override fun getRefreshKey(state: PagingState<Int, GalleryPreview>) = state.getClippedRefreshKey()

            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GalleryPreview> = withIOContext {
                val itemCount = pageCount.takeIf { it > 0 } ?: Int.MAX_VALUE
                val key = params.key ?: 0
                val up = getOffset(params, key, itemCount)
                val end = up + getLimit(params, key) - 1
                runSuspendCatching {
                    loadLocalDetailPreviewPage(previewSession, up, end)
                }.foldToLoadResult { (items, total) ->
                    pageCount = total
                    val prevK = if (up <= 0 || items.isEmpty()) null else up
                    val nextEnd = up + items.size - 1
                    val nextK = if (nextEnd >= total - 1) null else nextEnd + 1
                    LoadResult.Page(
                        data = items,
                        prevKey = prevK,
                        nextKey = nextK,
                        itemsBefore = up,
                        itemsAfter = (total - nextEnd - 1).coerceAtLeast(0),
                    )
                }
            }
        }
    }.flow.cachedIn(viewModelScope)
}.collectAsLazyPagingItems()

context(_: Context)
private fun LazyGridScope.galleryPreview(
    data: LazyPagingItems<GalleryPreview>,
    isV2Thumb: Boolean,
    enablePrefetch: Boolean,
    onClick: (Int) -> Unit,
) {
    items(
        count = data.itemCount,
        key = data.itemKey(key = { item -> item.position }),
        contentType = { "preview" },
    ) { index ->
        val item = data[index]
        EhPreviewItem(item, placeholderIndex = index) { onClick(index) }
        if (enablePrefetch) {
            PrefetchAround(data, index, if (isV2Thumb) 20 else 6) { imageRequest(it) }
        }
    }
}

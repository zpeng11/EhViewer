package com.hippo.ehviewer.ui.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.ui.component.FAB_ANIMATE_TIME
import com.ehviewer.core.ui.component.FabLayout
import com.ehviewer.core.ui.component.LocalSideSheetState
import com.ehviewer.core.ui.component.ProvideSideSheetContent
import com.ehviewer.core.ui.util.asyncState
import com.ehviewer.core.ui.util.takeAndClear
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.onEachLatest
import com.ehviewer.core.util.withUIContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.client.data.FavListUrlBuilder
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.DrawerHandle
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.main.GalleryInfoGridItem
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.main.GalleryList
import com.hippo.ehviewer.ui.removeFromFavorites
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import moe.tarsin.coroutines.runSwallowingWithUI
import moe.tarsin.navigate

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.FavouritesScreen(navigator: DestinationsNavigator, viewModel: FavoritesViewModel = viewModel()) = Screen(navigator) {
    // Immutables
    val localFavName = stringResource(R.string.local_favorites)
    val animateItems by Settings.animateItems.collectAsState()
    var listMode by Settings.listMode.asMutableState()

    // Meta State
    var urlBuilder by viewModel.urlBuilder
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by remember { mutableIntStateOf(0) }
    var fabExpanded by remember { mutableStateOf(false) }
    var fabHidden by remember { mutableStateOf(false) }

    // Derived State
    val keyword = urlBuilder.keyword
    val favCatName = localFavName
    val title = if (keyword.isNullOrBlank()) {
        stringResource(R.string.favorites_title, favCatName)
    } else {
        stringResource(R.string.favorites_title_2, favCatName, keyword)
    }
    val density = LocalDensity.current
    val searchBarHint = stringResource(R.string.search_bar_hint, favCatName)
    val data = viewModel.data.collectAsLazyPagingItems()

    fun refresh(newUrlBuilder: FavListUrlBuilder = urlBuilder.copy(jumpTo = null, prev = null, next = null)) {
        urlBuilder = newUrlBuilder
        data.refresh()
    }

    LaunchedEffect(urlBuilder.favCat) {
        if (!urlBuilder.isLocal) {
            refresh(FavListUrlBuilder(FavListUrlBuilder.FAV_CAT_LOCAL, keyword = keyword))
            Settings.recentFavCat = FavListUrlBuilder.FAV_CAT_LOCAL
        }
    }

    ProvideSideSheetContent { sheetState ->
        val localFavCount by viewModel.localFavCount.collectAsState(0)
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.collections)) },
            windowInsets = WindowInsets(),
            colors = topBarOnDrawerColor(),
        )
        val faves = arrayOf(stringResource(id = R.string.local_favorites) to localFavCount)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
        ) {
            faves.forEach { (name, count) ->
                ListItem(
                    headlineContent = { Text(text = name) },
                    trailingContent = { Text(text = count.toString(), style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.clip(CardDefaults.shape).clickable {
                        refresh(FavListUrlBuilder(FavListUrlBuilder.FAV_CAT_LOCAL))
                        Settings.recentFavCat = FavListUrlBuilder.FAV_CAT_LOCAL
                        fabHidden = false
                        launch { sheetState.close() }
                    },
                    colors = listItemOnDrawerColor(urlBuilder.favCat == FavListUrlBuilder.FAV_CAT_LOCAL),
                )
            }
        }
    }

    val checkedInfoMap = remember { mutableStateMapOf<Long, BaseGalleryInfo>() }
    val selectMode = checkedInfoMap.isNotEmpty()
    DrawerHandle(!selectMode && !searchBarExpanded)

    SearchBarScreen(
        onApplySearch = { refresh(FavListUrlBuilder(urlBuilder.favCat, it)) },
        expanded = searchBarExpanded,
        onExpandedChange = {
            searchBarExpanded = it
            fabHidden = it
            if (it) checkedInfoMap.clear()
        },
        title = title,
        searchFieldHint = searchBarHint,
        localSearch = true,
        searchBarOffsetY = { searchBarOffsetY },
        trailingIcon = {
            val sheetState = LocalSideSheetState.current
            IconButton(
                onClick = {
                    listMode = if (listMode == 0) 1 else 0
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                val icon = if (listMode == 0) Icons.Default.GridView else Icons.AutoMirrored.Default.ViewList
                Icon(imageVector = icon, contentDescription = null)
            }
            IconButton(onClick = { launch { sheetState.open() } }, shapes = IconButtonDefaults.shapes()) {
                Icon(imageVector = Icons.Outlined.FolderSpecial, contentDescription = null)
            }
        },
    ) { contentPadding ->
        val height by collectListThumbSizeAsState()
        val showPages by Settings.showGalleryPages.collectAsState()
        val showProgress by Settings.showReadingProgress.collectAsState()
        val searchBarConnection = remember {
            val slop = ViewConfiguration.get(contextOf<Context>()).scaledTouchSlop
            val topPaddingPx = with(density) { contentPadding.calculateTopPadding().roundToPx() }
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = -consumed.y
                    if (dy >= slop) {
                        fabHidden = true
                    } else if (dy <= -slop / 2) {
                        fabHidden = false
                    }
                    searchBarOffsetY = (searchBarOffsetY - dy).roundToInt().coerceIn(-topPaddingPx, 0)
                    return Offset.Zero // We never consume it
                }
            }
        }
        GalleryList(
            data = data,
            contentModifier = Modifier.nestedScroll(searchBarConnection),
            contentPadding = contentPadding,
            listMode = listMode,
            detailItemContent = { info ->
                val checked = info.gid in checkedInfoMap
                CheckableItem(
                    checked = checked,
                    modifier = Modifier.thenIf(animateItems) { animateItem() },
                ) { interactionSource ->
                    GalleryInfoListItem(
                        onClick = {
                            if (selectMode) {
                                if (checked) {
                                    checkedInfoMap.remove(info.gid)
                                } else {
                                    checkedInfoMap[info.gid] = info
                                }
                            } else {
                                navigate(info.asDst())
                            }
                        },
                        onLongClick = {
                            checkedInfoMap[info.gid] = info
                        },
                        info = info,
                        showPages = showPages,
                        showProgress = showProgress,
                        modifier = Modifier.height(height),
                        isInFavScene = true,
                        interactionSource = interactionSource,
                    )
                }
            },
            thumbItemContent = { info ->
                val checked = info.gid in checkedInfoMap
                CheckableItem(
                    checked = checked,
                    modifier = Modifier.thenIf(animateItems) { animateItem() },
                ) { interactionSource ->
                    GalleryInfoGridItem(
                        onClick = {
                            if (selectMode) {
                                if (checked) {
                                    checkedInfoMap.remove(info.gid)
                                } else {
                                    checkedInfoMap[info.gid] = info
                                }
                            } else {
                                navigate(info.asDst())
                            }
                        },
                        onLongClick = {
                            checkedInfoMap[info.gid] = info
                        },
                        info = info,
                        showPages = showPages,
                        showProgress = showProgress,
                        showFavoriteStatus = false,
                        interactionSource = interactionSource,
                    )
                }
            },
            searchBarOffsetY = { searchBarOffsetY },
            scrollToTopOnRefresh = urlBuilder.favCat != FavListUrlBuilder.FAV_CAT_LOCAL,
            onRefresh = { refresh() },
            onLoading = { searchBarOffsetY = 0 },
        )
    }

    val hideFab by asyncState(
        produce = { fabHidden },
        transform = {
            onEachLatest { hide ->
                if (!hide) delay(FAB_ANIMATE_TIME.toLong())
            }
        },
    )

    FabLayout(
        hidden = hideFab && !selectMode,
        expanded = fabExpanded || selectMode,
        onExpandChanged = {
            fabExpanded = it
            checkedInfoMap.clear()
        },
        autoCancel = !selectMode,
    ) {
        if (!selectMode) {
            onClick(Icons.Default.Shuffle) {
                EhDB.randomLocalFav()?.let { info ->
                    withUIContext { navigate(info.asDst()) }
                }
            }
            onClick(Icons.Default.Refresh) {
                refresh()
            }
        } else {
            onClick(Icons.Default.DoneAll, autoClose = false) {
                val info = data.itemSnapshotList.items.associateBy { it.gid }
                checkedInfoMap.putAll(info)
            }
            onClick(Icons.Default.HeartBroken) {
                val info = checkedInfoMap.takeAndClear()
                runSwallowingWithUI {
                    info.forEach {
                        removeFromFavorites(it)
                    }
                }
                data.refresh()
            }
        }
    }
}

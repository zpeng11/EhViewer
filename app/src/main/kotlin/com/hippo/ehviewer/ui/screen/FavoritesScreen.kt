package com.hippo.ehviewer.ui.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.fork.SwipeToDismissBox
import androidx.compose.material3.fork.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.ehviewer.core.database.model.LocalFavoriteFolder
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
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.DrawerHandle
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.createLocalFavoriteFolder
import com.hippo.ehviewer.ui.deleteLocalFavoriteFolder
import com.hippo.ehviewer.ui.main.GalleryInfoGridItem
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.main.GalleryList
import com.hippo.ehviewer.ui.moveLocalFavoritesToTargetFolder
import com.hippo.ehviewer.ui.removeFromFavorites
import com.hippo.ehviewer.ui.renameLocalFavoriteFolder
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitInputText
import com.hippo.ehviewer.ui.tools.awaitSelectItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import moe.tarsin.coroutines.runSwallowingWithUI
import moe.tarsin.navigate
import moe.tarsin.tip
import splitties.init.appCtx

private sealed interface FavoriteFolderTarget {
    data object Default : FavoriteFolderTarget

    data class Extra(val slot: Int) : FavoriteFolderTarget
}

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.FavouritesScreen(navigator: DestinationsNavigator, viewModel: FavoritesViewModel = viewModel()) = Screen(navigator) {
    // Immutables
    val localFavName = stringResource(R.string.local_favorites)
    val collectionsTitle = stringResource(R.string.collections)
    val createFolderTitle = stringResource(R.string.create_favorite_folder_title)
    val renameFolderTitle = stringResource(R.string.rename_favorite_folder_title)
    val folderNameHint = stringResource(R.string.favorite_folder_name_hint)
    val folderLimitReached = stringResource(R.string.favorite_folder_limit_reached)
    val labelEmpty = stringResource(R.string.label_text_is_empty)
    val animateItems by Settings.animateItems.collectAsState()
    var listMode by Settings.listMode.asMutableState()

    // Meta State
    var route by viewModel.route
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by remember { mutableIntStateOf(0) }
    var fabExpanded by remember { mutableStateOf(false) }
    var fabHidden by remember { mutableStateOf(false) }

    val dialogState by rememberUpdatedState(contextOf<DialogState>())
    val localFavoriteFoldersState by viewModel.localFavoriteFolders.collectAsState<List<LocalFavoriteFolder>, List<LocalFavoriteFolder>?>(null)
    val localFavoriteFolders = localFavoriteFoldersState.orEmpty()

    // Derived State
    val keyword = route.keyword
    val selectedExtraSlot = route.folderSlot
    val selectedFolder = localFavoriteFolders.firstOrNull { it.slot == selectedExtraSlot }
    val favCatName = selectedFolder?.name ?: localFavName
    val title = if (keyword.isNullOrBlank()) {
        stringResource(R.string.favorites_title, favCatName)
    } else {
        stringResource(R.string.favorites_title_2, favCatName, keyword)
    }
    val density = LocalDensity.current
    val searchBarHint = stringResource(R.string.search_bar_hint, favCatName)
    val data = viewModel.data.collectAsLazyPagingItems()
    val checkedInfoMap = remember { mutableStateMapOf<Long, BaseGalleryInfo>() }
    val selectMode = checkedInfoMap.isNotEmpty()

    fun extraFavoriteSlotBadge(info: BaseGalleryInfo): Int? =
        info.favoriteSlot.takeIf {
            selectedExtraSlot == null && it in LocalFavoriteFolder.VALID_SLOT_RANGE
        }

    fun refresh(newRoute: LocalFavoritesRoute = route.copy()) {
        route = newRoute
        data.refresh()
    }

    fun openLocalFolder(slot: Int? = null) {
        refresh(LocalFavoritesRoute(folderSlot = slot, keyword = keyword))
        fabHidden = false
    }

    suspend fun selectFavoriteFolderTarget(): FavoriteFolderTarget? {
        val extraFolderTargets = localFavoriteFolders.filterNot { it.slot == selectedExtraSlot }
        val allowDefaultTarget = selectedExtraSlot != null
        val items = buildList {
            if (allowDefaultTarget) {
                add(localFavName)
            }
            addAll(extraFolderTargets.map { "[${it.slot}] ${it.name}" })
        }
        if (items.isEmpty()) {
            tip(R.string.no_extra_favorite_folders)
            return null
        }
        val selectedIndex = with(dialogState) {
            awaitSelectItem(
                items = items,
                title = if (allowDefaultTarget) {
                    R.string.select_target_favorite_folder
                } else {
                    R.string.select_extra_favorite_folder
                },
                selected = 0,
            )
        }
        if (allowDefaultTarget && selectedIndex == 0) {
            return FavoriteFolderTarget.Default
        }
        val extraFolderIndex = selectedIndex - if (allowDefaultTarget) 1 else 0
        return extraFolderTargets.getOrNull(extraFolderIndex)?.let { FavoriteFolderTarget.Extra(it.slot) }
    }

    suspend fun moveSelectionTo(target: FavoriteFolderTarget) {
        val selectedInfo = checkedInfoMap.values.toList()
        if (selectedInfo.isEmpty()) return
        val targetSlot = when (target) {
            FavoriteFolderTarget.Default -> null
            is FavoriteFolderTarget.Extra -> target.slot
        }
        runCatching {
            moveLocalFavoritesToTargetFolder(selectedInfo, targetSlot)
        }.onSuccess { changed ->
            if (changed > 0) {
                tip(R.string.move_to_favorite_folder_success)
                checkedInfoMap.clear()
                data.refresh()
            } else {
                tip(R.string.move_to_favorite_folder_failure)
            }
        }.onFailure {
            tip(R.string.move_to_favorite_folder_failure)
        }
    }

    LaunchedEffect(selectedExtraSlot, localFavoriteFoldersState) {
        if (localFavoriteFoldersState != null && selectedExtraSlot != null && selectedFolder == null) {
            openLocalFolder()
        }
    }

    ProvideSideSheetContent { sheetState ->
        val localFavCount by viewModel.localFavCount.collectAsState(0)
        val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold

        suspend fun promptCreateExtraFolder() {
            val name = with(dialogState) {
                awaitInputText(title = createFolderTitle, hint = folderNameHint) { text ->
                    if (text.isBlank()) {
                        raise(labelEmpty)
                    }
                }
            }
            val created = createLocalFavoriteFolder(name)
            if (created == null) {
                tip(folderLimitReached)
                return
            }
            openLocalFolder(created.slot)
            sheetState.close()
        }

        suspend fun promptRenameExtraFolder(folder: LocalFavoriteFolder) {
            val updatedName = with(dialogState) {
                awaitInputText(
                    initial = folder.name,
                    title = renameFolderTitle,
                    hint = folderNameHint,
                ) { text ->
                    if (text.isBlank()) {
                        raise(labelEmpty)
                    }
                }
            }
            renameLocalFavoriteFolder(folder.slot, updatedName)
        }

        suspend fun promptDeleteExtraFolder(folder: LocalFavoriteFolder): Boolean {
            with(dialogState) {
                awaitConfirmationOrCancel(confirmText = R.string.delete) {
                    Text(text = appCtx.getString(R.string.delete_favorite_folder, folder.name))
                }
            }
            val deleted = deleteLocalFavoriteFolder(folder.slot)
            if (deleted && selectedExtraSlot == folder.slot) {
                openLocalFolder()
            }
            return deleted
        }

        TopAppBar(
            title = { Text(text = collectionsTitle) },
            windowInsets = WindowInsets(),
            colors = topBarOnDrawerColor(),
            actions = {
                IconButton(onClick = { launch { dialogState.runCatching { promptCreateExtraFolder() } } }, shapes = IconButtonDefaults.shapes()) {
                    Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = null)
                }
            },
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
        ) {
            ListItem(
                headlineContent = { Text(text = localFavName) },
                trailingContent = { Text(text = localFavCount.toString(), style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.clip(CardDefaults.shape).clickable {
                    openLocalFolder()
                    launch { sheetState.close() }
                },
                colors = listItemOnDrawerColor(selectedExtraSlot == null),
            )
            localFavoriteFolders.forEach { folder ->
                key(folder.slot) {
                    val folderCount by remember(folder.slot) { viewModel.extraFavCount(folder.slot) }.collectAsState(0)
                    val dismissState = remember {
                        SwipeToDismissBoxState(
                            initialValue = SwipeToDismissBoxValue.Settled,
                            positionalThreshold = positionalThreshold,
                        )
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {},
                        enableDismissFromStartToEnd = false,
                        onDismiss = {
                            dialogState.runCatching { promptDeleteExtraFolder(folder) }
                                .onSuccess { deleted ->
                                    if (!deleted) {
                                        dismissState.reset()
                                    }
                                }
                                .onFailure {
                                    dismissState.reset()
                                }
                        },
                    ) {
                        ListItem(
                            headlineContent = { Text(text = "[${folder.slot}] ${folder.name}") },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(text = folderCount.toString(), style = MaterialTheme.typography.bodyLarge)
                                    IconButton(
                                        onClick = {
                                            launch { dialogState.runCatching { promptRenameExtraFolder(folder) } }
                                        },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier.clip(CardDefaults.shape).clickable {
                                openLocalFolder(folder.slot)
                                launch { sheetState.close() }
                            },
                            colors = listItemOnDrawerColor(selectedExtraSlot == folder.slot),
                        )
                    }
                }
            }
        }
    }

    DrawerHandle(!selectMode && !searchBarExpanded)

    SearchBarScreen(
        onApplySearch = { refresh(LocalFavoritesRoute(folderSlot = route.folderSlot, keyword = it)) },
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
                        localOnlyThumb = true,
                        extraFavoriteSlotBadge = extraFavoriteSlotBadge(info),
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
                        localOnlyThumb = true,
                        extraFavoriteSlotBadge = extraFavoriteSlotBadge(info),
                        interactionSource = interactionSource,
                    )
                }
            },
            searchBarOffsetY = { searchBarOffsetY },
            prefetchThumbnails = false,
            scrollToTopOnRefresh = selectedExtraSlot != null,
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
            onClick(Icons.Default.Favorite, autoClose = false) {
                val target = dialogState.runCatching { selectFavoriteFolderTarget() }.getOrNull() ?: return@onClick
                moveSelectionTo(target)
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

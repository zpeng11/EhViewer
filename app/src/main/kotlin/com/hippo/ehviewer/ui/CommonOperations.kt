/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.database.model.LocalFavoriteFolder
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.exists
import com.ehviewer.core.files.isDirectory
import com.ehviewer.core.files.write
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.GalleryInfo.Companion.LOCAL_FAVORITED
import com.ehviewer.core.model.GalleryInfo.Companion.NOT_FAVORITED
import com.ehviewer.core.ui.component.LabeledCheckbox
import com.ehviewer.core.util.mapToLongArray
import com.ehviewer.core.util.toEpochMillis
import com.ehviewer.core.util.toLocalDateTime
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.downloadDir
import com.hippo.ehviewer.download.downloadLocation
import com.hippo.ehviewer.download.tempDownloadDir
import com.hippo.ehviewer.ui.destinations.ReaderScreenDestination
import com.hippo.ehviewer.ui.reader.ReaderScreenArgs
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitResult
import com.hippo.ehviewer.ui.tools.awaitSelectDate
import com.hippo.ehviewer.ui.tools.awaitSelectItem
import com.hippo.ehviewer.ui.tools.awaitSelectItemWithIcon
import com.hippo.ehviewer.util.FavouriteStatusRouter
import com.hippo.ehviewer.util.restartApplication
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import moe.tarsin.coroutines.runSuspendCatching
import moe.tarsin.tip
import okio.Path
import splitties.init.appCtx

private fun removeNoMediaFile(downloadDir: Path) {
    (downloadDir / ".nomedia").delete()
}

private fun ensureNoMediaFile(downloadDir: Path) {
    (downloadDir / ".nomedia").apply { if (!exists()) write {} }
}

private val lck = Mutex()

suspend fun keepNoMediaFileStatus(downloadDir: Path = downloadLocation, mediaScan: Boolean = Settings.mediaScan.value) {
    if (downloadDir.isDirectory) {
        lck.withLock {
            if (mediaScan) {
                removeNoMediaFile(downloadDir)
            } else {
                ensureNoMediaFile(downloadDir)
            }
        }
    }
}

fun getFavoriteIcon(favorited: Boolean) = if (favorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder

context(_: DialogState)
suspend fun addToFavorites(galleryInfo: GalleryInfo): Boolean = updateLocalFavorite(galleryInfo, true)

context(_: DialogState)
suspend fun addToFavorites(galleryInfo: GalleryInfo, extraSlot: Int): Boolean = addToFavorites(listOf(galleryInfo), extraSlot) > 0

context(_: DialogState, _: MainActivity)
suspend fun selectExtraFavoriteFolder(excludedSlot: Int? = null): LocalFavoriteFolder? {
    val localFavoriteFolders = EhDB.localFavoriteFolders.first().filterNot { it.slot == excludedSlot }
    if (localFavoriteFolders.isEmpty()) {
        tip(R.string.no_extra_favorite_folders)
        return null
    }
    val selected = awaitSelectItem(
        items = localFavoriteFolders.map { "[${it.slot}] ${it.name}" },
        title = R.string.select_extra_favorite_folder,
        selected = 0,
    )
    return localFavoriteFolders.getOrNull(selected)
}

suspend fun addToFavorites(galleryInfoList: Collection<GalleryInfo>, extraSlot: Int): Int {
    if (galleryInfoList.isEmpty()) return 0
    EhDB.getLocalFavoriteFolder(extraSlot) ?: return 0
    EhDB.putLocalFavorites(galleryInfoList)
    val updatedGids = EhDB.moveLocalFavoritesToExtraFolder(galleryInfoList.mapToLongArray(GalleryInfo::gid), extraSlot)
    if (updatedGids.isEmpty()) return 0
    val updatedGidsSet = updatedGids.toSet()
    galleryInfoList.forEach { galleryInfo ->
        if (galleryInfo.gid in updatedGidsSet) {
            galleryInfo.favoriteSlot = extraSlot
        }
    }
    FavouriteStatusRouter.notify(
        gids = updatedGids,
        favoriteSlot = extraSlot,
    )
    return updatedGids.size
}

context(_: DialogState)
suspend fun modifyFavorites(galleryInfo: GalleryInfo): Boolean {
    val status = EhDB.getLocalFavoriteStatus(galleryInfo.gid)
    return updateLocalFavorite(galleryInfo, status is EhDB.FavoriteStatus.NotFavorited)
}

private suspend fun updateLocalFavorite(galleryInfo: GalleryInfo, favorited: Boolean): Boolean = with(galleryInfo) {
    if (favorited) {
        EhDB.putLocalFavorites(galleryInfo)
        favoriteSlot = LOCAL_FAVORITED
    } else {
        EhDB.removeLocalFavorites(galleryInfo)
        favoriteSlot = NOT_FAVORITED
    }
    FavouriteStatusRouter.notify(galleryInfo)
    favorited
}

suspend fun removeFromFavorites(galleryInfo: GalleryInfo) = updateLocalFavorite(galleryInfo, false)

suspend fun createLocalFavoriteFolder(name: String): LocalFavoriteFolder? = EhDB.createLocalFavoriteFolder(name)

suspend fun renameLocalFavoriteFolder(slot: Int, name: String): Boolean {
    val result = EhDB.renameLocalFavoriteFolder(slot, name) ?: return false
    FavouriteStatusRouter.notify(
        gids = result.affectedGids,
        favoriteSlot = result.folder.slot,
    )
    return true
}

suspend fun deleteLocalFavoriteFolder(slot: Int): Boolean {
    val affectedGids = EhDB.deleteLocalFavoriteFolder(slot) ?: return false
    FavouriteStatusRouter.notify(
        gids = affectedGids,
        favoriteSlot = LOCAL_FAVORITED,
    )
    return true
}

suspend fun moveLocalFavoritesToExtraFolder(gids: LongArray, slot: Int): Int {
    val folder = EhDB.getLocalFavoriteFolder(slot) ?: return 0
    val updatedGids = EhDB.moveLocalFavoritesToExtraFolder(gids, slot)
    FavouriteStatusRouter.notify(
        gids = updatedGids,
        favoriteSlot = slot,
    )
    return updatedGids.size
}

suspend fun moveLocalFavoritesToExtraFolder(galleryInfoList: Collection<GalleryInfo>, slot: Int): Int {
    if (galleryInfoList.isEmpty()) return 0
    EhDB.getLocalFavoriteFolder(slot) ?: return 0
    val updatedGids = EhDB.moveLocalFavoritesToExtraFolder(galleryInfoList.mapToLongArray(GalleryInfo::gid), slot)
    if (updatedGids.isEmpty()) return 0
    val updatedGidsSet = updatedGids.toSet()
    galleryInfoList.forEach { galleryInfo ->
        if (galleryInfo.gid in updatedGidsSet) {
            galleryInfo.favoriteSlot = slot
        }
    }
    FavouriteStatusRouter.notify(
        gids = updatedGids,
        favoriteSlot = slot,
    )
    return updatedGids.size
}

suspend fun moveLocalFavoritesToDefaultFolder(gids: LongArray): Int {
    val updatedGids = EhDB.moveLocalFavoritesToDefaultFolder(gids)
    FavouriteStatusRouter.notify(
        gids = updatedGids,
        favoriteSlot = LOCAL_FAVORITED,
    )
    return updatedGids.size
}

suspend fun moveLocalFavoritesToDefaultFolder(galleryInfoList: Collection<GalleryInfo>): Int {
    if (galleryInfoList.isEmpty()) return 0
    val updatedGids = EhDB.moveLocalFavoritesToDefaultFolder(galleryInfoList.mapToLongArray(GalleryInfo::gid))
    if (updatedGids.isEmpty()) return 0
    val updatedGidsSet = updatedGids.toSet()
    galleryInfoList.forEach { galleryInfo ->
        if (galleryInfo.gid in updatedGidsSet) {
            galleryInfo.favoriteSlot = LOCAL_FAVORITED
        }
    }
    FavouriteStatusRouter.notify(
        gids = updatedGids,
        favoriteSlot = LOCAL_FAVORITED,
    )
    return updatedGids.size
}

suspend fun moveLocalFavoritesToTargetFolder(galleryInfoList: Collection<GalleryInfo>, slot: Int?): Int = if (slot == null) {
    moveLocalFavoritesToDefaultFolder(galleryInfoList)
} else {
    moveLocalFavoritesToExtraFolder(galleryInfoList, slot)
}

context(_: DestinationsNavigator)
fun navToReader(info: BaseGalleryInfo, page: Int = -1) = navToReader(ReaderScreenArgs.Gallery(info, page))

context(_: DestinationsNavigator)
fun navToReader(path: String) = navToReader(ReaderScreenArgs.Archive(path))

context(nav: DestinationsNavigator)
private fun navToReader(args: ReaderScreenArgs) = nav.navigate(ReaderScreenDestination(args)) { launchSingleTop = true }

context(_: DialogState, _: MainActivity, _: DestinationsNavigator)
suspend fun doGalleryInfoAction(info: BaseGalleryInfo) {
    val downloaded = DownloadManager.containDownloadInfo(info.gid)
    val favorited = EhDB.containLocalFavorites(info.gid)
    data class GalleryAction(
        val icon: ImageVector,
        val title: Int,
        val onClick: suspend () -> Unit,
    )

    val actions = buildList {
        add(
            GalleryAction(Icons.AutoMirrored.Default.MenuBook, R.string.read) {
                navToReader(info)
            },
        )
        if (downloaded) {
            add(
                GalleryAction(Icons.Default.Delete, R.string.delete_downloads) {
                    confirmRemoveDownload(info)
                },
            )
        }
        add(
            GalleryAction(
                icon = if (favorited) Icons.Default.HeartBroken else Icons.Default.Favorite,
                title = if (favorited) R.string.remove_from_local_favourites else R.string.add_to_local_favourites,
            ) {
                if (favorited) {
                    runSuspendCatching {
                        removeFromFavorites(info)
                        tip(R.string.remove_from_favorite_success)
                    }.onFailure {
                        tip(R.string.remove_from_favorite_failure)
                    }
                } else {
                    runSuspendCatching {
                        addToFavorites(info)
                        tip(R.string.add_to_favorite_success)
                    }.onFailure {
                        tip(R.string.add_to_favorite_failure)
                    }
                }
            },
        )
        if (downloaded) {
            add(
                GalleryAction(Icons.AutoMirrored.Default.DriveFileMove, R.string.download_move_dialog_title) {
                    showMoveDownloadLabel(info)
                },
            )
        }
    }
    val selected = awaitSelectItemWithIcon(actions.map { it.icon to it.title }, EhUtils.getSuitableTitle(info))
    actions[selected].onClick()
}

context(_: DialogState, _: MainActivity, _: DestinationsNavigator)
suspend fun doHistoryInfoAction(info: BaseGalleryInfo, canReadLocally: Boolean) {
    val favorited = EhDB.containLocalFavorites(info.gid)
    val items = buildList {
        if (canReadLocally) {
            add(Icons.AutoMirrored.Default.MenuBook to R.string.read)
        }
        val favorite = if (favorited) {
            Icons.Default.HeartBroken to R.string.remove_from_local_favourites
        } else {
            Icons.Default.Favorite to R.string.add_to_local_favourites
        }
        add(favorite)
        add(Icons.Default.Delete to R.string.remove_from_history)
    }
    val selected = awaitSelectItemWithIcon(items, EhUtils.getSuitableTitle(info))
    var offset = 0
    if (canReadLocally) {
        if (selected == offset) {
            navToReader(info)
            return
        }
        offset++
    }
    if (selected == offset) {
        if (favorited) {
            runSuspendCatching {
                removeFromFavorites(info)
                tip(R.string.remove_from_favorite_success)
            }.onFailure {
                tip(R.string.remove_from_favorite_failure)
            }
        } else {
            runSuspendCatching {
                addToFavorites(info)
                tip(R.string.add_to_favorite_success)
            }.onFailure {
                tip(R.string.add_to_favorite_failure)
            }
        }
        return
    }
    EhDB.deleteHistoryInfo(info)
}

context(_: DialogState)
private suspend fun confirmRemoveDownload(text: String): Boolean {
    val checked = awaitResult(
        initial = Settings.removeImageFiles,
        title = R.string.download_remove_dialog_title,
    ) {
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LabeledCheckbox(
                modifier = Modifier.fillMaxWidth(),
                checked = expectedValue,
                onCheckedChange = { expectedValue = it },
                label = stringResource(id = R.string.download_remove_dialog_check_text),
                indication = null,
            )
        }
    }
    Settings.removeImageFiles = checked
    return checked
}

context(_: DialogState)
suspend fun confirmRemoveDownload(info: GalleryInfo) {
    val text = appCtx.getString(R.string.download_remove_dialog_message, EhUtils.getSuitableTitle(info))
    val checked = confirmRemoveDownload(text)
    withIOContext {
        DownloadManager.deleteDownload(info.gid, checked)
    }
}

context(_: DialogState)
suspend fun confirmRemoveDownloadRange(list: Collection<DownloadInfo>) {
    val text = appCtx.getString(R.string.download_remove_dialog_message_2, list.size)
    val checked = confirmRemoveDownload(text)
    withIOContext {
        // Delete
        DownloadManager.deleteRangeDownload(list.mapToLongArray(DownloadInfo::gid))
        // Delete image files
        if (checked) {
            list.forEach { info ->
                // Delete file
                info.downloadDir?.delete()
                info.tempDownloadDir?.delete()
                // Remove download path
                EhDB.removeDownloadDirname(info.gid)
            }
        }
    }
}

context(_: DialogState)
suspend fun showMoveDownloadLabel(info: GalleryInfo) {
    val defaultLabel = appCtx.getString(R.string.default_download_label_name)
    val labels = buildList {
        add(defaultLabel)
        DownloadManager.labelList.forEach {
            add(it.label)
        }
    }
    val selected = awaitSelectItem(labels, R.string.download_move_dialog_title)
    val downloadInfo = DownloadManager.getDownloadInfo(info.gid) ?: return
    val label = if (selected == 0) null else labels[selected]
    DownloadManager.changeLabel(listOf(downloadInfo), label)
}

context(_: DialogState)
suspend fun showMoveDownloadLabelList(list: Collection<DownloadInfo>): String? {
    val defaultLabel = appCtx.getString(R.string.default_download_label_name)
    val labels = buildList {
        add(defaultLabel)
        DownloadManager.labelList.forEach {
            add(it.label)
        }
    }
    val selected = awaitSelectItem(labels, R.string.download_move_dialog_title)
    val label = if (selected == 0) null else labels[selected]
    DownloadManager.changeLabel(list, label)
    return label
}

context(_: DialogState)
suspend fun awaitSelectDate(): String {
    val initial = LocalDate(2007, 3, 21)
    val yesterday = Clock.System.todayIn(TimeZone.UTC).minus(1, DateTimeUnit.DAY)
    val initialMillis = initial.toEpochMillis()
    val yesterdayMillis = yesterday.toEpochMillis()
    val dateRange = initialMillis..yesterdayMillis
    val dateMillis = awaitSelectDate(
        title = R.string.go_to,
        yearRange = initial.year..yesterday.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in dateRange
        },
    )
    return dateMillis.toLocalDateTime().date.toString()
}

context(_: Context, _: DialogState)
suspend fun showRestartDialog() {
    awaitConfirmationOrCancel {
        Text(stringResource(R.string.settings_restart))
    }
    restartApplication()
}

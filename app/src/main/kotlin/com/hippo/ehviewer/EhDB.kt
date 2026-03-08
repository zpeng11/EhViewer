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
package com.hippo.ehviewer

import androidx.room.execSQL
import androidx.room.useWriterConnection
import arrow.fx.coroutines.resource
import arrow.fx.coroutines.resourceScope
import com.ehviewer.core.data.model.asEntity
import com.ehviewer.core.database.EhDatabase
import com.ehviewer.core.database.Schema17to18
import com.ehviewer.core.database.getDatabasePath
import com.ehviewer.core.database.model.DownloadArtist
import com.ehviewer.core.database.model.DownloadDirname
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.database.model.DownloadLabel
import com.ehviewer.core.database.model.Filter
import com.ehviewer.core.database.model.GalleryEntity
import com.ehviewer.core.database.model.HistoryInfo
import com.ehviewer.core.database.model.LocalFavoriteFolder
import com.ehviewer.core.database.model.LocalFavoriteInfo
import com.ehviewer.core.database.model.ProgressInfo
import com.ehviewer.core.database.model.QuickSearch
import com.ehviewer.core.database.roomDb
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.sendTo
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.GalleryInfo
import com.hippo.ehviewer.download.DownloadManager
import kotlinx.coroutines.flow.Flow
import okio.Path
import splitties.init.appCtx

object EhDB {
    private const val DB_NAME = "eh.db"
    private val db = roomDb<EhDatabase>(DB_NAME) {
        addMigrations(Schema17to18())
    }
    private val localFavoriteName by lazy { appCtx.getString(R.string.local_favorites) }

    data class LocalFavoriteFolderRenameResult(
        val folder: LocalFavoriteFolder,
        val affectedGids: LongArray,
    )

    suspend fun putGalleryInfo(galleryInfo: GalleryEntity) {
        db.galleryDao().upsert(galleryInfo)
    }

    private suspend fun deleteGalleryInfo(galleryInfo: GalleryEntity) {
        runCatching { db.galleryDao().delete(galleryInfo) }
    }

    suspend fun updateGalleryInfo(galleryInfoList: List<GalleryEntity>) {
        db.galleryDao().update(galleryInfoList)
    }

    suspend fun getGalleryInfoMap(gids: Collection<Long>): Map<Long, GalleryEntity> {
        if (gids.isEmpty()) return emptyMap()
        return db.galleryDao().load(gids.distinct()).associateBy(GalleryEntity::gid)
    }

    fun getReadProgressFlow(gid: Long) = db.progressDao().getPageFlow(gid)
    suspend fun getReadProgress(gid: Long) = db.progressDao().getPage(gid)
    suspend fun putReadProgress(gid: Long, page: Int) = db.progressDao().upsert(ProgressInfo(gid, page))
    suspend fun clearProgressInfo() = db.progressDao().deleteAll()

    suspend fun getAllDownloadInfo() = db.downloadsDao().joinList().onEach {
        if (it.state == DownloadInfo.STATE_WAIT || it.state == DownloadInfo.STATE_DOWNLOAD) {
            it.state = DownloadInfo.STATE_NONE
        }
    }

    suspend fun updateDownloadInfo(downloadInfo: Collection<DownloadInfo>) {
        val dao = db.downloadsDao()
        dao.update(downloadInfo.map(DownloadInfo::downloadInfo))
    }

    suspend fun putDownloadInfo(downloadInfo: DownloadInfo) {
        putGalleryInfo(downloadInfo.galleryInfo)
        db.downloadsDao().upsert(downloadInfo.downloadInfo)
    }

    suspend fun removeDownloadInfo(downloadInfo: DownloadInfo) {
        val dao = db.downloadsDao()
        dao.delete(downloadInfo.downloadInfo)
        deleteGalleryInfo(downloadInfo.galleryInfo)
    }

    suspend fun randomLocalFav() = db.localFavoritesDao().random()

    suspend fun removeDownloadInfo(downloadInfo: List<DownloadInfo>) {
        val dao = db.downloadsDao()
        downloadInfo.forEach {
            dao.delete(it.downloadInfo)
            deleteGalleryInfo(it.galleryInfo)
        }
    }

    suspend fun getDownloadDirname(gid: Long): String? {
        val dao = db.downloadDirnameDao()
        val raw = dao.load(gid)
        return raw?.dirname
    }

    suspend fun putDownloadDirname(gid: Long, dirname: String) {
        val dao = db.downloadDirnameDao()
        dao.upsert(DownloadDirname(gid, dirname))
    }

    suspend fun removeDownloadDirname(gid: Long) {
        val dao = db.downloadDirnameDao()
        dao.deleteByKey(gid)
    }

    private suspend fun importDownloadDirname(downloadDirnameList: List<DownloadDirname>) {
        val dao = db.downloadDirnameDao()
        dao.insertOrIgnore(downloadDirnameList)
    }

    val downloadsCountByLabel
        get() = db.downloadsDao().countByLabel()

    val downloadsCountByArtist
        get() = db.downloadsDao().countByArtist()

    suspend fun getAllDownloadLabelList() = db.downloadLabelDao().list()

    suspend fun addDownloadLabel(raw: DownloadLabel): DownloadLabel {
        // Reset id
        raw.id = null
        val dao = db.downloadLabelDao()
        raw.id = dao.insert(raw)
        return raw
    }

    suspend fun updateDownloadLabel(raw: DownloadLabel) {
        val dao = db.downloadLabelDao()
        dao.update(raw)
    }

    suspend fun updateDownloadLabel(downloadLabels: List<DownloadLabel>) {
        val dao = db.downloadLabelDao()
        dao.update(downloadLabels)
    }

    suspend fun removeDownloadLabel(raw: DownloadLabel) {
        val dao = db.downloadLabelDao()
        dao.delete(raw)
        dao.fill(raw.position)
    }

    suspend fun searchDownloadLabel(keyword: String, limit: Int) = db.downloadLabelDao().search("%$keyword%", limit)

    suspend fun putDownloadArtist(gid: Long, artists: List<DownloadArtist>) {
        if (artists.isNotEmpty()) {
            val dao = db.downloadArtistDao()
            dao.deleteByGid(gid)
            dao.insertOrIgnore(artists)
        }
    }

    suspend fun removeLocalFavorites(galleryInfo: GalleryInfo) {
        db.localFavoritesDao().deleteByKey(galleryInfo.gid)
        deleteGalleryInfo(galleryInfo.asEntity())
    }

    suspend fun removeLocalFavorites(galleryInfoList: Collection<GalleryInfo>) {
        galleryInfoList.forEach {
            removeLocalFavorites(it)
        }
    }

    suspend fun containLocalFavorites(gid: Long): Boolean {
        val dao = db.localFavoritesDao()
        return dao.contains(gid)
    }

    suspend fun getLocalFavoriteSlot(gid: Long): Int {
        val dao = db.localFavoritesDao()
        if (!dao.contains(gid)) return GalleryInfo.NOT_FAVORITED
        return dao.getExtraSlot(gid) ?: GalleryInfo.LOCAL_FAVORITED
    }

    suspend fun putLocalFavorites(galleryInfo: GalleryInfo) {
        putGalleryInfo(galleryInfo.asEntity())
        db.localFavoritesDao().upsert(LocalFavoriteInfo(galleryInfo.gid))
    }

    suspend fun putLocalFavorites(galleryInfoList: Collection<GalleryInfo>) {
        galleryInfoList.forEach {
            putLocalFavorites(it)
        }
    }

    private suspend fun importLocalFavorites(localFavorites: List<LocalFavoriteInfo>) {
        db.localFavoritesDao().insertOrIgnore(localFavorites)
    }

    suspend fun getAllQuickSearch() = db.quickSearchDao().list()

    suspend fun insertQuickSearch(quickSearch: QuickSearch) {
        val dao = db.quickSearchDao()
        quickSearch.id = dao.insert(quickSearch)
    }

    private suspend fun importQuickSearch(quickSearchList: List<QuickSearch>) {
        val dao = db.quickSearchDao()
        dao.insert(quickSearchList)
    }

    suspend fun deleteQuickSearch(quickSearch: QuickSearch) {
        val dao = db.quickSearchDao()
        dao.delete(quickSearch)
        dao.fill(quickSearch.position)
    }

    suspend fun updateQuickSearch(quickSearchList: List<QuickSearch>) {
        val dao = db.quickSearchDao()
        dao.update(quickSearchList)
    }

    val historyLazyList
        get() = db.historyDao().joinListLazy()

    fun searchHistory(keyword: String) = db.historyDao().joinListLazy("*$keyword*")

    val localFavLazyList
        get() = db.localFavoritesDao().joinListLazy()

    val localFavCount: Flow<Int>
        get() = db.localFavoritesDao().count()

    fun localFavCount(slot: Int): Flow<Int> {
        require(slot in LocalFavoriteFolder.VALID_SLOT_RANGE) {
            "slot should be in ${LocalFavoriteFolder.VALID_SLOT_RANGE}"
        }
        return db.localFavoritesDao().countInExtraSlot(slot)
    }

    val localFavoriteFolderCount: Flow<Int>
        get() = db.localFavoriteFolderDao().count()

    val localFavoriteFolders
        get() = db.localFavoriteFolderDao().listFlow()

    fun searchLocalFav(keyword: String) = db.localFavoritesDao().joinListLazy("*$keyword*")

    fun localFavLazyList(extraSlot: Int) = db.localFavoritesDao().joinListByExtraSlotLazy(extraSlot)

    fun searchLocalFav(extraSlot: Int, keyword: String) = db.localFavoritesDao().joinListByExtraSlotLazy(extraSlot, "*$keyword*")

    suspend fun putHistoryInfo(galleryInfo: GalleryInfo) {
        putGalleryInfo(galleryInfo.asEntity())
        db.historyDao().upsert(HistoryInfo(galleryInfo.gid))
    }

    suspend fun updateFavoriteSlot(gid: Long, slot: Int) {
        val dao = db.galleryDao()
        dao.load(gid)?.let {
            it.favoriteSlot = slot
            dao.update(it)
        }
    }

    suspend fun getLocalFavoriteFolder(slot: Int): LocalFavoriteFolder? {
        require(slot in LocalFavoriteFolder.VALID_SLOT_RANGE) {
            "slot should be in ${LocalFavoriteFolder.VALID_SLOT_RANGE}"
        }
        return db.localFavoriteFolderDao().load(slot)
    }

    suspend fun createLocalFavoriteFolder(name: String): LocalFavoriteFolder? {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "name should not be blank" }
        val dao = db.localFavoriteFolderDao()
        val usedSlots = dao.list().mapTo(mutableSetOf()) { it.slot }
        val availableSlot = LocalFavoriteFolder.VALID_SLOT_RANGE.firstOrNull { it !in usedSlots } ?: return null
        val folder = LocalFavoriteFolder(slot = availableSlot, name = normalizedName)
        dao.upsert(folder)
        return folder
    }

    suspend fun renameLocalFavoriteFolder(slot: Int, name: String): LocalFavoriteFolderRenameResult? {
        require(slot in LocalFavoriteFolder.VALID_SLOT_RANGE) {
            "slot should be in ${LocalFavoriteFolder.VALID_SLOT_RANGE}"
        }
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "name should not be blank" }
        val folderDao = db.localFavoriteFolderDao()
        val original = folderDao.load(slot) ?: return null
        val updated = LocalFavoriteFolder(slot = original.slot, name = normalizedName)
        folderDao.upsert(updated)
        val gids = db.localFavoritesDao().listGidsInExtraSlot(slot).toLongArray()
        return LocalFavoriteFolderRenameResult(updated, gids)
    }

    suspend fun deleteLocalFavoriteFolder(slot: Int): LongArray? {
        require(slot in LocalFavoriteFolder.VALID_SLOT_RANGE) {
            "slot should be in ${LocalFavoriteFolder.VALID_SLOT_RANGE}"
        }
        val folderDao = db.localFavoriteFolderDao()
        folderDao.load(slot) ?: return null
        val localFavoritesDao = db.localFavoritesDao()
        val gids = localFavoritesDao.listGidsInExtraSlot(slot).toLongArray()
        localFavoritesDao.clearExtraSlot(slot)
        folderDao.deleteBySlot(slot)
        return gids
    }

    suspend fun moveLocalFavoritesToExtraFolder(gids: LongArray, slot: Int): LongArray {
        require(slot in LocalFavoriteFolder.VALID_SLOT_RANGE) {
            "slot should be in ${LocalFavoriteFolder.VALID_SLOT_RANGE}"
        }
        if (gids.isEmpty()) return LongArray(0)
        checkNotNull(db.localFavoriteFolderDao().load(slot)) { "Folder $slot does not exist" }
        val dao = db.localFavoritesDao()
        val existingGids = dao.listExistingGids(gids).toLongArray()
        if (existingGids.isEmpty()) return existingGids
        dao.updateExtraSlot(existingGids, slot)
        return existingGids
    }

    suspend fun moveLocalFavoritesToDefaultFolder(gids: LongArray): LongArray {
        if (gids.isEmpty()) return LongArray(0)
        val dao = db.localFavoritesDao()
        val existingGids = dao.listExistingGids(gids).toLongArray()
        if (existingGids.isEmpty()) return existingGids
        dao.updateExtraSlot(existingGids, null)
        return existingGids
    }

    suspend fun getLocalFavoriteStatus(gid: Long): FavoriteStatus {
        val localFavoritesDao = db.localFavoritesDao()
        val isLocal = localFavoritesDao.contains(gid)
        if (!isLocal) return FavoriteStatus.NotFavorited
        val extraSlot = localFavoritesDao.getExtraSlot(gid)
        if (extraSlot == null) {
            return FavoriteStatus.LocalDefault
        }
        val name = db.localFavoriteFolderDao().load(extraSlot)?.name ?: localFavoriteName
        return FavoriteStatus.LocalExtra(extraSlot, name)
    }

    sealed interface FavoriteStatus {
        data object NotFavorited : FavoriteStatus

        data object LocalDefault : FavoriteStatus

        data class LocalExtra(
            val slot: Int,
            val name: String,
        ) : FavoriteStatus
    }

    private suspend fun importHistoryInfo(historyInfoList: List<HistoryInfo>) {
        val dao = db.historyDao()
        dao.insertOrIgnore(historyInfoList)
    }

    suspend fun deleteHistoryInfo(galleryInfo: GalleryEntity) {
        deleteHistoryInfo(galleryInfo.gid)
    }

    suspend fun deleteHistoryInfo(galleryInfo: GalleryInfo) {
        deleteHistoryInfo(galleryInfo.gid)
    }

    suspend fun deleteHistoryInfo(gid: Long) {
        val dao = db.historyDao()
        dao.deleteByKey(gid)
        db.galleryDao().load(gid)?.let {
            deleteGalleryInfo(it)
        }
    }

    suspend fun clearHistoryInfo() {
        val dao = db.historyDao()
        val historyList = dao.list()
        dao.deleteAll()
        historyList.forEach { runCatching { db.galleryDao().deleteByKey(it.gid) } }
    }

    suspend fun getAllFilter() = db.filterDao().list()

    suspend fun addFilter(filter: Filter): Boolean {
        val existFilter = runCatching { db.filterDao().load(filter.text, filter.mode.field) }.getOrNull()
        return if (existFilter == null) {
            filter.id = null
            filter.id = db.filterDao().insert(filter)
            true
        } else {
            false
        }
    }

    suspend fun deleteFilter(filter: Filter) {
        db.filterDao().delete(filter)
    }

    suspend fun updateFilter(filter: Filter) {
        db.filterDao().update(filter)
    }

    suspend fun exportDB(file: Path) {
        db.useWriterConnection { conn ->
            conn.execSQL("PRAGMA wal_checkpoint(FULL)")
            conn.execSQL("VACUUM")
        }
        val dbFile = getDatabasePath(DB_NAME)
        dbFile sendTo file
    }

    suspend fun importDB(file: Path) = resourceScope {
        val tempDBName = "tmp.db"
        val dbFile = getDatabasePath(tempDBName)
        file sendTo dbFile
        val oldDB = resource {
            roomDb<EhDatabase>(tempDBName) {
                addMigrations(Schema17to18())
            }
        } release { db ->
            db.close()
            dbFile.delete()
        }

        db.galleryDao().insertOrIgnore(oldDB.galleryDao().list())
        db.progressDao().insertOrIgnore(oldDB.progressDao().list())

        val downloadLabelList = oldDB.downloadLabelDao().list()
        DownloadManager.addDownloadLabel(downloadLabelList)

        oldDB.downloadDirnameDao().list().let {
            importDownloadDirname(it)
        }

        val downloadInfoList = oldDB.downloadsDao().joinList().asReversed()
        DownloadManager.addDownload(downloadInfoList)

        val historyInfoList = oldDB.historyDao().list()
        importHistoryInfo(historyInfoList)

        val quickSearchList = oldDB.quickSearchDao().list()
        val currentQuickSearchList = db.quickSearchDao().list()
        val offset = currentQuickSearchList.size
        val importList = quickSearchList.filter { newQS ->
            currentQuickSearchList.none { it.name == newQS.name }
        }.onEachIndexed { index, q ->
            q.id = null
            q.position = index + offset
        }
        importQuickSearch(importList)

        oldDB.localFavoritesDao().list().let {
            importLocalFavorites(it)
        }

        oldDB.filterDao().list().forEach {
            addFilter(it)
        }
    }
}

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
package com.hippo.ehviewer.download

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import arrow.fx.coroutines.parMapNotNull
import com.ehviewer.core.data.model.asEntity
import com.ehviewer.core.database.model.DownloadArtist
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.database.model.DownloadLabel
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.find
import com.ehviewer.core.files.isCifsDocumentPath
import com.ehviewer.core.files.isDirectory
import com.ehviewer.core.files.toOkioPath
import com.ehviewer.core.files.toUri
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.preferences.edit
import com.ehviewer.core.util.logcat
import com.ehviewer.core.util.mapNotNull
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.data.needsComicInfoTagNormalization
import com.hippo.ehviewer.library.LocalLibraryResolver
import com.hippo.ehviewer.spider.COMIC_INFO_FILE
import com.hippo.ehviewer.spider.SPIDER_INFO_FILENAME
import com.hippo.ehviewer.spider.readComicInfo
import com.hippo.ehviewer.spider.readComicInfoFromArchive
import com.hippo.ehviewer.spider.readCompatFromPath
import com.hippo.ehviewer.spider.toSimpleTags
import com.hippo.ehviewer.util.AppConfig
import com.hippo.ehviewer.util.insertWith
import com.hippo.ehviewer.util.runAssertingNotMainThread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import logcat.LogPriority
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath

object DownloadManager {
    private val sortMode
        get() = SortMode.from(Settings.downloadSortMode.value)

    private val allInfoList = runAssertingNotMainThread { EhDB.getAllDownloadInfo() as MutableList }

    val downloadInfoList: List<DownloadInfo>
        get() = allInfoList

    private val allInfoMap = allInfoList.associateBy { it.gid } as MutableMap<Long, DownloadInfo>

    val labelList = Snapshot.withMutableSnapshot {
        mutableStateListOf<DownloadLabel>().apply {
            addAll(runAssertingNotMainThread { EhDB.getAllDownloadLabelList() })
        }
    }

    private val mutableNotifyFlow = MutableSharedFlow<DownloadInfo>(extraBufferCapacity = 1)
    val notifyFlow = mutableNotifyFlow.asSharedFlow()

    private val sortMutex = Mutex()

    private val _isInitialized = MutableStateFlow(false)
    val isInitializedFlow: StateFlow<Boolean> = _isInitialized.asStateFlow()
    val isInitialized: Boolean
        get() = _isInitialized.value

    private val localThumbVersionFlow = MutableStateFlow<Map<Long, Int>>(emptyMap())

    init {
        val mode = sortMode
        if (mode != SortMode.Default) {
            allInfoList.sortWith(mode.comparator())
        }
        _isInitialized.value = true
    }

    fun containLabel(label: String?): Boolean {
        if (label == null) return false
        return labelList.any { it.label == label }
    }

    fun containDownloadInfo(gid: Long) = allInfoMap.containsKey(gid)

    fun getDownloadInfo(gid: Long) = allInfoMap[gid]

    fun getReadableDownloadInfo(gid: Long) = LocalLibraryResolver.resolveReadableEntry(getDownloadInfo(gid))

    fun canReadGalleryLocally(gid: Long) = LocalLibraryResolver.canRead(getDownloadInfo(gid))

    fun canExportDownload(gid: Long) = LocalLibraryResolver.canExport(getDownloadInfo(gid))

    @Stable
    @Composable
    fun collectContainDownloadInfo(gid: Long): State<Boolean> = remember(gid) {
        notifyFlow.transform { if (it.gid == gid) emit(containDownloadInfo(gid)) }
    }.collectAsState(containDownloadInfo(gid))

    @Stable
    @Composable
    fun collectCanReadGalleryLocally(gid: Long): State<Boolean> = remember(gid) {
        notifyFlow.transform { if (it.gid == gid) emit(canReadGalleryLocally(gid)) }
    }.collectAsState(canReadGalleryLocally(gid))

    @Stable
    @Composable
    fun collectLocalThumbVersion(gid: Long): State<Int> = remember(gid) {
        localThumbVersionFlow.map { it[gid] ?: 0 }
    }.collectAsState(0)

    fun notifyLocalThumbReady(gid: Long) {
        localThumbVersionFlow.update { map ->
            map + (gid to ((map[gid] ?: 0) + 1))
        }
    }

    private suspend fun emitUpdates(infoList: Iterable<DownloadInfo>) {
        infoList.forEach { mutableNotifyFlow.emit(it) }
    }

    suspend fun addDownload(downloadInfoList: List<DownloadInfo>) {
        val added = mutableListOf<DownloadInfo>()
        sortMutex.withLock {
            val comparator = sortMode.comparator()
            downloadInfoList.forEach { info ->
                if (containDownloadInfo(info.gid)) return@forEach
                allInfoList.insertWith(info, comparator)
                allInfoMap[info.gid] = info
                added += info
            }
        }
        added.forEach {
            EhDB.putDownloadInfo(it)
            EhDB.putDownloadArtist(it.gid, it.artistInfoList)
        }
        emitUpdates(added)
    }

    suspend fun addDownloadLabel(downloadLabelList: List<DownloadLabel>) {
        downloadLabelList.forEach { label ->
            if (!containLabel(label.label)) {
                Snapshot.withMutableSnapshot {
                    label.position = labelList.size
                    labelList.add(EhDB.addDownloadLabel(label))
                }
            }
        }
    }

    suspend fun restoreDownload(galleryInfo: BaseGalleryInfo, dirname: String) {
        val info = DownloadInfo(galleryInfo.asEntity(), dirname)
        sortMutex.withLock {
            allInfoList.insertWith(info, sortMode.comparator())
            allInfoMap[galleryInfo.gid] = info
        }
        EhDB.putDownloadInfo(info)
        mutableNotifyFlow.emit(info)
    }

    suspend fun deleteDownload(gid: Long, deleteFiles: Boolean = false) {
        val info = sortMutex.withLock {
            allInfoMap.remove(gid)?.also { allInfoList.remove(it) }
        } ?: return
        EhDB.removeDownloadInfo(info)
        if (deleteFiles) {
            info.downloadDir?.delete()
            info.tempDownloadDir?.delete()
            EhDB.removeDownloadDirname(info.gid)
        }
        mutableNotifyFlow.emit(info)
    }

    suspend fun deleteRangeDownload(gidList: LongArray) {
        val list = sortMutex.withLock {
            gidList.mapNotNull { gid ->
                allInfoMap.remove(gid)?.also { allInfoList.remove(it) }
            }
        }
        if (list.isEmpty()) return
        EhDB.removeDownloadInfo(list)
        emitUpdates(list)
    }

    suspend fun sortDownloads(mode: SortMode) = sortMutex.withLock {
        allInfoList.sortWith(mode.comparator())
    }

    suspend fun resetAllReadingProgress() = runCatching {
        EhDB.clearProgressInfo()
    }.onFailure { logcat(it) }

    suspend fun changeLabel(list: Collection<DownloadInfo>, label: String?) {
        if (label != null && !containLabel(label)) {
            logcat(TAG, LogPriority.ERROR) { "Not exits label: $label" }
            return
        }
        list.forEach { it.label = label }
        EhDB.updateDownloadInfo(list)
        emitUpdates(list)
    }

    suspend fun addLabel(label: String?) {
        if (label == null || containLabel(label)) return
        Snapshot.withMutableSnapshot {
            labelList.add(EhDB.addDownloadLabel(DownloadLabel(label, labelList.size)))
        }
    }

    suspend fun renameLabel(from: String, to: String) {
        val index = labelList.indexOfFirst { it.label == from }
        if (index == -1) return
        val renamed = Snapshot.withMutableSnapshot {
            val exist = labelList.removeAt(index)
            val updated = exist.copy(label = to)
            labelList.add(index, updated)
            updated
        }
        EhDB.updateDownloadLabel(renamed)
        val updated = sortMutex.withLock {
            allInfoList.filter {
                it.label == from
            }.onEach {
                it.label = to
            }
        }
        emitUpdates(updated)
    }

    suspend fun deleteLabel(label: String) {
        Snapshot.withMutableSnapshot {
            with(labelList) {
                val index = indexOfFirst { it.label == label }
                if (index == -1) return
                val item = get(index)
                EhDB.removeDownloadLabel(item)
                subList(index + 1, size).forEach {
                    it.position--
                }
                removeAt(index)
            }
        }
        val updated = sortMutex.withLock {
            allInfoList.filter {
                it.label == label
            }.onEach {
                it.label = null
            }
        }
        emitUpdates(updated)
    }

    suspend fun readMetadataFromLocal() {
        val isCifs = downloadLocation.isCifsDocumentPath()
        val concurrency = if (isCifs) 2 else 5
        val list = sortMutex.withLock {
            allInfoList.mapNotNull {
                val updateGallery = it.pages == 0 || it.simpleTags == null || needsComicInfoTagNormalization(it.simpleTags)
                val updateArtist = it.artistInfoList.isEmpty()
                if (updateGallery || updateArtist) {
                    Triple(it, updateGallery, updateArtist)
                } else {
                    null
                }
            }
        }.parMapNotNull(concurrency = concurrency) { (info, updateGallery, updateArtist) ->
            info.downloadDir?.run {
                val comicInfo = find(COMIC_INFO_FILE)?.let { readComicInfo(it) }
                    ?: info.archiveFile?.let { readComicInfoFromArchive(it) }
                if (comicInfo != null) {
                    val galleryInfo = if (updateGallery) {
                        info.pages = comicInfo.pageCount
                        info.simpleTags = comicInfo.toSimpleTags()
                        info.generateSLang()
                        info.galleryInfo
                    } else {
                        null
                    }

                    val artistList = if (updateArtist && comicInfo.penciller != null) {
                        info.artistInfoList = DownloadArtist.from(info.gid, comicInfo.penciller)
                        info.gid to info.artistInfoList
                    } else {
                        null
                    }

                    if (galleryInfo != null || artistList != null) {
                        galleryInfo to artistList
                    } else {
                        null
                    }
                } else if (info.pages == 0) {
                    val galleryInfo = find(SPIDER_INFO_FILENAME)?.let {
                        readCompatFromPath(it)?.run {
                            info.pages = pages
                            info.galleryInfo
                        }
                    }

                    galleryInfo?.let { it to null }
                } else {
                    null
                }
            }
        }

        val galleryInfoList = buildList {
            list.forEach { (info, artists) ->
                info?.let { add(it) }
                artists?.let { (gid, updateList) ->
                    EhDB.putDownloadArtist(gid, updateList)
                }
            }
        }
        if (galleryInfoList.isNotEmpty()) {
            EhDB.updateGalleryInfo(galleryInfoList)
        }
    }

    suspend fun backfillLocalThumbs() {
        val isCifs = downloadLocation.isCifsDocumentPath()
        val snapshot = sortMutex.withLock { allInfoList.toList() }
        snapshot.forEach { info ->
            if (isCifs) yield()
            val hadLocalThumb = info.findLocalThumbFile() != null
            if (!hadLocalThumb && info.ensureLocalThumbFile() != null) {
                notifyLocalThumbReady(info.gid)
            }
        }
    }

    private const val TAG = "DownloadManager"
}

var downloadLocation: Path
    get() = with(Settings) {
        if (downloadScheme != null) {
            Uri.Builder().apply {
                scheme(downloadScheme)
                encodedAuthority(downloadAuthority)
                encodedPath(downloadPath)
                encodedQuery(downloadQuery)
                encodedFragment(downloadFragment)
            }.build().toOkioPath()
        } else {
            AppConfig.defaultDownloadDir?.toOkioPath() ?: "".toPath()
        }
    }
    set(value) = with(value.toUri()) {
        Settings.edit {
            downloadScheme = scheme
            downloadAuthority = encodedAuthority
            downloadPath = encodedPath
            downloadQuery = encodedQuery
            downloadFragment = encodedFragment
        }
    }

val DownloadInfo.downloadDir get() = dirname?.let { downloadLocation / it }
val DownloadInfo.archiveFile get() = downloadDir?.run { find("$gid.cbz") ?: find("$gid.zip") }
val GalleryInfo.tempDownloadDir get() = AppConfig.externalTempPersistDir?.let { it / "$gid" }

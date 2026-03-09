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
package com.hippo.ehviewer.spider

import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.closeable
import arrow.fx.coroutines.parMap
import arrow.fx.coroutines.resourceScope
import com.ehviewer.core.database.model.DownloadArtist
import com.ehviewer.core.files.exists
import com.ehviewer.core.files.find
import com.ehviewer.core.files.openFileDescriptor
import com.ehviewer.core.files.sendTo
import com.ehviewer.core.model.GalleryInfo
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.client.EhUtils.getSuitableTitle
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.downloadLocation
import com.hippo.ehviewer.image.PathSource
import com.hippo.ehviewer.jni.archiveFdBatch
import com.hippo.ehviewer.library.content.LocalGalleryContent
import com.hippo.ehviewer.util.FileUtils
import okio.Path

class SpiderDen(private val info: GalleryInfo) {
    private val gid = info.gid
    private var downloadDir: Path? = null
    private var localContentDelegate: LocalGalleryContent? = null
    private val archiveName = "$gid.cbz"

    constructor(info: GalleryInfo, dirname: String) : this(info) {
        downloadDir = downloadLocation / dirname
    }

    private fun localContent(): LocalGalleryContent {
        val current = localContentDelegate
        if (current != null && current.downloadDir == downloadDir) {
            return current
        }
        return LocalGalleryContent(info, downloadDir, null).also {
            localContentDelegate = it
        }
    }

    private fun getImageSource(index: Int): PathSource {
        return localContent().getImageSource(index)
    }

    suspend fun exportAsCbz(file: Path) = downloadDir!!.find(archiveName)?.sendTo(file) ?: archiveTo(file)

    private suspend fun archiveTo(file: Path) = resourceScope {
        val comicInfo = closeable {
            val f = downloadDir!! / COMIC_INFO_FILE
            if (!f.exists()) {
                writeComicInfo()
            } else if (info.pages == 0) {
                info.pages = readComicInfo(f)!!.pageCount
            }
            f.openFileDescriptor("r")
        }
        val pages = info.pages
        val (fdBatch, names) = (0 until pages).parMap { idx ->
            val f = autoCloseable { getImageSource(idx) }
            closeable { f.source.openFileDescriptor("r") }.fd to perFilename(idx, f.type)
        }.run { plus(comicInfo.fd to COMIC_INFO_FILE) }.unzip()
        val arcFd = closeable { file.openFileDescriptor("rw") }
        archiveFdBatch(fdBatch.toIntArray(), names.toTypedArray(), arcFd.fd, pages + 1)
    }

    private suspend fun writeComicInfo() {
        downloadDir?.run {
            val pageCount = localContent().getLocalPageCount()
            if (pageCount > 0) {
                info.pages = pageCount
            }
            resolve(COMIC_INFO_FILE).also {
                info.getComicInfo().apply {
                    writeComicInfo(this, it)
                    penciller?.let { artists ->
                        DownloadManager.getDownloadInfo(gid)?.let { downloadInfo ->
                            downloadInfo.artistInfoList = DownloadArtist.from(gid, artists)
                            EhDB.putDownloadArtist(gid, downloadInfo.artistInfoList)
                        }
                    }
                }
            }
        }
    }
}

fun perFilename(index: Int, extension: String = ""): String = "%08d.%s".format(index + 1, extension)

suspend fun GalleryInfo.downloadDirname(): String {
    var dirname = EhDB.getDownloadDirname(gid)
    if (dirname == null) {
        val title = getSuitableTitle(this)
        dirname = FileUtils.sanitizeFilename("$gid-$title")
        EhDB.putDownloadDirname(gid, dirname)
    }
    return dirname
}

suspend fun getGalleryDownloadDir(info: GalleryInfo): Path {
    val dirname = info.downloadDirname()
    return downloadLocation / dirname
}

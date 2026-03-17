package com.hippo.ehviewer.library.content

import com.ehviewer.core.files.delete
import com.ehviewer.core.files.exists
import com.ehviewer.core.files.find
import com.ehviewer.core.files.isCifsDocumentPath
import com.ehviewer.core.files.isDirectory
import com.ehviewer.core.files.list
import com.ehviewer.core.files.mkdirs
import com.ehviewer.core.files.moveTo
import com.ehviewer.core.files.sendTo
import com.ehviewer.core.files.sendToWithCifsReadLease
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.EhApplication.Companion.imageCache as sCache
import com.hippo.ehviewer.client.getImageKey
import com.hippo.ehviewer.coil.read
import com.hippo.ehviewer.download.downloadLocation
import com.hippo.ehviewer.download.tempDownloadDir
import com.hippo.ehviewer.image.PathSource
import com.hippo.ehviewer.spider.COMIC_INFO_FILE
import com.hippo.ehviewer.spider.getGalleryDownloadDir
import com.hippo.ehviewer.spider.perFilename
import com.hippo.ehviewer.spider.readComicInfo
import com.hippo.ehviewer.util.AppConfig
import com.hippo.ehviewer.util.FileUtils
import java.io.IOException
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Path

class LocalGalleryContent(
    private val info: GalleryInfo,
    dirname: String? = null,
) {
    private val gid = info.gid
    private val configuredDownloadDir = dirname?.let { downloadLocation / it }

    var downloadDir: Path? = configuredDownloadDir
        private set

    private var tempDownloadDir: Path? = null
    val resolvedTempDownloadDir: Path?
        get() = tempDownloadDir
    private val lock = ReentrantReadWriteLock()
    private val readerSessionId = UUID.randomUUID().toString()
    private val readerIsolationLock = Any()
    private val readerIsolationMap = mutableMapOf<Int, ReaderIsolatedEntry>()

    @Volatile
    private var readerIsolationDir: Path? = null

    private val fileCache by lazy {
        listOfNotNull(tempDownloadDir, downloadDir).map(Path::list).flatten().associateBy { it.name } as MutableMap
    }

    constructor(
        info: GalleryInfo,
        downloadDir: Path?,
        tempDownloadDir: Path?,
    ) : this(info) {
        this.downloadDir = downloadDir
        this.tempDownloadDir = tempDownloadDir
    }

    suspend fun initDownloadDirIfExist() {
        downloadDir = configuredDownloadDir?.takeIf { it.isDirectory } ?: getGalleryDownloadDir(info).takeIf { it.isDirectory }
        tempDownloadDir = info.tempDownloadDir?.takeIf { it.isDirectory }
    }

    suspend fun initDownloadDir() {
        downloadDir = (configuredDownloadDir ?: getGalleryDownloadDir(info)).apply { mkdirs() }
        tempDownloadDir = info.tempDownloadDir?.takeIf { it.isDirectory }
    }

    private fun findImageFile(index: Int, temp: Boolean = false) = lock.read {
        val head = perFilename(index)
        fileCache.entries.firstOrNull { (name) -> name.startsWith(head) && temp == name.endsWith(TEMP_SUFFIX) }?.value
    }

    private fun containInCache(index: Int): Boolean {
        val key = getImageKey(gid, index)
        return sCache.read(key) {} != null
    }

    private fun containInDownloadDir(index: Int): Boolean = findImageFile(index) != null

    operator fun contains(index: Int): Boolean = containInCache(index) || containInDownloadDir(index)

    fun saveToPath(index: Int, file: Path): Boolean {
        val key = getImageKey(gid, index)

        sCache.read(key) {
            runCatching {
                data sendTo file
                return true
            }.onFailure {
                logcat(it)
                return false
            }
        }

        runCatching {
            requireNotNull(findImageFile(index)) sendTo file
        }.onFailure {
            logcat(it)
            return false
        }.onSuccess {
            return true
        }
        return false
    }

    fun getExtension(index: Int): String? {
        val key = getImageKey(gid, index)
        return sCache.read(key) { metadata.toFile().readText() }
            ?: findImageFile(index)?.name.let { FileUtils.getExtensionFromFilename(it) }
    }

    fun getImageSource(index: Int): PathSource {
        val key = getImageKey(gid, index)
        val snapshot = sCache.openSnapshot(key)
        if (snapshot != null) {
            return object : PathSource, AutoCloseable by snapshot {
                override val source = snapshot.data
                override val type by lazy {
                    snapshot.metadata.toFile().readText()
                }
            }
        }
        val source = requireNotNull(findImageFile(index)) { "Source $index not found!" }
        return object : PathSource {
            override val source = source
            override val type by lazy {
                FileUtils.getExtensionFromFilename(source.name)!!
            }

            override fun close() = Unit
        }
    }

    suspend fun getImageSourceForReader(index: Int): PathSource = getImageSourceWithCifsStaging(index) { source ->
        !source.source.isPhysicalPath
    }

    suspend fun getImageSourceForPreview(index: Int): PathSource {
        val source = getImageSource(index)
        if (source.source.isPhysicalPath) {
            return source
        }
        return copyForPreviewIsolationWithRetry(index, source)
    }

    fun getLocalPageCount(): Int {
        downloadDir?.find(COMIC_INFO_FILE)?.let { file ->
            readComicInfo(file)?.pageCount?.takeIf { it > 0 }?.let { return it }
        }
        val count = lock.read {
            fileCache.keys.count { name ->
                !name.endsWith(TEMP_SUFFIX) && PAGE_FILE_REGEX.matches(name)
            }
        }
        return count.takeIf { it > 0 } ?: info.pages
    }

    fun clearReaderIsolationCache() {
        val dir = synchronized(readerIsolationLock) {
            readerIsolationMap.clear()
            readerIsolationDir.also { readerIsolationDir = null }
        }
        dir?.runCatching { delete() }?.onFailure(::logcat)
    }

    private fun getOrCreateReaderIsolationDir() = synchronized(readerIsolationLock) {
        readerIsolationDir?.takeIf(Path::exists) ?: (AppConfig.tempDir / READER_ISOLATION_DIR / "$gid-$readerSessionId")
            .apply { mkdirs() }
            .also { readerIsolationDir = it }
    }

    private fun getCachedReaderIsolation(index: Int) = synchronized(readerIsolationLock) {
        val entry = readerIsolationMap[index] ?: return@synchronized null
        if (entry.path.exists()) {
            entry
        } else {
            readerIsolationMap.remove(index)
            null
        }
    }

    private suspend fun getImageSourceWithCifsStaging(
        index: Int,
        shouldStage: (PathSource) -> Boolean,
    ): PathSource {
        getCachedReaderIsolation(index)?.let(::createReaderIsolatedSource)?.let { return it }
        val source = getImageSource(index)
        if (!shouldStage(source)) {
            return source
        }
        val isolated = copyForReaderIsolationWithRetry(index, source)
        return createReaderIsolatedSource(isolated)
    }

    private suspend fun copyForReaderIsolationWithRetry(index: Int, source: PathSource): ReaderIsolatedEntry = source.use { pathSource ->
        val type = pathSource.type.lowercase()
        getCachedReaderIsolation(index)?.let { return@use it }
        val dir = getOrCreateReaderIsolationDir()
        val target = dir / perFilename(index, type)
        var lastError: Throwable? = null
        repeat(READER_COPY_MAX_ATTEMPTS) { attempt ->
            val tempTarget = dir / "${target.name}.part"
            runCatching {
                if (tempTarget.exists()) tempTarget.delete()
                pathSource.source.sendToWithCifsStagingPermit(tempTarget)
                if (target.exists()) target.delete()
                tempTarget moveTo target
                val entry = ReaderIsolatedEntry(target, type)
                synchronized(readerIsolationLock) {
                    readerIsolationMap[index] = entry
                }
                return@use entry
            }.onFailure { err ->
                lastError = err
                runCatching { tempTarget.delete() }
                if (err is CancellationException) throw err
                if (attempt + 1 < READER_COPY_MAX_ATTEMPTS) {
                    delay(READER_COPY_RETRY_DELAY_MS)
                }
            }
        }
        throw IOException("Failed to isolate CIFS source for page $index after $READER_COPY_MAX_ATTEMPTS attempts", lastError)
    }

    private suspend fun copyForPreviewIsolationWithRetry(index: Int, source: PathSource): PathSource = source.use { pathSource ->
        val type = pathSource.type.lowercase()
        val dir = (AppConfig.tempDir / PREVIEW_ISOLATION_DIR).apply { mkdirs() }
        var lastError: Throwable? = null
        repeat(PREVIEW_COPY_MAX_ATTEMPTS) { attempt ->
            val target = dir / "$gid-${UUID.randomUUID()}-${perFilename(index, type)}"
            val tempTarget = dir / "${target.name}.part"
            runCatching {
                if (tempTarget.exists()) tempTarget.delete()
                pathSource.source.sendToWithCifsStagingPermit(tempTarget)
                tempTarget moveTo target
                return@use createPreviewIsolatedSource(target, type)
            }.onFailure { err ->
                lastError = err
                runCatching { tempTarget.delete() }
                runCatching { if (target.exists()) target.delete() }
                if (err is CancellationException) throw err
                if (attempt + 1 < PREVIEW_COPY_MAX_ATTEMPTS) {
                    delay(PREVIEW_COPY_RETRY_DELAY_MS)
                }
            }
        }
        throw IOException("Failed to stage preview source for page $index after $PREVIEW_COPY_MAX_ATTEMPTS attempts", lastError)
    }

    private fun createReaderIsolatedSource(entry: ReaderIsolatedEntry) = object : PathSource {
        override val source = entry.path
        override val type = entry.type
        override fun close() = Unit
    }

    private fun createPreviewIsolatedSource(path: Path, type: String) = object : PathSource {
        override val source = path
        override val type = type

        override fun close() {
            if (path.exists()) {
                runCatching { path.delete() }.onFailure(::logcat)
            }
        }
    }
}

private const val TEMP_SUFFIX = ".tmp"
private const val READER_ISOLATION_DIR = "reader_cifs_isolation"
private const val READER_COPY_MAX_ATTEMPTS = 2
private const val READER_COPY_RETRY_DELAY_MS = 100L
private const val PREVIEW_ISOLATION_DIR = "detail_preview_isolation"
private const val PREVIEW_COPY_MAX_ATTEMPTS = 2
private const val PREVIEW_COPY_RETRY_DELAY_MS = 100L
private val PAGE_FILE_REGEX = Regex("^\\d{8}\\.\\w{3,4}")
private val cifsStagingCopySemaphore = Semaphore(1)
private val Path.isPhysicalPath get() = toString().startsWith('/')
private data class ReaderIsolatedEntry(val path: Path, val type: String)

private suspend fun Path.sendToWithCifsStagingPermit(target: Path) {
    if (!isCifsDocumentPath()) {
        this sendTo target
        return
    }
    cifsStagingCopySemaphore.withPermit {
        this@sendToWithCifsStagingPermit.sendToWithCifsReadLease(target)
    }
}

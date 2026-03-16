package com.ehviewer.core.files

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import android.system.ErrnoException
import android.system.Int64Ref
import android.system.Os
import android.webkit.MimeTypeMap
import androidx.core.database.getLongOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Sink
import okio.Source

/**
 * Limits concurrent IPC calls to CIFS DocumentProvider to prevent
 * connection/transaction exhaustion that leads to provider deadlock.
 */
private val cifsSemaphore = java.util.concurrent.Semaphore(4)

/**
 * Tracks the last time a CIFS IPC operation succeeded.
 * Used to detect stale SMB sessions after idle periods.
 */
private val cifsLastSuccess = java.util.concurrent.atomic.AtomicLong(0L)

/**
 * Serializes CIFS recovery attempts so that only one thread
 * probes the stale connection at a time, preventing a burst
 * of reconnection attempts from overwhelming the provider.
 */
private val cifsRecoveryLock = java.util.concurrent.Semaphore(1)
private const val CIFS_STALE_THRESHOLD_MS = 30_000L

class AndroidFileSystem(context: Context) : FileSystem() {
    private val contentResolver = context.contentResolver
    private val physicalFileSystem = SYSTEM

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        TODO("Not yet implemented")
    }

    override fun atomicMove(source: Path, target: Path) {
        if (source.isPhysicalFile()) {
            return physicalFileSystem.atomicMove(source, target)
        }

        val isCifs = source.toUri().isCifsDocument()
        source.runCatching {
            withCifsThrottle(isCifs) {
                DocumentsContract.renameDocument(contentResolver, toUri(), target.name)
            }
        }.onFailure {
            // ExternalStorageProvider always throw exception when renameDocument on API 28
            // https://android.googlesource.com/platform/frameworks/base/+/7bf90408e36613a84dc2a665905fde2c83cfa797
            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) {
                throw FileNotFoundException("Failed to move $source to $target")
            }
        }
    }

    override fun canonicalize(path: Path): Path {
        TODO("Not yet implemented")
    }

    override fun copy(source: Path, target: Path) {
        val usesCifsDocument = source.toUri().isCifsDocument() || target.toUri().isCifsDocument()
        if (usesCifsDocument) {
            source.inputStream().use { src ->
                target.outputStream().use { dst ->
                    src.copyTo(dst)
                }
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            source.openFileDescriptor("r").use { src ->
                target.openFileDescriptor("wt").use { dst ->
                    try {
                        Os.sendfile(dst.fileDescriptor, src.fileDescriptor, Int64Ref(0), Long.MAX_VALUE)
                        return
                    } catch (_: ErrnoException) {}
                }
            }
        }

        // Fallback to transferTo if sendfile is not available or fails
        source.inputStream().use { src ->
            target.outputStream().use { dst ->
                src.channel.transferTo(0, Long.MAX_VALUE, dst.channel)
            }
        }
    }

    fun copyFromCifsWithLease(source: Path, target: Path) {
        require(source.toUri().isCifsDocument()) { "Source must be a CIFS document: $source" }
        withCifsReadLease {
            ParcelFileDescriptor.AutoCloseInputStream(openRawCifsReadFileDescriptor(source)).use { src ->
                target.outputStream().use { dst ->
                    src.copyTo(dst, CIFS_READ_COPY_BUFFER_SIZE)
                }
            }
        }
    }

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        if (dir.isPhysicalFile()) {
            return physicalFileSystem.createDirectory(dir, mustCreate)
        }

        val alreadyExist = metadataOrNull(dir)?.isDirectory == true
        if (alreadyExist) {
            if (mustCreate) {
                throw IOException("$dir already exist")
            } else {
                return
            }
        }

        val isCifs = dir.toUri().isCifsDocument()
        dir.parent?.runCatching {
            withCifsThrottle(isCifs) {
                DocumentsContract.createDocument(contentResolver, toUri(), Document.MIME_TYPE_DIR, dir.name)
            }
        }?.getOrNull() ?: throw IOException("Failed to create directory: $dir")
    }

    override fun createSymlink(source: Path, target: Path) {
        TODO("Not yet implemented")
    }

    override fun delete(path: Path, mustExist: Boolean) {
        if (path.isPhysicalFile()) {
            return physicalFileSystem.delete(path, mustExist)
        }

        val metadata = metadataOrNull(path)

        if (metadata != null) {
            var uri = path.toUri()
            val isCifs = uri.isCifsDocument()
            if (isCifs && metadata.isDirectory) {
                uri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getDocumentId(uri) + '/')
            }

            val deleted = runCatching {
                withCifsThrottle(isCifs) {
                    DocumentsContract.deleteDocument(contentResolver, uri)
                }
            }.getOrDefault(false)
            if (!deleted) {
                throw IOException("Failed to delete $path")
            }
        } else if (mustExist) {
            throw FileNotFoundException("$path does not exist")
        }
    }

    override fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) {
        if (fileOrDirectory.isPhysicalFile()) {
            if (metadataOrNull(fileOrDirectory)?.isDirectory == true) {
                physicalFileSystem.deleteRecursively(fileOrDirectory, mustExist)
            } else {
                physicalFileSystem.delete(fileOrDirectory, mustExist)
            }
        } else {
            delete(fileOrDirectory, mustExist)
        }
    }

    override fun list(dir: Path): List<Path> = list(dir, throwOnFailure = true)!!

    override fun listOrNull(dir: Path): List<Path>? = list(dir, throwOnFailure = false)

    private fun list(dir: Path, throwOnFailure: Boolean): List<Path>? {
        if (dir.isPhysicalFile()) {
            return if (throwOnFailure) {
                physicalFileSystem.list(dir)
            } else {
                physicalFileSystem.listOrNull(dir)
            }
        }

        val uri = dir.toUri()
        val isCifs = uri.isCifsDocument()
        return runCatching {
            var documentId = DocumentsContract.getDocumentId(uri)
            if (isCifs) {
                documentId += '/'
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)

            withCifsThrottle(isCifs) {
                contentResolver.query(childrenUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                    List(c.count) {
                        c.moveToNext()
                        val displayName = c.getString(0)
                        dir / displayName
                    }
                }
            }
        }.getOrElse { if (throwOnFailure) throw FileNotFoundException("Failed to list $dir") else null }
    }

    override fun metadataOrNull(path: Path): FileMetadata? {
        if (path.isPhysicalFile()) {
            return physicalFileSystem.metadataOrNull(path)
        }

        val uri = path.toUri()
        val isCifs = uri.isCifsDocument()
        return runCatching {
            val isMediaUri = uri.authority == MediaStore.AUTHORITY
            val projection = if (isMediaUri) {
                arrayOf(MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_MODIFIED)
            } else {
                arrayOf(Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED)
            }

            withCifsThrottle(isCifs) {
                contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    if (!c.moveToNext()) return@withCifsThrottle null

                    val mimeType = c.getString(0)
                    val lastModified = c.getLongOrNull(1)?.let { if (isMediaUri) it * 1000 else it }
                    val isDirectory = mimeType == Document.MIME_TYPE_DIR

                    FileMetadata(
                        isRegularFile = !isDirectory,
                        isDirectory = isDirectory,
                        lastModifiedAtMillis = lastModified,
                    )
                }
            }
        }.getOrNull()
    }

    override fun openReadOnly(file: Path): FileHandle {
        TODO("Not yet implemented")
    }

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
        TODO("Not yet implemented")
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        TODO("Not yet implemented")
    }

    override fun source(file: Path): Source {
        TODO("Not yet implemented")
    }

    fun rawSink(file: Path) = file.outputStream().asSink()

    fun rawSource(file: Path) = file.inputStream().asSource()

    fun openFileDescriptor(path: Path, mode: String): ParcelFileDescriptor {
        if (path.isPhysicalFile()) {
            return ParcelFileDescriptor.open(path.toFile(), ParcelFileDescriptor.parseMode(mode))
        }

        val uri = path.toUri()
        val isCifs = uri.isCifsDocument()
        // Separate exists-check and create from the open call to avoid
        // nested cifsSemaphore acquisition which can deadlock.
        if (isCifs && 'w' in mode && !exists(path)) {
            val parent = path.parent ?: throw FileNotFoundException("Failed to open file: $path")
            val displayName = path.name
            val extension = displayName.substringAfterLast('.', "").ifEmpty { null }?.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
            withCifsThrottle(true) {
                DocumentsContract.createDocument(contentResolver, parent.toUri(), mimeType, displayName)
            }
        }
        return runCatching {
            withCifsThrottle(isCifs) {
                if (!isCifs && 'w' in mode && !exists(path)) {
                    val parent = path.parent ?: return@withCifsThrottle null
                    val displayName = path.name
                    val extension = displayName.substringAfterLast('.', "").ifEmpty { null }?.lowercase()
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
                    DocumentsContract.createDocument(contentResolver, parent.toUri(), mimeType, displayName)
                }
                contentResolver.openFileDescriptor(uri, mode)
            }
        }.getOrNull() ?: throw FileNotFoundException("Failed to open file: $path")
    }

    private fun Path.inputStream() = ParcelFileDescriptor.AutoCloseInputStream(openFileDescriptor(this, "r"))

    private fun Path.outputStream() = ParcelFileDescriptor.AutoCloseOutputStream(openFileDescriptor(this, "wt"))

    private fun openRawCifsReadFileDescriptor(path: Path): ParcelFileDescriptor {
        val uri = path.toUri()
        require(uri.isCifsDocument()) { "Path must be a CIFS document: $path" }
        return contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("Failed to open file: $path")
    }
}

private fun Path.isPhysicalFile() = toString().startsWith('/')

private fun Uri.isCifsDocument() = authority == CIFS_DOCUMENT_AUTHORITY

private const val CIFS_OP_TIMEOUT_MS = 30_000L
private const val CIFS_READ_COPY_BUFFER_SIZE = 1024 * 1024

private fun acquireCifsPermit() {
    if (!cifsSemaphore.tryAcquire(CIFS_OP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
        throw IOException("CIFS semaphore acquisition timed out")
    }
}

private inline fun <T> withCifsReadLease(block: () -> T): T {
    val stale = System.currentTimeMillis() - cifsLastSuccess.get() > CIFS_STALE_THRESHOLD_MS
    val maxAttempts = if (stale) 3 else 2
    var lastFailure: Throwable? = null

    repeat(maxAttempts) { attempt ->
        acquireCifsPermit()
        try {
            val result = block()
            cifsLastSuccess.set(System.currentTimeMillis())
            return result
        } catch (e: Throwable) {
            lastFailure = e
        } finally {
            cifsSemaphore.release()
        }

        if (attempt + 1 < maxAttempts) {
            Thread.sleep(if (stale) 500L * (attempt + 1) else 500L)
        }
    }

    throw lastFailure ?: IOException("CIFS read lease failed without an exception")
}

private inline fun <T> withCifsThrottle(isCifs: Boolean, block: () -> T): T {
    if (!isCifs) return block()

    val stale = System.currentTimeMillis() - cifsLastSuccess.get() > CIFS_STALE_THRESHOLD_MS
    if (stale) {
        // Connection may be stale after idle.  Serialize recovery so that
        // only one thread probes the provider at a time, preventing a
        // burst of reconnection attempts from crashing it.
        cifsRecoveryLock.acquire()
        try {
            // Another thread may have recovered while we waited for the lock.
            if (System.currentTimeMillis() - cifsLastSuccess.get() > CIFS_STALE_THRESHOLD_MS) {
                var lastEx: Throwable? = null
                repeat(3) { attempt ->
                    acquireCifsPermit()
                    try {
                        val result = block()
                        cifsLastSuccess.set(System.currentTimeMillis())
                        return result
                    } catch (e: Throwable) {
                        lastEx = e
                    } finally {
                        cifsSemaphore.release()
                    }
                    // Give the provider time to re-establish the SMB session.
                    Thread.sleep(500L * (attempt + 1))
                }
                throw lastEx!!
            }
        } finally {
            cifsRecoveryLock.release()
        }
    }

    // Normal path – connection is fresh.
    acquireCifsPermit()
    lateinit var firstFailure: Throwable
    try {
        val result = block()
        cifsLastSuccess.set(System.currentTimeMillis())
        return result
    } catch (e: Throwable) {
        firstFailure = e
    } finally {
        cifsSemaphore.release()
    }

    // Single retry on failure for the normal (non-stale) path.
    Thread.sleep(500L)
    acquireCifsPermit()
    try {
        val result = block()
        cifsLastSuccess.set(System.currentTimeMillis())
        return result
    } catch (_: Throwable) {
        throw firstFailure
    } finally {
        cifsSemaphore.release()
    }
}

package com.ehviewer.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.ehviewer.core.model.GalleryInfo
import kotlin.time.Clock

@Entity(
    tableName = "DOWNLOADS",
    foreignKeys = [
        ForeignKey(GalleryEntity::class, ["GID"], ["GID"]),
        ForeignKey(
            DownloadLabel::class,
            ["LABEL"],
            ["LABEL"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class DownloadEntity(
    @PrimaryKey
    @ColumnInfo(name = "GID")
    var gid: Long = 0,

    @ColumnInfo(name = "TIME")
    var time: Long = Clock.System.now().toEpochMilliseconds(),

    @ColumnInfo(name = "LABEL", index = true)
    var label: String? = null,
)

data class DownloadInfo(
    @Relation(parentColumn = "GID", entityColumn = "GID")
    val galleryInfo: GalleryEntity,

    @ColumnInfo(name = "DIRNAME")
    val dirname: String?,

    @Relation(parentColumn = "GID", entityColumn = "GID")
    var artistInfoList: List<DownloadArtist> = emptyList(),

    @Embedded
    val downloadInfo: DownloadEntity = DownloadEntity(galleryInfo.gid),
) : GalleryInfo by galleryInfo {

    var time: Long
        get() = downloadInfo.time
        set(value) {
            downloadInfo.time = value
        }

    var label: String?
        get() = downloadInfo.label
        set(value) {
            downloadInfo.label = value
        }
}

val DownloadInfo.artists: List<String>
    get() = artistInfoList.map { it.artist }

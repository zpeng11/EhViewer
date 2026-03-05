package com.ehviewer.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import kotlin.time.Clock

@Entity(tableName = "LOCAL_FAVORITES", foreignKeys = [ForeignKey(GalleryEntity::class, ["GID"], ["GID"])])
class LocalFavoriteInfo(
    gid: Long = 0,
    time: Long = Clock.System.now().toEpochMilliseconds(),
    @ColumnInfo(name = "EXTRA_FAVORITE_SLOT", index = true)
    var extraFavoriteSlot: Int? = null,
) : TimeInfo(gid, time) {
    init {
        check(extraFavoriteSlot == null || extraFavoriteSlot in LocalFavoriteFolder.VALID_SLOT_RANGE) {
            "extraFavoriteSlot should be in ${LocalFavoriteFolder.VALID_SLOT_RANGE} when not null"
        }
    }
}

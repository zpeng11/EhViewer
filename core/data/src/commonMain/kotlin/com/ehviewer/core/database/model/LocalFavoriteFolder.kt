package com.ehviewer.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "LOCAL_FAVORITE_FOLDERS")
data class LocalFavoriteFolder(
    @PrimaryKey
    @ColumnInfo(name = "SLOT")
    val slot: Int,
    @ColumnInfo(name = "NAME")
    val name: String,
) {
    init {
        check(slot in VALID_SLOT_RANGE) {
            "slot should be in $VALID_SLOT_RANGE"
        }
    }

    companion object {
        val VALID_SLOT_RANGE = 1..9
    }
}

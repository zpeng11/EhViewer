package com.ehviewer.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.ehviewer.core.database.model.LocalFavoriteFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalFavoriteFolderDao {
    @Query("SELECT * FROM LOCAL_FAVORITE_FOLDERS ORDER BY SLOT ASC")
    fun listFlow(): Flow<List<LocalFavoriteFolder>>

    @Query("SELECT * FROM LOCAL_FAVORITE_FOLDERS ORDER BY SLOT ASC")
    suspend fun list(): List<LocalFavoriteFolder>

    @Query("SELECT * FROM LOCAL_FAVORITE_FOLDERS WHERE SLOT = :slot")
    suspend fun load(slot: Int): LocalFavoriteFolder?

    @Query("SELECT COUNT(*) FROM LOCAL_FAVORITE_FOLDERS")
    fun count(): Flow<Int>

    @Upsert
    suspend fun upsert(folder: LocalFavoriteFolder)

    @Delete
    suspend fun delete(folder: LocalFavoriteFolder)

    @Query("DELETE FROM LOCAL_FAVORITE_FOLDERS WHERE SLOT = :slot")
    suspend fun deleteBySlot(slot: Int)
}

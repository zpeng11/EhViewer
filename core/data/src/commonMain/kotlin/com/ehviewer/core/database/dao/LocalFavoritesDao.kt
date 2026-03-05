package com.ehviewer.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ehviewer.core.database.model.GalleryEntity
import com.ehviewer.core.database.model.LocalFavoriteInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalFavoritesDao {
    @Query("SELECT COUNT(*) FROM LOCAL_FAVORITES")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM LOCAL_FAVORITES WHERE EXTRA_FAVORITE_SLOT = :slot")
    fun countInExtraSlot(slot: Int): Flow<Int>

    @Query("SELECT LOCAL_FAVORITES.* FROM LOCAL_FAVORITES JOIN GALLERIES USING(GID) ORDER BY TIME")
    suspend fun list(): List<LocalFavoriteInfo>

    @Query("SELECT GALLERIES.* FROM LOCAL_FAVORITES JOIN GALLERIES USING(GID) ORDER BY TIME DESC")
    fun joinListLazy(): PagingSource<Int, GalleryEntity>

    @Query(
        """SELECT GALLERIES.* FROM LOCAL_FAVORITES JOIN GALLERIES USING(GID)
        JOIN GALLERIES_FTS ON GALLERIES.rowid = docid WHERE GALLERIES_FTS MATCH :title ORDER BY TIME DESC""",
    )
    fun joinListLazy(title: String): PagingSource<Int, GalleryEntity>

    @Query("SELECT GALLERIES.* FROM LOCAL_FAVORITES JOIN GALLERIES USING(GID) WHERE EXTRA_FAVORITE_SLOT = :slot ORDER BY TIME DESC")
    fun joinListByExtraSlotLazy(slot: Int): PagingSource<Int, GalleryEntity>

    @Query(
        """SELECT GALLERIES.* FROM LOCAL_FAVORITES JOIN GALLERIES USING(GID)
        JOIN GALLERIES_FTS ON GALLERIES.rowid = docid
        WHERE EXTRA_FAVORITE_SLOT = :slot AND GALLERIES_FTS MATCH :title ORDER BY TIME DESC""",
    )
    fun joinListByExtraSlotLazy(slot: Int, title: String): PagingSource<Int, GalleryEntity>

    @Query("SELECT GALLERIES.* FROM LOCAL_FAVORITES JOIN GALLERIES USING(GID) ORDER BY RANDOM() LIMIT 1")
    suspend fun random(): GalleryEntity?

    @Query("SELECT EXISTS(SELECT * FROM LOCAL_FAVORITES WHERE GID = :gid)")
    suspend fun contains(gid: Long): Boolean

    @Query("SELECT GID FROM LOCAL_FAVORITES WHERE GID IN (:gids)")
    suspend fun listExistingGids(gids: LongArray): List<Long>

    @Query("SELECT GID FROM LOCAL_FAVORITES WHERE EXTRA_FAVORITE_SLOT = :slot")
    suspend fun listGidsInExtraSlot(slot: Int): List<Long>

    @Query("SELECT EXTRA_FAVORITE_SLOT FROM LOCAL_FAVORITES WHERE GID = :gid")
    suspend fun getExtraSlot(gid: Long): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(localFavorites: List<LocalFavoriteInfo>)

    @Upsert
    suspend fun upsert(t: LocalFavoriteInfo)

    @Query("UPDATE LOCAL_FAVORITES SET EXTRA_FAVORITE_SLOT = :slot WHERE GID = :gid")
    suspend fun updateExtraSlot(gid: Long, slot: Int?)

    @Query("UPDATE LOCAL_FAVORITES SET EXTRA_FAVORITE_SLOT = :slot WHERE GID IN (:gids)")
    suspend fun updateExtraSlot(gids: LongArray, slot: Int?)

    @Query("UPDATE LOCAL_FAVORITES SET EXTRA_FAVORITE_SLOT = NULL WHERE EXTRA_FAVORITE_SLOT = :slot")
    suspend fun clearExtraSlot(slot: Int)

    @Query("DELETE FROM LOCAL_FAVORITES WHERE GID = :gid")
    suspend fun deleteByKey(gid: Long)
}

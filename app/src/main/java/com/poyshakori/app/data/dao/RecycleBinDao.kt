package com.poyshakori.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poyshakori.app.data.entity.RecycleBinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleBinDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: RecycleBinEntity): Long

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    fun getAllDeletedItems(): Flow<List<RecycleBinEntity>>

    @Query("SELECT * FROM recycle_bin WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): RecycleBinEntity?

    @Query("SELECT COUNT(*) FROM recycle_bin")
    fun getDeletedItemCount(): Flow<Int>

    @Delete
    suspend fun deleteItem(item: RecycleBinEntity)

    @Query("DELETE FROM recycle_bin WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM recycle_bin")
    suspend fun emptyRecycleBin()
}

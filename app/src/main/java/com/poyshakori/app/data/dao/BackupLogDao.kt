package com.poyshakori.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poyshakori.app.data.entity.BackupLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BackupLogEntity): Long

    @Query("SELECT * FROM backup_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<BackupLogEntity>>

    @Query("SELECT * FROM backup_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 10): Flow<List<BackupLogEntity>>
}

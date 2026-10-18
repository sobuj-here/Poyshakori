package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_logs")
data class BackupLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val filename: String,
    val uriString: String? = null,
    val backupType: String,            // EXPORT, RESTORE, INTEGRITY_CHECK
    val timestamp: Long = System.currentTimeMillis(),
    val status: String,                // SUCCESS, FAILED
    val checksum: String? = null,
    val notes: String? = null
)

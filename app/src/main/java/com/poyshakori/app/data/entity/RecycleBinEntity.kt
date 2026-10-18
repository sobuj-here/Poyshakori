package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val itemType: String,              // "TRANSACTION", "DEBT", "BUDGET"
    val originalId: Long,              // Original primary key ID
    val title: String,                 // Display title (e.g., "Salary", "Loan to Rahim")
    val amount: Long = 0L,             // Associated amount in BDT
    val details: String? = null,       // Human-readable summary
    val payloadJson: String,           // Full serialized JSON for accurate restoration
    val deletedAt: Long = System.currentTimeMillis()
)

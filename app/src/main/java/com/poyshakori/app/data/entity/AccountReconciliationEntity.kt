package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "account_reconciliations",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["reconciledDate"])
    ]
)
data class AccountReconciliationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val accountId: Long,
    val systemBalance: Long,
    val actualBalance: Long,
    val discrepancy: Long,
    val reconciledDate: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

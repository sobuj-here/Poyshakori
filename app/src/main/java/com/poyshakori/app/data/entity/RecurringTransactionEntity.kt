package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_transactions",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["nextDueDate"])
    ]
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val type: String,                  // INCOME, EXPENSE, TRANSFER
    val amount: Long,
    val accountId: Long,
    val targetAccountId: Long? = null,
    val category: String,
    val frequency: String,             // DAILY, WEEKLY, MONTHLY, YEARLY
    val startDate: String,             // YYYY-MM-DD
    val nextDueDate: String,           // YYYY-MM-DD
    val autoRecord: Boolean = false,
    val lastRecordedDate: String? = null,
    val note: String? = null,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

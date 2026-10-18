package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "savings_contributions",
    indices = [
        Index(value = ["goalId"]),
        Index(value = ["accountId"])
    ]
)
data class SavingsContributionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val goalId: Long,
    val amount: Long,
    val accountId: Long,
    val date: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

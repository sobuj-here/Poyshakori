package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "debt_repayments",
    indices = [
        Index(value = ["debtId"]),
        Index(value = ["accountId"])
    ]
)
data class DebtRepaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val debtId: Long,
    val amount: Long,
    val accountId: Long,
    val date: String,
    val time: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

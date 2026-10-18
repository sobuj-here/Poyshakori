package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String,                   // "DEBT" (I owe) or "RECEIVABLE" (Owed to me)
    val personName: String,             // Name of the person / party
    val amount: Long,                   // Total amount
    val paidAmount: Long = 0L,          // Repaid amount so far
    val dueDate: String,                // Due date (e.g. "2026-11-01")
    val status: String = "PENDING",     // "PENDING", "PARTIALLY_PAID", "PAID"
    val note: String? = null,
    val accountId: Long? = null,        // Linked account ID (disbursement or deposit)
    val createdAt: Long = System.currentTimeMillis(),
    val date: String = "",              // Origination / Lending / Borrowing date (YYYY-MM-DD)
    val reference: String? = null       // Optional payment reference / transaction identifier
)
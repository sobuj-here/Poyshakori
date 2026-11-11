package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["date"]),
        Index(value = ["accountId"]),
        Index(value = ["category"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String,                   // INCOME, EXPENSE, TRANSFER, LOAN_LEND, LOAN_BORROW, DEBT_REPAYMENT, RECEIVABLE_REPAYMENT
    val amount: Long,                   // Exact amount in integer units (BDT)
    val accountId: Long,                // Linked source account ID
    val targetAccountId: Long? = null,  // Destination account ID for transfers
    val category: String,               // Category name
    val paymentMethod: String = "Cash", // Cash, Card, Mobile Banking, Bank
    val date: String,                   // Format: YYYY-MM-DD
    val time: String,                   // Format: HH:mm:ss
    val note: String? = null,           // Optional description / note
    val reference: String? = null,      // Optional reference / receipt #
    val serviceCharge: Double = 0.0,    // Service fee / charge (supports float, e.g. 18.50 BDT)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

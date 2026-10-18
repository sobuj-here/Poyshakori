package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val category: String,              // e.g., Food, Transport, Shopping
    val monthlyLimit: Long,            // Budget limit amount
    val monthYear: String,             // Format: "YYYY-MM" (e.g., "2026-10")
    val rollover: Boolean = false,     // Carry forward unused allocation
    val carriedOverAmount: Long = 0L   // Rolled over balance from previous month
)
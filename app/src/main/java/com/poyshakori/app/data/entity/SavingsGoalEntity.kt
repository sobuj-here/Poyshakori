package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,                  // Goal name, e.g. "Emergency Fund", "New Laptop"
    val targetAmount: Long,             // Target savings amount in BDT
    val savedAmount: Long = 0L,         // Currently saved amount
    val targetDate: String,             // Format: "YYYY-MM-DD"
    val note: String? = null,
    val status: String = "IN_PROGRESS", // "IN_PROGRESS", "COMPLETED"
    val createdAt: Long = System.currentTimeMillis()
)

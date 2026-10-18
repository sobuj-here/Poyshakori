package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name", "type"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,                   // e.g., Food, Salary, Rent
    val type: String,                   // "INCOME" or "EXPENSE"
    val icon: String? = null,
    val color: String? = null
)
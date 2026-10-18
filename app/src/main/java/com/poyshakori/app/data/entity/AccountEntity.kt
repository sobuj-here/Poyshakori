package com.poyshakori.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,               // e.g., Cash, Bank, bKash
    val type: String = "General",   // e.g., Cash, Bank, Mobile Wallet
    val openingBalance: Long = 0L,
    val currentBalance: Long = 0L,   // সঠিক কলাম নাম currentBalance ব্যবহার করা হলো
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val active: Boolean = true
)
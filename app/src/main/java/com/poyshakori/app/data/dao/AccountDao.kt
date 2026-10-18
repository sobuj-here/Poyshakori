package com.poyshakori.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.poyshakori.app.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("UPDATE accounts SET active = 0, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun softDeleteAccount(accountId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM accounts WHERE active = 1 ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccountsIncludingInactive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun getAccountByName(name: String): AccountEntity?

    @Query("SELECT SUM(currentBalance) FROM accounts WHERE active = 1")
    fun getTotalBalance(): Flow<Long?>

    @Query("UPDATE accounts SET currentBalance = currentBalance + :amount, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun updateBalance(accountId: Long, amount: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE accounts SET currentBalance = :balance, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun setAccountBalance(accountId: Long, balance: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()
}
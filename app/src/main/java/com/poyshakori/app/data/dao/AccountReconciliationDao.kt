package com.poyshakori.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poyshakori.app.data.entity.AccountReconciliationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountReconciliationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconciliation(reconciliation: AccountReconciliationEntity): Long

    @Query("SELECT * FROM account_reconciliations WHERE accountId = :accountId ORDER BY reconciledDate DESC, id DESC")
    fun getReconciliationsForAccount(accountId: Long): Flow<List<AccountReconciliationEntity>>

    @Query("SELECT * FROM account_reconciliations ORDER BY reconciledDate DESC, id DESC")
    fun getAllReconciliations(): Flow<List<AccountReconciliationEntity>>

    @Delete
    suspend fun deleteReconciliation(reconciliation: AccountReconciliationEntity)
}

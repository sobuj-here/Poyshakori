package com.poyshakori.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.poyshakori.app.data.entity.DebtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity): Long

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Delete
    suspend fun deleteDebt(debt: DebtEntity)

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getDebtById(id: Long): DebtEntity?

    @Query("SELECT * FROM debts ORDER BY dueDate ASC, id DESC")
    fun getAllDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE type = :type ORDER BY dueDate ASC, id DESC")
    fun getDebtsByType(type: String): Flow<List<DebtEntity>>

    @Query("SELECT SUM(amount - paidAmount) FROM debts WHERE type = 'DEBT' AND status != 'PAID'")
    fun getTotalPendingDebts(): Flow<Long?>

    @Query("SELECT SUM(amount - paidAmount) FROM debts WHERE type = 'RECEIVABLE' AND status != 'PAID'")
    fun getTotalPendingReceivables(): Flow<Long?>

    @Query("UPDATE debts SET paidAmount = amount, status = 'PAID' WHERE id = :id")
    suspend fun markAsPaid(id: Long)

    @Query("UPDATE debts SET paidAmount = paidAmount + :repaidAmount, status = CASE WHEN paidAmount + :repaidAmount >= amount THEN 'PAID' ELSE 'PARTIALLY_PAID' END WHERE id = :id")
    suspend fun recordRepayment(id: Long, repaidAmount: Long)

    @Query("UPDATE debts SET paidAmount = :newPaidAmount, status = :status WHERE id = :id")
    suspend fun updateDebtRepaymentStatus(id: Long, newPaidAmount: Long, status: String)

    @Query("SELECT COUNT(*) FROM debts WHERE accountId = :accountId")
    suspend fun getDebtsCountForAccount(accountId: Long): Int

    @Query("SELECT SUM(amount) FROM debts WHERE type = 'RECEIVABLE'")
    fun getTotalLent(): Flow<Long?>

    @Query("SELECT SUM(amount) FROM debts WHERE type = 'DEBT'")
    fun getTotalBorrowed(): Flow<Long?>

    @Query("SELECT SUM(paidAmount) FROM debts WHERE type = 'RECEIVABLE'")
    fun getTotalLentRepaid(): Flow<Long?>

    @Query("SELECT SUM(paidAmount) FROM debts WHERE type = 'DEBT'")
    fun getTotalBorrowedRepaid(): Flow<Long?>
}
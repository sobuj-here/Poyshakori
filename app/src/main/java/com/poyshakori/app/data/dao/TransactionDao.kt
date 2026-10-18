package com.poyshakori.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.poyshakori.app.data.entity.CategorySpending
import com.poyshakori.app.data.entity.MonthlySummary
import com.poyshakori.app.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC, time DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, time DESC, id DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Long?>

    // TRANSFER is an internal account rebalance, NOT an expense
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalExpense(): Flow<Long?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND date LIKE :monthYear || '%'")
    fun getMonthlyIncome(monthYear: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date LIKE :monthYear || '%'")
    fun getMonthlyExpense(monthYear: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND date BETWEEN :startDate AND :endDate")
    fun getTotalIncomeByDateRange(startDate: String, endDate: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :startDate AND :endDate")
    fun getTotalExpenseByDateRange(startDate: String, endDate: String): Flow<Long?>

    @Query("SELECT category, SUM(amount) as totalAmount FROM transactions WHERE type = 'EXPENSE' AND date LIKE :monthYear || '%' GROUP BY category ORDER BY totalAmount DESC")
    fun getCategorySpendingForMonth(monthYear: String): Flow<List<CategorySpending>>

    @Query("SELECT category, SUM(amount) as totalAmount FROM transactions WHERE type = 'EXPENSE' GROUP BY category ORDER BY totalAmount DESC")
    fun getAllTimeCategorySpending(): Flow<List<CategorySpending>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId OR targetAccountId = :accountId ORDER BY date DESC, time DESC")
    fun getTransactionsByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE category LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' OR reference LIKE '%' || :query || '%' ORDER BY date DESC, time DESC")
    fun searchTransactions(query: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC, time DESC")
    fun getTransactionsByType(type: String): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR targetAccountId = :accountId")
    suspend fun getTransactionCountForAccount(accountId: Long): Int

    @Query("SELECT SUBSTR(date, 1, 7) as monthYear, SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as income, SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as expense FROM transactions GROUP BY SUBSTR(date, 1, 7) ORDER BY monthYear DESC")
    fun getMonthlySummaries(): Flow<List<MonthlySummary>>

    @Query("SELECT * FROM transactions WHERE amount = :amount AND accountId = :accountId AND date = :date AND category = :category LIMIT 1")
    suspend fun findPotentialDuplicate(amount: Long, accountId: Long, date: String, category: String): TransactionEntity?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date LIKE :monthYear || '%'")
    suspend fun getMonthlyExpenseAmount(monthYear: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("SELECT * FROM transactions WHERE reference LIKE 'DEBT_DISBURSEMENT:' || :debtId || '%' OR reference LIKE 'DEBT_REPAYMENT:' || :debtId || '%'")
    suspend fun getTransactionsForDebt(debtId: Long): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE reference LIKE 'DEBT_DISBURSEMENT:' || :debtId || '%' OR reference LIKE 'DEBT_REPAYMENT:' || :debtId || '%'")
    suspend fun deleteTransactionsForDebt(debtId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
package com.poyshakori.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poyshakori.app.data.entity.DebtRepaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtRepaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayment(repayment: DebtRepaymentEntity): Long

    @Query("SELECT * FROM debt_repayments WHERE debtId = :debtId ORDER BY date DESC, time DESC")
    fun getRepaymentsForDebt(debtId: Long): Flow<List<DebtRepaymentEntity>>

    @Query("SELECT * FROM debt_repayments ORDER BY date DESC, time DESC")
    fun getAllRepayments(): Flow<List<DebtRepaymentEntity>>

    @Query("SELECT SUM(amount) FROM debt_repayments WHERE debtId = :debtId")
    suspend fun getTotalRepaidForDebt(debtId: Long): Long?

    @Delete
    suspend fun deleteRepayment(repayment: DebtRepaymentEntity)

    @Query("DELETE FROM debt_repayments WHERE debtId = :debtId")
    suspend fun deleteRepaymentsForDebt(debtId: Long)
}

package com.poyshakori.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.poyshakori.app.data.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: SavingsGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: SavingsGoalEntity)

    @Delete
    suspend fun deleteGoal(goal: SavingsGoalEntity)

    @Query("SELECT * FROM savings_goals ORDER BY id DESC")
    fun getAllGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: Long): SavingsGoalEntity?

    @Query("SELECT SUM(savedAmount) FROM savings_goals")
    fun getTotalSaved(): Flow<Long?>

    @Query("UPDATE savings_goals SET savedAmount = savedAmount + :contribution, status = CASE WHEN savedAmount + :contribution >= targetAmount THEN 'COMPLETED' ELSE 'IN_PROGRESS' END WHERE id = :id")
    suspend fun addContribution(id: Long, contribution: Long)
}

package com.poyshakori.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poyshakori.app.data.entity.SavingsContributionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsContributionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(contribution: SavingsContributionEntity): Long

    @Query("SELECT * FROM savings_contributions WHERE goalId = :goalId ORDER BY date DESC")
    fun getContributionsForGoal(goalId: Long): Flow<List<SavingsContributionEntity>>

    @Query("SELECT * FROM savings_contributions ORDER BY date DESC")
    fun getAllContributions(): Flow<List<SavingsContributionEntity>>

    @Delete
    suspend fun deleteContribution(contribution: SavingsContributionEntity)
}

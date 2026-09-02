package com.el.sapiospend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContributionDao {

    @Insert
    suspend fun insert(contribution: ContributionEntity)

    @Update
    suspend fun update(contribution: ContributionEntity)

    // Newest first, matching the expense list next to it on screen.
    @Query("SELECT * FROM contributions WHERE deletedAt IS NULL ORDER BY dateCreated DESC")
    fun getAllContributions(): Flow<List<ContributionEntity>>

    @Query("SELECT * FROM contributions WHERE eventId = :eventId AND deletedAt IS NULL ORDER BY dateCreated DESC")
    fun getContributionsForEvent(eventId: String): Flow<List<ContributionEntity>>

    @Query("UPDATE contributions SET deletedAt = :now, updatedAt = :now WHERE id = :contributionId")
    suspend fun markContributionDeleted(contributionId: String, now: Long)
}

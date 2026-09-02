package com.el.sapiospend.fake

import com.el.sapiospend.data.local.ContributionDao
import com.el.sapiospend.data.local.ContributionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** In-memory ContributionDao used only in unit tests. */
class FakeContributionDao(private val db: FakeDatabase) : ContributionDao {

    override suspend fun insert(contribution: ContributionEntity) {
        db.contributions.value = db.contributions.value + contribution
    }

    override suspend fun update(contribution: ContributionEntity) {
        db.contributions.value = db.contributions.value.map { if (it.id == contribution.id) contribution else it }
    }

    override fun getAllContributions(): Flow<List<ContributionEntity>> =
        db.contributions.map { list -> list.filter { it.deletedAt == null }.sortedByDescending { it.dateCreated } }

    override fun getContributionsForEvent(eventId: String): Flow<List<ContributionEntity>> =
        db.contributions.map { list ->
            list.filter { it.eventId == eventId && it.deletedAt == null }.sortedByDescending { it.dateCreated }
        }

    override suspend fun markContributionDeleted(contributionId: String, now: Long) {
        db.contributions.value = db.contributions.value.map {
            if (it.id == contributionId) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }
}

package com.el.sapiospend

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.el.sapiospend.data.local.AppDatabase
import com.el.sapiospend.data.local.MIGRATION_6_7
import com.el.sapiospend.domain.budget.BudgetDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 6 -> 7 adds the budget's direction.
 *
 * One column, but the default is the whole point: every budget on an installed phone was
 * created when spending was the only thing a budget could do, and a NULL or an empty
 * string here would come back as an unknown direction that the UI would have to guess at.
 */
@RunWith(AndroidJUnit4::class)
class Migration6To7Test {

    private val dbName = "migration-6-7-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate6To7_defaultsExistingBudgetsToSpending() {
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL(
                "INSERT INTO events (id, name, budget, eventType, guestCount, dateCreated, startDate, endDate, ownerId, updatedAt, deletedAt) " +
                    "VALUES ('e1', 'Tolu & Ada', 7000000.0, 'Wedding', 120, 111, NULL, NULL, 'local', 111, NULL)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        migrated.query("SELECT moneyDirection, name FROM events WHERE id = 'e1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(BudgetDirection.SPENDING.name, cursor.getString(0))
            assertEquals("Tolu & Ada", cursor.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migrate6To7_acceptsASavingsGoalAfterMigrating() {
        helper.createDatabase(dbName, 6).close()

        val migrated = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)
        migrated.execSQL(
            "INSERT INTO events (id, name, budget, eventType, moneyDirection, guestCount, dateCreated, startDate, endDate, ownerId, updatedAt, deletedAt) " +
                "VALUES ('e2', 'New laptop', 900000.0, 'Personal', '${BudgetDirection.SAVING.name}', NULL, 1, NULL, NULL, 'local', 1, NULL)"
        )

        migrated.query("SELECT moneyDirection FROM events WHERE id = 'e2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(BudgetDirection.SAVING.name, cursor.getString(0))
        }
        migrated.close()
    }
}

package com.el.sapiospend

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.el.sapiospend.data.local.AppDatabase
import com.el.sapiospend.data.local.MIGRATION_4_5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4 -> 5 adds the budget period columns.
 *
 * Unlike the 3 -> 4 tests next door, this one goes through [MigrationTestHelper]: schema
 * export started at version 4, so Room can build the old database itself and — the part
 * that matters — validate that the migrated result matches 5.json exactly. A migration
 * whose SQL drifts from the entity fails here rather than on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    private val dbName = "migration-4-5-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate4To5_addsPeriodColumnsAndKeepsExistingEvents() {
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL(
                "INSERT INTO events (id, name, budget, eventType, dateCreated, ownerId, updatedAt, deletedAt) " +
                    "VALUES ('e1', 'Tolu & Ada', 7000000.0, 'Wedding', 111, 'local', 111, NULL)"
            )
            db.execSQL(
                "INSERT INTO expenses (id, eventId, title, category, amount, notes, dateCreated, ownerId, updatedAt, deletedAt) " +
                    "VALUES ('x1', 'e1', 'Catering', 'Food', 50000.0, '', 112, 'local', 112, NULL)"
            )
        }

        // validateDroppedTables = true, so a rebuild that forgot to clean up after itself
        // is caught too.
        val migrated = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        migrated.query("SELECT startDate, endDate, name FROM events WHERE id = 'e1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("startDate should be null on a pre-period event", cursor.isNull(0))
            assertTrue("endDate should be null on a pre-period event", cursor.isNull(1))
            assertEquals("Tolu & Ada", cursor.getString(2))
        }

        // The child rows must survive: the columns are added in place, but a botched
        // table rebuild here would take the expense history with it.
        migrated.query("SELECT COUNT(*) FROM expenses WHERE eventId = 'e1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrate4To5_acceptsPeriodDatesAfterMigrating() {
        helper.createDatabase(dbName, 4).close()

        val migrated = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)
        migrated.execSQL(
            "INSERT INTO events (id, name, budget, eventType, dateCreated, startDate, endDate, ownerId, updatedAt, deletedAt) " +
                "VALUES ('e2', 'August Salary', 300000.0, 'Personal', 1, 100, 200, 'local', 1, NULL)"
        )

        migrated.query("SELECT startDate, endDate FROM events WHERE id = 'e2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(100L, cursor.getLong(0))
            assertEquals(200L, cursor.getLong(1))
        }
        migrated.close()
    }
}

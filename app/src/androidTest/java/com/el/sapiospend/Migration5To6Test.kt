package com.el.sapiospend

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.el.sapiospend.data.local.AppDatabase
import com.el.sapiospend.data.local.MIGRATION_5_6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 5 -> 6 adds payment tracking, guest counts, contributions and recurring rules.
 *
 * The case that matters is the back-fill. Every expense recorded before this version was
 * money already spent, so an untouched amountPaid of zero would open the app on a screen
 * telling the user they owe their entire spending history — the kind of bug that gets
 * reported as "the app lost my money".
 *
 * Through [MigrationTestHelper] so the migrated result is validated against 6.json:
 * SQL that drifts from the entity fails here rather than on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class Migration5To6Test {

    private val dbName = "migration-5-6-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private fun seedVersion5() {
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL(
                "INSERT INTO events (id, name, budget, eventType, dateCreated, startDate, endDate, ownerId, updatedAt, deletedAt) " +
                    "VALUES ('e1', 'Tolu & Ada', 7000000.0, 'Wedding', 111, NULL, NULL, 'local', 111, NULL)"
            )
            db.execSQL(
                "INSERT INTO expenses (id, eventId, title, category, amount, notes, dateCreated, ownerId, updatedAt, deletedAt) " +
                    "VALUES ('x1', 'e1', 'Catering', 'Food', 250000.0, '', 112, 'local', 112, NULL)"
            )
        }
    }

    @Test
    fun migrate5To6_marksEveryExistingExpenseAsAlreadyPaid() {
        seedVersion5()

        val migrated = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        migrated.query("SELECT amount, amountPaid, vendor, dueDate, receiptPath FROM expenses WHERE id = 'x1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(250000.0, cursor.getDouble(0), 0.01)
            assertEquals("a pre-payment-tracking expense is settled by definition", 250000.0, cursor.getDouble(1), 0.01)
            assertEquals("", cursor.getString(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
        }
        migrated.close()
    }

    @Test
    fun migrate5To6_leavesGuestCountUnansweredRatherThanGuessing() {
        seedVersion5()

        val migrated = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        migrated.query("SELECT guestCount, name FROM events WHERE id = 'e1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("nobody counted the guests, so no number should be invented", cursor.isNull(0))
            assertEquals("Tolu & Ada", cursor.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migrate5To6_addsTheFundingAndRecurringTables() {
        seedVersion5()

        val migrated = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        migrated.execSQL(
            "INSERT INTO contributions (id, eventId, source, amount, receivedAt, notes, dateCreated, ownerId, updatedAt, deletedAt) " +
                "VALUES ('c1', 'e1', 'Client deposit', 500000.0, 200, '', 200, 'local', 200, NULL)"
        )
        migrated.execSQL(
            "INSERT INTO recurring_expenses (id, eventId, title, category, vendor, amount, frequency, nextDueDate, until, active, dateCreated, ownerId, updatedAt, deletedAt) " +
                "VALUES ('r1', 'e1', 'Venue hire', 'Venue', '', 50000.0, 'WEEKLY', 300, NULL, 1, 300, 'local', 300, NULL)"
        )

        migrated.query("SELECT amount, receivedAt FROM contributions WHERE id = 'c1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(500000.0, cursor.getDouble(0), 0.01)
            assertEquals(200L, cursor.getLong(1))
        }
        migrated.query("SELECT frequency, nextDueDate, active FROM recurring_expenses WHERE id = 'r1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("WEEKLY", cursor.getString(0))
            assertEquals(300L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
        }
        migrated.close()
    }

    @Test
    fun migrate5To6_cascadesFromAnEventToItsFunding() {
        seedVersion5()
        val migrated = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)
        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL(
            "INSERT INTO contributions (id, eventId, source, amount, receivedAt, notes, dateCreated, ownerId, updatedAt, deletedAt) " +
                "VALUES ('c1', 'e1', 'Client deposit', 500000.0, 200, '', 200, 'local', 200, NULL)"
        )

        // The app deletes softly, so this only proves the constraint is in place — a
        // contribution must not be able to outlive the event it funds.
        migrated.execSQL("DELETE FROM events WHERE id = 'e1'")

        migrated.query("SELECT COUNT(*) FROM contributions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }
}

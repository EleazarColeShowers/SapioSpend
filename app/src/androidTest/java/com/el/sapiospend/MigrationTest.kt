package com.el.sapiospend

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.el.sapiospend.data.local.AppDatabase
import com.el.sapiospend.data.local.LocalOwner
import com.el.sapiospend.data.local.MIGRATION_3_4
import com.el.sapiospend.data.local.MIGRATION_4_5
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 3 -> 4 migration rewrites both tables and regenerates every primary key, so it is
 * the single most dangerous piece of code in the app: getting it wrong loses a paying
 * planner's expense history. These tests build a real version-3 database, migrate it,
 * and check that nothing was dropped or mislinked.
 *
 * Built by hand rather than through MigrationTestHelper because schema export only
 * started at version 4 — there is no 3.json to generate the old database from.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test-db"
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var migrated: AppDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    /** Creates the schema exactly as version 3 shipped it, then lets [seed] add rows. */
    private fun createVersion3Database(seed: (SupportSQLiteDatabase) -> Unit) {
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `budget` REAL NOT NULL, `eventType` TEXT NOT NULL, " +
                        "`dateCreated` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`eventId` INTEGER NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`amount` REAL NOT NULL, `notes` TEXT NOT NULL, `dateCreated` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_eventId` ON `expenses` (`eventId`)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        helper.writableDatabase.use { seed(it) }
        helper.close()
    }

    // Every migration from 3 onwards, so these tests exercise the same upgrade path a
    // user on the oldest shipped version actually takes.
    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .build()
            .also { migrated = it }

    @Test
    fun migrate3To4_keepsEveryEvent() = runTest {
        createVersion3Database { db ->
            db.execSQL("INSERT INTO events (name, budget, eventType, dateCreated) VALUES ('Tolu & Ada', 7000000.0, 'Wedding', 111)")
            db.execSQL("INSERT INTO events (name, budget, eventType, dateCreated) VALUES ('Corporate Retreat', 500000.0, 'Corporate', 222)")
        }

        val events = openMigrated().eventDao().getAllEvents().first()

        assertEquals(2, events.size)
        val wedding = events.first { it.name == "Tolu & Ada" }
        assertEquals(7_000_000.0, wedding.budget, 0.01)
        assertEquals("Wedding", wedding.eventType)
        assertEquals(111L, wedding.dateCreated)
    }

    @Test
    fun migrate3To4_replacesIntegerIdsWithOpaqueStringIds() = runTest {
        createVersion3Database { db ->
            db.execSQL("INSERT INTO events (name, budget, eventType, dateCreated) VALUES ('Party', 1000.0, 'Birthday', 1)")
        }

        val event = openMigrated().eventDao().getAllEvents().first().single()

        // The old value was the integer 1; anything that short means the rewrite did not happen.
        assertTrue("Expected a generated id, got '${event.id}'", event.id.length >= 16)
        assertEquals(LocalOwner.ID, event.ownerId)
        assertNull(event.deletedAt)
    }

    @Test
    fun migrate3To4_keepsExpensesAttachedToTheRightEvent() = runTest {
        createVersion3Database { db ->
            db.execSQL("INSERT INTO events (name, budget, eventType, dateCreated) VALUES ('Wedding', 100.0, 'Wedding', 1)")
            db.execSQL("INSERT INTO events (name, budget, eventType, dateCreated) VALUES ('Birthday', 200.0, 'Birthday', 2)")
            // Event ids 1 and 2 respectively.
            db.execSQL("INSERT INTO expenses (eventId, title, category, amount, notes, dateCreated) VALUES (1, 'Catering', 'Food', 50.0, '', 10)")
            db.execSQL("INSERT INTO expenses (eventId, title, category, amount, notes, dateCreated) VALUES (1, 'Decor', 'Decoration', 25.0, 'deposit', 11)")
            db.execSQL("INSERT INTO expenses (eventId, title, category, amount, notes, dateCreated) VALUES (2, 'Cake', 'Food', 30.0, '', 12)")
        }

        val db = openMigrated()
        val events = db.eventDao().getAllEvents().first()
        val wedding = events.first { it.name == "Wedding" }
        val birthday = events.first { it.name == "Birthday" }

        val weddingExpenses = db.expenseDao().getExpensesForEvent(wedding.id).first()
        val birthdayExpenses = db.expenseDao().getExpensesForEvent(birthday.id).first()

        assertEquals(2, weddingExpenses.size)
        assertEquals(1, birthdayExpenses.size)
        assertEquals("Cake", birthdayExpenses.single().title)
        assertEquals(75.0, weddingExpenses.sumOf { it.amount }, 0.01)
        assertEquals("deposit", weddingExpenses.first { it.title == "Decor" }.notes)
    }

    @Test
    fun migrate3To4_addsTheBudgetLinesTable() = runTest {
        createVersion3Database { db ->
            db.execSQL("INSERT INTO events (name, budget, eventType, dateCreated) VALUES ('Wedding', 100.0, 'Wedding', 1)")
        }

        val db = openMigrated()
        val event = db.eventDao().getAllEvents().first().single()

        // Writing through the FK proves the new table exists and references the migrated ids.
        db.eventDao().insertBudgetLines(
            listOf(
                com.el.sapiospend.data.local.BudgetLineEntity(
                    eventId = event.id, category = "Catering", plannedAmount = 60.0
                )
            )
        )

        assertEquals(1, db.budgetLineDao().getBudgetLinesForEvent(event.id).first().size)
    }

    @Test
    fun migrate3To4_survivesAnEmptyDatabase() = runTest {
        createVersion3Database { }

        val db = openMigrated()

        assertTrue(db.eventDao().getAllEvents().first().isEmpty())
        assertEquals(0, db.eventDao().countActiveEvents())
        assertNotNull(db.budgetLineDao().getAllBudgetLines().first())
    }

    @Test
    fun migrate3To5_leavesLegacyEventsWithoutABudgetPeriod() = runTest {
        createVersion3Database { db ->
            db.execSQL("INSERT INTO events (name, budget, eventType, dateCreated) VALUES ('Wedding', 100.0, 'Wedding', 1)")
        }

        val event = openMigrated().eventDao().getAllEvents().first().single()

        // Events that predate periods stay open-ended. Inventing dates here would start
        // showing pace warnings on budgets that never had a deadline.
        assertNull(event.startDate)
        assertNull(event.endDate)
        assertFalse(event.hasPeriod)
    }

    @Test
    fun migrate3To4_dropsExpensesWhoseEventIsAlreadyGone() = runTest {
        createVersion3Database { db ->
            db.execSQL("PRAGMA foreign_keys = OFF")
            db.execSQL("INSERT INTO events (name, budget, eventType, dateCreated) VALUES ('Wedding', 100.0, 'Wedding', 1)")
            // An orphan left behind by an earlier bug would violate the new foreign key
            // at commit time and abort the whole migration, so it must be dropped.
            db.execSQL("INSERT INTO expenses (eventId, title, category, amount, notes, dateCreated) VALUES (999, 'Ghost', 'Food', 10.0, '', 1)")
        }

        val db = openMigrated()

        assertEquals(1, db.eventDao().getAllEvents().first().size)
        assertTrue(db.expenseDao().getAllExpenses().first().isEmpty())
    }
}

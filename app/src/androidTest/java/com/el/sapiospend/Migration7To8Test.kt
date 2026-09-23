package com.el.sapiospend

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.el.sapiospend.data.local.AppDatabase
import com.el.sapiospend.data.local.MIGRATION_7_8
import com.el.sapiospend.domain.budget.BudgetDirection
import com.el.sapiospend.settings.AppCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 7 -> 8 gives each budget a currency of its own.
 *
 * The column is nullable and nothing is backfilled, which is the part worth testing: a
 * budget created before this migration is denominated in whatever the app was recording
 * in, and writing today's base currency into every row would be a guess — wrong for
 * anyone who set their currency on a fresh install, and wrong again if the base ever
 * moves. NULL is the honest answer and [com.el.sapiospend.data.local.EventEntity.currency]
 * resolves it at the point of use.
 */
@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private val dbName = "migration-7-8-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate7To8_leavesExistingBudgetsWithoutACurrencyOfTheirOwn() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL(
                "INSERT INTO events (id, name, budget, eventType, moneyDirection, guestCount, dateCreated, startDate, endDate, ownerId, updatedAt, deletedAt) " +
                    "VALUES ('e1', 'Tolu & Ada', 7000000.0, 'Wedding', '${BudgetDirection.SPENDING.name}', 120, 111, NULL, NULL, 'local', 111, NULL)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        migrated.query("SELECT currencyCode, budget FROM events WHERE id = 'e1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            // The figure is untouched: this migration adds a column, it does not
            // re-denominate anything.
            assertEquals(7_000_000.0, cursor.getDouble(1), 0.01)
        }
        migrated.close()
    }

    @Test
    fun migrate7To8_acceptsBudgetsInDifferentCurrenciesSideBySide() {
        helper.createDatabase(dbName, 7).close()

        val migrated = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)
        migrated.execSQL(
            "INSERT INTO events (id, name, budget, eventType, moneyDirection, currencyCode, guestCount, dateCreated, startDate, endDate, ownerId, updatedAt, deletedAt) " +
                "VALUES ('e2', 'August Salary', 600000.0, 'Personal', '${BudgetDirection.SPENDING.name}', '${AppCurrency.NGN.code}', NULL, 1, NULL, NULL, 'local', 1, NULL)"
        )
        migrated.execSQL(
            "INSERT INTO events (id, name, budget, eventType, moneyDirection, currencyCode, guestCount, dateCreated, startDate, endDate, ownerId, updatedAt, deletedAt) " +
                "VALUES ('e3', 'Laptop Fund', 5000.0, 'Personal', '${BudgetDirection.SAVING.name}', '${AppCurrency.USD.code}', NULL, 2, NULL, NULL, 'local', 2, NULL)"
        )

        migrated.query("SELECT id, currencyCode FROM events ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(AppCurrency.NGN.code, cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals(AppCurrency.USD.code, cursor.getString(1))
        }
        migrated.close()
    }
}

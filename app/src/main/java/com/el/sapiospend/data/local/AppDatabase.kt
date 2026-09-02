package com.el.sapiospend.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * version 4 introduced client-generated ids, soft deletes and budget_lines.
 * version 5 added the optional start/end dates that turn an event into a budget period.
 * version 6 added payment tracking on expenses, guest counts, contributions and
 * recurring expense rules.
 *
 * There is deliberately no destructive-migration fallback. Once a plan is paid for,
 * dropping the database on a schema change means a planner loses the expense history
 * for a live event — every version bump from here needs a real Migration.
 */
@Database(
    entities = [
        EventEntity::class,
        ExpenseEntity::class,
        BudgetLineEntity::class,
        ContributionEntity::class,
        RecurringExpenseEntity::class
    ],
    version = 6
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetLineDao(): BudgetLineDao
    abstract fun contributionDao(): ContributionDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sapio_spend_db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

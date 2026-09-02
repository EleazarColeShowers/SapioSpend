package com.el.sapiospend.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 3 -> 4: integer primary keys become client-generated ids, every table gains
 * ownerId/updatedAt/deletedAt, and budget_lines is introduced.
 *
 * SQLite cannot change a column's type in place, so each table is rebuilt and copied.
 * The tricky part is the foreign key: expenses.eventId holds old integers, so a
 * temporary map table records old-int -> new-text for each event and the copy joins
 * through it. Without that map the parent-child links would be lost.
 *
 * defer_foreign_keys postpones FK enforcement to the end of the transaction, which is
 * what lets the parent table be dropped and rebuilt while children still reference it.
 */
/**
 * 4 -> 5: events gain an optional start and end date, so a budget can cover a period
 * (a salary month, a wedding week) rather than running open-ended forever.
 *
 * Both columns are added nullable and left NULL on existing rows. Back-filling them
 * from dateCreated was tempting and wrong: it would invent an end date nobody chose and
 * start telling users they were behind pace on events that never had a deadline.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `events` ADD COLUMN `startDate` INTEGER")
        db.execSQL("ALTER TABLE `events` ADD COLUMN `endDate` INTEGER")
    }
}

/**
 * 5 -> 6: the money-owed and money-in half of the app.
 *
 * expenses gain a vendor, the amount actually handed over, a due date and a receipt
 * path; events gain a guest count; and two tables arrive — contributions (funding
 * promised and received) and recurring_expenses (a cost that comes back).
 *
 * The one line that matters is the amountPaid back-fill. Every expense recorded before
 * this version was a record of money already gone, so leaving them at the column default
 * of zero would open the app on a screen claiming the user owes their entire spending
 * history. They are settled by definition, and are marked so.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `events` ADD COLUMN `guestCount` INTEGER")

        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `vendor` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `amountPaid` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `dueDate` INTEGER")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `receiptPath` TEXT")
        db.execSQL("UPDATE `expenses` SET `amountPaid` = `amount`")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `contributions` (
                `id` TEXT NOT NULL,
                `eventId` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `receivedAt` INTEGER,
                `notes` TEXT NOT NULL,
                `dateCreated` INTEGER NOT NULL,
                `ownerId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contributions_eventId` ON `contributions` (`eventId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_expenses` (
                `id` TEXT NOT NULL,
                `eventId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `vendor` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `frequency` TEXT NOT NULL,
                `nextDueDate` INTEGER NOT NULL,
                `until` INTEGER,
                `active` INTEGER NOT NULL,
                `dateCreated` INTEGER NOT NULL,
                `ownerId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_eventId` ON `recurring_expenses` (`eventId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA defer_foreign_keys = TRUE")

        val now = System.currentTimeMillis()

        // randomblob(16) is SQLite's only source of randomness here; hex-encoded it
        // gives a 32-character opaque id, equivalent in practice to a UUID.
        db.execSQL("CREATE TABLE `event_id_map` (`oldId` INTEGER NOT NULL PRIMARY KEY, `newId` TEXT NOT NULL)")
        db.execSQL("INSERT INTO `event_id_map` (`oldId`, `newId`) SELECT `id`, lower(hex(randomblob(16))) FROM `events`")

        db.execSQL(
            """
            CREATE TABLE `events_new` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `budget` REAL NOT NULL,
                `eventType` TEXT NOT NULL,
                `dateCreated` INTEGER NOT NULL,
                `ownerId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `events_new` (`id`, `name`, `budget`, `eventType`, `dateCreated`, `ownerId`, `updatedAt`, `deletedAt`)
            SELECT m.`newId`, e.`name`, e.`budget`, e.`eventType`, e.`dateCreated`, '${LocalOwner.ID}', $now, NULL
            FROM `events` e JOIN `event_id_map` m ON m.`oldId` = e.`id`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `events`")
        db.execSQL("ALTER TABLE `events_new` RENAME TO `events`")

        db.execSQL(
            """
            CREATE TABLE `expenses_new` (
                `id` TEXT NOT NULL,
                `eventId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `notes` TEXT NOT NULL,
                `dateCreated` INTEGER NOT NULL,
                `ownerId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        // The join also drops any expense whose parent event no longer exists, which
        // would otherwise violate the foreign key at commit time.
        db.execSQL(
            """
            INSERT INTO `expenses_new` (`id`, `eventId`, `title`, `category`, `amount`, `notes`, `dateCreated`, `ownerId`, `updatedAt`, `deletedAt`)
            SELECT lower(hex(randomblob(16))), m.`newId`, x.`title`, x.`category`, x.`amount`, x.`notes`, x.`dateCreated`, '${LocalOwner.ID}', $now, NULL
            FROM `expenses` x JOIN `event_id_map` m ON m.`oldId` = x.`eventId`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `expenses`")
        db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_eventId` ON `expenses` (`eventId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budget_lines` (
                `id` TEXT NOT NULL,
                `eventId` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `plannedAmount` REAL NOT NULL,
                `ownerId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_lines_eventId` ON `budget_lines` (`eventId`)")

        db.execSQL("DROP TABLE `event_id_map`")
    }
}

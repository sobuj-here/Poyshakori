package com.poyshakori.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.poyshakori.app.data.dao.AccountDao
import com.poyshakori.app.data.dao.AccountReconciliationDao
import com.poyshakori.app.data.dao.BackupLogDao
import com.poyshakori.app.data.dao.BudgetDao
import com.poyshakori.app.data.dao.CategoryDao
import com.poyshakori.app.data.dao.DebtDao
import com.poyshakori.app.data.dao.DebtRepaymentDao
import com.poyshakori.app.data.dao.RecurringTransactionDao
import com.poyshakori.app.data.dao.SavingsContributionDao
import com.poyshakori.app.data.dao.SavingsGoalDao
import com.poyshakori.app.data.dao.TransactionDao
import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.data.entity.AccountReconciliationEntity
import com.poyshakori.app.data.entity.BackupLogEntity
import com.poyshakori.app.data.entity.BudgetEntity
import com.poyshakori.app.data.entity.CategoryEntity
import com.poyshakori.app.data.entity.DebtEntity
import com.poyshakori.app.data.entity.DebtRepaymentEntity
import com.poyshakori.app.data.entity.RecurringTransactionEntity
import com.poyshakori.app.data.entity.SavingsContributionEntity
import com.poyshakori.app.data.entity.SavingsGoalEntity
import com.poyshakori.app.data.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        DebtEntity::class,
        CategoryEntity::class,
        SavingsGoalEntity::class,
        DebtRepaymentEntity::class,
        RecurringTransactionEntity::class,
        SavingsContributionEntity::class,
        AccountReconciliationEntity::class,
        BackupLogEntity::class,
        com.poyshakori.app.data.entity.RecycleBinEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun debtDao(): DebtDao
    abstract fun categoryDao(): CategoryDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun debtRepaymentDao(): DebtRepaymentDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun savingsContributionDao(): SavingsContributionDao
    abstract fun accountReconciliationDao(): AccountReconciliationDao
    abstract fun backupLogDao(): BackupLogDao
    abstract fun recycleBinDao(): com.poyshakori.app.data.dao.RecycleBinDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Alter budgets table for rollover
                db.execSQL("ALTER TABLE budgets ADD COLUMN rollover INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE budgets ADD COLUMN carriedOverAmount INTEGER NOT NULL DEFAULT 0")

                // Create debt_repayments
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS debt_repayments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        debtId INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        accountId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        note TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_repayments_debtId ON debt_repayments(debtId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_repayments_accountId ON debt_repayments(accountId)")

                // Create recurring_transactions
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recurring_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        accountId INTEGER NOT NULL,
                        targetAccountId INTEGER,
                        category TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        nextDueDate TEXT NOT NULL,
                        autoRecord INTEGER NOT NULL DEFAULT 0,
                        lastRecordedDate TEXT,
                        note TEXT,
                        active INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_accountId ON recurring_transactions(accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_nextDueDate ON recurring_transactions(nextDueDate)")

                // Create savings_contributions
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS savings_contributions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        accountId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        note TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_contributions_goalId ON savings_contributions(goalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_contributions_accountId ON savings_contributions(accountId)")

                // Create account_reconciliations
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS account_reconciliations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountId INTEGER NOT NULL,
                        systemBalance INTEGER NOT NULL,
                        actualBalance INTEGER NOT NULL,
                        discrepancy INTEGER NOT NULL,
                        reconciledDate TEXT NOT NULL,
                        note TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_account_reconciliations_accountId ON account_reconciliations(accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_account_reconciliations_reconciledDate ON account_reconciliations(reconciledDate)")

                // Create backup_logs
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS backup_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        filename TEXT NOT NULL,
                        uriString TEXT,
                        backupType TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        checksum TEXT,
                        notes TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN accountId INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recycle_bin (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemType TEXT NOT NULL,
                        originalId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        amount INTEGER NOT NULL DEFAULT 0,
                        details TEXT,
                        payloadJson TEXT NOT NULL,
                        deletedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recycle_bin_deletedAt ON recycle_bin(deletedAt)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add date and reference columns to debts
                db.execSQL("ALTER TABLE debts ADD COLUMN date TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE debts ADD COLUMN reference TEXT DEFAULT NULL")

                // 2. Backfill date from createdAt if empty
                db.execSQL("UPDATE debts SET date = strftime('%Y-%m-%d', createdAt / 1000, 'unixepoch') WHERE (date = '' OR date IS NULL) AND createdAt > 0")

                // 3. Safe reconciliation of existing incorrectly classified loan & repayment transactions
                // Excludes them cleanly from ordinary operational income/expenses
                db.execSQL("UPDATE transactions SET type = 'LOAN_LEND', category = 'Loan Given' WHERE type = 'EXPENSE' AND reference LIKE 'DEBT_DISBURSEMENT:%'")
                db.execSQL("UPDATE transactions SET type = 'LOAN_BORROW', category = 'Loan Received' WHERE type = 'INCOME' AND reference LIKE 'DEBT_DISBURSEMENT:%'")
                db.execSQL("UPDATE transactions SET type = 'DEBT_REPAYMENT', category = 'Loan Repayment' WHERE type = 'EXPENSE' AND reference LIKE 'DEBT_REPAYMENT:%'")
                db.execSQL("UPDATE transactions SET type = 'RECEIVABLE_REPAYMENT', category = 'Repayment Received' WHERE type = 'INCOME' AND reference LIKE 'DEBT_REPAYMENT:%'")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN serviceCharge REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_UNIVERSAL: Array<Migration> = (1..12).map { fromVersion ->
            object : Migration(fromVersion, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    applySchemaUpdatesUpTo13(db)
                }
            } as Migration
        }.toTypedArray()

        private fun applySchemaUpdatesUpTo13(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE transactions ADD COLUMN serviceCharge REAL NOT NULL DEFAULT 0.0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE budgets ADD COLUMN rollover INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE budgets ADD COLUMN carriedOverAmount INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}

            try {
                db.execSQL("ALTER TABLE debts ADD COLUMN accountId INTEGER DEFAULT NULL")
            } catch (_: Exception) {}

            try {
                db.execSQL("ALTER TABLE debts ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}

            try {
                db.execSQL("ALTER TABLE debts ADD COLUMN date TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {}

            try {
                db.execSQL("ALTER TABLE debts ADD COLUMN reference TEXT DEFAULT NULL")
            } catch (_: Exception) {}

            try {
                db.execSQL("UPDATE debts SET date = strftime('%Y-%m-%d', createdAt / 1000, 'unixepoch') WHERE (date = '' OR date IS NULL) AND createdAt > 0")
            } catch (_: Exception) {}

            try {
                db.execSQL("UPDATE transactions SET type = 'LOAN_LEND', category = 'Loan Given' WHERE type = 'EXPENSE' AND reference LIKE 'DEBT_DISBURSEMENT:%'")
                db.execSQL("UPDATE transactions SET type = 'LOAN_BORROW', category = 'Loan Received' WHERE type = 'INCOME' AND reference LIKE 'DEBT_DISBURSEMENT:%'")
                db.execSQL("UPDATE transactions SET type = 'DEBT_REPAYMENT', category = 'Loan Repayment' WHERE type = 'EXPENSE' AND reference LIKE 'DEBT_REPAYMENT:%'")
                db.execSQL("UPDATE transactions SET type = 'RECEIVABLE_REPAYMENT', category = 'Repayment Received' WHERE type = 'INCOME' AND reference LIKE 'DEBT_REPAYMENT:%'")
            } catch (_: Exception) {}

            try {
                db.execSQL("ALTER TABLE accounts ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
            } catch (_: Exception) {}

            try {
                db.execSQL("ALTER TABLE accounts ADD COLUMN currentBalance INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}

            try {
                db.execSQL("ALTER TABLE transactions ADD COLUMN targetAccountId INTEGER DEFAULT NULL")
            } catch (_: Exception) {}

            try {
                db.execSQL("ALTER TABLE transactions ADD COLUMN reference TEXT DEFAULT NULL")
            } catch (_: Exception) {}

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    icon TEXT,
                    color TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name_type ON categories(name, type)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS savings_goals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    targetAmount INTEGER NOT NULL,
                    savedAmount INTEGER NOT NULL,
                    targetDate TEXT NOT NULL,
                    note TEXT,
                    status TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS debt_repayments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    debtId INTEGER NOT NULL,
                    amount INTEGER NOT NULL,
                    accountId INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    time TEXT NOT NULL,
                    note TEXT,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_repayments_debtId ON debt_repayments(debtId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_repayments_accountId ON debt_repayments(accountId)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS recurring_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    type TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    accountId INTEGER NOT NULL,
                    targetAccountId INTEGER,
                    category TEXT NOT NULL,
                    frequency TEXT NOT NULL,
                    startDate TEXT NOT NULL,
                    nextDueDate TEXT NOT NULL,
                    autoRecord INTEGER NOT NULL DEFAULT 0,
                    lastRecordedDate TEXT,
                    note TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_accountId ON recurring_transactions(accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_nextDueDate ON recurring_transactions(nextDueDate)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS savings_contributions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    goalId INTEGER NOT NULL,
                    amount INTEGER NOT NULL,
                    accountId INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    note TEXT,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_contributions_goalId ON savings_contributions(goalId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_contributions_accountId ON savings_contributions(accountId)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS account_reconciliations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    accountId INTEGER NOT NULL,
                    systemBalance INTEGER NOT NULL,
                    actualBalance INTEGER NOT NULL,
                    discrepancy INTEGER NOT NULL,
                    reconciledDate TEXT NOT NULL,
                    note TEXT,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_account_reconciliations_accountId ON account_reconciliations(accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_account_reconciliations_reconciledDate ON account_reconciliations(reconciledDate)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS backup_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    filename TEXT NOT NULL,
                    uriString TEXT,
                    backupType TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    checksum TEXT,
                    notes TEXT
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS recycle_bin (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    itemType TEXT NOT NULL,
                    originalId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    amount INTEGER NOT NULL DEFAULT 0,
                    details TEXT,
                    payloadJson TEXT NOT NULL,
                    deletedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recycle_bin_deletedAt ON recycle_bin(deletedAt)")
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "poyshakori_db"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, *MIGRATION_UNIVERSAL)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.let {
                    if (it.isOpen) {
                        it.close()
                    }
                }
                INSTANCE = null
            }
        }
    }
}
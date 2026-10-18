package com.poyshakori.app.data.repository

import androidx.room.withTransaction
import com.poyshakori.app.data.database.AppDatabase
import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.data.entity.AccountReconciliationEntity
import com.poyshakori.app.data.entity.BackupLogEntity
import com.poyshakori.app.data.entity.BudgetEntity
import com.poyshakori.app.data.entity.CategorySpending
import com.poyshakori.app.data.entity.DebtEntity
import com.poyshakori.app.data.entity.DebtRepaymentEntity
import com.poyshakori.app.data.entity.MonthlySummary
import com.poyshakori.app.data.entity.RecurringTransactionEntity
import com.poyshakori.app.data.entity.SavingsContributionEntity
import com.poyshakori.app.data.entity.SavingsGoalEntity
import com.poyshakori.app.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TransactionRepository(
    private val db: AppDatabase
) {
    private val transactionDao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val budgetDao = db.budgetDao()
    private val debtDao = db.debtDao()
    private val savingsGoalDao = db.savingsGoalDao()
    private val debtRepaymentDao = db.debtRepaymentDao()
    private val recurringTransactionDao = db.recurringTransactionDao()
    private val savingsContributionDao = db.savingsContributionDao()
    private val accountReconciliationDao = db.accountReconciliationDao()
    private val backupLogDao = db.backupLogDao()
    private val recycleBinDao = db.recycleBinDao()

    // Transactions Flow
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val recentTransactions: Flow<List<TransactionEntity>> = transactionDao.getRecentTransactions(10)
    val totalIncome: Flow<Long?> = transactionDao.getTotalIncome()
    val totalExpense: Flow<Long?> = transactionDao.getTotalExpense()
    val monthlySummaries: Flow<List<MonthlySummary>> = transactionDao.getMonthlySummaries()

    // Accounts Flow
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val allAccountsIncludingInactive: Flow<List<AccountEntity>> = accountDao.getAllAccountsIncludingInactive()
    val totalBalance: Flow<Long?> = accountDao.getTotalBalance()

    // Debts & Repayments Flow
    val allDebts: Flow<List<DebtEntity>> = debtDao.getAllDebts()
    val totalPendingDebts: Flow<Long?> = debtDao.getTotalPendingDebts()
    val totalPendingReceivables: Flow<Long?> = debtDao.getTotalPendingReceivables()
    val totalLent: Flow<Long?> = debtDao.getTotalLent()
    val totalBorrowed: Flow<Long?> = debtDao.getTotalBorrowed()
    val totalLentRepaid: Flow<Long?> = debtDao.getTotalLentRepaid()
    val totalBorrowedRepaid: Flow<Long?> = debtDao.getTotalBorrowedRepaid()
    val allDebtRepayments: Flow<List<DebtRepaymentEntity>> = debtRepaymentDao.getAllRepayments()

    // Savings Goals Flow
    val allSavingsGoals: Flow<List<SavingsGoalEntity>> = savingsGoalDao.getAllGoals()
    val totalSaved: Flow<Long?> = savingsGoalDao.getTotalSaved()

    // Recurring & Reconciliation Flow
    val allActiveRecurring: Flow<List<RecurringTransactionEntity>> = recurringTransactionDao.getAllActiveRecurring()
    val allRecurring: Flow<List<RecurringTransactionEntity>> = recurringTransactionDao.getAllRecurring()
    val allReconciliations: Flow<List<AccountReconciliationEntity>> = accountReconciliationDao.getAllReconciliations()
    val backupLogs: Flow<List<BackupLogEntity>> = backupLogDao.getAllLogs()

    // Recycle Bin Flow
    val allRecycledItems: Flow<List<com.poyshakori.app.data.entity.RecycleBinEntity>> = recycleBinDao.getAllDeletedItems()
    val recycledItemCount: Flow<Int> = recycleBinDao.getDeletedItemCount()

    fun getMonthlyIncome(monthYear: String): Flow<Long?> = transactionDao.getMonthlyIncome(monthYear)
    fun getMonthlyExpense(monthYear: String): Flow<Long?> = transactionDao.getMonthlyExpense(monthYear)

    suspend fun getMonthlyExpenseAmount(monthYear: String): Long {
        return transactionDao.getMonthlyExpenseAmount(monthYear) ?: 0L
    }

    suspend fun findPotentialDuplicate(amount: Long, accountId: Long, date: String, category: String): TransactionEntity? {
        return transactionDao.findPotentialDuplicate(amount, accountId, date, category)
    }

    suspend fun getTransactionById(id: Long): TransactionEntity? {
        return transactionDao.getTransactionById(id)
    }

    /**
     * Atomically inserts a transaction and updates the affected account balance(s).
     */
    suspend fun insertTransaction(transaction: TransactionEntity): Long = db.withTransaction {
        require(transaction.amount > 0) { "Transaction amount must be strictly greater than 0" }
        require(transaction.category.isNotBlank()) { "Category must not be blank" }

        val insertedId = transactionDao.insertTransaction(transaction)

        when (transaction.type) {
            "INCOME", "LOAN_BORROW", "RECEIVABLE_REPAYMENT" -> {
                accountDao.updateBalance(transaction.accountId, transaction.amount)
            }
            "EXPENSE", "LOAN_LEND", "DEBT_REPAYMENT" -> {
                accountDao.updateBalance(transaction.accountId, -transaction.amount)
            }
            "TRANSFER" -> {
                val targetId = transaction.targetAccountId
                    ?: throw IllegalArgumentException("Target account ID required for Transfer")
                require(transaction.accountId != targetId) { "Source and destination accounts must be different" }
                accountDao.updateBalance(transaction.accountId, -transaction.amount)
                accountDao.updateBalance(targetId, transaction.amount)
            }
        }
        insertedId
    }

    /**
     * Atomically deletes a transaction and rolls back its balance impact.
     */
    suspend fun deleteTransaction(transaction: TransactionEntity) = db.withTransaction {
        when (transaction.type) {
            "INCOME", "LOAN_BORROW", "RECEIVABLE_REPAYMENT" -> {
                accountDao.updateBalance(transaction.accountId, -transaction.amount)
            }
            "EXPENSE", "LOAN_LEND", "DEBT_REPAYMENT" -> {
                accountDao.updateBalance(transaction.accountId, transaction.amount)
            }
            "TRANSFER" -> {
                accountDao.updateBalance(transaction.accountId, transaction.amount)
                transaction.targetAccountId?.let { targetId ->
                    accountDao.updateBalance(targetId, -transaction.amount)
                }
            }
        }
        transactionDao.deleteTransaction(transaction)
    }

    /**
     * Atomically updates a transaction by rolling back the old balance impact and applying the new impact.
     */
    suspend fun updateTransaction(newTransaction: TransactionEntity, oldTransaction: TransactionEntity) = db.withTransaction {
        // Rollback old transaction
        when (oldTransaction.type) {
            "INCOME", "LOAN_BORROW", "RECEIVABLE_REPAYMENT" -> accountDao.updateBalance(oldTransaction.accountId, -oldTransaction.amount)
            "EXPENSE", "LOAN_LEND", "DEBT_REPAYMENT" -> accountDao.updateBalance(oldTransaction.accountId, oldTransaction.amount)
            "TRANSFER" -> {
                accountDao.updateBalance(oldTransaction.accountId, oldTransaction.amount)
                oldTransaction.targetAccountId?.let { accountDao.updateBalance(it, -oldTransaction.amount) }
            }
        }

        // Apply new transaction
        when (newTransaction.type) {
            "INCOME", "LOAN_BORROW", "RECEIVABLE_REPAYMENT" -> accountDao.updateBalance(newTransaction.accountId, newTransaction.amount)
            "EXPENSE", "LOAN_LEND", "DEBT_REPAYMENT" -> accountDao.updateBalance(newTransaction.accountId, -newTransaction.amount)
            "TRANSFER" -> {
                val targetId = newTransaction.targetAccountId
                    ?: throw IllegalArgumentException("Target account ID required for Transfer")
                require(newTransaction.accountId != targetId) { "Source and target accounts must be different" }
                accountDao.updateBalance(newTransaction.accountId, -newTransaction.amount)
                accountDao.updateBalance(targetId, newTransaction.amount)
            }
        }

        transactionDao.updateTransaction(newTransaction)
    }

    fun searchTransactions(query: String): Flow<List<TransactionEntity>> {
        return transactionDao.searchTransactions(query)
    }

    fun getTransactionsByType(type: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByType(type)
    }

    fun getTransactionsByAccount(accountId: Long): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByAccount(accountId)
    }

    fun getCategorySpendingForMonth(monthYear: String): Flow<List<CategorySpending>> {
        return transactionDao.getCategorySpendingForMonth(monthYear)
    }

    fun getAllTimeCategorySpending(): Flow<List<CategorySpending>> {
        return transactionDao.getAllTimeCategorySpending()
    }

    fun getTotalIncomeByDateRange(startDate: String, endDate: String): Flow<Long?> {
        return transactionDao.getTotalIncomeByDateRange(startDate, endDate)
    }

    fun getTotalExpenseByDateRange(startDate: String, endDate: String): Flow<Long?> {
        return transactionDao.getTotalExpenseByDateRange(startDate, endDate)
    }

    // Accounts
    suspend fun insertAccount(account: AccountEntity): Long = db.withTransaction {
        val accountId = accountDao.insertAccount(account)
        if (account.openingBalance > 0) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val tx = TransactionEntity(
                type = "INCOME",
                amount = account.openingBalance,
                accountId = accountId,
                category = "Opening Balance",
                paymentMethod = account.name,
                date = today,
                time = time,
                note = "Opening balance for ${account.name}",
                reference = "OPENING_BALANCE:$accountId"
            )
            transactionDao.insertTransaction(tx)
        }
        accountId
    }

    suspend fun updateAccount(account: AccountEntity) {
        val existing = accountDao.getAccountById(account.id)
        if (existing != null) {
            accountDao.updateAccount(
                account.copy(
                    currentBalance = existing.currentBalance,
                    openingBalance = existing.openingBalance
                )
            )
        } else {
            accountDao.updateAccount(account)
        }
    }

    /**
     * Safe Account Deletion:
     * If the account has historical transactions, it is archived (soft deleted)
     * so financial history is never destroyed. If empty, hard delete is performed.
     */
    suspend fun deleteAccountSafe(account: AccountEntity): Boolean = db.withTransaction {
        val txCount = transactionDao.getTransactionCountForAccount(account.id)
        val debtCount = debtDao.getDebtsCountForAccount(account.id)
        if (txCount > 0 || debtCount > 0) {
            accountDao.softDeleteAccount(account.id)
            false // Soft deleted (archived) to preserve historical transactions and debt relationships
        } else {
            accountDao.deleteAccount(account)
            true // Hard deleted
        }
    }

    suspend fun getAccountById(id: Long): AccountEntity? {
        return accountDao.getAccountById(id)
    }

    // Budgets & Rollover
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>> {
        return budgetDao.getBudgetsForMonth(monthYear)
    }

    fun getAllBudgets(): Flow<List<BudgetEntity>> {
        return budgetDao.getAllBudgets()
    }

    suspend fun insertBudget(budget: BudgetEntity): Long {
        return budgetDao.insertBudget(budget)
    }

    suspend fun deleteBudget(budget: BudgetEntity) {
        budgetDao.deleteBudget(budget)
    }

    suspend fun getSpentAmountForCategory(category: String, monthYear: String): Long {
        return budgetDao.getSpentAmountForCategory(category, monthYear) ?: 0L
    }

    /**
     * Process month rollover: carries over positive unspent balances from the previous month
     * for budgets that have rollover enabled.
     */
    suspend fun processBudgetRollover(currentMonthYear: String) = db.withTransaction {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(currentMonthYear) ?: Date()
        cal.add(Calendar.MONTH, -1)
        val previousMonthYear = sdf.format(cal.time)

        val previousBudgets = budgetDao.getBudgetsForMonthList(previousMonthYear)
        for (prev in previousBudgets) {
            if (prev.rollover) {
                val spent = getSpentAmountForCategory(prev.category, previousMonthYear)
                val effectiveLimit = prev.monthlyLimit + prev.carriedOverAmount
                val remaining = effectiveLimit - spent
                if (remaining > 0) {
                    val currentBudget = budgetDao.getBudgetForCategoryAndMonth(prev.category, currentMonthYear)
                    if (currentBudget != null) {
                        budgetDao.updateBudget(currentBudget.copy(carriedOverAmount = remaining))
                    } else {
                        budgetDao.insertBudget(
                            BudgetEntity(
                                category = prev.category,
                                monthlyLimit = prev.monthlyLimit,
                                monthYear = currentMonthYear,
                                rollover = true,
                                carriedOverAmount = remaining
                            )
                        )
                    }
                }
            }
        }
    }

    // Debts & Repayments
    fun getDebtsByType(type: String): Flow<List<DebtEntity>> {
        return debtDao.getDebtsByType(type)
    }

    suspend fun insertDebt(debt: DebtEntity): Long = db.withTransaction {
        val debtId = debtDao.insertDebt(debt)
        debt.accountId?.let { accId ->
            if (accId > 0) {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val isReceivable = debt.type == "RECEIVABLE"
                // Money Lent = LOAN_LEND (Cash decreases, Receivable asset increases, NOT an ordinary expense)
                // Money Borrowed = LOAN_BORROW (Cash increases, Liability increases, NOT ordinary income)
                val txType = if (isReceivable) "LOAN_LEND" else "LOAN_BORROW"
                val catName = if (isReceivable) "Loan Given" else "Loan Received"
                val noteText = if (isReceivable) "Loan given to ${debt.personName}" else "Borrowed from ${debt.personName}"
                val finalNote = if (!debt.note.isNullOrBlank()) "$noteText (${debt.note})" else noteText
                val txDate = if (debt.date.isNotBlank()) debt.date else today

                val accountName = accountDao.getAccountById(accId)?.name ?: "Cash"
                val tx = TransactionEntity(
                    type = txType,
                    amount = debt.amount,
                    accountId = accId,
                    category = catName,
                    paymentMethod = accountName,
                    date = txDate,
                    time = time,
                    note = finalNote,
                    reference = debt.reference ?: "DEBT_DISBURSEMENT:$debtId"
                )
                transactionDao.insertTransaction(tx)

                // Cash balance movement:
                // DEBT (I borrow money): money enters user's account (+amount)
                // RECEIVABLE (someone borrows money from user): money leaves user's account (-amount)
                val delta = if (debt.type == "DEBT") debt.amount else -debt.amount
                accountDao.updateBalance(accId, delta)
            }
        }
        debtId
    }

    suspend fun updateDebt(debt: DebtEntity) {
        debtDao.updateDebt(debt)
    }

    suspend fun deleteDebt(debt: DebtEntity) = db.withTransaction {
        debtDao.deleteDebt(debt)
        debtRepaymentDao.deleteRepaymentsForDebt(debt.id)
        transactionDao.deleteTransactionsForDebt(debt.id)
        debt.accountId?.let { accId ->
            if (accId > 0) {
                val remaining = debt.amount - debt.paidAmount
                if (remaining > 0) {
                    val delta = if (debt.type == "DEBT") -remaining else remaining
                    accountDao.updateBalance(accId, delta)
                }
            }
        }
    }

    /**
     * Record a partial or full debt repayment atomically.
     * Cash movements update the selected account without double counting.
     */
    suspend fun recordDebtRepayment(
        debtId: Long,
        repaymentAmount: Long,
        accountId: Long,
        date: String,
        time: String,
        note: String? = null
    ) = db.withTransaction {
        require(repaymentAmount > 0) { "Repayment amount must be positive" }
        val debt = debtDao.getDebtById(debtId)
            ?: throw IllegalArgumentException("Debt with ID $debtId not found")

        val remaining = debt.amount - debt.paidAmount
        require(repaymentAmount <= remaining) { "Repayment amount ($repaymentAmount) exceeds outstanding balance ($remaining)" }

        val newPaidAmount = debt.paidAmount + repaymentAmount
        val newStatus = if (newPaidAmount >= debt.amount) "PAID" else "PARTIALLY_PAID"

        // Update debt record
        debtDao.updateDebtRepaymentStatus(debtId, newPaidAmount, newStatus)

        // Insert repayment audit record
        val repaymentId = debtRepaymentDao.insertRepayment(
            DebtRepaymentEntity(
                debtId = debtId,
                amount = repaymentAmount,
                accountId = accountId,
                date = date,
                time = time,
                note = note
            )
        )

        // Insert corresponding transaction so it appears in the Transactions tab
        // DEBT_REPAYMENT: User repays borrowed principal (Cash decreases, liability decreases, NOT an expense)
        // RECEIVABLE_REPAYMENT: Counterparty repays user (Cash increases, receivable decreases, NOT income)
        val isDebt = debt.type == "DEBT"
        val txType = if (isDebt) "DEBT_REPAYMENT" else "RECEIVABLE_REPAYMENT"
        val catName = if (isDebt) "Loan Repayment" else "Repayment Received"
        val noteText = if (isDebt) "Debt repayment to ${debt.personName}" else "Repayment received from ${debt.personName}"
        val finalNote = if (!note.isNullOrBlank()) "$noteText ($note)" else noteText
        val accountName = accountDao.getAccountById(accountId)?.name ?: "Cash"

        val tx = TransactionEntity(
            type = txType,
            amount = repaymentAmount,
            accountId = accountId,
            category = catName,
            paymentMethod = accountName,
            date = date,
            time = time,
            note = finalNote,
            reference = "DEBT_REPAYMENT:$debtId:$repaymentId"
        )
        transactionDao.insertTransaction(tx)

        // Cash Movement:
        // DEBT: User owes someone money. Repaying means money leaves user's account (-repaymentAmount)
        // RECEIVABLE: Someone owes user. Repaying means money enters user's account (+repaymentAmount)
        if (isDebt) {
            accountDao.updateBalance(accountId, -repaymentAmount)
        } else {
            accountDao.updateBalance(accountId, repaymentAmount)
        }
    }

    fun getRepaymentsForDebt(debtId: Long): Flow<List<DebtRepaymentEntity>> {
        return debtRepaymentDao.getRepaymentsForDebt(debtId)
    }

    // Savings Goals
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity): Long {
        return savingsGoalDao.insertGoal(goal)
    }

    suspend fun updateSavingsGoal(goal: SavingsGoalEntity) {
        savingsGoalDao.updateGoal(goal)
    }

    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity) {
        savingsGoalDao.deleteGoal(goal)
    }

    /**
     * Atomically adds contribution to a savings goal and deducts money from the source account.
     */
    suspend fun addSavingsContribution(
        goalId: Long,
        amount: Long,
        accountId: Long,
        date: String,
        note: String? = null
    ) = db.withTransaction {
        require(amount > 0) { "Contribution amount must be positive" }
        val goal = requireNotNull(savingsGoalDao.getGoalById(goalId)) { "Savings goal with ID $goalId not found" }

        // Deduct money from source account
        accountDao.updateBalance(accountId, -amount)

        // Add to savings goal
        savingsGoalDao.addContribution(goalId, amount)

        // Insert contribution log
        savingsContributionDao.insertContribution(
            SavingsContributionEntity(
                goalId = goalId,
                amount = amount,
                accountId = accountId,
                date = date,
                note = note
            )
        )

        // Insert corresponding transaction so it appears in the Transactions tab
        val timeNow = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val noteText = "Savings goal: ${goal.title}" + if (!note.isNullOrBlank()) " ($note)" else ""
        val accountName = accountDao.getAccountById(accountId)?.name ?: "Savings"
        val tx = TransactionEntity(
            type = "EXPENSE",
            amount = amount,
            accountId = accountId,
            category = "Savings",
            paymentMethod = accountName,
            date = date,
            time = timeNow,
            note = noteText,
            reference = "SAVINGS_CONTRIBUTION:$goalId"
        )
        transactionDao.insertTransaction(tx)
    }

    fun getContributionsForGoal(goalId: Long): Flow<List<SavingsContributionEntity>> {
        return savingsContributionDao.getContributionsForGoal(goalId)
    }

    // Recurring Transactions
    suspend fun insertRecurring(recurring: RecurringTransactionEntity): Long {
        return recurringTransactionDao.insertRecurring(recurring)
    }

    suspend fun updateRecurring(recurring: RecurringTransactionEntity) {
        recurringTransactionDao.updateRecurring(recurring)
    }

    suspend fun deleteRecurring(recurring: RecurringTransactionEntity) {
        recurringTransactionDao.deleteRecurring(recurring)
    }

    /**
     * Execute due recurring transactions: automatically records transaction and advances nextDueDate.
     */
    suspend fun processDueRecurringTransactions(currentDate: String) = db.withTransaction {
        val dueList = recurringTransactionDao.getDueRecurring(currentDate)
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        for (item in dueList) {
            if (item.autoRecord) {
                // Record the transaction
                val tx = TransactionEntity(
                    type = item.type,
                    amount = item.amount,
                    accountId = item.accountId,
                    targetAccountId = item.targetAccountId,
                    category = item.category,
                    paymentMethod = "Recurring",
                    date = currentDate,
                    time = currentTime,
                    note = "[Recurring] ${item.title}" + (if (!item.note.isNullOrBlank()) " - ${item.note}" else "")
                )
                insertTransaction(tx)
            }

            // Calculate next due date
            val nextDate = calculateNextDueDate(item.nextDueDate, item.frequency)
            recurringTransactionDao.updateRecurring(
                item.copy(
                    nextDueDate = nextDate,
                    lastRecordedDate = currentDate
                )
            )
        }
    }

    private fun calculateNextDueDate(currentDue: String, frequency: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(currentDue) ?: Date()
        when (frequency.uppercase(Locale.ROOT)) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "YEARLY" -> cal.add(Calendar.YEAR, 1)
            else -> cal.add(Calendar.MONTH, 1) // Default MONTHLY
        }
        return sdf.format(cal.time)
    }

    // Reconciliations
    suspend fun insertReconciliation(
        accountId: Long,
        systemBalance: Long,
        actualBalance: Long,
        reconciledDate: String,
        note: String? = null
    ): Long {
        val discrepancy = actualBalance - systemBalance
        return accountReconciliationDao.insertReconciliation(
            AccountReconciliationEntity(
                accountId = accountId,
                systemBalance = systemBalance,
                actualBalance = actualBalance,
                discrepancy = discrepancy,
                reconciledDate = reconciledDate,
                note = note
            )
        )
    }

    fun getReconciliationsForAccount(accountId: Long): Flow<List<AccountReconciliationEntity>> {
        return accountReconciliationDao.getReconciliationsForAccount(accountId)
    }

    // Backup Logs
    suspend fun logBackupOperation(
        filename: String,
        uriString: String?,
        backupType: String,
        status: String,
        checksum: String? = null,
        notes: String? = null
    ): Long {
        return backupLogDao.insertLog(
            BackupLogEntity(
                filename = filename,
                uriString = uriString,
                backupType = backupType,
                status = status,
                checksum = checksum,
                notes = notes
            )
        )
    }

    suspend fun getTransactionCountForAccount(accountId: Long): Int {
        return transactionDao.getTransactionCountForAccount(accountId)
    }

    /**
     * Soft delete a transaction into the Recycle Bin.
     * Safely rolls back the transaction's cash effect on account balance.
     */
    suspend fun recycleTransaction(transaction: TransactionEntity) = db.withTransaction {
        val json = org.json.JSONObject().apply {
            put("id", transaction.id)
            put("type", transaction.type)
            put("amount", transaction.amount)
            put("accountId", transaction.accountId)
            put("targetAccountId", transaction.targetAccountId ?: -1L)
            put("category", transaction.category)
            put("paymentMethod", transaction.paymentMethod)
            put("date", transaction.date)
            put("time", transaction.time)
            put("note", transaction.note ?: "")
            put("reference", transaction.reference ?: "")
            put("createdAt", transaction.createdAt)
            put("updatedAt", transaction.updatedAt)
        }.toString()

        recycleBinDao.insertItem(
            com.poyshakori.app.data.entity.RecycleBinEntity(
                itemType = "TRANSACTION",
                originalId = transaction.id,
                title = "${transaction.type}: ${transaction.category}",
                amount = transaction.amount,
                details = "${transaction.date} • ${transaction.paymentMethod}",
                payloadJson = json
            )
        )

        transactionDao.deleteTransaction(transaction)

        when (transaction.type) {
            "INCOME", "LOAN_BORROW", "RECEIVABLE_REPAYMENT" -> accountDao.updateBalance(transaction.accountId, -transaction.amount)
            "EXPENSE", "LOAN_LEND", "DEBT_REPAYMENT" -> accountDao.updateBalance(transaction.accountId, transaction.amount)
            "TRANSFER" -> {
                accountDao.updateBalance(transaction.accountId, transaction.amount)
                transaction.targetAccountId?.let { accountDao.updateBalance(it, -transaction.amount) }
            }
            else -> {}
        }
    }

    /**
     * Soft delete a debt/loan into the Recycle Bin.
     * Safely rolls back the remaining financial impact.
     */
    suspend fun recycleDebt(debt: DebtEntity) = db.withTransaction {
        val json = org.json.JSONObject().apply {
            put("id", debt.id)
            put("type", debt.type)
            put("personName", debt.personName)
            put("amount", debt.amount)
            put("paidAmount", debt.paidAmount)
            put("dueDate", debt.dueDate)
            put("status", debt.status)
            put("note", debt.note ?: "")
            put("accountId", debt.accountId ?: -1L)
            put("createdAt", debt.createdAt)
            put("date", debt.date)
            put("reference", debt.reference ?: "")
        }.toString()

        val outstanding = debt.amount - debt.paidAmount
        recycleBinDao.insertItem(
            com.poyshakori.app.data.entity.RecycleBinEntity(
                itemType = "DEBT",
                originalId = debt.id,
                title = "${if (debt.type == "DEBT") "Borrowing" else "Lending"}: ${debt.personName}",
                amount = outstanding,
                details = "Due: ${if (debt.dueDate.isNotBlank()) debt.dueDate else "No due date"}",
                payloadJson = json
            )
        )

        debtDao.deleteDebt(debt)

        debt.accountId?.let { accId ->
            if (accId > 0 && outstanding > 0) {
                val delta = if (debt.type == "DEBT") -outstanding else outstanding
                accountDao.updateBalance(accId, delta)
            }
        }
    }

    /**
     * Soft delete a budget into the Recycle Bin.
     */
    suspend fun recycleBudget(budget: BudgetEntity) = db.withTransaction {
        val json = org.json.JSONObject().apply {
            put("id", budget.id)
            put("category", budget.category)
            put("monthlyLimit", budget.monthlyLimit)
            put("monthYear", budget.monthYear)
            put("rollover", budget.rollover)
            put("carriedOverAmount", budget.carriedOverAmount)
        }.toString()

        recycleBinDao.insertItem(
            com.poyshakori.app.data.entity.RecycleBinEntity(
                itemType = "BUDGET",
                originalId = budget.id,
                title = "Budget: ${budget.category}",
                amount = budget.monthlyLimit,
                details = "Month: ${budget.monthYear}",
                payloadJson = json
            )
        )

        budgetDao.deleteBudget(budget)
    }

    /**
     * Restore an item from the Recycle Bin back to its active table.
     */
    suspend fun restoreRecycledItem(item: com.poyshakori.app.data.entity.RecycleBinEntity) = db.withTransaction {
        val json = org.json.JSONObject(item.payloadJson)
        when (item.itemType) {
            "TRANSACTION" -> {
                val tx = TransactionEntity(
                    id = 0L,
                    type = json.getString("type"),
                    amount = json.getLong("amount"),
                    accountId = json.getLong("accountId"),
                    targetAccountId = if (json.optLong("targetAccountId", -1L) != -1L) json.getLong("targetAccountId") else null,
                    category = json.getString("category"),
                    paymentMethod = json.optString("paymentMethod", "Cash"),
                    date = json.getString("date"),
                    time = json.optString("time", "12:00:00"),
                    note = json.optString("note").ifEmpty { null },
                    reference = json.optString("reference").ifEmpty { null },
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = System.currentTimeMillis()
                )
                transactionDao.insertTransaction(tx)

                when (tx.type) {
                    "INCOME", "LOAN_BORROW", "RECEIVABLE_REPAYMENT" -> accountDao.updateBalance(tx.accountId, tx.amount)
                    "EXPENSE", "LOAN_LEND", "DEBT_REPAYMENT" -> accountDao.updateBalance(tx.accountId, -tx.amount)
                    "TRANSFER" -> {
                        accountDao.updateBalance(tx.accountId, -tx.amount)
                        tx.targetAccountId?.let { accountDao.updateBalance(it, tx.amount) }
                    }
                    else -> {}
                }
            }
            "DEBT" -> {
                val accId = json.optLong("accountId", -1L)
                val debt = DebtEntity(
                    id = 0L,
                    type = json.getString("type"),
                    personName = json.getString("personName"),
                    amount = json.getLong("amount"),
                    paidAmount = json.optLong("paidAmount", 0L),
                    dueDate = json.optString("dueDate", ""),
                    status = json.optString("status", "PENDING"),
                    note = json.optString("note").ifEmpty { null },
                    accountId = if (accId != -1L) accId else null,
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    date = json.optString("date", ""),
                    reference = json.optString("reference").ifEmpty { null }
                )
                debtDao.insertDebt(debt)

                if (debt.accountId != null && debt.accountId > 0) {
                    val remaining = debt.amount - debt.paidAmount
                    if (remaining > 0) {
                        val delta = if (debt.type == "DEBT") remaining else -remaining
                        accountDao.updateBalance(debt.accountId, delta)
                    }
                }
            }
            "BUDGET" -> {
                val budget = BudgetEntity(
                    id = 0L,
                    category = json.getString("category"),
                    monthlyLimit = json.getLong("monthlyLimit"),
                    monthYear = json.getString("monthYear"),
                    rollover = json.optBoolean("rollover", false),
                    carriedOverAmount = json.optLong("carriedOverAmount", 0L)
                )
                budgetDao.insertBudget(budget)
            }
            else -> {}
        }

        recycleBinDao.deleteItemById(item.id)
    }

    suspend fun deleteRecycledItemPermanently(item: com.poyshakori.app.data.entity.RecycleBinEntity) {
        recycleBinDao.deleteItem(item)
    }

    suspend fun emptyRecycleBin() {
        recycleBinDao.emptyRecycleBin()
    }
}
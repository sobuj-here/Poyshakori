package com.poyshakori.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poyshakori.app.data.database.AppDatabase
import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.data.entity.AccountReconciliationEntity
import com.poyshakori.app.data.entity.BackupLogEntity
import com.poyshakori.app.data.entity.BudgetEntity
import com.poyshakori.app.data.entity.CategoryEntity
import com.poyshakori.app.data.entity.CategorySpending
import com.poyshakori.app.data.entity.DebtEntity
import com.poyshakori.app.data.entity.DebtRepaymentEntity
import com.poyshakori.app.data.entity.MonthlySummary
import com.poyshakori.app.data.entity.RecurringTransactionEntity
import com.poyshakori.app.data.entity.SavingsContributionEntity
import com.poyshakori.app.data.entity.SavingsGoalEntity
import com.poyshakori.app.data.entity.TransactionEntity
import com.poyshakori.app.backup.AutoBackupManager
import com.poyshakori.app.data.repository.CategoryRepository
import com.poyshakori.app.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository
    private val categoryRepository: CategoryRepository

    // Date / Period State
    private val _currentMonthYear = MutableStateFlow(
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    )
    val currentMonthYear: StateFlow<String> = _currentMonthYear.asStateFlow()

    // Transactions Flow
    val allTransactions: StateFlow<List<TransactionEntity>>
    val recentTransactions: StateFlow<List<TransactionEntity>>
    val totalIncome: StateFlow<Long?>
    val totalExpense: StateFlow<Long?>
    val currentMonthIncome: StateFlow<Long?>
    val currentMonthExpense: StateFlow<Long?>
    val totalBalance: StateFlow<Double?>
    val allAccounts: StateFlow<List<AccountEntity>>
    val allBudgets: StateFlow<List<BudgetEntity>>
    val monthlySummaries: StateFlow<List<MonthlySummary>>

    // Debts & Loans
    val allDebts: StateFlow<List<DebtEntity>>
    val totalPendingDebts: StateFlow<Long?>
    val totalPendingReceivables: StateFlow<Long?>
    val totalLent: StateFlow<Long?>
    val totalBorrowed: StateFlow<Long?>
    val totalLentRepaid: StateFlow<Long?>
    val totalBorrowedRepaid: StateFlow<Long?>

    // Savings Goals
    val allSavingsGoals: StateFlow<List<SavingsGoalEntity>>
    val totalSaved: StateFlow<Long?>

    // Recurring & Reconciliation
    val allActiveRecurring: StateFlow<List<RecurringTransactionEntity>>
    val allRecurring: StateFlow<List<RecurringTransactionEntity>>
    val backupLogs: StateFlow<List<BackupLogEntity>>
    val allRecycledItems: StateFlow<List<com.poyshakori.app.data.entity.RecycleBinEntity>>
    val recycledItemCount: StateFlow<Int>

    // Search & Filter state for Transactions tab
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow("ALL") // "ALL", "INCOME", "EXPENSE", "TRANSFER"
    val selectedTypeFilter: StateFlow<String> = _selectedTypeFilter.asStateFlow()

    private val _selectedAccountIdFilter = MutableStateFlow<Long?>(null)
    val selectedAccountIdFilter: StateFlow<Long?> = _selectedAccountIdFilter.asStateFlow()

    private val _startDateFilter = MutableStateFlow<String?>(null)
    val startDateFilter: StateFlow<String?> = _startDateFilter.asStateFlow()

    private val _endDateFilter = MutableStateFlow<String?>(null)
    val endDateFilter: StateFlow<String?> = _endDateFilter.asStateFlow()

    private val _sortByFilter = MutableStateFlow("NEWEST") // "NEWEST", "OLDEST", "HIGHEST", "LOWEST"
    val sortByFilter: StateFlow<String> = _sortByFilter.asStateFlow()

    private val _minAmountFilter = MutableStateFlow<Long?>(null)
    val minAmountFilter: StateFlow<Long?> = _minAmountFilter.asStateFlow()

    private val _maxAmountFilter = MutableStateFlow<Long?>(null)
    val maxAmountFilter: StateFlow<Long?> = _maxAmountFilter.asStateFlow()

    val filteredTransactions: StateFlow<List<TransactionEntity>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TransactionRepository(db)
        categoryRepository = CategoryRepository(db.categoryDao())

        allTransactions = repository.allTransactions.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        recentTransactions = repository.recentTransactions.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        totalIncome = repository.totalIncome.stateIn(
            viewModelScope, SharingStarted.Lazily, 0L
        )

        totalExpense = repository.totalExpense.stateIn(
            viewModelScope, SharingStarted.Lazily, 0L
        )

        currentMonthIncome = _currentMonthYear.flatMapLatest { monthYear ->
            repository.getMonthlyIncome(monthYear)
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

        currentMonthExpense = _currentMonthYear.flatMapLatest { monthYear ->
            repository.getMonthlyExpense(monthYear)
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

        totalBalance = repository.totalBalance.stateIn(
            viewModelScope, SharingStarted.Lazily, 0.0
        )

        allAccounts = repository.allAccounts.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        allBudgets = repository.getAllBudgets().stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        allDebts = repository.allDebts.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        totalPendingDebts = repository.totalPendingDebts.stateIn(
            viewModelScope, SharingStarted.Lazily, 0L
        )

        totalPendingReceivables = repository.totalPendingReceivables.stateIn(
            viewModelScope, SharingStarted.Lazily, 0L
        )

        totalLent = repository.totalLent.stateIn(
            viewModelScope, SharingStarted.Lazily, 0L
        )

        totalBorrowed = repository.totalBorrowed.stateIn(
            viewModelScope, SharingStarted.Lazily, 0L
        )

        totalLentRepaid = repository.totalLentRepaid.stateIn(
            viewModelScope, SharingStarted.Lazily, 0L
        )

        totalBorrowedRepaid = repository.totalBorrowedRepaid.stateIn(
            viewModelScope, SharingStarted.Lazily, 0L
        )

        allSavingsGoals = repository.allSavingsGoals.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        totalSaved = repository.totalSaved.stateIn(
            viewModelScope, SharingStarted.Lazily, 0L
        )

        allActiveRecurring = repository.allActiveRecurring.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        allRecurring = repository.allRecurring.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        monthlySummaries = repository.monthlySummaries.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        backupLogs = repository.backupLogs.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        allRecycledItems = repository.allRecycledItems.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        recycledItemCount = repository.recycledItemCount.stateIn(
            viewModelScope, SharingStarted.Lazily, 0
        )

        filteredTransactions = combine(
            allTransactions,
            _searchQuery,
            _selectedTypeFilter,
            _selectedAccountIdFilter,
            _startDateFilter,
            _endDateFilter,
            _sortByFilter,
            _minAmountFilter,
            _maxAmountFilter
        ) { args: Array<Any?> ->
            @Suppress("UNCHECKED_CAST")
            val transactions = args[0] as List<TransactionEntity>
            val query = args[1] as String
            val typeFilter = args[2] as String
            val accountId = args[3] as? Long
            val startDate = args[4] as? String
            val endDate = args[5] as? String
            val sortBy = (args[6] as? String) ?: "NEWEST"
            val minAmount = args[7] as? Long
            val maxAmount = args[8] as? Long

            val filtered = transactions.filter { tx ->
                val matchesQuery = query.isBlank() ||
                        tx.category.contains(query, ignoreCase = true) ||
                        (tx.note != null && tx.note.contains(query, ignoreCase = true)) ||
                        (tx.reference != null && tx.reference.contains(query, ignoreCase = true)) ||
                        tx.amount.toString().contains(query)
                val matchesType = when (typeFilter) {
                    "ALL" -> true
                    "LOAN", "DEBT", "DEBT_LOAN", "DEBTS_AND_LOAN" ->
                        tx.type in listOf("LOAN_LEND", "LOAN_BORROW", "DEBT_REPAYMENT", "RECEIVABLE_REPAYMENT", "DEBT", "RECEIVABLE") ||
                        tx.type.startsWith("LOAN_") || tx.type.startsWith("DEBT_") || tx.type.startsWith("RECEIVABLE_") ||
                        tx.reference?.startsWith("DEBT_") == true
                    else -> tx.type.equals(typeFilter, ignoreCase = true)
                }
                val matchesAccount = accountId == null || tx.accountId == accountId || tx.targetAccountId == accountId
                val matchesStartDate = startDate.isNullOrBlank() || tx.date >= startDate
                val matchesEndDate = endDate.isNullOrBlank() || tx.date <= endDate
                val matchesMinAmount = minAmount == null || tx.amount >= minAmount
                val matchesMaxAmount = maxAmount == null || tx.amount <= maxAmount

                matchesQuery && matchesType && matchesAccount && matchesStartDate && matchesEndDate && matchesMinAmount && matchesMaxAmount
            }

            when (sortBy) {
                "OLDEST" -> filtered.sortedWith(compareBy({ it.date }, { it.time }, { it.id }))
                "HIGHEST" -> filtered.sortedByDescending { it.amount }
                "LOWEST" -> filtered.sortedBy { it.amount }
                else -> filtered.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.time }.thenByDescending { it.id })
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // Check for due recurring transactions on launch
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            repository.processDueRecurringTransactions(today)
        }
    }

    fun setCurrentMonthYear(monthYear: String) {
        _currentMonthYear.value = monthYear
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTypeFilter(type: String) {
        _selectedTypeFilter.value = type
    }

    fun setAccountFilter(accountId: Long?) {
        _selectedAccountIdFilter.value = accountId
    }

    fun setDateRangeFilter(startDate: String?, endDate: String?) {
        _startDateFilter.value = startDate
        _endDateFilter.value = endDate
    }

    fun setSortBy(sort: String) {
        _sortByFilter.value = sort
    }

    fun setAmountRangeFilter(min: Long?, max: Long?) {
        _minAmountFilter.value = min
        _maxAmountFilter.value = max
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedTypeFilter.value = "ALL"
        _selectedAccountIdFilter.value = null
        _startDateFilter.value = null
        _endDateFilter.value = null
        _sortByFilter.value = "NEWEST"
        _minAmountFilter.value = null
        _maxAmountFilter.value = null
    }

    // Transactions
    fun insertTransaction(transaction: TransactionEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.insertTransaction(transaction)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
        onSuccess?.invoke()
    }

    fun updateTransaction(newTx: TransactionEntity, oldTx: TransactionEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.updateTransaction(newTx, oldTx)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
        onSuccess?.invoke()
    }

    fun deleteTransaction(transaction: TransactionEntity) = viewModelScope.launch {
        repository.deleteTransaction(transaction)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
    }

    // Accounts
    fun insertAccount(account: AccountEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.insertAccount(account)
        onSuccess?.invoke()
    }

    fun updateAccount(account: AccountEntity) = viewModelScope.launch {
        repository.updateAccount(account)
    }

    fun deleteAccountSafe(account: AccountEntity, onResult: ((isHardDeleted: Boolean) -> Unit)? = null) = viewModelScope.launch {
        val hardDeleted = repository.deleteAccountSafe(account)
        onResult?.invoke(hardDeleted)
    }

    // Budgets
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>> {
        return repository.getBudgetsForMonth(monthYear)
    }

    fun insertBudget(budget: BudgetEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.insertBudget(budget)
        onSuccess?.invoke()
    }

    fun deleteBudget(budget: BudgetEntity) = viewModelScope.launch {
        repository.deleteBudget(budget)
    }

    suspend fun getSpentAmountForCategory(category: String, monthYear: String): Long {
        return repository.getSpentAmountForCategory(category, monthYear)
    }

    // Debts & Loans
    fun insertDebt(debt: DebtEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.insertDebt(debt)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
        onSuccess?.invoke()
    }

    fun updateDebt(debt: DebtEntity) = viewModelScope.launch {
        repository.updateDebt(debt)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
    }

    fun deleteDebt(debt: DebtEntity) = viewModelScope.launch {
        repository.deleteDebt(debt)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
    }

    fun recordDebtRepayment(
        debtId: Long,
        amount: Long,
        accountId: Long,
        date: String,
        time: String,
        note: String? = null,
        newDueDate: String? = null,
        onSuccess: (() -> Unit)? = null
    ) = viewModelScope.launch {
        repository.recordDebtRepayment(debtId, amount, accountId, date, time, note, newDueDate)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
        onSuccess?.invoke()
    }

    fun updateDebtDueDate(
        debtId: Long,
        newDueDate: String,
        reason: String? = null,
        onSuccess: (() -> Unit)? = null
    ) = viewModelScope.launch {
        repository.updateDebtDueDate(debtId, newDueDate, reason)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
        onSuccess?.invoke()
    }

    fun settleDebtExcess(
        debtId: Long,
        refundAmount: Long,
        accountId: Long,
        date: String,
        time: String,
        note: String? = null,
        onSuccess: (() -> Unit)? = null
    ) = viewModelScope.launch {
        repository.settleDebtExcess(debtId, refundAmount, accountId, date, time, note)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
        onSuccess?.invoke()
    }

    fun getRepaymentsForDebt(debtId: Long): Flow<List<DebtRepaymentEntity>> {
        return repository.getRepaymentsForDebt(debtId)
    }

    // Savings Goals
    fun insertSavingsGoal(goal: SavingsGoalEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.insertSavingsGoal(goal)
        onSuccess?.invoke()
    }

    fun updateSavingsGoal(goal: SavingsGoalEntity) = viewModelScope.launch {
        repository.updateSavingsGoal(goal)
    }

    fun deleteSavingsGoal(goal: SavingsGoalEntity) = viewModelScope.launch {
        repository.deleteSavingsGoal(goal)
    }

    fun addSavingsContribution(
        goalId: Long,
        amount: Long,
        accountId: Long,
        date: String,
        note: String? = null,
        onSuccess: (() -> Unit)? = null
    ) = viewModelScope.launch {
        repository.addSavingsContribution(goalId, amount, accountId, date, note)
        onSuccess?.invoke()
    }

    fun getContributionsForGoal(goalId: Long): Flow<List<SavingsContributionEntity>> {
        return repository.getContributionsForGoal(goalId)
    }

    // Recurring Transactions
    fun insertRecurring(recurring: RecurringTransactionEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.insertRecurring(recurring)
        onSuccess?.invoke()
    }

    fun updateRecurring(recurring: RecurringTransactionEntity) = viewModelScope.launch {
        repository.updateRecurring(recurring)
    }

    fun deleteRecurring(recurring: RecurringTransactionEntity) = viewModelScope.launch {
        repository.deleteRecurring(recurring)
    }

    // Reconciliation
    fun recordReconciliation(
        accountId: Long,
        systemBalance: Long,
        actualBalance: Long,
        date: String,
        note: String? = null,
        onSuccess: (() -> Unit)? = null
    ) = viewModelScope.launch {
        repository.insertReconciliation(accountId, systemBalance, actualBalance, date, note)
        onSuccess?.invoke()
    }

    fun getReconciliationsForAccount(accountId: Long): Flow<List<AccountReconciliationEntity>> {
        return repository.getReconciliationsForAccount(accountId)
    }

    // Categories
    val allCategories: Flow<List<CategoryEntity>> = categoryRepository.allCategories

    fun getCategoriesByType(type: String): Flow<List<CategoryEntity>> {
        return categoryRepository.getCategoriesByType(type)
    }

    fun insertCategory(category: CategoryEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        categoryRepository.insertCategory(category)
        onSuccess?.invoke()
    }

    fun deleteCategory(category: CategoryEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        categoryRepository.deleteCategory(category)
        onSuccess?.invoke()
    }

    suspend fun findPotentialDuplicate(amount: Long, accountId: Long, date: String, category: String): TransactionEntity? {
        return repository.findPotentialDuplicate(amount, accountId, date, category)
    }

    suspend fun getSpendingComparison(): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance()
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
        cal.add(java.util.Calendar.MONTH, -1)
        val prevMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
        val currentExp = repository.getMonthlyExpenseAmount(currentMonth)
        val prevExp = repository.getMonthlyExpenseAmount(prevMonth)
        return Pair(currentExp, prevExp)
    }

    fun importTransactionsFromCsv(
        context: android.content.Context,
        uri: android.net.Uri,
        accountId: Long,
        onResult: (count: Int, error: String?) -> Unit
    ) = viewModelScope.launch {
        val (count, error) = com.poyshakori.app.backup.ExportManager.importTransactionsFromCsv(
            context,
            uri,
            repository,
            accountId
        )
        onResult(count, error)
    }

    // Reports / Spending
    fun getCategorySpendingForMonth(monthYear: String): Flow<List<CategorySpending>> {
        return repository.getCategorySpendingForMonth(monthYear)
    }

    fun getAllTimeCategorySpending(): Flow<List<CategorySpending>> {
        return repository.getAllTimeCategorySpending()
    }

    fun logBackupOperation(
        filename: String,
        uriString: String?,
        backupType: String,
        status: String,
        checksum: String? = null,
        notes: String? = null
    ) = viewModelScope.launch {
        repository.logBackupOperation(filename, uriString, backupType, status, checksum, notes)
    }

    // Recycle Bin & Safe Deletion
    fun recycleTransaction(transaction: TransactionEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.recycleTransaction(transaction)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
        onSuccess?.invoke()
    }

    fun recycleDebt(debt: DebtEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.recycleDebt(debt)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
        onSuccess?.invoke()
    }

    fun recycleBudget(budget: BudgetEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.recycleBudget(budget)
        onSuccess?.invoke()
    }

    fun restoreRecycledItem(item: com.poyshakori.app.data.entity.RecycleBinEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.restoreRecycledItem(item)
        AutoBackupManager.triggerDebouncedBackupOnDataChange(getApplication())
        onSuccess?.invoke()
    }

    fun deleteRecycledItemPermanently(item: com.poyshakori.app.data.entity.RecycleBinEntity, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.deleteRecycledItemPermanently(item)
        onSuccess?.invoke()
    }

    fun emptyRecycleBin(onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        repository.emptyRecycleBin()
        onSuccess?.invoke()
    }

    suspend fun getTransactionCountForAccount(accountId: Long): Int {
        return repository.getTransactionCountForAccount(accountId)
    }
}
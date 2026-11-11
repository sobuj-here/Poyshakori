package com.poyshakori.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.poyshakori.app.backup.ExportManager
import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.data.entity.BudgetEntity
import com.poyshakori.app.data.entity.DebtEntity
import com.poyshakori.app.data.entity.RecurringTransactionEntity
import com.poyshakori.app.data.entity.SavingsGoalEntity
import com.poyshakori.app.data.entity.TransactionEntity
import com.poyshakori.app.notification.DebtReminderHelper
import com.poyshakori.app.security.SecurityManager
import com.poyshakori.app.ui.accounts.AccountAdapter
import com.poyshakori.app.ui.budget.BudgetAdapter
import com.poyshakori.app.ui.dashboard.MainViewModel
import com.poyshakori.app.ui.debt.DebtAdapter
import com.poyshakori.app.ui.reports.CategoryChartView
import com.poyshakori.app.ui.settings.SettingsActivity
import com.poyshakori.app.ui.transactions.TransactionAdapter
import com.poyshakori.app.util.CategoryHelper
import com.poyshakori.app.util.CurrencyFormatter
import com.poyshakori.app.util.FinancialAnalyticsHelper
import com.poyshakori.app.util.LocaleHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Adapters
    private lateinit var recentTransactionsAdapter: TransactionAdapter
    private lateinit var allTransactionsAdapter: TransactionAdapter
    private lateinit var accountAdapter: AccountAdapter
    private lateinit var budgetAdapter: BudgetAdapter
    private lateinit var debtAdapter: DebtAdapter

    // Tab Containers
    private lateinit var viewDashboard: View
    private lateinit var viewTransactions: View
    private lateinit var viewAccounts: View
    private lateinit var viewBudgetsDebts: View
    private lateinit var viewReportsSettings: View
    private lateinit var bottomNavigationView: BottomNavigationView

    // Dashboard Views
    private lateinit var tvTotalBalance: TextView
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvTotalExpense: TextView
    private lateinit var tvDashboardEmptyState: TextView
    private lateinit var rvRecentTransactions: RecyclerView

    // Accounts Views
    private lateinit var tvAccountsTotalBalance: TextView
    private lateinit var rvAccounts: RecyclerView

    // Budgets & Debts Views
    private lateinit var subViewBudgets: View
    private lateinit var subViewDebts: View
    private lateinit var btnSubTabBudgets: Button
    private lateinit var btnSubTabDebts: Button
    private lateinit var rvBudgets: RecyclerView
    private lateinit var rvDebts: RecyclerView
    private lateinit var tvTotalDebtsSummary: TextView
    private lateinit var tvTotalReceivablesSummary: TextView

    // App Lock Overlay Views
    private lateinit var layoutMainContent: View
    private lateinit var layoutAppLockOverlay: View
    private lateinit var btnUnlockApp: MaterialButton
    private lateinit var btnUnlockWithPin: MaterialButton
    private lateinit var tvAppLockMessage: TextView
    private var isDataLoaded = false

    // Reports Views
    private lateinit var chartCategorySpending: CategoryChartView
    private lateinit var tvCategoryBreakdownSummary: TextView
    private var forecastDays = 7

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            DebtReminderHelper.checkAndNotifyDueDebts(this)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val localizedContext = LocaleHelper.applySavedLocale(newBase)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupBottomNavigation()
        setupAdapters()

        checkNotificationPermissionOnFirstLaunch()
        handleIntentExtras(intent)

        checkAppLockState(autoPrompt = true)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent?.getStringExtra("EXTRA_NAVIGATE_TO") == "DEBTS") {
            bottomNavigationView.selectedItemId = R.id.nav_budgets
            setBudgetsDebtsSubTab(false)
        }
    }

    private fun checkNotificationPermissionOnFirstLaunch() {
        DebtReminderHelper.createNotificationChannel(this)
        DebtReminderHelper.scheduleDailyReminder(this)

        val prefs = getSharedPreferences("poyshakori_prefs", Context.MODE_PRIVATE)
        val hasRequested = prefs.getBoolean("has_requested_notification_permission", false)

        if (!hasRequested) {
            prefs.edit().putBoolean("has_requested_notification_permission", true).apply()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DebtReminderHelper.checkAndNotifyDueDebts(this)
        checkAppLockState(autoPrompt = true)
    }

    override fun onBackPressed() {
        if (SecurityManager.isAppCurrentlyLocked(this)) {
            finishAffinity()
            return
        }
        super.onBackPressed()
    }

    private fun checkAppLockState(autoPrompt: Boolean = false) {
        if (SecurityManager.isAppCurrentlyLocked(this)) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
            layoutMainContent.visibility = View.GONE
            layoutAppLockOverlay.visibility = View.VISIBLE

            if (SecurityManager.hasPin(this)) {
                btnUnlockWithPin.visibility = View.VISIBLE
            } else {
                btnUnlockWithPin.visibility = View.GONE
            }

            if (autoPrompt && !SecurityManager.isPromptShowing) {
                triggerUnlock()
            }
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            layoutAppLockOverlay.visibility = View.GONE
            layoutMainContent.visibility = View.VISIBLE

            if (!isDataLoaded) {
                isDataLoaded = true
                loadAppData()
            }
        }
    }

    private fun triggerUnlock() {
        if (!SecurityManager.isDeviceLockAvailable(this) && SecurityManager.hasPin(this)) {
            showPinUnlockDialog()
            return
        }

        SecurityManager.showBiometricPrompt(
            activity = this,
            title = getString(R.string.biometric_lock),
            subtitle = getString(R.string.biometric_lock_sub),
            negativeButtonText = getString(R.string.btn_cancel),
            onSuccess = {
                runOnUiThread {
                    checkAppLockState()
                }
            },
            onError = { errorMsg ->
                runOnUiThread {
                    if (SecurityManager.hasPin(this)) {
                        btnUnlockWithPin.visibility = View.VISIBLE
                    }
                }
            }
        )
    }

    private fun showPinUnlockDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }
        val etPin = EditText(this).apply {
            hint = getString(R.string.pin_enter_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }
        layout.addView(etPin)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pin_enter_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                val entered = etPin.text.toString().trim()
                if (SecurityManager.verifyPin(this, entered)) {
                    checkAppLockState()
                } else {
                    Toast.makeText(this, getString(R.string.pin_incorrect), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun initViews() {
        layoutMainContent = findViewById(R.id.layoutMainContent)
        layoutAppLockOverlay = findViewById(R.id.layoutAppLockOverlay)
        btnUnlockApp = findViewById(R.id.btnUnlockApp)
        btnUnlockWithPin = findViewById(R.id.btnUnlockWithPin)
        tvAppLockMessage = findViewById(R.id.tvAppLockMessage)

        btnUnlockApp.setOnClickListener {
            triggerUnlock()
        }

        btnUnlockWithPin.setOnClickListener {
            showPinUnlockDialog()
        }

        viewDashboard = findViewById(R.id.viewDashboard)
        viewTransactions = findViewById(R.id.viewTransactions)
        viewAccounts = findViewById(R.id.viewAccounts)
        viewBudgetsDebts = findViewById(R.id.viewBudgetsDebts)
        viewReportsSettings = findViewById(R.id.viewReportsSettings)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)

        // Dashboard
        tvTotalBalance = findViewById(R.id.tvTotalBalance)
        tvTotalIncome = findViewById(R.id.tvTotalIncome)
        tvTotalExpense = findViewById(R.id.tvTotalExpense)
        tvDashboardEmptyState = findViewById(R.id.tvDashboardEmptyState)
        rvRecentTransactions = findViewById(R.id.rvRecentTransactions)

        // Accounts
        tvAccountsTotalBalance = findViewById(R.id.tvAccountsTotalBalance)
        rvAccounts = findViewById(R.id.rvAccounts)

        // Budgets & Debts
        subViewBudgets = findViewById(R.id.subViewBudgets)
        subViewDebts = findViewById(R.id.subViewDebts)
        btnSubTabBudgets = findViewById(R.id.btnSubTabBudgets)
        btnSubTabDebts = findViewById(R.id.btnSubTabDebts)
        rvBudgets = findViewById(R.id.rvBudgets)
        rvDebts = findViewById(R.id.rvDebts)
        tvTotalDebtsSummary = findViewById(R.id.tvTotalDebtsSummary)
        tvTotalReceivablesSummary = findViewById(R.id.tvTotalReceivablesSummary)

        // Reports Views
        chartCategorySpending = findViewById(R.id.chartCategorySpending)
        tvCategoryBreakdownSummary = findViewById(R.id.tvCategoryBreakdownSummary)

        // Settings Header Navigation
        findViewById<View>(R.id.btnSettingsHeader)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickAddHeader).setOnClickListener {
            showAddTransactionDialog()
        }

        findViewById<View>(R.id.btnQuickAddExpense).setOnClickListener {
            showAddTransactionDialog(defaultType = "EXPENSE")
        }
        findViewById<View>(R.id.btnQuickAddIncome).setOnClickListener {
            showAddTransactionDialog(defaultType = "INCOME")
        }
        findViewById<View>(R.id.btnQuickAddTransfer).setOnClickListener {
            showAddTransactionDialog(defaultType = "TRANSFER")
        }
        findViewById<View>(R.id.tvSeeAllTransactions).setOnClickListener {
            bottomNavigationView.selectedItemId = R.id.nav_transactions
        }

        findViewById<View>(R.id.btnAddAccount).setOnClickListener {
            showAddAccountDialog()
        }

        btnSubTabBudgets.setOnClickListener {
            setBudgetsDebtsSubTab(isBudgets = true)
        }
        btnSubTabDebts.setOnClickListener {
            setBudgetsDebtsSubTab(isBudgets = false)
        }
        findViewById<View>(R.id.btnAddBudget).setOnClickListener {
            showSetBudgetDialog()
        }
        findViewById<View>(R.id.btnAddDebt).setOnClickListener {
            showAddDebtDialog()
        }

        // Cash Flow Forecast Toggle Buttons
        val btnForecast7 = findViewById<MaterialButton>(R.id.btnForecast7Days)
        val btnForecast30 = findViewById<MaterialButton>(R.id.btnForecast30Days)
        btnForecast7?.setOnClickListener {
            forecastDays = 7
            btnForecast7.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            btnForecast7.setTextColor(ContextCompat.getColor(this, R.color.black))
            btnForecast30?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_variant))
            btnForecast30?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            updateCashFlowForecast()
        }
        btnForecast30?.setOnClickListener {
            forecastDays = 30
            btnForecast30.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            btnForecast30.setTextColor(ContextCompat.getColor(this, R.color.black))
            btnForecast7?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_variant))
            btnForecast7?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            updateCashFlowForecast()
        }

        // Reports & Data Export Actions
        findViewById<View>(R.id.btnExportCsv)?.setOnClickListener {
            exportCsv()
        }
        findViewById<View>(R.id.btnExportFinancialReport)?.setOnClickListener {
            exportFinancialReport()
        }

        // Financial Tools Actions
        findViewById<View>(R.id.btnMonthlySummaries).setOnClickListener {
            showMonthlySummariesDialog()
        }
        findViewById<View>(R.id.btnRecurringTransactions).setOnClickListener {
            showRecurringTransactionsDialog()
        }
        findViewById<View>(R.id.btnSavingsGoals).setOnClickListener {
            showSavingsGoalsDialog()
        }
        findViewById<View>(R.id.btnReconciliation).setOnClickListener {
            showAccountReconciliationDialog()
        }

        setupTransactionsTab()
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener { item ->
            viewDashboard.visibility = View.GONE
            viewTransactions.visibility = View.GONE
            viewAccounts.visibility = View.GONE
            viewBudgetsDebts.visibility = View.GONE
            viewReportsSettings.visibility = View.GONE

            when (item.itemId) {
                R.id.nav_dashboard -> {
                    viewDashboard.visibility = View.VISIBLE
                    true
                }
                R.id.nav_transactions -> {
                    viewTransactions.visibility = View.VISIBLE
                    true
                }
                R.id.nav_accounts -> {
                    viewAccounts.visibility = View.VISIBLE
                    true
                }
                R.id.nav_budgets -> {
                    viewBudgetsDebts.visibility = View.VISIBLE
                    true
                }
                R.id.nav_reports -> {
                    viewReportsSettings.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun setupAdapters() {
        recentTransactionsAdapter = TransactionAdapter(
            onItemClick = { tx -> showTransactionDetailsDialog(tx) },
            onItemLongClick = { tx -> confirmDeleteTransaction(tx) }
        )
        rvRecentTransactions.layoutManager = LinearLayoutManager(this)
        rvRecentTransactions.adapter = recentTransactionsAdapter

        allTransactionsAdapter = TransactionAdapter(
            onItemClick = { tx -> showTransactionDetailsDialog(tx) },
            onItemLongClick = { tx -> confirmDeleteTransaction(tx) }
        )
        val rvAllTransactions = findViewById<RecyclerView>(R.id.rvAllTransactions)
        rvAllTransactions.layoutManager = LinearLayoutManager(this)
        rvAllTransactions.adapter = allTransactionsAdapter

        accountAdapter = AccountAdapter(
            onItemClick = { account -> showAccountDetailsDialog(account) },
            onItemLongClick = { account -> confirmDeleteAccount(account) }
        )
        rvAccounts.layoutManager = LinearLayoutManager(this)
        rvAccounts.adapter = accountAdapter

        budgetAdapter = BudgetAdapter(
            onItemLongClick = { budget -> confirmDeleteBudget(budget) }
        )
        rvBudgets.layoutManager = LinearLayoutManager(this)
        rvBudgets.adapter = budgetAdapter

        debtAdapter = DebtAdapter(
            onItemClick = { debt -> showDebtDetailsDialog(debt) },
            onRecordRepaymentClick = { debt -> showRecordRepaymentDialog(debt) },
            onRefundExcessClick = { debt -> showRefundExcessDialog(debt) },
            onItemLongClick = { debt -> confirmDeleteDebt(debt) }
        )
        rvDebts.layoutManager = LinearLayoutManager(this)
        rvDebts.adapter = debtAdapter
    }

    private fun setupTransactionsTab() {
        val etSearch = findViewById<EditText>(R.id.etSearchTransactions)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val btnAll = findViewById<Button>(R.id.btnFilterAll)
        val btnExpense = findViewById<Button>(R.id.btnFilterExpense)
        val btnIncome = findViewById<Button>(R.id.btnFilterIncome)
        val btnTransfer = findViewById<Button>(R.id.btnFilterTransfer)
        val btnDebtLoan = findViewById<Button>(R.id.btnFilterDebtLoan)

        val filterButtons = listOf(btnAll, btnExpense, btnIncome, btnTransfer, btnDebtLoan)

        fun updateFilterUi(selectedIndex: Int) {
            for (i in filterButtons.indices) {
                if (i == selectedIndex) {
                    filterButtons[i].backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
                    filterButtons[i].setTextColor(ContextCompat.getColor(this, R.color.black))
                } else {
                    filterButtons[i].backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_variant))
                    filterButtons[i].setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
            }
        }

        btnAll.setOnClickListener {
            viewModel.setTypeFilter("ALL")
            updateFilterUi(0)
        }
        btnExpense.setOnClickListener {
            viewModel.setTypeFilter("EXPENSE")
            updateFilterUi(1)
        }
        btnIncome.setOnClickListener {
            viewModel.setTypeFilter("INCOME")
            updateFilterUi(2)
        }
        btnTransfer.setOnClickListener {
            viewModel.setTypeFilter("TRANSFER")
            updateFilterUi(3)
        }
        btnDebtLoan.setOnClickListener {
            viewModel.setTypeFilter("DEBT_LOAN")
            updateFilterUi(4)
        }

        val btnFilterDateRange = findViewById<MaterialButton>(R.id.btnFilterDateRange)
        val btnFilterAccount = findViewById<MaterialButton>(R.id.btnFilterAccount)
        val btnSortTransactions = findViewById<MaterialButton>(R.id.btnSortTransactions)
        val btnClearFilters = findViewById<MaterialButton>(R.id.btnClearFilters)

        btnFilterDateRange?.setOnClickListener {
            showDateRangeFilterDialog(btnFilterDateRange, btnClearFilters)
        }

        btnFilterAccount?.setOnClickListener {
            showAccountFilterDialog(btnFilterAccount, btnClearFilters)
        }

        btnSortTransactions?.setOnClickListener {
            showSortTransactionsDialog(btnSortTransactions)
        }

        btnClearFilters?.setOnClickListener {
            viewModel.clearFilters()
            etSearch.setText("")
            updateFilterUi(0)
            btnFilterDateRange.text = getString(R.string.filter_date_range)
            btnFilterAccount.text = getString(R.string.filter_account)
            btnSortTransactions.text = getString(R.string.filter_sort)
            btnClearFilters.visibility = View.GONE
        }
    }

    private fun showDateRangeFilterDialog(btnDateRange: MaterialButton, btnClearFilters: MaterialButton) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        val etStart = EditText(this).apply {
            hint = getString(R.string.start_date)
            isFocusable = false
        }
        etStart.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                cal.set(year, month, day)
                etStart.setText(sdf.format(cal.time))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val etEnd = EditText(this).apply {
            hint = getString(R.string.end_date)
            isFocusable = false
        }
        etEnd.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                cal.set(year, month, day)
                etEnd.setText(sdf.format(cal.time))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        layout.addView(etStart)
        layout.addView(etEnd)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_date_range))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_ok)) { _, _ ->
                val start = etStart.text.toString().trim().ifBlank { null }
                val end = etEnd.text.toString().trim().ifBlank { null }
                viewModel.setDateRangeFilter(start, end)
                if (start != null || end != null) {
                    btnDateRange.text = "${start ?: "..."} - ${end ?: "..."}"
                    btnClearFilters.visibility = View.VISIBLE
                } else {
                    btnDateRange.text = getString(R.string.filter_date_range)
                }
            }
            .setNeutralButton(getString(R.string.clear_all_filters)) { _, _ ->
                viewModel.setDateRangeFilter(null, null)
                btnDateRange.text = getString(R.string.filter_date_range)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAccountFilterDialog(btnAccount: MaterialButton, btnClearFilters: MaterialButton) {
        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
            val names = mutableListOf(getString(R.string.all_accounts))
            names.addAll(accounts.map { it.name })

            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.filter_account))
                .setItems(names.toTypedArray()) { _, which ->
                    if (which == 0) {
                        viewModel.setAccountFilter(null)
                        btnAccount.text = getString(R.string.filter_account)
                    } else {
                        val acc = accounts[which - 1]
                        viewModel.setAccountFilter(acc.id)
                        btnAccount.text = acc.name
                        btnClearFilters.visibility = View.VISIBLE
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    private fun showSortTransactionsDialog(btnSort: MaterialButton) {
        val options = arrayOf(
            getString(R.string.sort_newest),
            getString(R.string.sort_oldest),
            getString(R.string.sort_highest),
            getString(R.string.sort_lowest)
        )
        val keys = arrayOf("NEWEST", "OLDEST", "HIGHEST", "LOWEST")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_sort))
            .setItems(options) { _, which ->
                viewModel.setSortBy(keys[which])
                btnSort.text = options[which]
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun setBudgetsDebtsSubTab(isBudgets: Boolean) {
        if (isBudgets) {
            subViewBudgets.visibility = View.VISIBLE
            subViewDebts.visibility = View.GONE
            btnSubTabBudgets.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            btnSubTabBudgets.setTextColor(ContextCompat.getColor(this, R.color.black))
            btnSubTabDebts.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_variant))
            btnSubTabDebts.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            subViewBudgets.visibility = View.GONE
            subViewDebts.visibility = View.VISIBLE
            btnSubTabDebts.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            btnSubTabDebts.setTextColor(ContextCompat.getColor(this, R.color.black))
            btnSubTabBudgets.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_variant))
            btnSubTabBudgets.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun loadAppData() {
        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        // Observe Accounts
        lifecycleScope.launch {
            viewModel.allAccounts.collectLatest { accounts ->
                accountAdapter.submitList(accounts)
                recentTransactionsAdapter.setAccounts(accounts)
                allTransactionsAdapter.setAccounts(accounts)
                val totalInAccounts = accounts.sumOf { it.currentBalance }
                tvAccountsTotalBalance.text = CurrencyFormatter.formatAmount(this@MainActivity, totalInAccounts)
            }
        }

        // Observe Total Net Balance
        lifecycleScope.launch {
            viewModel.totalBalance.collectLatest { balance ->
                val safeBalance = balance ?: 0.0
                tvTotalBalance.text = CurrencyFormatter.formatAmount(this@MainActivity, safeBalance)
            }
        }

        // Observe Period-Specific (Current Month) Income & Expense for Dashboard
        lifecycleScope.launch {
            viewModel.currentMonthIncome.collectLatest { income ->
                tvTotalIncome.text = CurrencyFormatter.formatAmount(this@MainActivity, income ?: 0L)
            }
        }
        lifecycleScope.launch {
            viewModel.currentMonthExpense.collectLatest { expense ->
                tvTotalExpense.text = CurrencyFormatter.formatAmount(this@MainActivity, expense ?: 0L)
            }
        }

        // Observe Recent Transactions (Dashboard)
        lifecycleScope.launch {
            viewModel.recentTransactions.collectLatest { transactions ->
                recentTransactionsAdapter.submitList(transactions)
                tvDashboardEmptyState.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // Observe Filtered Transactions (Transactions tab)
        val tvTransactionsEmptyState = findViewById<TextView>(R.id.tvTransactionsEmptyState)
        lifecycleScope.launch {
            viewModel.filteredTransactions.collectLatest { transactions ->
                allTransactionsAdapter.submitList(transactions)
                tvTransactionsEmptyState.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // Observe Budgets
        lifecycleScope.launch {
            viewModel.getBudgetsForMonth(currentMonthYear).collectLatest { budgets ->
                budgetAdapter.submitList(budgets)
                val spentMap = mutableMapOf<String, Long>()
                for (budget in budgets) {
                    spentMap[budget.category] = viewModel.getSpentAmountForCategory(budget.category, currentMonthYear)
                }
                budgetAdapter.setSpentAmounts(spentMap)
            }
        }

        // Observe Debts & Receivables (Settled 0-balance debts are filtered out and archived in Transactions)
        lifecycleScope.launch {
            viewModel.allDebts.collectLatest { debts ->
                val activeDebts = debts.filter { it.amount != it.paidAmount }
                debtAdapter.submitList(activeDebts)
                findViewById<TextView>(R.id.tvEmptyDebts)?.visibility = if (activeDebts.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.totalPendingDebts.collectLatest { debt ->
                tvTotalDebtsSummary.text = CurrencyFormatter.formatAmount(this@MainActivity, debt ?: 0L)
            }
        }
        lifecycleScope.launch {
            viewModel.totalPendingReceivables.collectLatest { receivable ->
                tvTotalReceivablesSummary.text = CurrencyFormatter.formatAmount(this@MainActivity, receivable ?: 0L)
            }
        }

        // Observe Financial Overview for Reports
        lifecycleScope.launch {
            viewModel.totalBalance.collectLatest { balance ->
                findViewById<TextView>(R.id.tvReportsNetBalance)?.text =
                    CurrencyFormatter.formatAmount(this@MainActivity, balance ?: 0.0)
            }
        }
        lifecycleScope.launch {
            combine(
                viewModel.currentMonthIncome,
                viewModel.currentMonthExpense
            ) { income, expense ->
                Pair(income ?: 0L, expense ?: 0L)
            }.collectLatest { (income, expense) ->
                findViewById<TextView>(R.id.tvReportsMonthIncome)?.text =
                    "+${CurrencyFormatter.formatAmount(this@MainActivity, income)}"
                findViewById<TextView>(R.id.tvReportsMonthExpense)?.text =
                    "-${CurrencyFormatter.formatAmount(this@MainActivity, expense)}"
                val net = income - expense
                val netTv = findViewById<TextView>(R.id.tvReportsMonthNet)
                netTv?.text = CurrencyFormatter.formatAmount(this@MainActivity, net)
                if (net >= 0) {
                    netTv?.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.income))
                } else {
                    netTv?.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.expense))
                }
            }
        }
        lifecycleScope.launch {
            combine(
                viewModel.totalPendingReceivables,
                viewModel.totalPendingDebts
            ) { lent, debt ->
                Pair(lent ?: 0L, debt ?: 0L)
            }.collectLatest { (lent, debt) ->
                val lentStr = CurrencyFormatter.formatAmount(this@MainActivity, lent)
                val debtStr = CurrencyFormatter.formatAmount(this@MainActivity, debt)
                findViewById<TextView>(R.id.tvReportsLentBorrowed)?.text =
                    "${getString(R.string.debt_type_owed_to_me)}: $lentStr • ${getString(R.string.debt_type_owe)}: $debtStr"
            }
        }

        // Observe Spending Breakdown for Reports
        lifecycleScope.launch {
            viewModel.getCategorySpendingForMonth(currentMonthYear).collectLatest { spendingList ->
                chartCategorySpending.setData(spendingList)
                val tvReportsTotalSpending = findViewById<TextView>(R.id.tvReportsTotalSpending)
                if (spendingList.isEmpty()) {
                    tvCategoryBreakdownSummary.text = getString(R.string.no_transactions_found)
                    tvReportsTotalSpending?.text = getString(R.string.total_monthly_spending, CurrencyFormatter.formatAmount(this@MainActivity, 0L))
                } else {
                    val total = spendingList.sumOf { it.totalAmount }
                    tvReportsTotalSpending?.text = getString(R.string.total_monthly_spending, CurrencyFormatter.formatAmount(this@MainActivity, total))
                    val summary = StringBuilder(getString(R.string.spending_breakdown) + " ($currentMonthYear):\n\n")
                    for (item in spendingList) {
                        val pct = if (total > 0) ((item.totalAmount.toDouble() / total.toDouble()) * 100).toInt() else 0
                        val catDisplay = CategoryHelper.getDisplayName(this@MainActivity, item.category)
                        val formattedAmt = CurrencyFormatter.formatAmount(this@MainActivity, item.totalAmount)
                        val formattedPct = CurrencyFormatter.formatPercentage(this@MainActivity, pct)
                        summary.append("• $catDisplay: $formattedAmt ($formattedPct)\n")
                    }
                    tvCategoryBreakdownSummary.text = summary.toString().trimEnd()
                }
            }
        }

        // Observe Spending Insights & Cash Flow Forecast
        lifecycleScope.launch {
            combine(
                viewModel.allTransactions,
                viewModel.allBudgets,
                viewModel.allAccounts,
                viewModel.allRecurring
            ) { txs: List<TransactionEntity>, budgets: List<BudgetEntity>, accounts: List<AccountEntity>, recurring: List<RecurringTransactionEntity> ->
                val insights = FinancialAnalyticsHelper.calculateSpendingInsights(currentMonthYear, txs, budgets)
                val forecast = FinancialAnalyticsHelper.calculateCashFlowForecast(accounts, recurring, forecastDays)
                Pair(insights, forecast)
            }.collectLatest { (insights, forecast) ->
                renderSpendingInsights(insights)
                renderCashFlowForecast(forecast)
            }
        }
    }

    private fun renderSpendingInsights(insights: FinancialAnalyticsHelper.SpendingInsights) {
        val tvSpendingChangeSummary = findViewById<TextView>(R.id.tvSpendingChangeSummary) ?: return
        val tvSpendingBudgetAlerts = findViewById<TextView>(R.id.tvSpendingBudgetAlerts) ?: return

        if (!insights.hasPreviousData) {
            tvSpendingChangeSummary.text = getString(R.string.spending_no_data)
            tvSpendingChangeSummary.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            val diff = Math.abs(insights.currentMonthSpent - insights.previousMonthSpent)
            val diffFormatted = CurrencyFormatter.formatAmount(this, diff)
            val pctFormatted = CurrencyFormatter.formatNumber(this, Math.abs(insights.percentageChange).toLong())
            when {
                insights.percentageChange > 0 -> {
                    tvSpendingChangeSummary.text = getString(R.string.spending_increased, diffFormatted, pctFormatted)
                    tvSpendingChangeSummary.setTextColor(ContextCompat.getColor(this, R.color.expense))
                }
                insights.percentageChange < 0 -> {
                    tvSpendingChangeSummary.text = getString(R.string.spending_decreased, diffFormatted, pctFormatted)
                    tvSpendingChangeSummary.setTextColor(ContextCompat.getColor(this, R.color.income))
                }
                else -> {
                    tvSpendingChangeSummary.text = getString(R.string.spending_same)
                    tvSpendingChangeSummary.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                }
            }
        }

        val alertsSb = StringBuilder()
        if (insights.overBudgetCategories.isNotEmpty()) {
            val overNames = insights.overBudgetCategories.joinToString(", ") { CategoryHelper.getDisplayName(this, it) }
            alertsSb.append("⚠️ Over budget: $overNames\n")
        }
        if (insights.nearBudgetCategories.isNotEmpty()) {
            val nearNames = insights.nearBudgetCategories.joinToString(", ") { CategoryHelper.getDisplayName(this, it) }
            alertsSb.append("⚡ Near budget limit (>80%): $nearNames\n")
        }
        if (insights.topSpendingCategory != null && insights.topCategoryAmount > 0) {
            val catName = CategoryHelper.getDisplayName(this, insights.topSpendingCategory)
            val catAmt = CurrencyFormatter.formatAmount(this, insights.topCategoryAmount)
            alertsSb.append("📊 Top spending: $catName ($catAmt)")
        }
        tvSpendingBudgetAlerts.text = alertsSb.toString().trim()
        tvSpendingBudgetAlerts.visibility = if (alertsSb.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun renderCashFlowForecast(forecast: FinancialAnalyticsHelper.CashFlowForecast) {
        val tvProjectedBalance = findViewById<TextView>(R.id.tvForecastProjectedBalance) ?: return
        val tvProjectedIncome = findViewById<TextView>(R.id.tvForecastProjectedIncome) ?: return
        val tvProjectedExpense = findViewById<TextView>(R.id.tvForecastProjectedExpense) ?: return
        val tvScheduledCount = findViewById<TextView>(R.id.tvForecastScheduledCount) ?: return

        tvProjectedBalance.text = CurrencyFormatter.formatAmount(this, forecast.projectedBalance)
        tvProjectedIncome.text = "+${CurrencyFormatter.formatAmount(this, forecast.expectedIncome)}"
        tvProjectedExpense.text = "-${CurrencyFormatter.formatAmount(this, forecast.expectedExpense)}"
        tvScheduledCount.text = "${forecast.scheduledItemsCount} items"
    }

    private fun updateCashFlowForecast() {
        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
            val recurring = viewModel.allRecurring.firstOrNull() ?: emptyList()
            val forecast = FinancialAnalyticsHelper.calculateCashFlowForecast(accounts, recurring, forecastDays)
            renderCashFlowForecast(forecast)
        }
    }

    private fun showAddTransactionDialog(defaultType: String = "EXPENSE") {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_transaction, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgTransactionType)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinnerAccount = dialogView.findViewById<Spinner>(R.id.spinnerAccount)
        val spinnerTargetAccount = dialogView.findViewById<Spinner>(R.id.spinnerTargetAccount)
        val layoutTargetAccount = dialogView.findViewById<View>(R.id.layoutTargetAccount)
        val etServiceCharge = dialogView.findViewById<EditText>(R.id.etServiceCharge)
        val etDate = dialogView.findViewById<EditText>(R.id.etDate)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveTransaction)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        etDate.setText(dateFormat.format(calendar.time))
        etDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    etDate.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
            val accountNames = accounts.map { it.name }
            val accountAdapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, accountNames)
            accountAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerAccount.adapter = accountAdapter
            val targetAdapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, accountNames)
            targetAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerTargetAccount.adapter = targetAdapter
            if (accounts.size > 1) {
                spinnerTargetAccount.setSelection(1)
            }
        }

        fun loadCategoriesForType(type: String) {
            lifecycleScope.launch {
                val categories = viewModel.getCategoriesByType(type).firstOrNull() ?: emptyList()
                val distinctNames = if (type == "TRANSFER") {
                    listOf("Transfer")
                } else {
                    val defaultList = if (type == "INCOME") {
                        listOf("Salary", "Business", "Freelance", "Investment", "Rental Income", "Gift", "Other")
                    } else {
                        listOf("Food & Dining", "Groceries", "Shopping", "Transportation", "Rent & Housing", "Bills & Utilities", "Healthcare", "Education", "Entertainment", "Family", "Travel", "Personal Care", "Gifts & Donations", "Other")
                    }
                    (defaultList + categories.map { it.name }).distinct()
                }
                val displayNames = distinctNames.map { CategoryHelper.getDisplayName(this@MainActivity, it) }
                val catAdapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, displayNames)
                catAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                spinnerCategory.adapter = catAdapter
            }
        }

        when (defaultType) {
            "INCOME" -> {
                rgType.check(R.id.rbIncome)
                layoutTargetAccount.visibility = View.GONE
                loadCategoriesForType("INCOME")
            }
            "TRANSFER" -> {
                rgType.check(R.id.rbTransfer)
                layoutTargetAccount.visibility = View.VISIBLE
                loadCategoriesForType("TRANSFER")
            }
            else -> {
                rgType.check(R.id.rbExpense)
                layoutTargetAccount.visibility = View.GONE
                loadCategoriesForType("EXPENSE")
            }
        }

        rgType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbTransfer -> {
                    layoutTargetAccount.visibility = View.VISIBLE
                    loadCategoriesForType("TRANSFER")
                }
                R.id.rbIncome -> {
                    layoutTargetAccount.visibility = View.GONE
                    loadCategoriesForType("INCOME")
                }
                else -> {
                    layoutTargetAccount.visibility = View.GONE
                    loadCategoriesForType("EXPENSE")
                }
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString().trim()
            val amount = amountStr.toLongOrNull()

            if (amount == null || amount <= 0) {
                etAmount.error = getString(R.string.valid_amount_error)
                return@setOnClickListener
            }

            val checkedId = rgType.checkedRadioButtonId
            val type = when (checkedId) {
                R.id.rbIncome -> "INCOME"
                R.id.rbTransfer -> "TRANSFER"
                else -> "EXPENSE"
            }

            val selectedCategoryDisplay = spinnerCategory.selectedItem?.toString() ?: if (type == "TRANSFER") "Transfer" else "Other"
            val category = CategoryHelper.getCanonicalName(selectedCategoryDisplay)
            val selectedDate = etDate.text.toString().trim()
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val note = etNote.text.toString().trim()

            lifecycleScope.launch {
                val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
                val selectedAccountIdx = spinnerAccount.selectedItemPosition
                val sourceAccount = accounts.getOrNull(selectedAccountIdx)

                if (sourceAccount == null) {
                    Toast.makeText(this@MainActivity, getString(R.string.valid_account_error), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                var targetAccountId: Long? = null
                if (type == "TRANSFER") {
                    val targetAccountIdx = spinnerTargetAccount.selectedItemPosition
                    val targetAccount = accounts.getOrNull(targetAccountIdx)
                    if (targetAccount == null || targetAccount.id == sourceAccount.id) {
                        Toast.makeText(this@MainActivity, getString(R.string.different_accounts_error), Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    targetAccountId = targetAccount.id
                }

                val serviceCharge = if (type == "TRANSFER") {
                    CurrencyFormatter.parseAmountDouble(etServiceCharge?.text?.toString()?.trim() ?: "") ?: 0.0
                } else 0.0

                val transaction = TransactionEntity(
                    type = type,
                    amount = amount,
                    serviceCharge = serviceCharge,
                    accountId = sourceAccount.id,
                    targetAccountId = targetAccountId,
                    category = category,
                    paymentMethod = sourceAccount.name,
                    date = selectedDate,
                    time = currentTime,
                    note = if (note.isEmpty()) null else note
                )

                val duplicate = viewModel.findPotentialDuplicate(amount, sourceAccount.id, selectedDate, category)
                if (duplicate != null) {
                    val formattedAmt = CurrencyFormatter.formatAmount(this@MainActivity, amount)
                    val formattedDate = CurrencyFormatter.formatDate(this@MainActivity, selectedDate)
                    val localizedCat = CategoryHelper.getDisplayName(this@MainActivity, category)
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.duplicate_transaction_title))
                        .setMessage(getString(R.string.duplicate_transaction_warning, formattedAmt, localizedCat, formattedDate))
                        .setPositiveButton(getString(R.string.btn_save_anyway)) { _, _ ->
                            viewModel.insertTransaction(transaction) {
                                Toast.makeText(this@MainActivity, getString(R.string.transaction_saved), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        }
                        .setNegativeButton(getString(R.string.btn_cancel), null)
                        .show()
                } else {
                    viewModel.insertTransaction(transaction) {
                        Toast.makeText(this@MainActivity, getString(R.string.transaction_saved), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showTransactionDetailsDialog(transaction: TransactionEntity) {
        val localizedCat = CategoryHelper.getDisplayName(this, transaction.category)
        val formattedAmt = CurrencyFormatter.formatAmount(this, transaction.amount)
        val formattedDate = CurrencyFormatter.formatDate(this, transaction.date)

        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
            val accountName = accounts.find { it.id == transaction.accountId }?.name ?: transaction.paymentMethod

            var details = "${getString(R.string.category_label)}: $localizedCat\n" +
                    "${getString(R.string.amount_bdt)}: $formattedAmt\n"
            if (transaction.type == "TRANSFER" && transaction.serviceCharge > 0.0) {
                val feeStr = CurrencyFormatter.formatAmount(this@MainActivity, transaction.serviceCharge)
                val totalWithFee = CurrencyFormatter.formatAmount(this@MainActivity, transaction.amount.toDouble() + transaction.serviceCharge)
                details += "${getString(R.string.service_charge_display, feeStr)}\n"
                details += "${getString(R.string.total_transferred_with_fee, totalWithFee)}\n"
            }
            details += "${getString(R.string.filter_account)}: $accountName\n" +
                    "${getString(R.string.date_label)}: $formattedDate ${transaction.time}\n" +
                    "${getString(R.string.note_optional)}: ${transaction.note ?: "—"}"

            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.transaction_details, transaction.type))
                .setMessage(details)
                .setPositiveButton(getString(R.string.btn_ok), null)
                .setNegativeButton(getString(R.string.btn_delete)) { _, _ ->
                    confirmDeleteTransaction(transaction)
                }
                .show()
        }
    }

    private fun confirmDeleteTransaction(transaction: TransactionEntity) {
        val formattedAmt = CurrencyFormatter.formatAmount(this, transaction.amount)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_transaction))
            .setMessage(getString(R.string.move_to_trash_confirm, "${transaction.type} (${formattedAmt})"))
            .setPositiveButton(getString(R.string.move_to_trash)) { _, _ ->
                viewModel.recycleTransaction(transaction) {
                    Toast.makeText(this, getString(R.string.item_moved_to_trash), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAddAccountDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_account, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val etName = dialogView.findViewById<EditText>(R.id.etAccountName)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerAccountType)
        val etBalance = dialogView.findViewById<EditText>(R.id.etInitialBalance)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveAccount)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelAccount)

        val types = listOf("Cash", "Bank", "Mobile Wallet", "Savings", "Credit Card")
        val typeAdapter = ArrayAdapter(this, R.layout.spinner_item, types)
        typeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerType.adapter = typeAdapter

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val balanceStr = etBalance.text.toString().trim()
            val balance = balanceStr.toLongOrNull() ?: 0L
            val type = spinnerType.selectedItem?.toString() ?: "General"

            if (name.isEmpty()) {
                etName.error = getString(R.string.account_name_required)
                return@setOnClickListener
            }

            val account = AccountEntity(
                name = name,
                type = type,
                openingBalance = balance.toDouble(),
                currentBalance = balance.toDouble()
            )

            viewModel.insertAccount(account) {
                Toast.makeText(this, getString(R.string.account_created, name), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAccountDetailsDialog(account: AccountEntity) {
        val formattedBal = CurrencyFormatter.formatAmount(this, account.currentBalance)
        val formattedOpening = CurrencyFormatter.formatAmount(this, account.openingBalance)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val tvInfo = TextView(this).apply {
            text = "${getString(R.string.account_details)}:\n\n" +
                    "${getString(R.string.repayment_account)}: ${account.name}\n" +
                    "Type: ${account.type}\n" +
                    "Opening Balance: $formattedOpening\n" +
                    "${getString(R.string.total_net_balance)}: $formattedBal\n\n" +
                    getString(R.string.edit_account_balance_locked_notice)
            setPadding(0, 0, 0, 16)
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
        }
        layout.addView(tvInfo)

        AlertDialog.Builder(this)
            .setTitle(account.name)
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_ok), null)
            .setNeutralButton(getString(R.string.edit_account_name_type)) { _, _ ->
                showEditAccountInfoDialog(account)
            }
            .setNegativeButton(getString(R.string.btn_delete)) { _, _ ->
                confirmDeleteAccount(account)
            }
            .show()
    }

    private fun showEditAccountInfoDialog(account: AccountEntity) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etName = EditText(this).apply {
            setText(account.name)
            hint = getString(R.string.repayment_account)
        }

        val spinnerType = Spinner(this).apply {
            val types = listOf("Cash", "Bank", "Mobile Wallet", "Savings", "Credit Card", "Other")
            val adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, types)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            this.adapter = adapter
            val pos = types.indexOf(account.type).coerceAtLeast(0)
            setSelection(pos)
        }

        layout.addView(etName)
        layout.addView(spinnerType)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_account_name_type))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, getString(R.string.account_name_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newType = spinnerType.selectedItem?.toString() ?: account.type
                val updated = account.copy(name = newName, type = newType, updatedAt = System.currentTimeMillis())
                viewModel.updateAccount(updated)
                Toast.makeText(this, getString(R.string.account_updated), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun confirmDeleteAccount(account: AccountEntity) {
        lifecycleScope.launch {
            if (Math.abs(account.currentBalance) > 0.001) {
                val formattedBal = CurrencyFormatter.formatAmount(this@MainActivity, account.currentBalance)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.delete_account))
                    .setMessage(getString(R.string.cannot_delete_account_balance, account.name, formattedBal))
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .show()
                return@launch
            }

            val txCount = viewModel.getTransactionCountForAccount(account.id)
            if (txCount > 0) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.delete_account))
                    .setMessage(getString(R.string.cannot_delete_account_transactions, account.name, txCount))
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .show()
                return@launch
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.delete_account))
                .setMessage(getString(R.string.delete_account_confirm, account.name))
                .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                    viewModel.deleteAccountSafe(account) { hardDeleted ->
                        if (hardDeleted) {
                            Toast.makeText(this@MainActivity, getString(R.string.account_removed), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.account_archived_notice), Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    private fun showSetBudgetDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_budget, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerBudgetCategory)
        val etLimit = dialogView.findViewById<EditText>(R.id.etMonthlyLimit)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveBudget)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelBudget)

        lifecycleScope.launch {
            val categories = viewModel.getCategoriesByType("EXPENSE").firstOrNull() ?: emptyList()
            val distinctNames = categories.map { it.name }.distinct()
            val displayNames = distinctNames.map { CategoryHelper.getDisplayName(this@MainActivity, it) }
            val budgetCatAdapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, displayNames)
            budgetCatAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerCategory.adapter = budgetCatAdapter
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val selectedCategoryDisplay = spinnerCategory.selectedItem?.toString() ?: "Other"
            val category = CategoryHelper.getCanonicalName(selectedCategoryDisplay)
            val limitStr = etLimit.text.toString().trim()
            val limit = limitStr.toLongOrNull()

            if (limit == null || limit <= 0) {
                etLimit.error = getString(R.string.valid_budget_error)
                return@setOnClickListener
            }

            val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            val budget = BudgetEntity(
                category = category,
                monthlyLimit = limit,
                monthYear = currentMonthYear,
                rollover = true
            )

            viewModel.insertBudget(budget) {
                Toast.makeText(this, getString(R.string.budget_saved, selectedCategoryDisplay), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun confirmDeleteBudget(budget: BudgetEntity) {
        val catName = CategoryHelper.getDisplayName(this, budget.category)
        val formattedLimit = CurrencyFormatter.formatAmount(this, budget.monthlyLimit)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_budget))
            .setMessage(getString(R.string.move_to_trash_confirm, "$catName ($formattedLimit)"))
            .setPositiveButton(getString(R.string.move_to_trash)) { _, _ ->
                viewModel.recycleBudget(budget) {
                    Toast.makeText(this, getString(R.string.item_moved_to_trash), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAddDebtDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_debt, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgDebtType)
        val etPerson = dialogView.findViewById<EditText>(R.id.etPersonName)
        val etAmount = dialogView.findViewById<EditText>(R.id.etDebtAmount)
        val spinnerAccount = dialogView.findViewById<Spinner>(R.id.spinnerDebtAccount)
        val etDate = dialogView.findViewById<EditText>(R.id.etDebtDate)
        val etDue = dialogView.findViewById<EditText>(R.id.etDebtDueDate)
        val etRef = dialogView.findViewById<EditText>(R.id.etDebtReference)
        val etNote = dialogView.findViewById<EditText>(R.id.etDebtNote)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveDebt)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDebt)

        var loadedAccounts: List<AccountEntity> = viewModel.allAccounts.value

        fun populateAccountSpinner(accounts: List<AccountEntity>) {
            loadedAccounts = accounts
            val names = accounts.map { "${it.name} (${CurrencyFormatter.formatAmount(this@MainActivity, it.currentBalance)})" }
            val adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, names)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerAccount.adapter = adapter
        }

        if (loadedAccounts.isNotEmpty()) {
            populateAccountSpinner(loadedAccounts)
        }

        lifecycleScope.launch {
            viewModel.allAccounts.collect { accounts ->
                if (accounts.isNotEmpty()) {
                    populateAccountSpinner(accounts)
                }
            }
        }

        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        etDate.setText(dateFormat.format(Date()))

        etDate.setOnClickListener {
            val dateCal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    dateCal.set(year, month, dayOfMonth)
                    etDate.setText(dateFormat.format(dateCal.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        etDue.setOnClickListener {
            val dueCal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    dueCal.set(year, month, dayOfMonth)
                    etDue.setText(dateFormat.format(dueCal.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val person = etPerson.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()
            val amount = CurrencyFormatter.parseAmount(amountStr)

            if (person.isEmpty()) {
                etPerson.error = getString(R.string.person_name)
                etPerson.requestFocus()
                return@setOnClickListener
            }
            if (amount == null || amount <= 0) {
                etAmount.error = getString(R.string.valid_amount_error)
                etAmount.requestFocus()
                return@setOnClickListener
            }

            btnSave.isEnabled = false

            lifecycleScope.launch {
                try {
                    if (loadedAccounts.isEmpty()) {
                        loadedAccounts = viewModel.allAccounts.value.ifEmpty {
                            viewModel.allAccounts.firstOrNull { it.isNotEmpty() } ?: emptyList()
                        }
                    }

                    val selectedAcc = if (loadedAccounts.isNotEmpty()) {
                        val pos = spinnerAccount.selectedItemPosition
                        if (pos in loadedAccounts.indices) loadedAccounts[pos] else loadedAccounts.firstOrNull()
                    } else null

                    val type = if (rgType.checkedRadioButtonId == R.id.rbReceivable) "RECEIVABLE" else "DEBT"
                    val originationDate = etDate.text.toString().trim().ifEmpty { dateFormat.format(Date()) }
                    val dueDate = etDue.text.toString().trim()
                    val reference = etRef.text.toString().trim().ifEmpty { null }
                    val note = etNote.text.toString().trim().ifEmpty { null }

                    val debt = DebtEntity(
                        type = type,
                        personName = person,
                        amount = amount,
                        date = originationDate,
                        dueDate = dueDate,
                        status = "PENDING",
                        reference = reference,
                        note = note,
                        accountId = selectedAcc?.id
                    )

                    viewModel.insertDebt(debt) {
                        Toast.makeText(this@MainActivity, getString(R.string.debt_saved), Toast.LENGTH_SHORT).show()
                        DebtReminderHelper.checkAndNotifyDueDebts(this@MainActivity)
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    btnSave.isEnabled = true
                    Toast.makeText(this@MainActivity, "Failed to save debt: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun showRecordRepaymentDialog(debt: DebtEntity) {
        val outstanding = debt.amount - debt.paidAmount
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val tvInfo = TextView(this).apply {
            text = "${getString(R.string.debt_amount)}: ${CurrencyFormatter.formatAmount(this@MainActivity, outstanding)}"
            setPadding(0, 0, 0, 16)
        }
        val etAmount = EditText(this).apply {
            hint = getString(R.string.repayment_amount)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (outstanding > 0) outstanding.toString() else "")
        }
        val spinnerAccounts = Spinner(this).apply {
            setPadding(0, 16, 0, 16)
        }
        val etNewDueDate = EditText(this).apply {
            hint = getString(R.string.new_due_date_optional)
            isFocusable = false
            isClickable = true
            setPadding(0, 16, 0, 16)
        }
        var selectedNewDueDate: String? = null
        val cal = Calendar.getInstance()
        val dFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        etNewDueDate.setOnClickListener {
            DatePickerDialog(
                this@MainActivity,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    val datePicked = dFormat.format(cal.time)
                    selectedNewDueDate = datePicked
                    etNewDueDate.setText(datePicked)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val etNote = EditText(this).apply {
            hint = getString(R.string.debt_note_optional)
        }

        layout.addView(tvInfo)
        layout.addView(etAmount)
        layout.addView(spinnerAccounts)
        layout.addView(etNewDueDate)
        layout.addView(etNote)

        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.value.ifEmpty {
                viewModel.allAccounts.firstOrNull { it.isNotEmpty() } ?: emptyList()
            }
            val names = accounts.map { "${it.name} (${CurrencyFormatter.formatAmount(this@MainActivity, it.currentBalance)})" }
            val adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, names)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerAccounts.adapter = adapter
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.record_repayment))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val amt = CurrencyFormatter.parseAmount(etAmount.text.toString().trim())
                if (amt == null || amt <= 0) {
                    Toast.makeText(this, getString(R.string.valid_amount_error), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val accounts = viewModel.allAccounts.value.ifEmpty {
                        viewModel.allAccounts.firstOrNull { it.isNotEmpty() } ?: emptyList()
                    }
                    val pos = spinnerAccounts.selectedItemPosition
                    val selectedAcc = if (pos in accounts.indices) accounts[pos] else accounts.firstOrNull()
                    if (selectedAcc == null) {
                        Toast.makeText(this@MainActivity, getString(R.string.valid_account_error), Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val note = etNote.text.toString().trim().ifEmpty { null }

                    viewModel.recordDebtRepayment(debt.id, amt, selectedAcc.id, date, time, note, selectedNewDueDate) {
                        if (amt > outstanding) {
                            val excess = amt - outstanding
                            val excessStr = CurrencyFormatter.formatAmount(this@MainActivity, excess)
                            Toast.makeText(this@MainActivity, "${getString(R.string.repayment_recorded)} (${getString(R.string.excess_note_label, excessStr)})", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.repayment_recorded), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showChangeDueDateDialog(debt: DebtEntity) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val tvCurrent = TextView(this).apply {
            val currDue = if (debt.dueDate.isNotBlank()) CurrencyFormatter.formatDate(this@MainActivity, debt.dueDate) else getString(R.string.no_due_date)
            text = "${getString(R.string.debt_due_date_label, currDue)}"
            setPadding(0, 0, 0, 16)
        }

        val cal = Calendar.getInstance()
        val dFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val etNewDate = EditText(this).apply {
            hint = getString(R.string.new_due_date_optional)
            isFocusable = false
            isClickable = true
            if (debt.dueDate.isNotBlank()) {
                setText(debt.dueDate)
            } else {
                setText(dFormat.format(cal.time))
            }
            setOnClickListener {
                DatePickerDialog(
                    this@MainActivity,
                    { _, y, m, d ->
                        cal.set(y, m, d)
                        setText(dFormat.format(cal.time))
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        val etReason = EditText(this).apply {
            hint = getString(R.string.reason_optional)
            setPadding(0, 16, 0, 16)
        }

        layout.addView(tvCurrent)
        layout.addView(etNewDate)
        layout.addView(etReason)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_due_date_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val newDate = etNewDate.text.toString().trim()
                if (newDate.isBlank()) {
                    Toast.makeText(this, "Please select a valid date", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val reason = etReason.text.toString().trim().ifEmpty { null }
                viewModel.updateDebtDueDate(debt.id, newDate, reason) {
                    Toast.makeText(this, getString(R.string.due_date_updated), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showRefundExcessDialog(debt: DebtEntity) {
        val excess = debt.paidAmount - debt.amount
        if (excess <= 0) return

        val formattedExcess = CurrencyFormatter.formatAmount(this, excess)
        val isReceivable = debt.type == "RECEIVABLE"
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val tvPrompt = TextView(this).apply {
            val confirmMsg = if (isReceivable) {
                getString(R.string.refund_excess_confirm, formattedExcess, debt.personName)
            } else {
                getString(R.string.receive_excess_confirm, formattedExcess, debt.personName)
            }
            text = confirmMsg
            textSize = 15f
            setPadding(0, 0, 0, 16)
        }

        val spinnerAccounts = Spinner(this).apply {
            setPadding(0, 16, 0, 16)
        }

        val etNote = EditText(this).apply {
            hint = getString(R.string.debt_note_optional)
        }

        layout.addView(tvPrompt)
        layout.addView(spinnerAccounts)
        layout.addView(etNote)

        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.value.ifEmpty {
                viewModel.allAccounts.firstOrNull { it.isNotEmpty() } ?: emptyList()
            }
            val names = accounts.map { "${it.name} (${CurrencyFormatter.formatAmount(this@MainActivity, it.currentBalance)})" }
            val adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, names)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerAccounts.adapter = adapter
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.refund_excess_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                lifecycleScope.launch {
                    val accounts = viewModel.allAccounts.value.ifEmpty {
                        viewModel.allAccounts.firstOrNull { it.isNotEmpty() } ?: emptyList()
                    }
                    val pos = spinnerAccounts.selectedItemPosition
                    val selectedAcc = if (pos in accounts.indices) accounts[pos] else accounts.firstOrNull()
                    if (selectedAcc == null) {
                        Toast.makeText(this@MainActivity, getString(R.string.valid_account_error), Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val note = etNote.text.toString().trim().ifEmpty { null }

                    viewModel.settleDebtExcess(debt.id, excess, selectedAcc.id, date, time, note) {
                        Toast.makeText(this@MainActivity, getString(R.string.excess_settled_successfully), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun confirmDeleteDebt(debt: DebtEntity) {
        val outstanding = debt.amount - debt.paidAmount
        val formattedAmt = CurrencyFormatter.formatAmount(this, outstanding)
        val debtTypeLabel = if (debt.type == "RECEIVABLE") getString(R.string.owed_to_me) else getString(R.string.i_owe)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_debt))
            .setMessage(getString(R.string.move_to_trash_confirm, "$debtTypeLabel: ${debt.personName} ($formattedAmt)"))
            .setPositiveButton(getString(R.string.move_to_trash)) { _, _ ->
                viewModel.recycleDebt(debt) {
                    Toast.makeText(this, getString(R.string.item_moved_to_trash), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            val transactions = viewModel.allTransactions.firstOrNull() ?: emptyList()
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()

            if (transactions.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.no_transactions_export), Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val csvFile = ExportManager.exportTransactionsToCsv(this@MainActivity, transactions, accounts)
                ExportManager.shareCsvFile(this@MainActivity, csvFile)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportFinancialReport() {
        lifecycleScope.launch {
            val transactions = viewModel.allTransactions.firstOrNull() ?: emptyList()
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
            val debts = viewModel.allDebts.firstOrNull() ?: emptyList()

            if (transactions.isEmpty() && accounts.isEmpty() && debts.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.no_transactions_export), Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val reportFile = ExportManager.exportFinancialReportSummary(this@MainActivity, transactions, accounts, debts)
                ExportManager.shareHtmlFile(this@MainActivity, reportFile)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDebtDetailsDialog(debt: DebtEntity) {
        lifecycleScope.launch {
            val repayments = viewModel.getRepaymentsForDebt(debt.id).firstOrNull() ?: emptyList()
            val outstanding = debt.amount - debt.paidAmount
            val isReceivable = debt.type == "RECEIVABLE"
            val directionLabel = if (isReceivable) getString(R.string.owed_to_me) else getString(R.string.i_owe)
            val origDateStr = if (debt.date.isNotBlank()) CurrencyFormatter.formatDate(this@MainActivity, debt.date) else getString(R.string.no_original_date)
            val dueDateStr = if (debt.dueDate.isNotBlank()) CurrencyFormatter.formatDate(this@MainActivity, debt.dueDate) else getString(R.string.no_due_date)
            val refStr = if (!debt.reference.isNullOrBlank()) debt.reference else "None"

            val layout = android.widget.LinearLayout(this@MainActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(40, 24, 40, 24)
            }

            val tvTitle = TextView(this@MainActivity).apply {
                text = "$directionLabel: ${debt.personName}"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MainActivity, if (isReceivable) R.color.income else R.color.expense))
                setPadding(0, 0, 0, 16)
            }
            layout.addView(tvTitle)

            val detailsText = StringBuilder().apply {
                append("• ${getString(R.string.debt_original_date, origDateStr)}\n")
                append("• ${getString(R.string.debt_due_date_label, dueDateStr)}\n")
                append("• Ref: $refStr\n")
                append("• ${getString(R.string.principal_amount, CurrencyFormatter.formatAmount(this@MainActivity, debt.amount))}\n")
                append("• ${getString(R.string.total_repaid, CurrencyFormatter.formatAmount(this@MainActivity, debt.paidAmount))}\n")
                append("• ${getString(R.string.outstanding_balance, CurrencyFormatter.formatAmount(this@MainActivity, outstanding))}\n")
                append("• Status: ${debt.status}\n")
                if (!debt.note.isNullOrBlank()) {
                    append("• Note: ${debt.note}\n")
                }
            }

            val tvDetails = TextView(this@MainActivity).apply {
                text = detailsText.toString()
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                setLineSpacing(6f, 1f)
                setPadding(0, 0, 0, 16)
            }
            layout.addView(tvDetails)

            // Repayment History Section
            val tvRepaymentHeader = TextView(this@MainActivity).apply {
                text = getString(R.string.repayment_history)
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                setPadding(0, 8, 0, 8)
            }
            layout.addView(tvRepaymentHeader)

            val tvRepaymentList = TextView(this@MainActivity).apply {
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setLineSpacing(4f, 1f)
                if (repayments.isEmpty()) {
                    text = getString(R.string.no_repayments_yet)
                } else {
                    val sb = StringBuilder()
                    for (r in repayments) {
                        val formattedAmt = CurrencyFormatter.formatAmount(this@MainActivity, r.amount)
                        sb.append("• ${r.date} ${r.time}: $formattedAmt")
                        if (!r.note.isNullOrBlank()) {
                            sb.append(" (${r.note})")
                        }
                        sb.append("\n")
                    }
                    text = sb.toString().trimEnd()
                }
            }
            layout.addView(tvRepaymentList)

            val scrollContainer = android.widget.ScrollView(this@MainActivity).apply {
                addView(layout)
            }

            val builder = AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.tab_debts))
                .setView(scrollContainer)
                .setNegativeButton(getString(R.string.btn_ok), null)

            val hasExcess = debt.paidAmount > debt.amount
            if (hasExcess) {
                val actionBtnText = if (debt.type == "RECEIVABLE") getString(R.string.btn_refund_excess) else getString(R.string.btn_receive_refund)
                builder.setPositiveButton(actionBtnText) { _, _ ->
                    showRefundExcessDialog(debt)
                }
            } else if (outstanding > 0) {
                builder.setPositiveButton(getString(R.string.record_repayment)) { _, _ ->
                    showRecordRepaymentDialog(debt)
                }
            }

            builder.setNeutralButton(getString(R.string.change_due_date)) { _, _ ->
                showChangeDueDateDialog(debt)
            }

            builder.show()
        }
    }


    private fun showMonthlySummariesDialog() {
        lifecycleScope.launch {
            val summaries = viewModel.monthlySummaries.firstOrNull() ?: emptyList()
            val message = if (summaries.isEmpty()) {
                getString(R.string.no_transactions_found)
            } else {
                val sb = StringBuilder()
                for (s in summaries) {
                    val inc = CurrencyFormatter.formatAmount(this@MainActivity, s.income)
                    val exp = CurrencyFormatter.formatAmount(this@MainActivity, s.expense)
                    val net = CurrencyFormatter.formatAmount(this@MainActivity, s.income - s.expense)
                    sb.append("📅 ${s.monthYear}\n")
                    sb.append("   ${getString(R.string.income)}: +$inc\n")
                    sb.append("   ${getString(R.string.expense)}: -$exp\n")
                    sb.append("   ${getString(R.string.total_net_balance)}: $net\n\n")
                }
                sb.toString().trimEnd()
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.monthly_summary_report))
                .setMessage(message)
                .setPositiveButton(getString(R.string.btn_ok), null)
                .show()
        }
    }

    private fun showRecurringTransactionsDialog() {
        lifecycleScope.launch {
            val recurringList = viewModel.allRecurring.firstOrNull() ?: emptyList()
            val names = if (recurringList.isEmpty()) {
                arrayOf(getString(R.string.no_transactions_found))
            } else {
                recurringList.map {
                    val amt = CurrencyFormatter.formatAmount(this@MainActivity, it.amount)
                    "${it.title} • $amt [${it.frequency}] • ${getString(R.string.next_due_label, it.nextDueDate)}"
                }.toTypedArray()
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.btn_recurring_transactions))
                .setItems(names) { _, which ->
                    if (recurringList.isNotEmpty()) {
                        val item = recurringList[which]
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(item.title)
                            .setMessage("Delete this recurring rule?")
                            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                                viewModel.deleteRecurring(item)
                            }
                            .setNegativeButton(getString(R.string.btn_cancel), null)
                            .show()
                    }
                }
                .setPositiveButton(getString(R.string.add_recurring)) { _, _ ->
                    showAddRecurringDialog()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    private fun showAddRecurringDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etTitle = EditText(this).apply { hint = getString(R.string.recurring_title) }
        val etAmount = EditText(this).apply {
            hint = getString(R.string.amount_bdt)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val spinnerFreq = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                R.layout.spinner_item,
                listOf(getString(R.string.freq_daily), getString(R.string.freq_weekly), getString(R.string.freq_monthly), getString(R.string.freq_yearly))
            )
        }
        val spinnerAcc = Spinner(this)
        val cbAuto = CheckBox(this).apply {
            text = getString(R.string.auto_record_label)
            isChecked = true
        }

        layout.addView(etTitle)
        layout.addView(etAmount)
        layout.addView(spinnerFreq)
        layout.addView(spinnerAcc)
        layout.addView(cbAuto)

        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
            val accNames = accounts.map { it.name }
            spinnerAcc.adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, accNames)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_recurring))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val title = etTitle.text.toString().trim()
                val amt = etAmount.text.toString().trim().toLongOrNull()
                if (title.isBlank() || amt == null || amt <= 0) {
                    Toast.makeText(this, getString(R.string.valid_amount_error), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
                    val acc = accounts.getOrNull(spinnerAcc.selectedItemPosition)
                    if (acc == null) return@launch

                    val freqs = listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")
                    val freq = freqs.getOrElse(spinnerFreq.selectedItemPosition) { "MONTHLY" }
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                    val recurring = RecurringTransactionEntity(
                        title = title,
                        type = "EXPENSE",
                        amount = amt,
                        accountId = acc.id,
                        category = "Other",
                        frequency = freq,
                        startDate = today,
                        nextDueDate = today,
                        autoRecord = cbAuto.isChecked
                    )
                    viewModel.insertRecurring(recurring) {
                        Toast.makeText(this@MainActivity, getString(R.string.transaction_saved), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showSavingsGoalsDialog() {
        lifecycleScope.launch {
            val goals = viewModel.allSavingsGoals.firstOrNull() ?: emptyList()
            val names = if (goals.isEmpty()) {
                arrayOf(getString(R.string.no_transactions_found))
            } else {
                goals.map {
                    val saved = CurrencyFormatter.formatAmount(this@MainActivity, it.savedAmount)
                    val target = CurrencyFormatter.formatAmount(this@MainActivity, it.targetAmount)
                    val pct = if (it.targetAmount > 0) ((it.savedAmount.toDouble() / it.targetAmount.toDouble()) * 100).toInt() else 0
                    "${it.title}: $saved / $target (${CurrencyFormatter.formatPercentage(this@MainActivity, pct)})"
                }.toTypedArray()
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.btn_savings_goals))
                .setItems(names) { _, which ->
                    if (goals.isNotEmpty()) {
                        showContributeToGoalDialog(goals[which])
                    }
                }
                .setPositiveButton(getString(R.string.add_savings_goal)) { _, _ ->
                    showAddSavingsGoalDialog()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    private fun showAddSavingsGoalDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etTitle = EditText(this).apply { hint = getString(R.string.goal_title) }
        val etTarget = EditText(this).apply {
            hint = getString(R.string.target_amount)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etDate = EditText(this).apply {
            hint = getString(R.string.target_date)
            setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
        }

        layout.addView(etTitle)
        layout.addView(etTarget)
        layout.addView(etDate)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_savings_goal))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val title = etTitle.text.toString().trim()
                val target = etTarget.text.toString().trim().toLongOrNull()
                if (title.isBlank() || target == null || target <= 0) {
                    Toast.makeText(this, getString(R.string.valid_amount_error), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val goal = SavingsGoalEntity(
                    title = title,
                    targetAmount = target,
                    targetDate = etDate.text.toString().trim()
                )
                viewModel.insertSavingsGoal(goal) {
                    Toast.makeText(this, "Savings goal saved", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showContributeToGoalDialog(goal: SavingsGoalEntity) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etAmount = EditText(this).apply {
            hint = getString(R.string.contribution_amount)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val spinnerAcc = Spinner(this)

        layout.addView(etAmount)
        layout.addView(spinnerAcc)

        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
            val accNames = accounts.map { "${it.name} (${CurrencyFormatter.formatAmount(this@MainActivity, it.currentBalance)})" }
            spinnerAcc.adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, accNames)
        }

        AlertDialog.Builder(this)
            .setTitle("${getString(R.string.contribute_to_goal)}: ${goal.title}")
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val amt = etAmount.text.toString().trim().toLongOrNull()
                if (amt == null || amt <= 0) return@setPositiveButton

                lifecycleScope.launch {
                    val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
                    val acc = accounts.getOrNull(spinnerAcc.selectedItemPosition) ?: return@launch
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                    viewModel.addSavingsContribution(goal.id, amt, acc.id, date) {
                        Toast.makeText(this@MainActivity, "Contribution recorded", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAccountReconciliationDialog() {
        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
            if (accounts.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.valid_account_error), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val layout = android.widget.LinearLayout(this@MainActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(40, 20, 40, 20)
            }

            val spinnerAcc = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_item, accounts.map { it.name })
            }
            val tvSystemBal = TextView(this@MainActivity).apply {
                setPadding(0, 16, 0, 12)
                textSize = 14f
            }
            val etActual = EditText(this@MainActivity).apply {
                hint = getString(R.string.actual_balance_label)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            val tvDiscrepancy = TextView(this@MainActivity).apply {
                setPadding(0, 12, 0, 12)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                visibility = View.GONE
            }
            val tvHistory = TextView(this@MainActivity).apply {
                setPadding(0, 12, 0, 8)
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
            }

            layout.addView(spinnerAcc)
            layout.addView(tvSystemBal)
            layout.addView(etActual)
            layout.addView(tvDiscrepancy)
            layout.addView(tvHistory)

            fun updateAccountInfo() {
                val acc = accounts.getOrNull(spinnerAcc.selectedItemPosition) ?: return
                tvSystemBal.text = getString(R.string.system_balance_label, CurrencyFormatter.formatAmount(this@MainActivity, acc.currentBalance))
                lifecycleScope.launch {
                    val history = viewModel.getReconciliationsForAccount(acc.id).firstOrNull() ?: emptyList()
                    if (history.isNotEmpty()) {
                        val latest = history.first()
                        val diff = latest.actualBalance - latest.systemBalance
                        tvHistory.text = "Last Reconciled: ${latest.reconciledDate}\nSystem: ${CurrencyFormatter.formatAmount(this@MainActivity, latest.systemBalance)} | Actual: ${CurrencyFormatter.formatAmount(this@MainActivity, latest.actualBalance)} | Diff: ${CurrencyFormatter.formatAmount(this@MainActivity, diff)}"
                    } else {
                        tvHistory.text = "No previous reconciliation records for this account."
                    }
                }
            }
            updateAccountInfo()

            spinnerAcc.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateAccountInfo()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            etActual.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val actualVal = s?.toString()?.trim()?.toLongOrNull()
                    val acc = accounts.getOrNull(spinnerAcc.selectedItemPosition)
                    if (actualVal != null && acc != null) {
                        val diff = actualVal.toDouble() - acc.currentBalance
                        tvDiscrepancy.visibility = View.VISIBLE
                        val diffStr = CurrencyFormatter.formatAmount(this@MainActivity, diff)
                        if (Math.abs(diff) < 0.001) {
                            tvDiscrepancy.text = getString(R.string.reconcile_match)
                            tvDiscrepancy.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.income))
                        } else {
                            tvDiscrepancy.text = getString(R.string.reconcile_difference, diffStr)
                            tvDiscrepancy.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.expense))
                        }
                    } else {
                        tvDiscrepancy.visibility = View.GONE
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            val scroll = android.widget.ScrollView(this@MainActivity).apply { addView(layout) }

            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.reconciliation_title))
                .setView(scroll)
                .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                    val actual = etActual.text.toString().trim().toLongOrNull() ?: return@setPositiveButton
                    val acc = accounts.getOrNull(spinnerAcc.selectedItemPosition) ?: return@setPositiveButton
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val diff = actual.toDouble() - acc.currentBalance

                    viewModel.recordReconciliation(acc.id, acc.currentBalance.toLong(), actual, date) {
                        if (Math.abs(diff) > 0.001) {
                            // Offer explicit audit adjustment transaction
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle(getString(R.string.reconcile_create_adjustment))
                                .setMessage(getString(R.string.reconcile_difference, CurrencyFormatter.formatAmount(this@MainActivity, diff)) +
                                        "\n\nWould you like to record an audit adjustment transaction so the account balance matches your actual balance?")
                                .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                                    val adjType = if (diff > 0) "INCOME" else "EXPENSE"
                                    val adjAmount = Math.round(Math.abs(diff))
                                    val adjTx = TransactionEntity(
                                        type = adjType,
                                        amount = adjAmount,
                                        category = "Adjustment",
                                        accountId = acc.id,
                                        date = date,
                                        time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                                        note = "Reconciliation Adjustment ($diff)"
                                    )
                                    viewModel.insertTransaction(adjTx) {
                                        Toast.makeText(this@MainActivity, getString(R.string.reconcile_adjustment_created), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .setNegativeButton(getString(R.string.btn_cancel), null)
                                .show()
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.reconciliation_saved), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }
}
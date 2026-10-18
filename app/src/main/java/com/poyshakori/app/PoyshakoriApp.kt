package com.poyshakori.app

import android.app.Application
import com.poyshakori.app.data.database.AppDatabase
import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.data.entity.CategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PoyshakoriApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        com.poyshakori.app.util.ThemeHelper.applySavedTheme(this)
        com.poyshakori.app.util.LocaleHelper.applySavedLocale(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivityCount = 0

            override fun onActivityStarted(activity: android.app.Activity) {
                if (startedActivityCount == 0) {
                    com.poyshakori.app.security.SecurityManager.onAppForegrounded(activity)
                }
                startedActivityCount++
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivityCount--
                if (startedActivityCount == 0) {
                    com.poyshakori.app.security.SecurityManager.onAppBackgrounded()
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        applicationScope.launch {
            seedDefaultAccounts()
            seedDefaultCategories()
        }
    }

    private suspend fun seedDefaultAccounts() {
        val accountDao = database.accountDao()
        val defaultAccounts = listOf(
            AccountEntity(name = "Cash", type = "Cash", currentBalance = 0L),
            AccountEntity(name = "Bank", type = "Bank", currentBalance = 0L),
            AccountEntity(name = "bKash", type = "Mobile Wallet", currentBalance = 0L),
            AccountEntity(name = "Nagad", type = "Mobile Wallet", currentBalance = 0L),
            AccountEntity(name = "Rocket", type = "Mobile Wallet", currentBalance = 0L)
        )

        for (account in defaultAccounts) {
            val existing = accountDao.getAccountByName(account.name)
            if (existing == null) {
                accountDao.insertAccount(account)
            }
        }
    }

    private suspend fun seedDefaultCategories() {
        val categoryDao = database.categoryDao()
        
        // Remove any historical duplicate entries
        categoryDao.deduplicateCategories()

        if (categoryDao.getCategoryCount() > 0) {
            return
        }

        val defaultIncomeCategories = listOf(
            "Salary", "Business", "Freelance", "Investment", "Gift", "Rental Income", "Other"
        )
        for (cat in defaultIncomeCategories) {
            val existing = categoryDao.getCategoryByNameAndType(cat, "INCOME")
            if (existing == null) {
                categoryDao.insertCategory(CategoryEntity(name = cat, type = "INCOME"))
            }
        }

        val defaultExpenseCategories = listOf(
            "Food & Dining", "Groceries", "Shopping", "Transportation", "Rent & Housing",
            "Bills & Utilities", "Healthcare", "Education", "Entertainment", "Family",
            "Travel", "Personal Care", "Gifts & Donations", "Other"
        )
        for (cat in defaultExpenseCategories) {
            val existing = categoryDao.getCategoryByNameAndType(cat, "EXPENSE")
            if (existing == null) {
                categoryDao.insertCategory(CategoryEntity(name = cat, type = "EXPENSE"))
            }
        }
    }

    companion object {
        lateinit var instance: PoyshakoriApp
            private set
    }
}

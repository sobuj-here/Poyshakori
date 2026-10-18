package com.poyshakori.app.util

import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.data.entity.BudgetEntity
import com.poyshakori.app.data.entity.RecurringTransactionEntity
import com.poyshakori.app.data.entity.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FinancialAnalyticsHelper {

    data class CashFlowForecast(
        val currentBalance: Long,
        val expectedIncome: Long,
        val expectedExpense: Long,
        val projectedBalance: Long,
        val daysAhead: Int,
        val scheduledItemsCount: Int
    )

    data class SpendingInsights(
        val currentMonthSpent: Long,
        val previousMonthSpent: Long,
        val percentageChange: Int,
        val hasPreviousData: Boolean,
        val topSpendingCategory: String?,
        val topCategoryAmount: Long,
        val overBudgetCategories: List<String>,
        val nearBudgetCategories: List<String>
    )

    /**
     * Compute cash flow projection for next N days based on current balances and recurring payments.
     */
    fun calculateCashFlowForecast(
        accounts: List<AccountEntity>,
        recurringItems: List<RecurringTransactionEntity>,
        daysAhead: Int
    ): CashFlowForecast {
        val currentBalance = accounts.sumOf { it.currentBalance }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, daysAhead)
        val cutoffStr = sdf.format(cal.time)

        var expectedIncome = 0L
        var expectedExpense = 0L
        var scheduledCount = 0

        val activeRecurring = recurringItems.filter { it.active }

        for (item in activeRecurring) {
            var dueDate = item.nextDueDate
            while (dueDate <= cutoffStr) {
                if (dueDate >= todayStr) {
                    scheduledCount++
                    if (item.type.equals("INCOME", ignoreCase = true)) {
                        expectedIncome += item.amount
                    } else if (item.type.equals("EXPENSE", ignoreCase = true)) {
                        expectedExpense += item.amount
                    }
                }
                dueDate = getNextDate(dueDate, item.frequency)
                if (dueDate <= item.nextDueDate) break // safeguard against infinite loops
            }
        }

        val projectedBalance = currentBalance + expectedIncome - expectedExpense

        return CashFlowForecast(
            currentBalance = currentBalance,
            expectedIncome = expectedIncome,
            expectedExpense = expectedExpense,
            projectedBalance = projectedBalance,
            daysAhead = daysAhead,
            scheduledItemsCount = scheduledCount
        )
    }

    private fun getNextDate(currentDateStr: String, frequency: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(currentDateStr) ?: return "9999-12-31"
            val cal = Calendar.getInstance().apply { time = date }
            when (frequency.uppercase(Locale.ROOT)) {
                "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                "YEARLY" -> cal.add(Calendar.YEAR, 1)
                else -> cal.add(Calendar.MONTH, 1)
            }
            sdf.format(cal.time)
        } catch (e: Exception) {
            "9999-12-31"
        }
    }

    /**
     * Compute smart spending insights comparing current month to previous month and budget limits.
     */
    fun calculateSpendingInsights(
        currentMonthYear: String,
        allTransactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>
    ): SpendingInsights {
        val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        val curDate = try { sdfMonth.parse(currentMonthYear) } catch (_: Exception) { null } ?: Date()
        cal.time = curDate
        cal.add(Calendar.MONTH, -1)
        val prevMonthYear = sdfMonth.format(cal.time)

        val currentMonthExpenses = allTransactions.filter {
            it.type.equals("EXPENSE", ignoreCase = true) && it.date.startsWith(currentMonthYear)
        }
        val prevMonthExpenses = allTransactions.filter {
            it.type.equals("EXPENSE", ignoreCase = true) && it.date.startsWith(prevMonthYear)
        }

        val currentTotalSpent = currentMonthExpenses.sumOf { it.amount }
        val prevTotalSpent = prevMonthExpenses.sumOf { it.amount }

        val hasPreviousData = prevMonthExpenses.isNotEmpty()
        val percentageChange = if (hasPreviousData && prevTotalSpent > 0L) {
            (((currentTotalSpent - prevTotalSpent).toDouble() / prevTotalSpent) * 100).toInt()
        } else {
            0
        }

        // Top category
        val categoryTotals = currentMonthExpenses
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val topCategoryEntry = categoryTotals.maxByOrNull { it.value }
        val topCategory = topCategoryEntry?.key
        val topAmount = topCategoryEntry?.value ?: 0L

        // Budget checks
        val currentBudgets = budgets.filter { it.monthYear == currentMonthYear }
        val overBudgetCategories = mutableListOf<String>()
        val nearBudgetCategories = mutableListOf<String>()

        for (budget in currentBudgets) {
            val spent = categoryTotals[budget.category] ?: 0L
            val limit = budget.monthlyLimit + budget.carriedOverAmount
            if (spent > limit) {
                overBudgetCategories.add(budget.category)
            } else if (limit > 0 && spent >= (limit * 0.8)) {
                nearBudgetCategories.add(budget.category)
            }
        }

        return SpendingInsights(
            currentMonthSpent = currentTotalSpent,
            previousMonthSpent = prevTotalSpent,
            percentageChange = percentageChange,
            hasPreviousData = hasPreviousData,
            topSpendingCategory = topCategory,
            topCategoryAmount = topAmount,
            overBudgetCategories = overBudgetCategories,
            nearBudgetCategories = nearBudgetCategories
        )
    }
}

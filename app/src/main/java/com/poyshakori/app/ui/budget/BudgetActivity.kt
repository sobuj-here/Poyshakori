package com.poyshakori.app.ui.budget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.poyshakori.app.R
import com.poyshakori.app.ui.dashboard.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BudgetActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var budgetAdapter: BudgetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewBudgets)
        budgetAdapter = BudgetAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = budgetAdapter

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        lifecycleScope.launch {
            viewModel.getBudgetsForMonth(currentMonthYear).collectLatest { budgets ->
                budgetAdapter.submitList(budgets)
            }
        }
    }
}
package com.poyshakori.app.ui.debt

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

class DebtActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var debtAdapter: DebtAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debt)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewDebts)
        debtAdapter = DebtAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = debtAdapter

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        lifecycleScope.launch {
            viewModel.allDebts.collectLatest { debts ->
                debtAdapter.submitList(debts)
            }
        }
    }
}
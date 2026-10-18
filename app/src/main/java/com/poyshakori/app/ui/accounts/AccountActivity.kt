package com.poyshakori.app.ui.accounts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.poyshakori.app.R
import com.poyshakori.app.ui.dashboard.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AccountActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var accountAdapter: AccountAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewAccounts)
        accountAdapter = AccountAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = accountAdapter

        // ViewModel ইনিশিয়ালাইজেশন
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // ডাটাবেস থেকে সব একাউন্ট অবজার্ভ করা
        lifecycleScope.launch {
            viewModel.allAccounts.collectLatest { accounts ->
                accountAdapter.submitList(accounts)
            }
        }
    }
}
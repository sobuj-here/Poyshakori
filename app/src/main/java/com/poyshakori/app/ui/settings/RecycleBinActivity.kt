package com.poyshakori.app.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import androidx.activity.viewModels
import com.poyshakori.app.R
import com.poyshakori.app.data.entity.RecycleBinEntity
import com.poyshakori.app.ui.dashboard.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecycleBinActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: RecycleBinAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycle_bin)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnEmptyTrash = findViewById<MaterialButton>(R.id.btnEmptyTrash)
        val tvSubtitle = findViewById<TextView>(R.id.tvRecycleBinSubtitle)
        val tvEmpty = findViewById<TextView>(R.id.tvRecycleBinEmpty)
        val rvItems = findViewById<RecyclerView>(R.id.rvRecycleBin)

        btnBack.setOnClickListener { finish() }

        adapter = RecycleBinAdapter(
            onRestoreClick = { item -> confirmRestore(item) },
            onDeletePermanentlyClick = { item -> confirmPermanentDelete(item) }
        )
        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = adapter

        btnEmptyTrash.setOnClickListener {
            confirmEmptyRecycleBin()
        }

        lifecycleScope.launch {
            viewModel.allRecycledItems.collectLatest { items ->
                adapter.submitList(items)
                tvSubtitle.text = getString(R.string.recycle_bin_count, items.size)
                if (items.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    btnEmptyTrash.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    btnEmptyTrash.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun confirmRestore(item: RecycleBinEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore))
            .setMessage(getString(R.string.restore_confirm))
            .setPositiveButton(getString(R.string.restore)) { _, _ ->
                viewModel.restoreRecycledItem(item) {
                    Toast.makeText(this, getString(R.string.item_restored), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun confirmPermanentDelete(item: RecycleBinEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_permanently))
            .setMessage(getString(R.string.delete_permanently_confirm))
            .setPositiveButton(getString(R.string.delete_permanently)) { _, _ ->
                viewModel.deleteRecycledItemPermanently(item) {
                    Toast.makeText(this, getString(R.string.recycle_bin_cleared), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun confirmEmptyRecycleBin() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.empty_recycle_bin))
            .setMessage(getString(R.string.empty_recycle_bin_confirm))
            .setPositiveButton(getString(R.string.empty_recycle_bin)) { _, _ ->
                viewModel.emptyRecycleBin {
                    Toast.makeText(this, getString(R.string.recycle_bin_cleared), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}

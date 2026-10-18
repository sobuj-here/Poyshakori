package com.poyshakori.app.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.poyshakori.app.R
import com.poyshakori.app.data.entity.RecycleBinEntity
import com.poyshakori.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecycleBinAdapter(
    private val onRestoreClick: (RecycleBinEntity) -> Unit,
    private val onDeletePermanentlyClick: (RecycleBinEntity) -> Unit
) : ListAdapter<RecycleBinEntity, RecycleBinAdapter.RecycleBinViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecycleBinViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recycled_record, parent, false)
        return RecycleBinViewHolder(view, onRestoreClick, onDeletePermanentlyClick)
    }

    override fun onBindViewHolder(holder: RecycleBinViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RecycleBinViewHolder(
        itemView: View,
        private val onRestoreClick: (RecycleBinEntity) -> Unit,
        private val onDeletePermanentlyClick: (RecycleBinEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvRecycledTitle)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvRecycledDetails)
        private val tvDate: TextView = itemView.findViewById(R.id.tvRecycledDate)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvRecycledAmount)
        private val btnRestore: Button = itemView.findViewById(R.id.btnRestoreItem)
        private val btnDelete: Button = itemView.findViewById(R.id.btnPermanentDelete)

        fun bind(item: RecycleBinEntity) {
            val context = itemView.context
            tvTitle.text = item.title
            tvDetails.text = item.details

            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.deletedAt))
            tvDate.text = "Deleted: $dateStr"

            tvAmount.text = CurrencyFormatter.formatAmount(context, item.amount)

            btnRestore.setOnClickListener { onRestoreClick(item) }
            btnDelete.setOnClickListener { onDeletePermanentlyClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RecycleBinEntity>() {
        override fun areItemsTheSame(oldItem: RecycleBinEntity, newItem: RecycleBinEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RecycleBinEntity, newItem: RecycleBinEntity) = oldItem == newItem
    }
}

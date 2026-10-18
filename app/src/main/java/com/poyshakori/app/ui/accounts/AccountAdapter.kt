package com.poyshakori.app.ui.accounts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.poyshakori.app.R
import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.util.CurrencyFormatter

class AccountAdapter(
    private val onItemClick: ((AccountEntity) -> Unit)? = null,
    private val onItemLongClick: ((AccountEntity) -> Unit)? = null
) : ListAdapter<AccountEntity, AccountAdapter.AccountViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false)
        return AccountViewHolder(view, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AccountViewHolder(
        itemView: View,
        private val onItemClick: ((AccountEntity) -> Unit)?,
        private val onItemLongClick: ((AccountEntity) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvAccountName)
        private val tvType: TextView = itemView.findViewById(R.id.tvAccountType)
        private val tvBalance: TextView = itemView.findViewById(R.id.tvAccountBalance)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivAccountIcon)

        fun bind(account: AccountEntity) {
            tvName.text = account.name
            tvType.text = account.type
            tvBalance.text = CurrencyFormatter.formatAmount(itemView.context, account.currentBalance)

            itemView.setOnClickListener {
                onItemClick?.invoke(account)
            }

            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(account)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AccountEntity>() {
        override fun areItemsTheSame(oldItem: AccountEntity, newItem: AccountEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AccountEntity, newItem: AccountEntity) = oldItem == newItem
    }
}
package com.poyshakori.app.ui.transactions

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.poyshakori.app.R
import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.data.entity.TransactionEntity
import com.poyshakori.app.util.CategoryHelper
import com.poyshakori.app.util.CurrencyFormatter

class TransactionAdapter(
    private val onItemClick: ((TransactionEntity) -> Unit)? = null,
    private val onItemLongClick: ((TransactionEntity) -> Unit)? = null
) : ListAdapter<TransactionEntity, TransactionAdapter.TransactionViewHolder>(DiffCallback()) {

    private var accounts: Map<Long, String> = emptyMap()

    fun setAccounts(accountList: List<AccountEntity>) {
        this.accounts = accountList.associate { it.id to it.name }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position), accounts)
    }

    class TransactionViewHolder(
        itemView: View,
        private val onItemClick: ((TransactionEntity) -> Unit)?,
        private val onItemLongClick: ((TransactionEntity) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvNote: TextView = itemView.findViewById(R.id.tvNote)
        private val tvAccountAndDate: TextView = itemView.findViewById(R.id.tvAccountAndDate)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvPaymentMethod: TextView = itemView.findViewById(R.id.tvPaymentMethod)
        private val ivTypeIcon: ImageView = itemView.findViewById(R.id.ivTypeIcon)

        fun bind(transaction: TransactionEntity, accounts: Map<Long, String>) {
            tvCategory.text = CategoryHelper.getDisplayName(itemView.context, transaction.category)

            if (!transaction.note.isNullOrBlank()) {
                tvNote.text = transaction.note
                tvNote.visibility = View.VISIBLE
            } else {
                tvNote.visibility = View.GONE
            }

            val sourceAccountName = accounts[transaction.accountId] ?: "#${transaction.accountId}"
            val formattedDate = CurrencyFormatter.formatDate(itemView.context, transaction.date)
            val accountInfo = if (transaction.type == "TRANSFER" && transaction.targetAccountId != null) {
                val targetAccountName = accounts[transaction.targetAccountId] ?: "#${transaction.targetAccountId}"
                "$sourceAccountName ➔ $targetAccountName • $formattedDate"
            } else {
                "$sourceAccountName • $formattedDate"
            }
            tvAccountAndDate.text = accountInfo
            if (!transaction.paymentMethod.isNullOrBlank() && transaction.paymentMethod != sourceAccountName) {
                tvPaymentMethod.text = transaction.paymentMethod
                tvPaymentMethod.visibility = View.VISIBLE
            } else {
                tvPaymentMethod.visibility = View.GONE
            }

            val formattedAmt = CurrencyFormatter.formatAmount(itemView.context, transaction.amount)

            when (transaction.type) {
                "INCOME", "LOAN_BORROW", "RECEIVABLE_REPAYMENT" -> {
                    tvAmount.text = "+ $formattedAmt"
                    val color = ContextCompat.getColor(itemView.context, R.color.income)
                    tvAmount.setTextColor(color)
                    val iconRes = if (transaction.type == "INCOME") R.drawable.ic_income else R.drawable.ic_debt
                    ivTypeIcon.setImageResource(iconRes)
                    ivTypeIcon.imageTintList = ColorStateList.valueOf(color)
                }
                "EXPENSE", "LOAN_LEND", "DEBT_REPAYMENT" -> {
                    tvAmount.text = "- $formattedAmt"
                    val color = ContextCompat.getColor(itemView.context, R.color.expense)
                    tvAmount.setTextColor(color)
                    val iconRes = if (transaction.type == "EXPENSE") R.drawable.ic_expense else R.drawable.ic_debt
                    ivTypeIcon.setImageResource(iconRes)
                    ivTypeIcon.imageTintList = ColorStateList.valueOf(color)
                }
                else -> { // TRANSFER
                    tvAmount.text = "⇄ $formattedAmt"
                    val color = ContextCompat.getColor(itemView.context, R.color.transfer)
                    tvAmount.setTextColor(color)
                    ivTypeIcon.setImageResource(R.drawable.ic_transfer)
                    ivTypeIcon.imageTintList = ColorStateList.valueOf(color)
                }
            }

            itemView.setOnClickListener {
                onItemClick?.invoke(transaction)
            }

            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(transaction)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
        override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) = oldItem == newItem
    }
}
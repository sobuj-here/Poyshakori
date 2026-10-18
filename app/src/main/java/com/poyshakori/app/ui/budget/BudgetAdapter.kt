package com.poyshakori.app.ui.budget

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.poyshakori.app.R
import com.poyshakori.app.data.entity.BudgetEntity
import com.poyshakori.app.util.CategoryHelper
import com.poyshakori.app.util.CurrencyFormatter

class BudgetAdapter(
    private val onItemLongClick: ((BudgetEntity) -> Unit)? = null
) : ListAdapter<BudgetEntity, BudgetAdapter.BudgetViewHolder>(DiffCallback()) {

    private var spentMap: Map<String, Long> = emptyMap()

    fun setSpentAmounts(spent: Map<String, Long>) {
        this.spentMap = spent
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_budget, parent, false)
        return BudgetViewHolder(view, onItemLongClick)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        holder.bind(getItem(position), spentMap)
    }

    class BudgetViewHolder(
        itemView: View,
        private val onItemLongClick: ((BudgetEntity) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvCategory: TextView = itemView.findViewById(R.id.tvBudgetCategory)
        private val tvLimit: TextView = itemView.findViewById(R.id.tvBudgetLimit)
        private val pbProgress: ProgressBar = itemView.findViewById(R.id.pbBudgetProgress)
        private val tvSpent: TextView = itemView.findViewById(R.id.tvBudgetSpent)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvBudgetStatus)

        fun bind(budget: BudgetEntity, spentMap: Map<String, Long>) {
            val context = itemView.context
            tvCategory.text = CategoryHelper.getDisplayName(context, budget.category)

            val effectiveLimit = budget.monthlyLimit + budget.carriedOverAmount
            val formattedLimit = CurrencyFormatter.formatAmount(context, effectiveLimit)
            tvLimit.text = context.getString(R.string.budget_limit, formattedLimit)

            val spent = spentMap[budget.category] ?: 0L
            val formattedSpent = CurrencyFormatter.formatAmount(context, spent)
            tvSpent.text = context.getString(R.string.budget_spent, formattedSpent)

            val percentage = if (effectiveLimit > 0) {
                ((spent.toDouble() / effectiveLimit.toDouble()) * 100).toInt()
            } else 0

            pbProgress.progress = percentage.coerceAtMost(100)

            val remaining = effectiveLimit - spent
            if (remaining >= 0) {
                val formattedRem = CurrencyFormatter.formatAmount(context, remaining)
                val formattedPct = CurrencyFormatter.formatPercentage(context, percentage)
                tvStatus.text = context.getString(R.string.budget_remaining, formattedRem, formattedPct)

                val color = if (percentage > 80) {
                    ContextCompat.getColor(context, R.color.debt)
                } else {
                    ContextCompat.getColor(context, R.color.income)
                }
                tvStatus.setTextColor(color)
                pbProgress.progressTintList = ColorStateList.valueOf(color)
            } else {
                val formattedOver = CurrencyFormatter.formatAmount(context, -remaining)
                tvStatus.text = context.getString(R.string.budget_over, formattedOver)
                val color = ContextCompat.getColor(context, R.color.expense)
                tvStatus.setTextColor(color)
                pbProgress.progressTintList = ColorStateList.valueOf(color)
            }

            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(budget)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<BudgetEntity>() {
        override fun areItemsTheSame(oldItem: BudgetEntity, newItem: BudgetEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BudgetEntity, newItem: BudgetEntity) = oldItem == newItem
    }
}
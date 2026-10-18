package com.poyshakori.app.ui.debt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.poyshakori.app.R
import com.poyshakori.app.data.entity.DebtEntity
import com.poyshakori.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebtAdapter(
    private val onItemClick: ((DebtEntity) -> Unit)? = null,
    private val onRecordRepaymentClick: ((DebtEntity) -> Unit)? = null,
    private val onItemLongClick: ((DebtEntity) -> Unit)? = null
) : ListAdapter<DebtEntity, DebtAdapter.DebtViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebtViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_debt, parent, false)
        return DebtViewHolder(view, onItemClick, onRecordRepaymentClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: DebtViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DebtViewHolder(
        itemView: View,
        private val onItemClick: ((DebtEntity) -> Unit)?,
        private val onRecordRepaymentClick: ((DebtEntity) -> Unit)?,
        private val onItemLongClick: ((DebtEntity) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvDebtDirection: TextView = itemView.findViewById(R.id.tvDebtDirection)
        private val tvPersonName: TextView = itemView.findViewById(R.id.tvPersonName)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvDebtAmount)
        private val tvDebtPrincipalSub: TextView = itemView.findViewById(R.id.tvDebtPrincipalSub)
        private val tvDebtDates: TextView = itemView.findViewById(R.id.tvDebtDates)
        private val tvDebtReference: TextView = itemView.findViewById(R.id.tvDebtReference)
        private val tvNote: TextView = itemView.findViewById(R.id.tvDebtNote)
        private val tvDebtStatus: TextView = itemView.findViewById(R.id.tvDebtStatus)
        private val btnMarkPaid: Button = itemView.findViewById(R.id.btnMarkPaid)

        fun bind(debt: DebtEntity) {
            val context = itemView.context
            val isReceivable = debt.type == "RECEIVABLE"

            if (isReceivable) {
                tvDebtDirection.text = context.getString(R.string.debt_type_owed_to_me)
                tvDebtDirection.setTextColor(ContextCompat.getColor(context, R.color.income))
                tvDebtDirection.backgroundTintList = ContextCompat.getColorStateList(context, R.color.income_bg)
                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income))
            } else {
                tvDebtDirection.text = context.getString(R.string.debt_type_owe)
                tvDebtDirection.setTextColor(ContextCompat.getColor(context, R.color.expense))
                tvDebtDirection.backgroundTintList = ContextCompat.getColorStateList(context, R.color.expense_bg)
                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense))
            }

            tvPersonName.text = debt.personName

            val outstanding = debt.amount - debt.paidAmount
            tvAmount.text = CurrencyFormatter.formatAmount(context, outstanding)

            val principalStr = CurrencyFormatter.formatAmount(context, debt.amount)
            val paidStr = CurrencyFormatter.formatAmount(context, debt.paidAmount)
            tvDebtPrincipalSub.text = context.getString(R.string.debt_principal_sub, principalStr, paidStr)

            val origDateStr = if (debt.date.isNotBlank()) {
                context.getString(R.string.debt_start_date, CurrencyFormatter.formatDate(context, debt.date))
            } else {
                context.getString(R.string.no_original_date)
            }

            val dueDateStr = if (debt.dueDate.isNotBlank()) {
                context.getString(R.string.due_label, CurrencyFormatter.formatDate(context, debt.dueDate))
            } else {
                context.getString(R.string.no_due_date)
            }
            tvDebtDates.text = "$origDateStr • $dueDateStr"

            if (!debt.reference.isNullOrBlank()) {
                tvDebtReference.text = context.getString(R.string.ref_prefix, debt.reference)
                tvDebtReference.visibility = View.VISIBLE
            } else {
                tvDebtReference.visibility = View.GONE
            }

            if (!debt.note.isNullOrBlank()) {
                tvNote.text = debt.note
                tvNote.visibility = View.VISIBLE
            } else {
                tvNote.visibility = View.GONE
            }

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val isOverdue = debt.status != "PAID" && debt.dueDate.isNotBlank() && debt.dueDate < today

            val (statusText, statusTextColor, statusBgColor) = when {
                debt.status == "PAID" || outstanding <= 0 -> Triple(
                    context.getString(R.string.status_paid),
                    R.color.income,
                    R.color.income_bg
                )
                isOverdue -> Triple(
                    context.getString(R.string.status_overdue),
                    R.color.expense,
                    R.color.expense_bg
                )
                debt.status == "PARTIALLY_PAID" || debt.paidAmount > 0 -> Triple(
                    context.getString(R.string.status_partially_paid),
                    R.color.transfer,
                    R.color.transfer_bg
                )
                else -> Triple(
                    context.getString(R.string.status_pending),
                    R.color.debt,
                    R.color.debt_bg
                )
            }

            tvDebtStatus.text = statusText
            tvDebtStatus.setTextColor(ContextCompat.getColor(context, statusTextColor))
            tvDebtStatus.backgroundTintList = ContextCompat.getColorStateList(context, statusBgColor)

            if (debt.status == "PAID" || outstanding <= 0) {
                btnMarkPaid.visibility = View.GONE
            } else {
                btnMarkPaid.visibility = View.VISIBLE
                btnMarkPaid.text = context.getString(R.string.record_repayment)
                btnMarkPaid.setOnClickListener {
                    onRecordRepaymentClick?.invoke(debt)
                }
            }

            itemView.setOnClickListener {
                onItemClick?.invoke(debt)
            }

            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(debt)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DebtEntity>() {
        override fun areItemsTheSame(oldItem: DebtEntity, newItem: DebtEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DebtEntity, newItem: DebtEntity) = oldItem == newItem
    }
}
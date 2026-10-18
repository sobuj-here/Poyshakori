package com.poyshakori.app.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.poyshakori.app.R
import com.poyshakori.app.data.entity.CategorySpending
import com.poyshakori.app.util.CurrencyFormatter

class CategoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val items = mutableListOf<CategorySpending>()
    private var totalAmount: Long = 0L

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 40f
        isFakeBoldText = true
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
    }

    private val colors = listOf(
        Color.parseColor("#10B981"), // Emerald
        Color.parseColor("#3B82F6"), // Blue
        Color.parseColor("#F59E0B"), // Amber
        Color.parseColor("#EC4899"), // Pink
        Color.parseColor("#8B5CF6"), // Purple
        Color.parseColor("#06B6D4"), // Cyan
        Color.parseColor("#F97316"), // Orange
        Color.parseColor("#64748B")  // Slate
    )

    private val oval = RectF()

    fun setData(spendingList: List<CategorySpending>) {
        items.clear()
        items.addAll(spendingList)
        totalAmount = spendingList.sumOf { it.totalAmount }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = width.coerceAtMost(height).toFloat()
        if (size <= 0f) return

        textPaint.color = ContextCompat.getColor(context, R.color.text_primary)
        subTextPaint.color = ContextCompat.getColor(context, R.color.text_secondary)

        val strokeWidth = size * 0.12f
        slicePaint.strokeWidth = strokeWidth

        val padding = strokeWidth / 2f + 20f
        oval.set(padding, padding, width - padding, height - padding)

        val centerX = width / 2f
        val centerY = height / 2f

        if (totalAmount == 0L || items.isEmpty()) {
            slicePaint.color = ContextCompat.getColor(context, R.color.card_stroke)
            canvas.drawArc(oval, 0f, 360f, false, slicePaint)
            canvas.drawText(context.getString(R.string.no_transactions_found), centerX, centerY, textPaint)
            return
        }

        var startAngle = -90f
        for ((index, item) in items.withIndex()) {
            val sweep = (item.totalAmount.toFloat() / totalAmount) * 360f
            slicePaint.color = colors[index % colors.size]

            canvas.drawArc(oval, startAngle, sweep.coerceAtLeast(1f), false, slicePaint)
            startAngle += sweep
        }

        val formattedTotal = CurrencyFormatter.formatAmount(context, totalAmount)
        canvas.drawText(formattedTotal, centerX, centerY - 5f, textPaint)
        canvas.drawText(context.getString(R.string.spending_breakdown), centerX, centerY + 35f, subTextPaint)
    }
}

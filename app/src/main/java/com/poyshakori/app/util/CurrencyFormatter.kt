package com.poyshakori.app.util

import android.content.Context
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {

    private val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun toBanglaDigits(numberStr: String): String {
        val sb = StringBuilder()
        for (ch in numberStr) {
            if (ch in '0'..'9') {
                sb.append(banglaDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun toAsciiDigits(numberStr: String): String {
        val sb = StringBuilder()
        for (ch in numberStr) {
            when (ch) {
                '০' -> sb.append('0')
                '১' -> sb.append('1')
                '২' -> sb.append('2')
                '৩' -> sb.append('3')
                '৪' -> sb.append('4')
                '৫' -> sb.append('5')
                '৬' -> sb.append('6')
                '৭' -> sb.append('7')
                '৮' -> sb.append('8')
                '৯' -> sb.append('9')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseAmount(input: String?): Long? {
        if (input.isNullOrBlank()) return null
        val cleaned = toAsciiDigits(input.trim())
            .replace(",", "")
            .replace("৳", "")
            .replace("টাকা", "")
            .replace("tk", "", ignoreCase = true)
            .replace("bdt", "", ignoreCase = true)
            .trim()
        return cleaned.toLongOrNull() ?: cleaned.toDoubleOrNull()?.toLong()
    }

    fun parseAmountDouble(input: String?): Double? {
        if (input.isNullOrBlank()) return null
        val cleaned = toAsciiDigits(input.trim())
            .replace(",", "")
            .replace("৳", "")
            .replace("টাকা", "")
            .replace("tk", "", ignoreCase = true)
            .replace("bdt", "", ignoreCase = true)
            .trim()
        return cleaned.toDoubleOrNull()
    }

    fun formatAmount(context: Context, amount: Double): String {
        val isBangla = LocaleHelper.getLanguage(context) == LocaleHelper.LANG_BANGLA
        val isWhole = (amount % 1.0 == 0.0)
        val pattern = if (isWhole) "#,##0" else "#,##0.00"
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat(pattern, symbols)
        val formatted = df.format(amount)
        return if (isBangla) {
            "৳ " + toBanglaDigits(formatted)
        } else {
            "৳ $formatted"
        }
    }

    fun formatAmount(context: Context, amount: Long): String {
        return formatAmount(context, amount.toDouble())
    }

    fun formatNumber(context: Context, number: Long): String {
        val isBangla = LocaleHelper.getLanguage(context) == LocaleHelper.LANG_BANGLA
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(number)
        return if (isBangla) toBanglaDigits(formatted) else formatted
    }

    fun formatPercentage(context: Context, percentage: Int): String {
        val isBangla = LocaleHelper.getLanguage(context) == LocaleHelper.LANG_BANGLA
        val pStr = "$percentage%"
        return if (isBangla) toBanglaDigits(pStr) else pStr
    }

    fun formatDate(context: Context, dateStr: String): String {
        val isBangla = LocaleHelper.getLanguage(context) == LocaleHelper.LANG_BANGLA
        return if (isBangla) toBanglaDigits(dateStr) else dateStr
    }
}

package com.poyshakori.app.util

import android.content.Context

object CategoryHelper {

    private val banglaCategoryMap = mapOf(
        "Food & Dining" to "খাবার ও ডাইনিং",
        "Groceries" to "মুদি ও বাজার",
        "Shopping" to "কেনাকাটা",
        "Transportation" to "যাতায়াত ও পরিবহন",
        "Rent & Housing" to "ভাড়া ও আবাসন",
        "Bills & Utilities" to "বিল ও ইউটিলিটি",
        "Healthcare" to "চিকিৎসা ও ওষুধ",
        "Education" to "শিক্ষা",
        "Entertainment" to "বিনোদন",
        "Family" to "পরিবার",
        "Travel" to "ভ্রমণ",
        "Personal Care" to "ব্যক্তিগত যত্ন",
        "Gifts & Donations" to "উপহার ও অনুদান",
        "Salary" to "বেতন",
        "Business" to "ব্যবসা",
        "Freelance" to "ফ্রিল্যান্সিং",
        "Investment" to "বিনিয়োগ",
        "Rental Income" to "ভাড়া থেকে আয়",
        "Gift" to "উপহার",
        "Transfer" to "স্থানান্তর",
        "Loan / Debt" to "ঋণ ও ধার",
        "Loan Repayment" to "ঋণ পরিশোধ",
        "Savings" to "সঞ্চয়",
        "Opening Balance" to "প্রারম্ভিক স্থিতি",
        "Adjustment" to "ব্যালেন্স সমন্বয়",
        "Other" to "অন্যান্য"
    )

    private val reverseBanglaMap = banglaCategoryMap.entries.associate { (k, v) -> v to k }

    fun getDisplayName(context: Context, englishOrBanglaName: String): String {
        val currentLang = LocaleHelper.getLanguage(context)
        return if (currentLang == LocaleHelper.LANG_BANGLA) {
            banglaCategoryMap[englishOrBanglaName] ?: englishOrBanglaName
        } else {
            reverseBanglaMap[englishOrBanglaName] ?: englishOrBanglaName
        }
    }

    fun getCanonicalName(displayName: String): String {
        return reverseBanglaMap[displayName] ?: displayName
    }
}

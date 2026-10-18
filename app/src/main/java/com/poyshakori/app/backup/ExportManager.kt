package com.poyshakori.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.data.entity.TransactionEntity
import com.poyshakori.app.data.repository.TransactionRepository
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    fun exportTransactionsToCsv(
        context: Context,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>
    ): File {
        val accountMap = accounts.associate { it.id to it.name }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "poyshakori_transactions_$timeStamp.csv"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)

        FileWriter(file).use { writer ->
            // CSV Header
            writer.append("ID,Type,Amount (BDT),Category,Payment Method,From Account,To Account,Date,Time,Note,Reference\n")

            for (tx in transactions) {
                val fromAccount = accountMap[tx.accountId] ?: "Account #${tx.accountId}"
                val toAccount = if (tx.targetAccountId != null) accountMap[tx.targetAccountId] ?: "Account #${tx.targetAccountId}" else ""
                val cleanNote = tx.note?.replace("\"", "\"\"") ?: ""
                val cleanRef = tx.reference?.replace("\"", "\"\"") ?: ""

                writer.append("${tx.id},")
                writer.append("${tx.type},")
                writer.append("${tx.amount},")
                writer.append("\"${tx.category}\",")
                writer.append("\"${tx.paymentMethod}\",")
                writer.append("\"$fromAccount\",")
                writer.append("\"$toAccount\",")
                writer.append("${tx.date},")
                writer.append("${tx.time},")
                writer.append("\"$cleanNote\",")
                writer.append("\"$cleanRef\"\n")
            }
        }
        return file
    }

    fun shareCsvFile(context: Context, csvFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            csvFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Poyshakori Transactions Export")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Export Transactions CSV"))
    }

    fun exportFinancialReportSummary(
        context: Context,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        debts: List<com.poyshakori.app.data.entity.DebtEntity>
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "poyshakori_financial_report_$timeStamp.html"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)

        val totalBalance = accounts.sumOf { it.currentBalance }
        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val netSavings = totalIncome - totalExpense

        val totalReceivable = debts.filter { it.type == "RECEIVABLE" }.sumOf { it.amount - it.paidAmount }
        val totalPayable = debts.filter { it.type == "DEBT" }.sumOf { it.amount - it.paidAmount }

        val categorySpending = transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .toList()
            .sortedByDescending { it.second }

        FileWriter(file).use { writer ->
            writer.append("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Poyshakori Financial Report</title>
                    <style>
                        body { font-family: sans-serif; margin: 24px; color: #1e293b; background: #f8fafc; }
                        h1 { color: #0f172a; border-bottom: 2px solid #00d09c; padding-bottom: 8px; }
                        h2 { color: #334155; margin-top: 24px; }
                        table { width: 100%; border-collapse: collapse; margin-top: 12px; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                        th, td { padding: 10px 14px; text-align: left; border-bottom: 1px solid #e2e8f0; }
                        th { background: #f1f5f9; font-weight: 600; color: #475569; }
                        .positive { color: #10b981; font-weight: bold; }
                        .negative { color: #ef4444; font-weight: bold; }
                        .metric-card { display: inline-block; width: 45%; margin: 8px 1%; padding: 14px; background: #ffffff; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                        .metric-title { font-size: 13px; color: #64748b; }
                        .metric-value { font-size: 20px; font-weight: bold; margin-top: 4px; }
                        .footer { margin-top: 40px; font-size: 12px; color: #94a3b8; text-align: center; }
                    </style>
                </head>
                <body>
                    <h1>Poyshakori Financial Report</h1>
                    <p>Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())} • 100% Offline & Private</p>

                    <div>
                        <div class="metric-card">
                            <div class="metric-title">Total Net Balance</div>
                            <div class="metric-value">BDT $totalBalance</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-title">Net Savings (Income - Expense)</div>
                            <div class="metric-value ${if (netSavings >= 0) "positive" else "negative"}">BDT $netSavings</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-title">Total Income</div>
                            <div class="metric-value positive">+ BDT $totalIncome</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-title">Total Expense</div>
                            <div class="metric-value negative">- BDT $totalExpense</div>
                        </div>
                    </div>

                    <h2>Account Balances</h2>
                    <table>
                        <tr><th>Account</th><th>Type</th><th>Current Balance</th></tr>
            """.trimIndent())

            for (acc in accounts) {
                writer.append("<tr><td>${acc.name}</td><td>${acc.type}</td><td>BDT ${acc.currentBalance}</td></tr>")
            }

            writer.append("""
                    </table>

                    <h2>Debts &amp; Loans Summary</h2>
                    <table>
                        <tr><th>Category</th><th>Outstanding Amount</th></tr>
                        <tr><td>Money Lent (Owed to Me)</td><td class="positive">BDT $totalReceivable</td></tr>
                        <tr><td>Money Borrowed (I Owe)</td><td class="negative">BDT $totalPayable</td></tr>
                    </table>

                    <h2>Spending Breakdown by Category</h2>
                    <table>
                        <tr><th>Category</th><th>Total Amount</th></tr>
            """.trimIndent())

            for ((cat, amt) in categorySpending) {
                writer.append("<tr><td>$cat</td><td>BDT $amt</td></tr>")
            }

            writer.append("""
                    </table>

                    <div class="footer">
                        Poyshakori v2.0 • Offline Personal Finance &amp; Accounting
                    </div>
                </body>
                </html>
            """.trimIndent())
        }
        return file
    }

    fun shareHtmlFile(context: Context, htmlFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            htmlFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_SUBJECT, "Poyshakori Financial Report")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share Financial Report"))
    }

    suspend fun importTransactionsFromCsv(
        context: Context,
        uri: Uri,
        repository: TransactionRepository,
        defaultAccountId: Long
    ): Pair<Int, String?> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Pair(0, "Cannot open selected file")

            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            reader.close()

            if (lines.isEmpty()) {
                return Pair(0, "File is empty")
            }

            var importedCount = 0
            val header = lines[0].split(",").map { it.trim().lowercase() }
            val typeIdx = header.indexOfFirst { it.contains("type") }
            val amountIdx = header.indexOfFirst { it.contains("amount") }
            val catIdx = header.indexOfFirst { it.contains("category") }
            val dateIdx = header.indexOfFirst { it.contains("date") }
            val noteIdx = header.indexOfFirst { it.contains("note") }

            val simpleDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = simpleDate.format(Date())

            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue

                val tokens = mutableListOf<String>()
                var inQuotes = false
                val sb = StringBuilder()
                for (ch in line) {
                    if (ch == '\"') {
                        inQuotes = !inQuotes
                    } else if (ch == ',' && !inQuotes) {
                        tokens.add(sb.toString().trim().removeSurrounding("\""))
                        sb.clear()
                    } else {
                        sb.append(ch)
                    }
                }
                tokens.add(sb.toString().trim().removeSurrounding("\""))

                val type = if (typeIdx != -1 && typeIdx < tokens.size) {
                    val rawType = tokens[typeIdx].uppercase()
                    if (rawType.contains("INC")) "INCOME" else "EXPENSE"
                } else "EXPENSE"

                val amount = if (amountIdx != -1 && amountIdx < tokens.size) {
                    tokens[amountIdx].replace("[^0-9.]".toRegex(), "").toDoubleOrNull()?.toLong() ?: 0L
                } else 0L

                if (amount <= 0L) continue

                val category = if (catIdx != -1 && catIdx < tokens.size && tokens[catIdx].isNotBlank()) {
                    tokens[catIdx]
                } else "Other"

                val date = if (dateIdx != -1 && dateIdx < tokens.size && tokens[dateIdx].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    tokens[dateIdx]
                } else todayStr

                val note = if (noteIdx != -1 && noteIdx < tokens.size) tokens[noteIdx] else null

                val tx = TransactionEntity(
                    type = type,
                    amount = amount,
                    accountId = defaultAccountId,
                    category = category,
                    paymentMethod = "Imported",
                    date = date,
                    time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                    note = note
                )
                repository.insertTransaction(tx)
                importedCount++
            }

            Pair(importedCount, null)
        } catch (e: Exception) {
            Pair(0, e.localizedMessage ?: "Unknown import error")
        }
    }
}
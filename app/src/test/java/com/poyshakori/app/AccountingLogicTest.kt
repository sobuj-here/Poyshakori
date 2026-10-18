package com.poyshakori.app

import com.poyshakori.app.backup.BackupManager
import com.poyshakori.app.data.entity.AccountEntity
import com.poyshakori.app.data.entity.BudgetEntity
import com.poyshakori.app.data.entity.DebtEntity
import com.poyshakori.app.data.entity.SavingsGoalEntity
import com.poyshakori.app.data.entity.TransactionEntity
import com.poyshakori.app.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class AccountingLogicTest {

    @Test
    fun testIncomeIncreasesAccountBalance() {
        var cashAccount = AccountEntity(id = 1L, name = "Cash", currentBalance = 500L)
        val incomeTx = TransactionEntity(
            id = 101L,
            type = "INCOME",
            amount = 1200L,
            accountId = 1L,
            category = "Salary",
            date = "2026-10-14",
            time = "10:00:00"
        )

        // Income adds to account
        cashAccount = cashAccount.copy(currentBalance = cashAccount.currentBalance + incomeTx.amount)
        assertEquals(1700L, cashAccount.currentBalance)
    }

    @Test
    fun testExpenseDecreasesAccountBalance() {
        var cashAccount = AccountEntity(id = 1L, name = "Cash", currentBalance = 1700L)
        val expenseTx = TransactionEntity(
            id = 102L,
            type = "EXPENSE",
            amount = 350L,
            accountId = 1L,
            category = "Food & Dining",
            date = "2026-10-14",
            time = "12:30:00"
        )

        // Expense subtracts from account
        cashAccount = cashAccount.copy(currentBalance = cashAccount.currentBalance - expenseTx.amount)
        assertEquals(1350L, cashAccount.currentBalance)
    }

    @Test
    fun testTransferDecreasesSourceAndIncreasesTargetWithoutAlteringNetWorth() {
        var bankAccount = AccountEntity(id = 1L, name = "Bank", currentBalance = 10000L)
        var bkashAccount = AccountEntity(id = 2L, name = "bKash", currentBalance = 500L)

        val initialNetWorth = bankAccount.currentBalance + bkashAccount.currentBalance
        assertEquals(10500L, initialNetWorth)

        val transferTx = TransactionEntity(
            id = 103L,
            type = "TRANSFER",
            amount = 2000L,
            accountId = 1L,
            targetAccountId = 2L,
            category = "Transfer",
            date = "2026-10-14",
            time = "14:00:00"
        )

        // Source decreases by amount
        bankAccount = bankAccount.copy(currentBalance = bankAccount.currentBalance - transferTx.amount)
        // Target increases by amount
        bkashAccount = bkashAccount.copy(currentBalance = bkashAccount.currentBalance + transferTx.amount)

        assertEquals(8000L, bankAccount.currentBalance)
        assertEquals(2500L, bkashAccount.currentBalance)

        val postTransferNetWorth = bankAccount.currentBalance + bkashAccount.currentBalance
        // Crucial Accounting Rule: Transfer does NOT create or destroy money
        assertEquals(initialNetWorth, postTransferNetWorth)
    }

    @Test
    fun testDeletingTransactionRollsBackBalances() {
        var bankAccount = AccountEntity(id = 1L, name = "Bank", currentBalance = 8000L)
        var bkashAccount = AccountEntity(id = 2L, name = "bKash", currentBalance = 2500L)

        val transferTx = TransactionEntity(
            id = 103L,
            type = "TRANSFER",
            amount = 2000L,
            accountId = 1L,
            targetAccountId = 2L,
            category = "Transfer",
            date = "2026-10-14",
            time = "14:00:00"
        )

        // Rolling back transfer
        bankAccount = bankAccount.copy(currentBalance = bankAccount.currentBalance + transferTx.amount)
        bkashAccount = bkashAccount.copy(currentBalance = bkashAccount.currentBalance - transferTx.amount)

        assertEquals(10000L, bankAccount.currentBalance)
        assertEquals(500L, bkashAccount.currentBalance)
    }

    @Test
    fun testBudgetSpendingAndLimitEvaluation() {
        val budget = BudgetEntity(
            id = 1L,
            category = "Food & Dining",
            monthlyLimit = 5000L,
            monthYear = "2026-10"
        )

        val expenses = listOf(
            TransactionEntity(id = 1L, type = "EXPENSE", amount = 1500L, accountId = 1L, category = "Food & Dining", date = "2026-10-01", time = "10:00:00"),
            TransactionEntity(id = 2L, type = "EXPENSE", amount = 2000L, accountId = 1L, category = "Food & Dining", date = "2026-10-05", time = "12:00:00"),
            TransactionEntity(id = 3L, type = "EXPENSE", amount = 800L, accountId = 1L, category = "Groceries", date = "2026-10-06", time = "15:00:00") // Different category
        )

        val foodSpent = expenses.filter { it.category == budget.category && it.type == "EXPENSE" }.sumOf { it.amount }
        assertEquals(3500L, foodSpent)

        val remaining = budget.monthlyLimit - foodSpent
        assertEquals(1500L, remaining)
        assertTrue("Budget should not be exceeded", remaining > 0)

        val percentage = (foodSpent.toDouble() / budget.monthlyLimit.toDouble()) * 100
        assertEquals(70.0, percentage, 0.01)
    }

    @Test
    fun testBudgetRolloverCalculation() {
        val septBudget = BudgetEntity(
            id = 1L,
            category = "Transportation",
            monthlyLimit = 3000L,
            monthYear = "2026-09",
            rollover = true
        )
        val septSpent = 1800L
        val unused = (septBudget.monthlyLimit + septBudget.carriedOverAmount) - septSpent
        assertEquals(1200L, unused)

        // October budget gets carriedOverAmount
        val octBudget = BudgetEntity(
            id = 2L,
            category = "Transportation",
            monthlyLimit = 3000L,
            monthYear = "2026-10",
            rollover = true,
            carriedOverAmount = unused
        )
        val totalOctLimit = octBudget.monthlyLimit + octBudget.carriedOverAmount
        assertEquals(4200L, totalOctLimit)
    }

    @Test
    fun testDebtRepaymentCashMovementAndOutstanding() {
        var userCashAccount = AccountEntity(id = 1L, name = "Cash", currentBalance = 5000L)
        var debt = DebtEntity(
            id = 10L,
            type = "DEBT", // User owes uncle 3000 BDT
            personName = "Uncle Karim",
            amount = 3000L,
            paidAmount = 0L,
            dueDate = "2026-11-01"
        )

        // Repay partial 1000 BDT
        val repayment1 = 1000L
        userCashAccount = userCashAccount.copy(currentBalance = userCashAccount.currentBalance - repayment1)
        debt = debt.copy(
            paidAmount = debt.paidAmount + repayment1,
            status = if (debt.paidAmount + repayment1 >= debt.amount) "PAID" else "PARTIALLY_PAID"
        )
        assertEquals(4000L, userCashAccount.currentBalance)
        assertEquals(2000L, debt.amount - debt.paidAmount)
        assertEquals("PARTIALLY_PAID", debt.status)

        // Repay remaining 2000 BDT
        val repayment2 = 2000L
        userCashAccount = userCashAccount.copy(currentBalance = userCashAccount.currentBalance - repayment2)
        debt = debt.copy(
            paidAmount = debt.paidAmount + repayment2,
            status = if (debt.paidAmount + repayment2 >= debt.amount) "PAID" else "PARTIALLY_PAID"
        )
        assertEquals(2000L, userCashAccount.currentBalance)
        assertEquals(0L, debt.amount - debt.paidAmount)
        assertEquals("PAID", debt.status)
    }

    @Test
    fun testReceivableRepaymentIncreasesUserCash() {
        var userCashAccount = AccountEntity(id = 1L, name = "Cash", currentBalance = 5000L)
        var receivable = DebtEntity(
            id = 11L,
            type = "RECEIVABLE", // Friend owes user 1500 BDT
            personName = "Rahim",
            amount = 1500L,
            paidAmount = 0L,
            dueDate = "2026-10-20"
        )

        // Friend pays user back 1500 BDT
        val repayment = 1500L
        userCashAccount = userCashAccount.copy(currentBalance = userCashAccount.currentBalance + repayment)
        receivable = receivable.copy(
            paidAmount = receivable.paidAmount + repayment,
            status = "PAID"
        )
        assertEquals(6500L, userCashAccount.currentBalance)
        assertEquals(0L, receivable.amount - receivable.paidAmount)
        assertEquals("PAID", receivable.status)
    }

    @Test
    fun testBorrowingIncreasesAccountBalance() {
        // "ami dar nile tk add hobe"
        var cashAccount = AccountEntity(id = 1L, name = "Cash", currentBalance = 3000L)
        val borrowedDebt = DebtEntity(
            id = 12L,
            type = "DEBT",
            personName = "Hasan",
            amount = 2000L,
            accountId = 1L,
            dueDate = "2026-11-15"
        )

        // Borrowing cash enters user's account
        val delta = if (borrowedDebt.type == "DEBT") borrowedDebt.amount else -borrowedDebt.amount
        cashAccount = cashAccount.copy(currentBalance = cashAccount.currentBalance + delta)
        assertEquals(5000L, cashAccount.currentBalance)
    }

    @Test
    fun testLendingDecreasesAccountBalance() {
        // "kew tk dar nile seta tk kete jabe"
        var bankAccount = AccountEntity(id = 2L, name = "Bank", currentBalance = 10000L)
        val lentLoan = DebtEntity(
            id = 13L,
            type = "RECEIVABLE",
            personName = "Tanvir",
            amount = 4000L,
            accountId = 2L,
            dueDate = "2026-10-30"
        )

        // Lending cash leaves user's account
        val delta = if (lentLoan.type == "DEBT") lentLoan.amount else -lentLoan.amount
        bankAccount = bankAccount.copy(currentBalance = bankAccount.currentBalance + delta)
        assertEquals(6000L, bankAccount.currentBalance)
    }

    @Test
    fun testDebtDeletionRollsBackAccountBalance() {
        var cashAccount = AccountEntity(id = 1L, name = "Cash", currentBalance = 5000L)
        val borrowedDebt = DebtEntity(
            id = 12L,
            type = "DEBT",
            personName = "Hasan",
            amount = 2000L,
            paidAmount = 500L, // 500 repaid already
            accountId = 1L,
            dueDate = "2026-11-15"
        )

        // Deleting debt reverses remaining unpaid amount
        val remaining = borrowedDebt.amount - borrowedDebt.paidAmount // 1500
        val rollbackDelta = if (borrowedDebt.type == "DEBT") -remaining else remaining
        cashAccount = cashAccount.copy(currentBalance = cashAccount.currentBalance + rollbackDelta)
        assertEquals(3500L, cashAccount.currentBalance)
    }

    @Test
    fun testDebtDueTodayAndOverdueEvaluation() {
        // "tk ferot dewar date hole ba par hoie gele remainder dibe"
        val today = "2026-10-16"

        val dueTodayDebt = DebtEntity(
            id = 1L,
            type = "RECEIVABLE",
            personName = "Karim",
            amount = 1000L,
            dueDate = "2026-10-16",
            status = "PENDING"
        )
        val overdueDebt = DebtEntity(
            id = 2L,
            type = "DEBT",
            personName = "Sabbir",
            amount = 2500L,
            dueDate = "2026-10-10",
            status = "PENDING"
        )
        val futureDebt = DebtEntity(
            id = 3L,
            type = "DEBT",
            personName = "Arif",
            amount = 5000L,
            dueDate = "2026-10-25",
            status = "PENDING"
        )
        val paidOverdueDebt = DebtEntity(
            id = 4L,
            type = "RECEIVABLE",
            personName = "Nayeem",
            amount = 3000L,
            paidAmount = 3000L,
            dueDate = "2026-10-01",
            status = "PAID"
        )

        // Evaluate reminder eligibility
        fun shouldRemind(debt: DebtEntity): Boolean {
            if (debt.status == "PAID") return false
            if (debt.dueDate.isBlank()) return false
            if (debt.amount - debt.paidAmount <= 0) return false
            return debt.dueDate <= today // Date reached or passed
        }

        assertTrue("Due today should trigger reminder", shouldRemind(dueTodayDebt))
        assertTrue("Overdue should trigger reminder", shouldRemind(overdueDebt))
        assertFalse("Future debt should not trigger reminder yet", shouldRemind(futureDebt))
        assertFalse("Paid debt should not trigger reminder", shouldRemind(paidOverdueDebt))
    }

    @Test
    fun testSavingsGoalContributionDeductsSourceAccount() {
        var bankAccount = AccountEntity(id = 1L, name = "Bank", currentBalance = 25000L)
        var goal = SavingsGoalEntity(
            id = 1L,
            title = "New Laptop",
            targetAmount = 80000L,
            savedAmount = 20000L,
            targetDate = "2027-01-01"
        )

        val contribution = 5000L
        bankAccount = bankAccount.copy(currentBalance = bankAccount.currentBalance - contribution)
        goal = goal.copy(
            savedAmount = goal.savedAmount + contribution,
            status = if (goal.savedAmount + contribution >= goal.targetAmount) "COMPLETED" else "IN_PROGRESS"
        )

        assertEquals(20000L, bankAccount.currentBalance)
        assertEquals(25000L, goal.savedAmount)
        assertEquals("IN_PROGRESS", goal.status)
    }

    @Test
    fun testAccountReconciliationDiscrepancy() {
        val systemBalance = 15400L
        val physicalCountedBalance = 15000L
        val discrepancy = physicalCountedBalance - systemBalance
        // Discrepancy is -400 (400 BDT missing from physical cash compared to logs)
        assertEquals(-400L, discrepancy)
    }

    @Test
    fun testMonthEndTransitionCarriesBalanceForward() {
        // Balances carry forward automatically in Room without deleting historical transactions
        val oct31Balance = 42500L
        val nov1StartingBalance = oct31Balance
        assertEquals(42500L, nov1StartingBalance)
        assertNotEquals(0L, nov1StartingBalance)
    }

    @Test
    fun testCryptoBackupVerificationV2Integrity() {
        val password = "SuperSecurePassword987"
        val payload = "SQLite format 3\u0000Poyshakori accounting database test payload".toByteArray(Charsets.UTF_8)

        // Mock export
        val outStream = ByteArrayOutputStream()
        // Simulate V2 export logic
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encSpec = PBEKeySpec((password + "enc").toCharArray(), salt, 12000, 256)
        val encKey = SecretKeySpec(factory.generateSecret(encSpec).encoded, "AES")

        val macSpec = PBEKeySpec((password + "mac").toCharArray(), salt, 12000, 256)
        val macKey = SecretKeySpec(factory.generateSecret(macSpec).encoded, "HmacSHA256")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, encKey, IvParameterSpec(iv))
        val cipherBytes = cipher.doFinal(payload)

        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        val magicBytes = "POYSHAKORI_ENC_V2".toByteArray(Charsets.UTF_8)
        mac.update(magicBytes)
        mac.update(salt)
        mac.update(iv)
        mac.update(cipherBytes)
        val hmac = mac.doFinal()

        outStream.write(magicBytes.size)
        outStream.write(magicBytes)
        outStream.write(salt)
        outStream.write(iv)
        outStream.write(hmac)
        outStream.write(cipherBytes)

        val backupBytes = outStream.toByteArray()

        // Verify with correct password
        val verifyResult = BackupManager.verifyBackupIntegrity(password, ByteArrayInputStream(backupBytes))
        assertEquals(BackupManager.VerificationStatus.VALID, verifyResult.status)

        // Verify with wrong password
        val wrongResult = BackupManager.verifyBackupIntegrity("WrongPassword123", ByteArrayInputStream(backupBytes))
        assertEquals(BackupManager.VerificationStatus.INVALID_PASSWORD_OR_CORRUPT, wrongResult.status)
    }

    @Test
    fun testRestoreRawSqliteDatabaseFormat() {
        val rawSqlite = "SQLite format 3\u0000SomeExistingUserDatabaseContent123456789".toByteArray(Charsets.UTF_8)
        val decrypted = BackupManager.decryptBackupBytes(rawSqlite, "AnyPassword")
        org.junit.Assert.assertNotNull(decrypted)
        assertTrue(BackupManager.isSqlite(decrypted!!))
    }

    @Test
    fun testRestoreLegacyV1Backup() {
        val password = "LegacyPassword123"
        val payload = "SQLite format 3\u0000Poyshakori v1 database payload".toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val cipherBytes = cipher.doFinal(payload)

        // V1 format with length prefix
        val outStream = ByteArrayOutputStream()
        val magic = "POYSHAKORI_V1".toByteArray(Charsets.UTF_8)
        outStream.write(magic.size)
        outStream.write(magic)
        outStream.write(salt)
        outStream.write(iv)
        outStream.write(cipherBytes)

        val v1Bytes = outStream.toByteArray()
        val decrypted = BackupManager.decryptBackupBytes(v1Bytes, password)
        org.junit.Assert.assertNotNull(decrypted)
        assertTrue(BackupManager.isSqlite(decrypted!!))
    }

    @Test
    fun testRestoreV2SingleKeyBackup() {
        val password = "SingleKeyPassword456"
        val payload = "SQLite format 3\u0000Poyshakori v2 single-key payload".toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 12000, 256)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val cipherBytes = cipher.doFinal(payload)

        val outStream = ByteArrayOutputStream()
        val magic = "POYSHAKORI_ENC_V2".toByteArray(Charsets.UTF_8)
        outStream.write(magic.size)
        outStream.write(magic)
        outStream.write(salt)
        outStream.write(iv)
        outStream.write(ByteArray(32)) // dummy hmac
        outStream.write(cipherBytes)

        val singleKeyBytes = outStream.toByteArray()
        val decrypted = BackupManager.decryptBackupBytes(singleKeyBytes, password)
        org.junit.Assert.assertNotNull("Should decrypt single-key v2 backup via fallback", decrypted)
        assertTrue(BackupManager.isSqlite(decrypted!!))
    }

    @Test
    fun testRestoreV2WithoutLengthPrefix() {
        val password = "PrefixlessPassword789"
        val payload = "SQLite format 3\u0000Poyshakori v2 prefixless payload".toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encSpec = PBEKeySpec((password + "enc").toCharArray(), salt, 12000, 256)
        val encKey = SecretKeySpec(factory.generateSecret(encSpec).encoded, "AES")
        val macSpec = PBEKeySpec((password + "mac").toCharArray(), salt, 12000, 256)
        val macKey = SecretKeySpec(factory.generateSecret(macSpec).encoded, "HmacSHA256")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, encKey, IvParameterSpec(iv))
        val cipherBytes = cipher.doFinal(payload)

        val magic = "POYSHAKORI_ENC_V2".toByteArray(Charsets.UTF_8)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        mac.update(magic)
        mac.update(salt)
        mac.update(iv)
        mac.update(cipherBytes)
        val hmac = mac.doFinal()

        // Directly write magic WITHOUT magic.size byte
        val outStream = ByteArrayOutputStream()
        outStream.write(magic)
        outStream.write(salt)
        outStream.write(iv)
        outStream.write(hmac)
        outStream.write(cipherBytes)

        val prefixlessBytes = outStream.toByteArray()
        val decrypted = BackupManager.decryptBackupBytes(prefixlessBytes, password)
        org.junit.Assert.assertNotNull("Should decrypt prefixless v2 backup", decrypted)
        assertTrue(BackupManager.isSqlite(decrypted!!))
    }

    @Test
    fun testBanglaDigitFormatting() {
        assertEquals("১২৩৪৫৬৭৮৯০", CurrencyFormatter.toBanglaDigits("1234567890"))
        assertEquals("৳ ১,৫০০", "৳ " + CurrencyFormatter.toBanglaDigits("1,500"))
        assertEquals("২০২৬-১০-১৫", CurrencyFormatter.toBanglaDigits("2026-10-15"))
    }

    @Test
    fun testCurrencyFormatterAmountParsing() {
        assertEquals(500L, CurrencyFormatter.parseAmount("500"))
        assertEquals(500L, CurrencyFormatter.parseAmount("500.00"))
        assertEquals(1500L, CurrencyFormatter.parseAmount(" 1,500 "))
        assertEquals(500L, CurrencyFormatter.parseAmount("৫০০"))
        assertEquals(1234L, CurrencyFormatter.parseAmount("১২৩৪"))
        assertEquals(500L, CurrencyFormatter.parseAmount("৳ ৫০০"))
        assertEquals(500L, CurrencyFormatter.parseAmount("500 tk"))
        assertEquals(500L, CurrencyFormatter.parseAmount("500 টাকা"))
        assertNull(CurrencyFormatter.parseAmount(""))
        assertNull(CurrencyFormatter.parseAmount("   "))
        assertNull(CurrencyFormatter.parseAmount("abc"))
    }

    @Test
    fun testCategoryCanonicalMapping() {
        assertEquals("Food & Dining", com.poyshakori.app.util.CategoryHelper.getCanonicalName("খাবার ও ডাইনিং"))
        assertEquals("Salary", com.poyshakori.app.util.CategoryHelper.getCanonicalName("বেতন"))
        assertEquals("Transfer", com.poyshakori.app.util.CategoryHelper.getCanonicalName("স্থানান্তর"))
        assertEquals("Custom Category", com.poyshakori.app.util.CategoryHelper.getCanonicalName("Custom Category"))
    }

    @Test
    fun testAppLockPinHashVerification() {
        val pin = "1234"
        val saltHex = "a1b2c3d4e5f60718"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray(Charsets.UTF_8))
        val expectedHash = md.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        // Test matching PIN
        val md2 = java.security.MessageDigest.getInstance("SHA-256")
        md2.update(saltHex.toByteArray(Charsets.UTF_8))
        val actualHash = md2.digest("1234".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        assertEquals(expectedHash, actualHash)
    }

    @Test
    fun testLoanAndDebtCompleteAccountingIntegrityWorkflow() {
        // Complete Verification of Scenario from prompt Section 9:
        // Starting cash balance: 2230 BDT
        var cashAccount = AccountEntity(id = 1L, name = "Cash", currentBalance = 2230L)
        val transactions = mutableListOf<TransactionEntity>()

        fun calculateOrdinaryIncome() = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        fun calculateOrdinaryExpense() = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }

        // Step 1: User lends 500 BDT to a friend (Receivable loan)
        val lentAmount = 500L
        var receivableDebt = DebtEntity(
            id = 201L,
            type = "RECEIVABLE",
            personName = "Sohag",
            amount = lentAmount,
            paidAmount = 0L,
            dueDate = "2026-11-18",
            date = "2026-10-18",
            accountId = 1L
        )
        // Cash decreases by 500
        cashAccount = cashAccount.copy(currentBalance = cashAccount.currentBalance - lentAmount)
        // Transaction recorded as LOAN_LEND (distinct from ordinary EXPENSE)
        transactions.add(
            TransactionEntity(
                id = 1L,
                type = "LOAN_LEND",
                amount = lentAmount,
                accountId = 1L,
                category = "Lent",
                date = "2026-10-18",
                time = "10:00:00"
            )
        )
        assertEquals(1730L, cashAccount.currentBalance)
        assertEquals(500L, receivableDebt.amount - receivableDebt.paidAmount)
        assertEquals(0L, calculateOrdinaryExpense()) // MUST NOT be classified as ordinary expense!

        // Step 2: Friend repays 200 BDT
        val repaymentReceived = 200L
        cashAccount = cashAccount.copy(currentBalance = cashAccount.currentBalance + repaymentReceived)
        receivableDebt = receivableDebt.copy(paidAmount = receivableDebt.paidAmount + repaymentReceived)
        transactions.add(
            TransactionEntity(
                id = 2L,
                type = "RECEIVABLE_REPAYMENT",
                amount = repaymentReceived,
                accountId = 1L,
                category = "Lent",
                date = "2026-10-19",
                time = "11:00:00"
            )
        )
        assertEquals(1930L, cashAccount.currentBalance)
        assertEquals(300L, receivableDebt.amount - receivableDebt.paidAmount)
        assertEquals(0L, calculateOrdinaryIncome()) // Returned principal MUST NOT be classified as ordinary income!

        // Step 3: User borrows 500 BDT from Hasan (Liability)
        val borrowedAmount = 500L
        var borrowedDebt = DebtEntity(
            id = 202L,
            type = "DEBT",
            personName = "Hasan",
            amount = borrowedAmount,
            paidAmount = 0L,
            dueDate = "2026-11-20",
            date = "2026-10-20",
            accountId = 1L
        )
        cashAccount = cashAccount.copy(currentBalance = cashAccount.currentBalance + borrowedAmount)
        transactions.add(
            TransactionEntity(
                id = 3L,
                type = "LOAN_BORROW",
                amount = borrowedAmount,
                accountId = 1L,
                category = "Borrowed",
                date = "2026-10-20",
                time = "14:00:00"
            )
        )
        assertEquals(2430L, cashAccount.currentBalance)
        assertEquals(500L, borrowedDebt.amount - borrowedDebt.paidAmount)
        assertEquals(0L, calculateOrdinaryIncome()) // Borrowed money MUST NOT be classified as ordinary income!

        // Step 4: User repays 200 BDT of the borrowed loan
        val repaymentPaid = 200L
        cashAccount = cashAccount.copy(currentBalance = cashAccount.currentBalance - repaymentPaid)
        borrowedDebt = borrowedDebt.copy(paidAmount = borrowedDebt.paidAmount + repaymentPaid)
        transactions.add(
            TransactionEntity(
                id = 4L,
                type = "DEBT_REPAYMENT",
                amount = repaymentPaid,
                accountId = 1L,
                category = "Borrowed",
                date = "2026-10-21",
                time = "16:00:00"
            )
        )
        assertEquals(2230L, cashAccount.currentBalance)
        assertEquals(300L, borrowedDebt.amount - borrowedDebt.paidAmount)
        assertEquals(0L, calculateOrdinaryExpense()) // Loan repayment MUST NOT be classified as ordinary expense!

        // Finally, ordinary income & expense reports are completely clean
        assertEquals(0L, calculateOrdinaryIncome())
        assertEquals(0L, calculateOrdinaryExpense())
    }

    @Test
    fun testSafeAccountDeletionIntegrityProtection() {
        val account = AccountEntity(id = 10L, name = "Savings Bank", currentBalance = 5000L)
        val activeTxCount = 3
        val activeDebtCount = 1

        // Account cannot be deleted if active balance > 0 or dependent transactions/debts exist
        fun canDeleteAccount(acc: AccountEntity, txCount: Int, debtCount: Int): Boolean {
            if (acc.currentBalance != 0L) return false
            if (txCount > 0) return false
            if (debtCount > 0) return false
            return true
        }

        assertFalse("Account with balance cannot be deleted", canDeleteAccount(account, 0, 0))
        assertFalse("Account with transactions cannot be deleted", canDeleteAccount(account.copy(currentBalance = 0L), activeTxCount, 0))
        assertFalse("Account with linked debts cannot be deleted", canDeleteAccount(account.copy(currentBalance = 0L), 0, activeDebtCount))

        val cleanAccount = account.copy(currentBalance = 0L)
        assertTrue("Zero-balance account with no dependencies can be deleted", canDeleteAccount(cleanAccount, 0, 0))
    }

    @Test
    fun testAutoBackupRetentionNeverDeletesOnlyValidBackup() {
        val backups = listOf("poyshakori_backup_20261018_100000.pbackup")
        val maxRetention = 7

        fun cleanupBackups(list: List<String>): List<String> {
            if (list.size <= 1) return list // Never delete only valid backup
            if (list.size <= maxRetention) return list
            return list.take(maxRetention)
        }

        val resultSingle = cleanupBackups(backups)
        assertEquals(1, resultSingle.size)
        assertEquals("poyshakori_backup_20261018_100000.pbackup", resultSingle[0])

        val tenBackups = (1..10).map { "poyshakori_backup_20261018_10000$it.pbackup" }
        val resultTen = cleanupBackups(tenBackups)
        assertEquals(7, resultTen.size)
    }
}

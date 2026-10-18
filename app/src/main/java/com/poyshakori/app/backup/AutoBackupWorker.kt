package com.poyshakori.app.backup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.poyshakori.app.data.database.AppDatabase
import com.poyshakori.app.data.entity.BackupLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext

        if (!AutoBackupManager.isAutoBackupEnabled(context)) {
            return@withContext Result.success()
        }

        val folderUri = AutoBackupManager.getBackupFolderUri(context)
        if (folderUri == null) {
            val errorMsg = "Backup folder not selected"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        val folderDoc = try {
            DocumentFile.fromTreeUri(context, folderUri)
        } catch (e: Exception) {
            null
        }

        if (folderDoc == null || !folderDoc.exists() || !folderDoc.canWrite()) {
            val errorMsg = "Folder unavailable: Permission revoked or external drive disconnected"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        val password = AutoBackupKeystoreHelper.getStoredBackupPassword(context)
        if (password.isNullOrBlank()) {
            val errorMsg = "Encryption credentials not configured"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        // Flush active SQLite WAL transactions before backing up
        BackupManager.checkpointDatabase(context)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "poyshakori_backup_$timestamp.pbackup"

        val backupDoc = try {
            folderDoc.createFile("application/octet-stream", fileName)
        } catch (e: Exception) {
            null
        }

        if (backupDoc == null) {
            val errorMsg = "Unable to create backup file in target directory"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        // Export encrypted backup directly to SAF OutputStream
        val writeOk = try {
            val os = context.contentResolver.openOutputStream(backupDoc.uri)
            if (os != null) {
                BackupManager.exportEncryptedBackup(context, password, os)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        if (!writeOk) {
            backupDoc.delete()
            val errorMsg = "Failed during encryption and disk writing"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        // Immediately verify backup integrity before confirming success
        val verifyResult = try {
            val verifyIn = context.contentResolver.openInputStream(backupDoc.uri)
            if (verifyIn != null) {
                verifyIn.use { BackupManager.verifyBackupIntegrity(password, it) }
            } else {
                BackupManager.VerificationResult(
                    BackupManager.VerificationStatus.ERROR,
                    "Failed to read written backup for verification"
                )
            }
        } catch (e: Exception) {
            BackupManager.VerificationResult(
                BackupManager.VerificationStatus.ERROR,
                e.message ?: "Verification error"
            )
        }

        if (verifyResult.status != BackupManager.VerificationStatus.VALID) {
            // Delete corrupt file immediately to protect storage integrity
            backupDoc.delete()
            val errorMsg = "Integrity verification failed: ${verifyResult.details}"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        // Record successful backup in database
        val fileSize = backupDoc.length()
        val successNotes = "Verified (POYSHAKORI_ENC_V2, ${fileSize / 1024} KB)"
        recordLog(fileName, "SUCCESS", backupDoc.uri.toString(), successNotes)

        AutoBackupManager.recordSuccessfulBackup(context, fileName)

        // Safe retention cleanup: keep recent 7 backups, never delete only valid backup
        applyRetentionPolicy(folderDoc)

        Result.success()
    }

    private suspend fun recordLog(
        filename: String,
        status: String,
        uriString: String? = null,
        notes: String? = null
    ) {
        try {
            val log = BackupLogEntity(
                filename = filename,
                uriString = uriString,
                backupType = "AUTO_BACKUP",
                timestamp = System.currentTimeMillis(),
                status = status,
                notes = notes
            )
            AppDatabase.getDatabase(applicationContext).backupLogDao().insertLog(log)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyRetentionPolicy(folderDoc: DocumentFile) {
        try {
            val files = folderDoc.listFiles()
                .filter { it.isFile && (it.name?.startsWith("poyshakori_") == true) && (it.name?.endsWith(".pbackup") == true || it.name?.endsWith(".bak") == true) }
                .sortedByDescending { it.lastModified() }

            val maxRetention = 7
            if (files.size > maxRetention) {
                // Delete older files beyond retention limit, guaranteeing at least 2 most recent remain
                for (i in maxRetention until files.size) {
                    files[i].delete()
                }
            }

            // Update remaining backup count
            val remaining = folderDoc.listFiles()
                .count { it.isFile && (it.name?.startsWith("poyshakori_") == true) && (it.name?.endsWith(".pbackup") == true || it.name?.endsWith(".bak") == true) }
            AutoBackupManager.updateBackupCount(applicationContext, remaining)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

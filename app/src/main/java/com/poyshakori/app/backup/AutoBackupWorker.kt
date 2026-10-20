package com.poyshakori.app.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.poyshakori.app.data.database.AppDatabase
import com.poyshakori.app.data.entity.BackupLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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

        // Single unified backup file for automatic backups
        val fileName = "poyshakori_auto_backup.pbackup"

        // 1. First encrypt backup to an internal cache temp file to guarantee atomic write and zero corruption
        val tempFile = File(context.cacheDir, "temp_auto_backup.pbackup")
        val writeTempOk = try {
            FileOutputStream(tempFile).use { fos ->
                BackupManager.exportEncryptedBackup(context, password, fos)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        if (!writeTempOk || !tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            val errorMsg = "Failed during database encryption"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        // 2. Immediately verify backup integrity on temp file before updating the target file
        val verifyResult = try {
            FileInputStream(tempFile).use { fis ->
                BackupManager.verifyBackupIntegrity(password, fis)
            }
        } catch (e: Exception) {
            BackupManager.VerificationResult(
                BackupManager.VerificationStatus.ERROR,
                e.message ?: "Verification error"
            )
        }

        if (verifyResult.status != BackupManager.VerificationStatus.VALID) {
            tempFile.delete()
            val errorMsg = "Integrity verification failed: ${verifyResult.details}"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        // 3. Find existing single backup file or create it in target directory
        var backupDoc = folderDoc.findFile(fileName)
        if (backupDoc == null) {
            backupDoc = try {
                folderDoc.createFile("application/octet-stream", fileName)
            } catch (e: Exception) {
                null
            }
        }

        if (backupDoc == null) {
            tempFile.delete()
            val errorMsg = "Unable to create backup file in target directory"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        // 4. Copy verified temp backup to the single SAF destination
        val writeSafOk = try {
            val os = context.contentResolver.openOutputStream(backupDoc.uri, "wt")
                ?: context.contentResolver.openOutputStream(backupDoc.uri)
            if (os != null) {
                os.use { out ->
                    FileInputStream(tempFile).use { input ->
                        input.copyTo(out)
                        out.flush()
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempFile.delete()
        }

        if (!writeSafOk) {
            val errorMsg = "Failed writing backup to target destination"
            AutoBackupManager.updateLastBackupStatus(context, "FAILED: $errorMsg")
            recordLog(errorMsg, "FAILED")
            return@withContext Result.failure()
        }

        // 5. Clean up any previous separate/timestamped auto backup files so only ONE single file remains
        cleanOldAutoBackupFiles(folderDoc, backupDoc.uri)

        // Record successful backup in database
        val fileSize = backupDoc.length()
        val successNotes = "Verified (POYSHAKORI_ENC_V2, ${fileSize / 1024} KB)"
        recordLog(fileName, "SUCCESS", backupDoc.uri.toString(), successNotes)

        AutoBackupManager.recordSuccessfulBackup(context, fileName)

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

    private fun cleanOldAutoBackupFiles(folderDoc: DocumentFile, currentBackupUri: Uri) {
        try {
            val files = folderDoc.listFiles()
            for (file in files) {
                if (file.isFile && file.uri != currentBackupUri) {
                    val name = file.name ?: ""
                    // Delete previous separate timestamped auto-backups or duplicate files
                    if ((name.startsWith("poyshakori_backup_") || name.startsWith("poyshakori_auto_backup")) &&
                        (name.endsWith(".pbackup") || name.endsWith(".bak"))) {
                        file.delete()
                    }
                }
            }
            AutoBackupManager.updateBackupCount(applicationContext, 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.poyshakori.app.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object AutoBackupManager {

    private const val PREFS_NAME = "poyshakori_auto_backup_prefs"
    private const val KEY_ENABLED = "auto_backup_enabled"
    private const val KEY_FOLDER_URI = "auto_backup_folder_uri"
    private const val KEY_FOLDER_NAME = "auto_backup_folder_name"
    private const val KEY_FREQUENCY = "auto_backup_frequency"
    private const val KEY_BACKUP_ON_CHANGES = "auto_backup_on_changes"
    private const val KEY_LAST_TIMESTAMP = "auto_backup_last_time"
    private const val KEY_LAST_STATUS = "auto_backup_last_status"
    private const val KEY_LAST_FILENAME = "auto_backup_last_filename"
    private const val KEY_BACKUP_COUNT = "auto_backup_file_count"

    const val FREQ_DAILY = "DAILY"
    const val FREQ_3_DAYS = "3_DAYS"
    const val FREQ_WEEKLY = "WEEKLY"

    private const val WORK_NAME_PERIODIC = "poyshakori_periodic_backup"
    private const val WORK_NAME_IMMEDIATE = "poyshakori_immediate_backup"
    private const val WORK_NAME_CHANGE = "poyshakori_change_backup"

    fun isAutoBackupEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (enabled) {
            schedulePeriodicBackup(context)
        } else {
            cancelPeriodicBackup(context)
        }
    }

    fun getBackupFolderUri(context: Context): Uri? {
        val uriStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            null
        }
    }

    fun setBackupFolderUri(context: Context, uri: Uri, displayName: String? = null) {
        val name = displayName ?: try {
            DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "Selected Folder"
        } catch (e: Exception) {
            "Selected Folder"
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .putString(KEY_FOLDER_NAME, name)
            .apply()
    }

    fun getFolderDisplayName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_NAME, "No folder selected") ?: "No folder selected"
    }

    fun getFrequency(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FREQUENCY, FREQ_DAILY) ?: FREQ_DAILY
    }

    fun setFrequency(context: Context, frequency: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FREQUENCY, frequency)
            .apply()

        if (isAutoBackupEnabled(context)) {
            schedulePeriodicBackup(context)
        }
    }

    fun isBackupOnChangesEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKUP_ON_CHANGES, true)
    }

    fun setBackupOnChangesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BACKUP_ON_CHANGES, enabled)
            .apply()
    }

    fun getLastBackupTimestamp(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_TIMESTAMP, 0L)
    }

    fun getLastBackupStatus(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_STATUS, "Not started") ?: "Not started"
    }

    fun getLastBackupFilename(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_FILENAME, "") ?: ""
    }

    fun getBackupCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_BACKUP_COUNT, 0)
    }

    fun updateLastBackupStatus(context: Context, status: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_STATUS, status)
            .apply()
    }

    fun updateBackupCount(context: Context, count: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BACKUP_COUNT, count)
            .apply()
    }

    fun recordSuccessfulBackup(context: Context, filename: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_LAST_STATUS, "SUCCESS")
            .putString(KEY_LAST_FILENAME, filename)
            .apply()
    }

    fun schedulePeriodicBackup(context: Context) {
        val freq = getFrequency(context)
        val days = when (freq) {
            FREQ_3_DAYS -> 3L
            FREQ_WEEKLY -> 7L
            else -> 1L
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    fun cancelPeriodicBackup(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
    }

    fun triggerImmediateBackup(context: Context) {
        val oneTimeWork = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            oneTimeWork
        )
    }

    fun triggerDebouncedBackupOnDataChange(context: Context) {
        if (!isAutoBackupEnabled(context) || !isBackupOnChangesEnabled(context)) return

        val debouncedWork = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_CHANGE,
            ExistingWorkPolicy.REPLACE,
            debouncedWork
        )
    }

    fun verifySelectedFolderAccessibility(context: Context): Boolean {
        val uri = getBackupFolderUri(context) ?: return false
        return try {
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.exists() && doc.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    fun getNextScheduledTimeString(context: Context): String {
        if (!isAutoBackupEnabled(context)) return "Disabled"
        val last = getLastBackupTimestamp(context)
        val freq = getFrequency(context)
        val daysMs = when (freq) {
            FREQ_3_DAYS -> 3L * 24 * 60 * 60 * 1000L
            FREQ_WEEKLY -> 7L * 24 * 60 * 60 * 1000L
            else -> 1L * 24 * 60 * 60 * 1000L
        }

        val nextTime = if (last > 0) last + daysMs else System.currentTimeMillis() + daysMs
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(nextTime))
    }
}

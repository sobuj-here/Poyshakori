package com.poyshakori.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.poyshakori.app.R
import com.poyshakori.app.backup.AutoBackupKeystoreHelper
import com.poyshakori.app.backup.AutoBackupManager
import com.poyshakori.app.backup.BackupManager
import com.poyshakori.app.backup.BackupSecurityHelper
import com.poyshakori.app.data.entity.CategoryEntity
import com.poyshakori.app.notification.DebtReminderHelper
import com.poyshakori.app.security.SecurityManager
import com.poyshakori.app.ui.dashboard.MainViewModel
import com.poyshakori.app.util.CategoryHelper
import com.poyshakori.app.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingExportPassword = ""
    private var pendingVerifyPassword = ""

    private val selectBackupFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val docFile = DocumentFile.fromTreeUri(this, uri)
            val name = docFile?.name ?: "Selected Folder"
            AutoBackupManager.setBackupFolderUri(this, uri, name)
            Toast.makeText(this, getString(R.string.folder_permission_persisted), Toast.LENGTH_SHORT).show()
            updateAutoBackupUi()
        }
    }

    private val exportBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && pendingExportPassword.isNotEmpty()) {
            executeBackupExportToUri(uri, pendingExportPassword)
        }
    }

    private val restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            promptPasswordAndPreviewRestore(uri)
        }
    }

    private val verifyBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pendingVerifyPassword.isNotEmpty()) {
            executeBackupVerifyFromUri(uri, pendingVerifyPassword)
        }
    }

    private val importCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            promptAccountAndImportCsv(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupViews()
    }

    private fun setupViews() {
        val prefs = getSharedPreferences("poyshakori_prefs", Context.MODE_PRIVATE)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Section 1: Appearance & Language
        val tvCurrentTheme = findViewById<TextView>(R.id.tvCurrentThemeLabel)
        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        tvCurrentTheme.text = when (savedTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.theme_dark)
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.theme_light)
            else -> getString(R.string.theme_system)
        }

        findViewById<View>(R.id.cardThemeSetting).setOnClickListener {
            showThemeSelectionDialog(tvCurrentTheme)
        }

        val tvCurrentLang = findViewById<TextView>(R.id.tvCurrentLanguageLabel)
        val savedLang = LocaleHelper.getLanguage(this)
        tvCurrentLang.text = if (savedLang == "bn") getString(R.string.lang_bangla) else getString(R.string.lang_english)

        findViewById<View>(R.id.cardLanguageSetting).setOnClickListener {
            showLanguageSelectionDialog(tvCurrentLang)
        }

        // Section 2: Security & App Lock
        val switchBiometric = findViewById<SwitchCompat>(R.id.switchBiometricLock)
        switchBiometric.isChecked = SecurityManager.isAppLockEnabled(this)
        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!SecurityManager.isAuthenticationAvailable(this)) {
                    showSetupAppLockDialog(switchBiometric)
                } else {
                    SecurityManager.setAppLockEnabled(this, true)
                    Toast.makeText(this, getString(R.string.app_lock_enabled), Toast.LENGTH_SHORT).show()
                }
            } else {
                SecurityManager.setAppLockEnabled(this, false)
                Toast.makeText(this, getString(R.string.app_lock_disabled), Toast.LENGTH_SHORT).show()
            }
        }

        val tvLockTimeout = findViewById<TextView>(R.id.tvLockTimeoutValue)
        fun updateTimeoutLabel() {
            val sec = SecurityManager.getLockTimeoutSeconds(this)
            tvLockTimeout.text = when (sec) {
                15 -> getString(R.string.lock_15_seconds)
                60 -> getString(R.string.lock_1_minute)
                300 -> getString(R.string.lock_5_minutes)
                else -> getString(R.string.lock_immediately)
            }
        }
        updateTimeoutLabel()
        findViewById<View>(R.id.cardLockTimeout).setOnClickListener {
            showLockTimeoutDialog { updateTimeoutLabel() }
        }

        val tvAppPinStatus = findViewById<TextView>(R.id.tvAppPinStatus)
        fun updatePinStatusLabel() {
            if (SecurityManager.hasPin(this)) {
                tvAppPinStatus.text = getString(R.string.pin_saved)
                tvAppPinStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))
            } else {
                tvAppPinStatus.text = getString(R.string.set_in_app_pin_sub)
                tvAppPinStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            }
        }
        updatePinStatusLabel()
        findViewById<View>(R.id.cardAppPin).setOnClickListener {
            showAppPinManagementDialog {
                updatePinStatusLabel()
                if (SecurityManager.hasPin(this) && !switchBiometric.isChecked) {
                    switchBiometric.isChecked = true
                }
            }
        }

        // Section 3: Automatic Encrypted Backup & Manual Backup
        val switchAutoBackup = findViewById<SwitchCompat>(R.id.switchAutoBackup)
        switchAutoBackup?.isChecked = AutoBackupManager.isAutoBackupEnabled(this)
        switchAutoBackup?.setOnClickListener {
            val isChecked = switchAutoBackup.isChecked
            if (isChecked) {
                if (!BackupSecurityHelper.hasConfiguredBackupPassword(this)) {
                    switchAutoBackup.isChecked = false
                    Toast.makeText(this, getString(R.string.setup_backup_pwd_required), Toast.LENGTH_LONG).show()
                    showBackupPasswordSecurityDialog()
                    return@setOnClickListener
                }
                if (AutoBackupManager.getBackupFolderUri(this) == null) {
                    switchAutoBackup.isChecked = false
                    Toast.makeText(this, getString(R.string.select_backup_folder), Toast.LENGTH_SHORT).show()
                    selectBackupFolderLauncher.launch(null)
                    return@setOnClickListener
                }
                if (!AutoBackupKeystoreHelper.hasStoredPassword(this)) {
                    promptPasswordForKeystoreSync {
                        AutoBackupManager.setAutoBackupEnabled(this, true)
                        updateAutoBackupUi()
                    }
                    return@setOnClickListener
                }
                AutoBackupManager.setAutoBackupEnabled(this, true)
                updateAutoBackupUi()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.auto_backup_title))
                    .setMessage(getString(R.string.disable_auto_backup_confirm))
                    .setPositiveButton(getString(R.string.btn_ok)) { _, _ ->
                        AutoBackupManager.setAutoBackupEnabled(this, false)
                        updateAutoBackupUi()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                        switchAutoBackup.isChecked = true
                    }
                    .show()
            }
        }

        findViewById<View>(R.id.btnSelectBackupFolder)?.setOnClickListener {
            selectBackupFolderLauncher.launch(null)
        }

        findViewById<View>(R.id.cardBackupFrequency)?.setOnClickListener {
            showBackupFrequencyDialog()
        }

        val switchBackupOnChanges = findViewById<SwitchCompat>(R.id.switchBackupOnChanges)
        switchBackupOnChanges?.isChecked = AutoBackupManager.isBackupOnChangesEnabled(this)
        switchBackupOnChanges?.setOnCheckedChangeListener { _, isChecked ->
            AutoBackupManager.setBackupOnChangesEnabled(this, isChecked)
        }

        findViewById<View>(R.id.btnAutoBackupNow)?.setOnClickListener {
            triggerAutoBackupNow()
        }

        findViewById<View>(R.id.btnBackupSecurity).setOnClickListener {
            showBackupPasswordSecurityDialog()
        }

        findViewById<View>(R.id.btnBackupEncrypted).setOnClickListener {
            promptPasswordForExport()
        }

        findViewById<View>(R.id.btnRestoreEncrypted).setOnClickListener {
            try {
                restoreBackupLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open file picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnVerifyBackup).setOnClickListener {
            promptPasswordForVerify()
        }

        updateAutoBackupUi()

        // Section 4: Notifications & Reminders
        val switchDebtReminders = findViewById<SwitchCompat>(R.id.switchDebtReminders)
        switchDebtReminders.isChecked = prefs.getBoolean("debt_reminders_enabled", true)
        switchDebtReminders.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("debt_reminders_enabled", isChecked).apply()
            if (isChecked) {
                DebtReminderHelper.scheduleDailyDebtCheck(this)
            } else {
                DebtReminderHelper.cancelDailyDebtCheck(this)
            }
        }

        val switchBillReminders = findViewById<SwitchCompat>(R.id.switchBillReminders)
        switchBillReminders.isChecked = prefs.getBoolean("bill_reminders_enabled", true)
        switchBillReminders.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("bill_reminders_enabled", isChecked).apply()
        }

        // Section 5: Data Management & Recycle Bin
        val tvRecycleStatus = findViewById<TextView>(R.id.tvRecycleBinStatus)
        val btnOpenRecycle = findViewById<MaterialButton>(R.id.btnOpenRecycleBin)

        btnOpenRecycle.setOnClickListener {
            startActivity(Intent(this, RecycleBinActivity::class.java))
        }

        findViewById<View>(R.id.btnManageCategories).setOnClickListener {
            showManageCategoriesDialog()
        }

        findViewById<View>(R.id.btnImportCsv).setOnClickListener {
            try {
                importCsvLauncher.launch(arrayOf("text/*", "*/*"))
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open file picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            viewModel.recycledItemCount.collectLatest { count ->
                if (count == 0) {
                    tvRecycleStatus.text = getString(R.string.recycle_bin_empty)
                } else {
                    tvRecycleStatus.text = getString(R.string.recycle_bin_count, count)
                }
            }
        }
    }

    private fun showThemeSelectionDialog(tvCurrentTheme: TextView) {
        val prefs = getSharedPreferences("poyshakori_prefs", Context.MODE_PRIVATE)
        val currentTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val options = arrayOf(
            getString(R.string.theme_dark),
            getString(R.string.theme_light),
            getString(R.string.theme_system)
        )
        val checkedItem = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> 0
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            else -> 2
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.theme_setting))
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val newMode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_YES
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                prefs.edit().putInt("app_theme", newMode).apply()
                AppCompatDelegate.setDefaultNightMode(newMode)
                tvCurrentTheme.text = options[which]
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showLanguageSelectionDialog(tvCurrentLang: TextView) {
        val currentLang = LocaleHelper.getLanguage(this)
        val options = arrayOf(getString(R.string.lang_english), getString(R.string.lang_bangla))
        val checkedItem = if (currentLang == "bn") 1 else 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.language_setting))
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val newLang = if (which == 1) "bn" else "en"
                if (newLang != currentLang) {
                    LocaleHelper.setLanguage(this, newLang)
                    tvCurrentLang.text = options[which]
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showSetupAppLockDialog(switchBiometric: SwitchCompat) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.biometric_lock))
            .setMessage(getString(R.string.device_lock_not_setup_msg))
            .setPositiveButton(getString(R.string.set_in_app_pin)) { _, _ ->
                showSetPinDialog {
                    switchBiometric.isChecked = true
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                switchBiometric.isChecked = false
            }
            .setCancelable(false)
            .show()
    }

    private fun showLockTimeoutDialog(onUpdated: () -> Unit) {
        val currentTimeout = SecurityManager.getLockTimeoutSeconds(this)
        val options = arrayOf(
            getString(R.string.lock_immediately),
            getString(R.string.lock_15_seconds),
            getString(R.string.lock_1_minute),
            getString(R.string.lock_5_minutes)
        )
        val values = intArrayOf(0, 15, 60, 300)
        val checkedIndex = when (currentTimeout) {
            15 -> 1
            60 -> 2
            300 -> 3
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.lock_timeout_title))
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                SecurityManager.setLockTimeoutSeconds(this, values[which])
                onUpdated()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAppPinManagementDialog(onUpdated: () -> Unit) {
        if (!SecurityManager.hasPin(this)) {
            showSetPinDialog(onUpdated)
        } else {
            val options = arrayOf(
                getString(R.string.change_in_app_pin),
                getString(R.string.remove_in_app_pin)
            )
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.set_in_app_pin))
                .setItems(options) { _, which ->
                    if (which == 0) {
                        promptCurrentPin {
                            showSetPinDialog(onUpdated)
                        }
                    } else {
                        promptCurrentPin {
                            SecurityManager.removePin(this)
                            Toast.makeText(this, getString(R.string.pin_removed), Toast.LENGTH_SHORT).show()
                            onUpdated()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    private fun promptCurrentPin(onSuccess: () -> Unit) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }
        val etPin = EditText(this).apply {
            hint = getString(R.string.pin_enter_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }
        layout.addView(etPin)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pin_enter_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                val entered = etPin.text.toString().trim()
                if (SecurityManager.verifyPin(this, entered)) {
                    onSuccess()
                } else {
                    Toast.makeText(this, getString(R.string.pin_incorrect), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showSetPinDialog(onSuccess: () -> Unit) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }
        val tvDesc = TextView(this).apply {
            text = getString(R.string.pin_setup_desc)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvDesc)

        val etPin = EditText(this).apply {
            hint = "4-digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }
        layout.addView(etPin)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pin_setup_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_proceed)) { _, _ ->
                val pin1 = etPin.text.toString().trim()
                if (pin1.length != 4) {
                    Toast.makeText(this, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                showConfirmPinDialog(pin1, onSuccess)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showConfirmPinDialog(firstPin: String, onSuccess: () -> Unit) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }
        val tvDesc = TextView(this).apply {
            text = getString(R.string.pin_confirm_desc)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvDesc)

        val etPin = EditText(this).apply {
            hint = "Re-enter 4-digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }
        layout.addView(etPin)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pin_setup_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val pin2 = etPin.text.toString().trim()
                if (pin2 != firstPin) {
                    Toast.makeText(this, getString(R.string.pin_mismatch), Toast.LENGTH_SHORT).show()
                } else {
                    SecurityManager.setPin(this, pin2)
                    SecurityManager.setAppLockEnabled(this, true)
                    Toast.makeText(this, getString(R.string.pin_saved), Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun promptPasswordForExport() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etPassword = EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val tvNote = TextView(this).apply {
            text = getString(R.string.forgot_password_warning)
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_muted))
            setPadding(0, 10, 0, 0)
        }
        layout.addView(etPassword)
        layout.addView(tvNote)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_backup_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_proceed)) { _, _ ->
                val password = etPassword.text.toString().trim()
                if (password.length < 6) {
                    Toast.makeText(this, getString(R.string.password_min_length), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                showBackupDestinationDialog(password)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showBackupDestinationDialog(password: String) {
        val options = arrayOf(
            getString(R.string.backup_share_option),
            getString(R.string.backup_saf_option)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_backup_method))
            .setItems(options) { _, which ->
                if (which == 0) {
                    // Option 1: Share / Send (Universal, never crashes)
                    executeShareBackup(password)
                } else {
                    // Option 2: SAF Storage Access
                    pendingExportPassword = password
                    try {
                        exportBackupLauncher.launch("poyshakori_backup.pbackup")
                    } catch (e: Exception) {
                        Toast.makeText(this, "Storage access unavailable. Using direct export...", Toast.LENGTH_SHORT).show()
                        executeShareBackup(password)
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun executeShareBackup(password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val backupFile = BackupManager.exportEncryptedBackupToFile(this@SettingsActivity, password)
            withContext(Dispatchers.Main) {
                if (backupFile != null) {
                    viewModel.logBackupOperation(backupFile.name, backupFile.absolutePath, "EXPORT", "SUCCESS")
                    BackupManager.shareBackupFile(this@SettingsActivity, backupFile)
                } else {
                    viewModel.logBackupOperation("backup_failed.bak", null, "EXPORT", "FAILED")
                    Toast.makeText(this@SettingsActivity, getString(R.string.backup_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun executeBackupExportToUri(uri: Uri, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, getString(R.string.backup_failed), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val success = BackupManager.exportEncryptedBackup(this@SettingsActivity, password, outputStream)
                withContext(Dispatchers.Main) {
                    if (success) {
                        viewModel.logBackupOperation("poyshakori_backup.pbackup", uri.toString(), "EXPORT", "SUCCESS")
                        Toast.makeText(this@SettingsActivity, getString(R.string.backup_success), Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.logBackupOperation("poyshakori_backup.pbackup", uri.toString(), "EXPORT", "FAILED")
                        Toast.makeText(this@SettingsActivity, getString(R.string.backup_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.backup_failed) + ": ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun promptPasswordAndPreviewRestore(uri: Uri) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etPassword = EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        layout.addView(etPassword)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_backup_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_proceed)) { _, _ ->
                val password = etPassword.text.toString().trim()
                if (password.isEmpty()) {
                    Toast.makeText(this, getString(R.string.password_min_length), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val inputStream: InputStream? = contentResolver.openInputStream(uri)
                        if (inputStream == null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SettingsActivity, getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        val (preview, errorMsg) = BackupManager.decryptAndPrepareRestorePreview(this@SettingsActivity, password, inputStream)
                        withContext(Dispatchers.Main) {
                            if (preview != null) {
                                showRestorePreviewDialog(preview, uri)
                            } else {
                                viewModel.logBackupOperation("restore_failed.bak", uri.toString(), "RESTORE", "FAILED")
                                AlertDialog.Builder(this@SettingsActivity)
                                    .setTitle(getString(R.string.restore_backup_title))
                                    .setMessage(getString(R.string.restore_failed) + "\n\n" + (errorMsg ?: ""))
                                    .setPositiveButton(getString(R.string.btn_ok), null)
                                    .show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showRestorePreviewDialog(preview: BackupManager.RestorePreview, uri: Uri) {
        val message = getString(
            R.string.restore_preview_content,
            preview.accountCount,
            preview.transactionCount,
            preview.debtCount,
            preview.budgetCount,
            preview.schemaVersion
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_preview_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val applied = BackupManager.applyPreparedRestore(this@SettingsActivity, preview)
                    withContext(Dispatchers.Main) {
                        if (applied) {
                            Toast.makeText(this@SettingsActivity, getString(R.string.restore_success), Toast.LENGTH_LONG).show()

                            // Restart app cleanly to reload Room DB with zero stale connections or closed pool errors
                            val restartIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            finishAffinity()
                            if (restartIntent != null) {
                                startActivity(restartIntent)
                            }
                            Runtime.getRuntime().exit(0)
                        } else {
                            try {
                                viewModel.logBackupOperation("restored_backup.bak", uri.toString(), "RESTORE", "FAILED")
                            } catch (_: Exception) {}
                            Toast.makeText(this@SettingsActivity, getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                preview.tempRestoreFile.delete()
            }
            .setOnCancelListener {
                preview.tempRestoreFile.delete()
            }
            .show()
    }

    private fun promptPasswordForVerify() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etPassword = EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        layout.addView(etPassword)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.verify_backup_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_proceed)) { _, _ ->
                val password = etPassword.text.toString().trim()
                pendingVerifyPassword = password
                try {
                    verifyBackupLauncher.launch(arrayOf("*/*"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open file picker: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun executeBackupVerifyFromUri(uri: Uri, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, getString(R.string.verify_failed, "Cannot open file"), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val result = BackupManager.verifyBackupIntegrity(password, inputStream)
                withContext(Dispatchers.Main) {
                    if (result.status == BackupManager.VerificationStatus.VALID) {
                        viewModel.logBackupOperation("verified_backup.bak", uri.toString(), "INTEGRITY_CHECK", "SUCCESS")
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(getString(R.string.verify_backup_title))
                            .setMessage(getString(R.string.verify_success) + "\n\n" + result.details)
                            .setPositiveButton(getString(R.string.btn_ok), null)
                            .show()
                    } else {
                        viewModel.logBackupOperation("verified_backup.bak", uri.toString(), "INTEGRITY_CHECK", "FAILED")
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(getString(R.string.verify_backup_title))
                            .setMessage(getString(R.string.verify_failed, result.details))
                            .setPositiveButton(getString(R.string.btn_ok), null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.verify_failed, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun promptPasswordForAutoBackup() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etPassword = EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        layout.addView(etPassword)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_auto_backup))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_proceed)) { _, _ ->
                val password = etPassword.text.toString().trim()
                if (password.length < 6) {
                    Toast.makeText(this, getString(R.string.password_min_length), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val file = BackupManager.autoBackupToAppStorage(this@SettingsActivity, password)
                    withContext(Dispatchers.Main) {
                        if (file != null) {
                            viewModel.logBackupOperation(file.name, file.absolutePath, "AUTO_BACKUP", "SUCCESS")
                            AlertDialog.Builder(this@SettingsActivity)
                                .setTitle(getString(R.string.btn_auto_backup))
                                .setMessage(getString(R.string.auto_backup_success, file.name))
                                .setPositiveButton(getString(R.string.btn_ok), null)
                                .show()
                        } else {
                            Toast.makeText(this@SettingsActivity, getString(R.string.backup_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun promptAccountAndImportCsv(uri: Uri) {
        lifecycleScope.launch {
            val accounts = viewModel.allAccounts.firstOrNull() ?: emptyList()
            if (accounts.isEmpty()) {
                Toast.makeText(this@SettingsActivity, getString(R.string.valid_account_error), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val accountNames = accounts.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(getString(R.string.import_csv_title))
                .setItems(accountNames) { _, which ->
                    val selectedAccount = accounts[which]
                    viewModel.importTransactionsFromCsv(this@SettingsActivity, uri, selectedAccount.id) { count, error ->
                        if (error != null) {
                            Toast.makeText(this@SettingsActivity, getString(R.string.import_csv_failed, error), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@SettingsActivity, getString(R.string.import_csv_success, count), Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    private fun showManageCategoriesDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_categories, null, false)
        val rvCategories = dialogView.findViewById<RecyclerView>(R.id.rvManageCategories)
        val btnAddCategory = dialogView.findViewById<MaterialButton>(R.id.btnAddCategoryPrompt)
        val btnClose = dialogView.findViewById<View>(R.id.btnCloseCategories)

        rvCategories.layoutManager = LinearLayoutManager(this)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        class CategoryAdapter(
            private var items: List<CategoryEntity>,
            private val onDeleteClick: (CategoryEntity) -> Unit
        ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

            inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
                val tvName: TextView = v.findViewById(R.id.tvCategoryName)
                val tvType: TextView = v.findViewById(R.id.tvCategoryType)
                val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteCategory)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_manage, parent, false)
                return ViewHolder(view)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val item = items[position]
                holder.tvName.text = CategoryHelper.getDisplayName(holder.itemView.context, item.name)
                holder.tvType.text = if (item.type == "INCOME") holder.itemView.context.getString(R.string.category_type_income) else holder.itemView.context.getString(R.string.category_type_expense)
                holder.btnDelete.setOnClickListener {
                    onDeleteClick(item)
                }
            }

            override fun getItemCount(): Int = items.size

            fun updateList(newList: List<CategoryEntity>) {
                items = newList
                notifyDataSetChanged()
            }
        }

        val adapter = CategoryAdapter(emptyList()) { category ->
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_category_confirm, category.name))
                .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                    viewModel.deleteCategory(category) {
                        Toast.makeText(this, getString(R.string.category_deleted), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
        rvCategories.adapter = adapter

        lifecycleScope.launch {
            viewModel.allCategories.collectLatest { list ->
                adapter.updateList(list)
            }
        }

        btnAddCategory.setOnClickListener {
            showAddCategoryPrompt()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAddCategoryPrompt() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etCategoryName = EditText(this).apply {
            hint = getString(R.string.category_name_hint)
        }

        val rgType = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        val rbExpense = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.category_type_expense)
            isChecked = true
        }
        val rbIncome = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.category_type_income)
        }
        rgType.addView(rbExpense)
        rgType.addView(rbIncome)

        layout.addView(etCategoryName)
        layout.addView(rgType)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_category))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val name = etCategoryName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.valid_category_error), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val type = if (rbIncome.isChecked) "INCOME" else "EXPENSE"
                val newCategory = CategoryEntity(name = name, type = type)
                viewModel.insertCategory(newCategory) {
                    Toast.makeText(this, getString(R.string.category_added, name), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showBackupPasswordSecurityDialog() {
        val hasConfigured = BackupSecurityHelper.hasConfiguredBackupPassword(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        var etCurrent: EditText? = null
        if (hasConfigured) {
            etCurrent = EditText(this).apply {
                hint = getString(R.string.current_password_hint)
                transformationMethod = PasswordTransformationMethod.getInstance()
            }
            layout.addView(etCurrent)
        }

        val etNew = EditText(this).apply {
            hint = getString(R.string.new_password_hint)
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val etConfirm = EditText(this).apply {
            hint = getString(R.string.confirm_new_password_hint)
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val cbShow = CheckBox(this).apply {
            text = "Show password"
            setOnCheckedChangeListener { _, isChecked ->
                val method = if (isChecked) null else PasswordTransformationMethod.getInstance()
                etCurrent?.transformationMethod = method
                etNew.transformationMethod = method
                etConfirm.transformationMethod = method
            }
        }
        val tvWarnings = TextView(this).apply {
            text = getString(R.string.forgot_password_warning) + "\n\n" + getString(R.string.old_backups_warning)
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_muted))
            setPadding(0, 10, 0, 0)
        }

        layout.addView(etNew)
        layout.addView(etConfirm)
        layout.addView(cbShow)
        layout.addView(tvWarnings)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_backup_security))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val newPass = etNew.text.toString().trim()
                val confirmPass = etConfirm.text.toString().trim()

                if (newPass.length < 6) {
                    Toast.makeText(this, getString(R.string.password_min_length), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass != confirmPass) {
                    Toast.makeText(this, getString(R.string.passwords_do_not_match), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (hasConfigured) {
                    val currentPass = etCurrent?.text?.toString()?.trim().orEmpty()
                    val changed = BackupSecurityHelper.changeBackupPassword(this, currentPass, newPass)
                    if (changed) {
                        AutoBackupKeystoreHelper.storeBackupPasswordSecurely(this, newPass)
                        Toast.makeText(this, getString(R.string.password_changed), Toast.LENGTH_SHORT).show()
                        updateAutoBackupUi()
                    } else {
                        Toast.makeText(this, getString(R.string.incorrect_current_password), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    BackupSecurityHelper.setBackupPassword(this, newPass)
                    AutoBackupKeystoreHelper.storeBackupPasswordSecurely(this, newPass)
                    Toast.makeText(this, getString(R.string.password_changed), Toast.LENGTH_SHORT).show()
                    updateAutoBackupUi()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateAutoBackupUi()
    }

    private fun showBackupFrequencyDialog() {
        val current = AutoBackupManager.getFrequency(this)
        val options = arrayOf(
            getString(R.string.backup_freq_daily),
            getString(R.string.backup_freq_3_days),
            getString(R.string.backup_freq_weekly)
        )
        val values = arrayOf(
            AutoBackupManager.FREQ_DAILY,
            AutoBackupManager.FREQ_3_DAYS,
            AutoBackupManager.FREQ_WEEKLY
        )
        val checkedIdx = when (current) {
            AutoBackupManager.FREQ_3_DAYS -> 1
            AutoBackupManager.FREQ_WEEKLY -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.backup_frequency_label))
            .setSingleChoiceItems(options, checkedIdx) { dialog, which ->
                AutoBackupManager.setFrequency(this, values[which])
                updateAutoBackupUi()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun triggerAutoBackupNow() {
        if (AutoBackupManager.getBackupFolderUri(this) == null) {
            Toast.makeText(this, getString(R.string.select_backup_folder), Toast.LENGTH_SHORT).show()
            selectBackupFolderLauncher.launch(null)
            return
        }
        if (!AutoBackupKeystoreHelper.hasStoredPassword(this)) {
            if (!BackupSecurityHelper.hasConfiguredBackupPassword(this)) {
                Toast.makeText(this, getString(R.string.setup_backup_pwd_required), Toast.LENGTH_LONG).show()
                showBackupPasswordSecurityDialog()
                return
            }
            promptPasswordForKeystoreSync {
                AutoBackupManager.triggerImmediateBackup(this)
                Toast.makeText(this, getString(R.string.backup_now_started), Toast.LENGTH_SHORT).show()
                findViewById<View>(R.id.btnAutoBackupNow)?.postDelayed({ updateAutoBackupUi() }, 2000)
            }
            return
        }
        AutoBackupManager.triggerImmediateBackup(this)
        Toast.makeText(this, getString(R.string.backup_now_started), Toast.LENGTH_SHORT).show()
        findViewById<View>(R.id.btnAutoBackupNow)?.postDelayed({ updateAutoBackupUi() }, 2000)
    }

    private fun promptPasswordForKeystoreSync(onSuccess: () -> Unit) {
        val input = EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_backup_security))
            .setMessage("Enter your backup password to authorize automatic background encryption:")
            .setView(input)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val pwd = input.text.toString().trim()
                if (BackupSecurityHelper.verifyPassword(this, pwd)) {
                    AutoBackupKeystoreHelper.storeBackupPasswordSecurely(this, pwd)
                    onSuccess()
                } else {
                    Toast.makeText(this, getString(R.string.incorrect_current_password), Toast.LENGTH_SHORT).show()
                    updateAutoBackupUi()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                updateAutoBackupUi()
            }
            .show()
    }

    private fun updateAutoBackupUi() {
        val switchAuto = findViewById<SwitchCompat>(R.id.switchAutoBackup) ?: return
        val tvFolder = findViewById<TextView>(R.id.tvBackupFolderPath) ?: return
        val tvFreq = findViewById<TextView>(R.id.tvBackupFrequencyValue) ?: return
        val switchChanges = findViewById<SwitchCompat>(R.id.switchBackupOnChanges) ?: return
        val tvLastTime = findViewById<TextView>(R.id.tvLastBackupTime) ?: return
        val tvLastStatus = findViewById<TextView>(R.id.tvLastBackupStatus) ?: return
        val tvNext = findViewById<TextView>(R.id.tvNextBackupScheduled) ?: return
        val tvCount = findViewById<TextView>(R.id.tvBackupFolderCount) ?: return

        switchAuto.isChecked = AutoBackupManager.isAutoBackupEnabled(this)
        tvFolder.text = AutoBackupManager.getFolderDisplayName(this)

        val freq = AutoBackupManager.getFrequency(this)
        tvFreq.text = when (freq) {
            AutoBackupManager.FREQ_3_DAYS -> getString(R.string.backup_freq_3_days)
            AutoBackupManager.FREQ_WEEKLY -> getString(R.string.backup_freq_weekly)
            else -> getString(R.string.backup_freq_daily)
        }

        switchChanges.isChecked = AutoBackupManager.isBackupOnChangesEnabled(this)

        val lastTimestamp = AutoBackupManager.getLastBackupTimestamp(this)
        val lastTimeStr = if (lastTimestamp > 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastTimestamp))
        } else {
            "Never"
        }
        tvLastTime.text = getString(R.string.last_backup_label, lastTimeStr)
        tvLastStatus.text = getString(R.string.last_backup_status_label, AutoBackupManager.getLastBackupStatus(this))
        tvNext.text = getString(R.string.next_backup_label, AutoBackupManager.getNextScheduledTimeString(this))

        val folderUri = AutoBackupManager.getBackupFolderUri(this)
        var count = AutoBackupManager.getBackupCount(this)
        if (folderUri != null) {
            try {
                val doc = DocumentFile.fromTreeUri(this, folderUri)
                if (doc != null && doc.exists()) {
                    val realCount = doc.listFiles().count {
                        it.isFile && (it.name?.startsWith("poyshakori_") == true) &&
                                (it.name?.endsWith(".pbackup") == true || it.name?.endsWith(".bak") == true)
                    }
                    count = realCount
                    AutoBackupManager.updateBackupCount(this, realCount)
                }
            } catch (_: Exception) {}
        }
        tvCount.text = getString(R.string.backup_count_label, count)
    }
}

package com.poyshakori.app.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executor

object SecurityManager {

    private const val PREFS_NAME = "poyshakori_security_prefs"
    private const val MAIN_PREFS_NAME = "poyshakori_prefs"
    private const val KEY_APP_LOCK_ENABLED = "key_app_lock_enabled"
    private const val KEY_BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"
    private const val KEY_LOCK_TIMEOUT_SECONDS = "key_lock_timeout_seconds"
    private const val KEY_APP_PIN_HASH = "key_app_pin_hash"
    private const val KEY_APP_PIN_SALT = "key_app_pin_salt"

    // In-memory unlock state
    @Volatile
    private var isUnlocked: Boolean = false

    @Volatile
    var isPromptShowing: Boolean = false
        private set

    private var lastBackgroundTimestamp: Long = 0L

    fun isAppLockEnabled(context: Context): Boolean {
        val secPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mainPrefs = context.getSharedPreferences(MAIN_PREFS_NAME, Context.MODE_PRIVATE)
        return secPrefs.getBoolean(KEY_APP_LOCK_ENABLED, false) ||
                mainPrefs.getBoolean(KEY_BIOMETRIC_LOCK_ENABLED, false)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        val secPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        secPrefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()

        val mainPrefs = context.getSharedPreferences(MAIN_PREFS_NAME, Context.MODE_PRIVATE)
        mainPrefs.edit().putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, enabled).apply()

        if (!enabled) {
            isUnlocked = true
        } else {
            isUnlocked = false
        }
    }

    fun getLockTimeoutSeconds(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LOCK_TIMEOUT_SECONDS, 0) // Default 0 = immediate lock
    }

    fun setLockTimeoutSeconds(context: Context, seconds: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LOCK_TIMEOUT_SECONDS, seconds).apply()
    }

    // In-App PIN Management
    fun hasPin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hash = prefs.getString(KEY_APP_PIN_HASH, null)
        val salt = prefs.getString(KEY_APP_PIN_SALT, null)
        return !hash.isNullOrEmpty() && !salt.isNullOrEmpty()
    }

    fun setPin(context: Context, pin: String) {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }

        val hashHex = hashPinWithSalt(pin, saltHex)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_APP_PIN_SALT, saltHex)
            .putString(KEY_APP_PIN_HASH, hashHex)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltHex = prefs.getString(KEY_APP_PIN_SALT, null) ?: return false
        val expectedHashHex = prefs.getString(KEY_APP_PIN_HASH, null) ?: return false

        val actualHashHex = hashPinWithSalt(pin, saltHex)
        val isValid = actualHashHex.equals(expectedHashHex, ignoreCase = true)
        if (isValid) {
            unlock()
        }
        return isValid
    }

    fun removePin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_APP_PIN_HASH)
            .remove(KEY_APP_PIN_SALT)
            .apply()
    }

    private fun hashPinWithSalt(pin: String, saltHex: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray(Charsets.UTF_8))
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun unlock() {
        isUnlocked = true
        isPromptShowing = false
    }

    fun lock() {
        isUnlocked = false
        isPromptShowing = false
    }

    fun onAppForegrounded(context: Context) {
        if (!isAppLockEnabled(context)) {
            isUnlocked = true
            return
        }
        val timeoutMs = getLockTimeoutSeconds(context) * 1000L
        val elapsed = System.currentTimeMillis() - lastBackgroundTimestamp
        if (lastBackgroundTimestamp == 0L || elapsed >= timeoutMs) {
            isUnlocked = false
        }
    }

    fun onAppBackgrounded() {
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun isAppCurrentlyLocked(context: Context): Boolean {
        if (!isAppLockEnabled(context)) return false
        return !isUnlocked
    }

    fun isDeviceLockAvailable(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager?.isDeviceSecure == true
        } else {
            keyguardManager?.isKeyguardSecure == true
        }

        val biometricManager = BiometricManager.from(context)
        val canBiometric = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }

        return canBiometric || isDeviceSecure
    }

    fun isAuthenticationAvailable(context: Context): Boolean {
        return isDeviceLockAvailable(context) || hasPin(context)
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Cancel",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isAppLockEnabled(activity) || isUnlocked) {
            isUnlocked = true
            isPromptShowing = false
            onSuccess()
            return
        }

        if (isPromptShowing) {
            return
        }
        isPromptShowing = true

        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isUnlocked = true
                    isPromptShowing = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isPromptShowing = false
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Non-fatal: Allow user to retry biometric touch
                }
            })

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager?.isDeviceSecure == true
        } else {
            keyguardManager?.isKeyguardSecure == true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else if (isDeviceSecure) {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        } else {
            promptInfoBuilder.setNegativeButtonText(negativeButtonText)
        }

        try {
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            isPromptShowing = false
            onError(e.message ?: "Authentication error")
        }
    }
}
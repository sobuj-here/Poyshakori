package com.poyshakori.app.backup

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object BackupSecurityHelper {

    private const val PREFS_NAME = "poyshakori_backup_security"
    private const val KEY_PASSWORD_HASH = "backup_pwd_hash"
    private const val KEY_PASSWORD_SALT = "backup_pwd_salt"
    private const val ITERATIONS = 12000
    private const val KEY_LENGTH = 256

    fun hasConfiguredBackupPassword(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PASSWORD_HASH) && prefs.contains(KEY_PASSWORD_SALT)
    }

    fun setBackupPassword(context: Context, newPassword: String): Boolean {
        if (newPassword.length < 6) return false
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        val hash = hashPassword(newPassword, salt)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PASSWORD_HASH, hashBase64)
            .putString(KEY_PASSWORD_SALT, saltBase64)
            .apply()
        return true
    }

    fun verifyPassword(context: Context, candidatePassword: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedHashBase64 = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val storedSaltBase64 = prefs.getString(KEY_PASSWORD_SALT, null) ?: return false

        val salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
        val candidateHash = hashPassword(candidatePassword, salt)
        val candidateHashBase64 = Base64.encodeToString(candidateHash, Base64.NO_WRAP)

        return storedHashBase64 == candidateHashBase64
    }

    fun changeBackupPassword(context: Context, currentPassword: String, newPassword: String): Boolean {
        if (!verifyPassword(context, currentPassword)) {
            return false
        }
        return setBackupPassword(context, newPassword)
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return factory.generateSecret(spec).encoded
    }
}

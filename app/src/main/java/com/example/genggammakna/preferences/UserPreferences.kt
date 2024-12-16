package com.example.genggammakna.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Patterns
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.genggammakna.repository.UserModel

class UserPreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyGenParameterSpec(
            KeyGenParameterSpec.Builder(
                MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
                .build()
        )
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_user_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveUser(user: UserModel): Boolean {
        return if (isValidUser(user)) {
            with(sharedPreferences.edit()) {
                putString("user_name", user.firstname)
                putString("user_email", user.email)
                apply()
            }
            true
        } else {
            false
        }
    }

    fun getUser(): UserModel? {
        val name = sharedPreferences.getString("user_name", null)
        val email = sharedPreferences.getString("user_email", null)

        return when {
            !name.isNullOrBlank() && !email.isNullOrBlank() && isValidEmail(email) -> {
                UserModel(name, email)
            }
            else -> null
        }
    }

    fun clearUser() {
        with(sharedPreferences.edit()) {
            clear()
            apply()
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidUser(user: UserModel): Boolean {
        return user.firstname.isNotBlank() &&
                user.email.isNotBlank() &&
                isValidEmail(user.email)
    }

    fun isUserSaved(): Boolean {
        return getUser() != null
    }

    fun getUsername(): String? {
        return sharedPreferences.getString("user_name", null)
    }

    fun getUserEmail(): String? {
        return sharedPreferences.getString("user_email", null)
    }
}
@file:Suppress("DEPRECATION")

package com.lomo.data.webdav

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavCredentialStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val masterKey: MasterKey by lazy {
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }

        private val prefs: SharedPreferences by lazy {
            EncryptedSharedPreferences.create(
                context,
                "webdav_credentials",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

        fun setUsername(username: String?) {
            prefs.edit().apply {
                if (username.isNullOrBlank()) {
                    remove(KEY_USERNAME)
                } else {
                    putString(KEY_USERNAME, username)
                }
                apply()
            }
        }

        fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

        fun setPassword(password: String?) {
            prefs.edit().apply {
                if (password.isNullOrBlank()) {
                    remove(KEY_PASSWORD)
                } else {
                    putString(KEY_PASSWORD, password)
                }
                apply()
            }
        }

        companion object {
            private const val KEY_USERNAME = "webdav_username"
            private const val KEY_PASSWORD = "webdav_password"
        }
    }

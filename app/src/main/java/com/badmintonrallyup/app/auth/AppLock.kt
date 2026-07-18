// Biometric / device-credential app lock. Opt-in via More → toggle; state
// lives in SharedPreferences, evaluated on every cold launch. 1:1 with the
// iOS AppLock (Face ID → BiometricPrompt).

package com.badmintonrallyup.app.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.badmintonrallyup.app.RallyUpApp

class AppLock {
    private val prefs
        get() = RallyUpApp.instance.getSharedPreferences("rallyup.settings", android.content.Context.MODE_PRIVATE)

    var enabled: Boolean = false
        get() = prefs.getBoolean("appLockEnabled", false)
        set(value) {
            field = value
            prefs.edit().putBoolean("appLockEnabled", value).apply()
        }

    var locked by mutableStateOf(prefs.getBoolean("appLockEnabled", false))
        private set

    val biometryLabel: String
        get() {
            val manager = BiometricManager.from(RallyUpApp.instance)
            return when (manager.canAuthenticate(BIOMETRIC_WEAK)) {
                BiometricManager.BIOMETRIC_SUCCESS -> "biometrics"
                else -> "screen lock"
            }
        }

    fun unlock(activity: FragmentActivity) {
        if (!locked) return
        val manager = BiometricManager.from(activity)
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            locked = false   // no screen lock set on device — nothing to gate with
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
                // Failure/cancel: stays locked; user can retry.
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock RallyUp")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}

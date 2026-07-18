// App-level auth state: signed out → signed in (no group) → in a group.
// Drives RootView. All mutations flow through here so screens stay dumb.
// 1:1 with the iOS SessionManager.

package com.badmintonrallyup.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.badmintonrallyup.app.BuildConfig
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.ApiException
import com.badmintonrallyup.app.api.EmptyData
import com.badmintonrallyup.app.api.LoginResult
import com.badmintonrallyup.app.api.MeProfile
import com.badmintonrallyup.app.api.VerificationPending
import com.badmintonrallyup.app.push.PushManager
import kotlinx.serialization.Serializable

class SessionManager {
    enum class Phase { Loading, SignedOut, Onboarding, Ready }

    var phase by mutableStateOf(Phase.Loading)
        private set
    var me by mutableStateOf<MeProfile?>(null)
        private set

    /** True when a group-less user tapped "Look around first". */
    var exploring by mutableStateOf(false)
        private set

    val hasActiveGroup: Boolean get() = me?.activeGroupId != null
    val showsMainApp: Boolean
        get() = phase == Phase.Ready || (phase == Phase.Onboarding && exploring)

    fun lookAround() { exploring = true }
    fun leaveExploring() { exploring = false }

    /** Dev/UI-test hook payload — intent extras in DEBUG builds only. */
    data class DevHooks(
        val api: String?,
        val access: String?,
        val refresh: String?,
        val reset: Boolean,
        val explore: Boolean,
    )

    suspend fun bootstrap(dev: DevHooks?) {
        ApiClient.setSessionExpiredHandler { phase = Phase.SignedOut }
        ApiClient.loadPersistedTokens()
        if (BuildConfig.DEBUG && dev != null) {
            dev.api?.let { ApiClient.baseUrl = it }
            if (dev.reset) ApiClient.signOutLocally()
            if (dev.access != null && dev.refresh != null) {
                ApiClient.adopt(dev.access, dev.refresh)
            }
        }
        if (!ApiClient.isAuthenticated) {
            phase = Phase.SignedOut
            return
        }
        refreshMe()
        if (BuildConfig.DEBUG && dev?.explore == true && phase == Phase.Onboarding) {
            exploring = true
        }
    }

    suspend fun refreshMe() {
        try {
            val profile: MeProfile = ApiClient.get("/api/auth/me")
            me = profile
            if (profile.groupsCount == 0L) {
                phase = Phase.Onboarding
            } else {
                exploring = false          // joined/created a group — leave explore mode
                phase = Phase.Ready
            }
            PushManager.retryUploadIfNeeded()
        } catch (e: Exception) {
            phase = Phase.SignedOut
        }
    }

    // MARK: Sign up / log in (email OTP)

    @Serializable
    data class StartAuth(val displayName: String? = null, val email: String)

    suspend fun requestCode(email: String, displayName: String? = null): VerificationPending {
        val path = if (displayName == null) "/api/auth/login" else "/api/auth/signup"
        return ApiClient.post(path, StartAuth(displayName, email))
    }

    @Serializable
    data class VerifyReq(val email: String, val code: String)

    suspend fun verify(email: String, code: String, isSignup: Boolean) {
        val path = if (isSignup) "/api/auth/signup/verify" else "/api/auth/login/verify"
        val result: LoginResult = ApiClient.post(path, VerifyReq(email, code))
        val access = result.accessToken
        val refresh = result.refreshToken
        if (access == null || refresh == null) {
            throw ApiException.Server("No session tokens returned — server too old?")
        }
        ApiClient.adopt(access, refresh)
        refreshMe()
    }

    @Serializable
    data class LogoutReq(val refreshToken: String? = null)

    suspend fun signOut() {
        val refresh = TokenStore.load()?.refreshToken
        try { ApiClient.post<EmptyData, LogoutReq>("/api/auth/logout", LogoutReq(refresh)) } catch (_: Exception) {}
        ApiClient.signOutLocally()
        me = null
        phase = Phase.SignedOut
    }

    suspend fun deleteAccount() {
        ApiClient.delete<EmptyData>("/api/auth/me")
        ApiClient.signOutLocally()
        me = null
        phase = Phase.SignedOut
    }
}

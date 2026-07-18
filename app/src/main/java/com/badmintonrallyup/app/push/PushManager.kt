// Native push plumbing: POST_NOTIFICATIONS permission on launch (13+), FCM
// token upload, deep-link fan-out. 1:1 with the iOS PushManager — and fully
// guarded: without google-services.json (Firebase config lands last, like the
// APNs key did on iOS) every call is a silent no-op.

package com.badmintonrallyup.app.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.badmintonrallyup.app.RallyUpApp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.EmptyData
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

object PushManager {
    /** Posted when a push is tapped; value is the target path ("/courts", "/", …). */
    val deepLink = MutableStateFlow<String?>(null)

    @Volatile
    private var latestToken: String? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private val firebaseReady: Boolean
        get() = FirebaseApp.getApps(RallyUpApp.instance).isNotEmpty()

    /** Standard flow: ask on first open (Android 13+); then fetch the FCM token. */
    fun requestOnLaunch(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
                fetchToken()
            }.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            fetchToken()
        }
    }

    private fun fetchToken() {
        if (!firebaseReady) return   // google-services.json not shipped yet
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            uploadDeviceToken(token)
        }
    }

    fun uploadDeviceToken(token: String) {
        latestToken = token
        scope.launch { pushTokenToBackend() }
    }

    /** Re-run after sign-in: the launch-time upload is skipped when unauthenticated. */
    fun retryUploadIfNeeded() {
        scope.launch { pushTokenToBackend() }
    }

    @Serializable
    private data class Req(val platform: String, val token: String, val deviceLabel: String? = null)

    private suspend fun pushTokenToBackend() {
        val token = latestToken ?: return
        if (!ApiClient.isAuthenticated) return
        try {
            ApiClient.post<EmptyData, Req>(
                "/api/push/device",
                Req(platform = "fcm", token = token, deviceLabel = Build.MODEL)
            )
        } catch (_: Exception) { /* retried on next sign-in */ }
    }
}

/** FCM callbacks: rotated tokens re-upload; backend payloads carry a "url". */
class RallyUpMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushManager.uploadDeviceToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Notification-style pushes are rendered by the system while backgrounded;
        // nothing extra needed for parity with the iOS foreground banner yet.
    }
}

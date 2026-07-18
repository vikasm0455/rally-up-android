// Haptics primitives backing the AppHaptics map (same events as iOS).

package com.badmintonrallyup.app.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.badmintonrallyup.app.RallyUpApp

object Haptics {
    private val vibrator: Vibrator?
        get() {
            val ctx = RallyUpApp.instance
            return if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }

    private fun tick(millis: Long, amplitude: Int) {
        vibrator?.vibrate(VibrationEffect.createOneShot(millis, amplitude))
    }

    fun light() = tick(12, 80)
    fun medium() = tick(18, 140)
    fun heavy() = tick(28, 255)
    fun success() { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 12, 60, 12), intArrayOf(0, 120, 0, 160), -1)) }
    fun warning() { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 20, 80, 20), intArrayOf(0, 160, 0, 160), -1)) }
}

// System camera + photo-library capture for the OCR flows (kiosk logins,
// status board). Mirrors iOS CameraCapture/PhotosPicker: full system UI in,
// normalized JPEG bytes out.

package com.badmintonrallyup.app.core

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

object Capture {
    /** False on devices without any camera — callers fall back to the library. */
    fun cameraAvailable(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    fun captureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "com.badmintonrallyup.app.fileprovider", file)
    }
}

/** Launch the system camera; delivers a normalized JPEG. */
@Composable
fun rememberCameraCapture(onJpeg: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val uriHolder = remember { arrayOfNulls<Uri>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = uriHolder[0]
        if (ok && uri != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ImagePipeline.normalizedJpeg(stream.readBytes())?.let(onJpeg)
            }
        }
    }
    return {
        val uri = Capture.captureUri(context)
        uriHolder[0] = uri
        launcher.launch(uri)
    }
}

/** Launch the system photo picker; delivers a normalized JPEG. */
@Composable
fun rememberPhotoPicker(onJpeg: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ImagePipeline.normalizedJpeg(stream.readBytes())?.let(onJpeg)
            }
        }
    }
    return {
        launcher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }
}

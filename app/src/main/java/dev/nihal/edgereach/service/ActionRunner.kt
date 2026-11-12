package dev.nihal.edgereach.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import dev.nihal.edgereach.data.Action
import dev.nihal.edgereach.data.ActionType

class ActionRunner(private val svc: EdgeService) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = svc.getSystemService(AudioManager::class.java)
    private val torch = Torch()

    fun run(encoded: String) {
        val a = Action.parse(encoded)
        try {
            when (a.type) {
                ActionType.NONE -> Unit
                ActionType.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK, a)
                ActionType.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME, a)
                ActionType.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, a)
                ActionType.PREVIOUS_APP -> previousApp()
                ActionType.APP_DRAWER -> global(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS, a)
                ActionType.SPLIT_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN, a)
                ActionType.NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, a)
                ActionType.QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, a)
                ActionType.CLOSE_SHADE -> global(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE, a)
                ActionType.SCREENSHOT -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, a)
                ActionType.LOCK_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, a)
                ActionType.POWER_MENU -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, a)
                ActionType.MEDIA_PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                ActionType.MEDIA_NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                ActionType.MEDIA_PREVIOUS -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                ActionType.VOLUME_PANEL -> volume(AudioManager.ADJUST_SAME)
                ActionType.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE)
                ActionType.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER)
                ActionType.RINGER -> ringer()
                ActionType.FLASHLIGHT -> torch.toggle()
                ActionType.ROTATION -> rotation()
                ActionType.ASSISTANT -> assistant()
                ActionType.LAUNCH_APP -> launch(a)
            }
        } catch (t: Throwable) {
            toast("Couldn't run \"${a.type.label}\"")
        }
    }

    fun release() = torch.release()

    private fun global(id: Int, a: Action) {
        if (!svc.performGlobalAction(id)) toast("\"${a.type.label}\" isn't supported here")
    }

    private fun previousApp() {
        val pkg = svc.previousAppPackage()
        val intent = pkg?.let { svc.packageManager.getLaunchIntentForPackage(it) }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            svc.startActivity(intent)
        } else {
            // No history yet: fall back to the launcher's "double recents" quick switch.
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            main.postDelayed({ svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) }, 320)
        }
    }

    private fun launch(a: Action) {
        val cmp = a.component ?: return
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cmp)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            svc.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            val fallback = svc.packageManager.getLaunchIntentForPackage(cmp.packageName)
            if (fallback != null) svc.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else toast("That app isn't installed anymore")
        }
    }

    private fun mediaKey(code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    private fun volume(direction: Int) {
        audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
    }

    private fun ringer() {
        val next = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE
        else AudioManager.RINGER_MODE_NORMAL
        try {
            audio.ringerMode = next
            toast(if (next == AudioManager.RINGER_MODE_VIBRATE) "Vibrate" else "Sound on")
        } catch (e: SecurityException) {
            toast("Turn off Do Not Disturb first")
        }
    }

    private fun rotation() {
        if (!Settings.System.canWrite(svc)) {
            toast("Allow \"Modify system settings\" for EdgeReach, then try again")
            svc.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${svc.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val cr = svc.contentResolver
        val on = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (on) 0 else 1)
        toast(if (on) "Auto-rotate off" else "Auto-rotate on")
    }

    private fun assistant() {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            svc.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(flags))
        } catch (e: ActivityNotFoundException) {
            svc.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(flags))
        }
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(svc, msg, Toast.LENGTH_SHORT).show() }
    }

    private inner class Torch {
        private val cm = svc.getSystemService(CameraManager::class.java)
        private var on = false
        private val id: String? = runCatching {
            cm.cameraIdList.firstOrNull {
                val ch = cm.getCameraCharacteristics(it)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
        private val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) on = enabled
            }
        }

        init {
            runCatching { cm.registerTorchCallback(callback, main) }
        }

        fun toggle() {
            val cam = id ?: return toast("No flashlight found")
            cm.setTorchMode(cam, !on)
        }

        fun release() {
            runCatching { cm.unregisterTorchCallback(callback) }
        }
    }
}

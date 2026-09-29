package com.lockin.focus.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.lockin.focus.service.AppWatchService

/** Everything the app needs switched on, and how to get there. */
data class GuardPermissions(
    val accessibilityOn: Boolean,
    val notificationsOn: Boolean,
    val fullScreenIntentOn: Boolean,
) {
    /** The only hard requirement: without it nothing can be enforced. */
    val canEnforce: Boolean get() = accessibilityOn
    val allGood: Boolean get() = accessibilityOn && notificationsOn && fullScreenIntentOn
}

object Permissions {

    fun read(context: Context): GuardPermissions = GuardPermissions(
        accessibilityOn = isAccessibilityServiceEnabled(context),
        notificationsOn = areNotificationsEnabled(context),
        fullScreenIntentOn = canUseFullScreenIntent(context),
    )

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppWatchService::class.java)
        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        if (enabledSetting.isEmpty()) return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledSetting)
        return splitter.any { entry ->
            ComponentName.unflattenFromString(entry)?.equals(expected) == true ||
                entry.equals(expected.flattenToString(), ignoreCase = true) ||
                entry.equals(expected.flattenToShortString(), ignoreCase = true)
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun canUseFullScreenIntent(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.canUseFullScreenIntent() == true
        } else {
            true
        }

    /**
     * The runtime notification permission only exists from Android 13. The
     * constant is a compile-time string inlined into the APK, so reading it on an
     * older device is harmless — it just is never requested, because every caller
     * guards on the SDK level first.
     */
    @SuppressLint("InlinedApi")
    fun notificationPermission(): String = Manifest.permission.POST_NOTIFICATIONS

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun hasPermission(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

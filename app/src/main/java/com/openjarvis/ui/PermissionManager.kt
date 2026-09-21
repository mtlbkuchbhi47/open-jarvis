package com.openjarvis.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.openjarvis.accessibility.JarvisAccessibilityService
import com.openjarvis.intelligence.JarvisNotificationListener

/** Centralized, defensive permission/access checks and settings intents. */
object PermissionManager {
    fun hasMicrophone(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    fun hasOverlay(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    fun hasAccessibility(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = ComponentName(context, JarvisAccessibilityService::class.java)
            .flattenToString()
        return enabled.split(':').any { it.equals(target, ignoreCase = true) }
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val target = ComponentName(context, JarvisNotificationListener::class.java)
            .flattenToString()
        return enabled.split(':').any { it.equals(target, ignoreCase = true) }
    }

    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlaySettings(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun notificationAccessSettings(): Intent =
        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

    fun appDetailsSettings(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
}

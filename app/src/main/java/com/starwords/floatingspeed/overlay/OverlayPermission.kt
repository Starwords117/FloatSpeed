package com.starwords.floatingspeed.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.starwords.floatingspeed.util.DebugLog

object OverlayPermission {

    private val MIUI_EDITOR_COMPONENTS = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
        ),
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
        ),
    )

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun intent(context: Context): Intent {
        for (componentName in MIUI_EDITOR_COMPONENTS) {
            val candidate = Intent().apply {
                component = componentName
                putExtra("extra_pkgname", context.packageName)
            }
            if (resolves(context, candidate)) {
                DebugLog.d("overlay permission intent: miui editor")
                return candidate
            }
        }
        DebugLog.d("overlay permission intent: generic manage")
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }

    private fun resolves(context: Context, intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, 0) != null
}

package io.github.marcocapile.garbaggio.system

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Tracks whether the user agreed to let the app read the list of installed apps.
 *
 * Stock Android grants package visibility at install time (QUERY_ALL_PACKAGES), but some
 * ROMs such as MIUI/HyperOS gate it behind a runtime permission, which is requested too.
 */
class AppListAccess(private val context: Context) {

    private val prefs = context.getSharedPreferences("access", Context.MODE_PRIVATE)

    val isGranted: Boolean get() = prefs.getBoolean(KEY_GRANTED, false)

    fun markGranted() = prefs.edit().putBoolean(KEY_GRANTED, true).apply()

    /** The ROM-specific runtime permission to request, or null if this device has none. */
    fun runtimePermissionToRequest(): String? {
        val exists = try {
            context.packageManager.getPermissionInfo(GET_INSTALLED_APPS, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        val granted = ContextCompat.checkSelfPermission(context, GET_INSTALLED_APPS) ==
            PackageManager.PERMISSION_GRANTED
        return GET_INSTALLED_APPS.takeIf { exists && !granted }
    }

    companion object {
        const val GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"
        private const val KEY_GRANTED = "granted"
    }
}

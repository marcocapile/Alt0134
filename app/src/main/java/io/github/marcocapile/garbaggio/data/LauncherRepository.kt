package io.github.marcocapile.garbaggio.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class LauncherRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** All launchers on the device, including disabled ones and ones removed for this user. */
    fun loadLaunchers(): List<LauncherInfo> {
        val defaultPackage = currentDefaultPackage()
        return queryHomeActivities(includeHidden = true)
            .filter { it.packageName != context.packageName && it.packageName !in IGNORED_PACKAGES }
            .distinctBy { it.packageName }
            .map { toLauncherInfo(it, defaultPackage) }
            .sortedWith(
                compareByDescending<LauncherInfo> { it.isDefault }
                    .thenByDescending { it.isSystem }
                    .thenBy { it.label.lowercase() },
            )
    }

    /** Package of the launcher the HOME button currently opens, or null if Android would ask. */
    fun currentDefaultPackage(): String? {
        val info = pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return null
        val isChooser = info.packageName == "android" || info.name.endsWith("ResolverActivity")
        return if (isChooser || info.packageName in IGNORED_PACKAGES) null else info.packageName
    }

    /** True while the package still exists for the current user. */
    fun isInstalledForUser(packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        val info = pm.getApplicationInfo(packageName, 0)
        info.flags and ApplicationInfo.FLAG_INSTALLED != 0
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Picks the launcher that shipped with the device, preferring well-known stock launchers. */
    fun pickStockLauncher(launchers: List<LauncherInfo>): LauncherInfo? {
        val system = launchers.filter { it.isSystem }
        return KNOWN_STOCK_LAUNCHERS.firstNotNullOfOrNull { pkg -> system.find { it.packageName == pkg } }
            ?: system.firstOrNull { it.isActive }
            ?: system.firstOrNull()
    }

    @Suppress("DEPRECATION")
    private fun queryHomeActivities(includeHidden: Boolean): List<ActivityInfo> {
        val flags = when {
            !includeHidden -> 0
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ->
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
            else -> PackageManager.GET_DISABLED_COMPONENTS or PackageManager.GET_UNINSTALLED_PACKAGES
        }
        return pm.queryIntentActivities(homeIntent(), flags).mapNotNull { it.activityInfo }
    }

    private fun toLauncherInfo(activity: ActivityInfo, defaultPackage: String?): LauncherInfo {
        val app = activity.applicationInfo
        val component = ComponentName(activity.packageName, activity.name)
        val componentEnabled = when (
            runCatching { pm.getComponentEnabledSetting(component) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        ) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> activity.enabled
            else -> false
        }
        val icon = runCatching { activity.loadIcon(pm).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap() }
            .getOrNull()
        val label = runCatching { activity.loadLabel(pm).toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: activity.packageName

        return LauncherInfo(
            packageName = activity.packageName,
            activityName = activity.name,
            label = label,
            icon = icon,
            versionName = versionName(activity.packageName),
            isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isUpdatedSystem = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            isEnabled = app.enabled && componentEnabled,
            isInstalledForUser = app.flags and ApplicationInfo.FLAG_INSTALLED != 0,
            isDefault = activity.packageName == defaultPackage,
        )
    }

    @Suppress("DEPRECATION")
    private fun versionName(packageName: String): String? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager.MATCH_UNINSTALLED_PACKAGES
        } else {
            PackageManager.GET_UNINSTALLED_PACKAGES
        }
        pm.getPackageInfo(packageName, flags).versionName
    }.getOrNull()

    companion object {
        private const val ICON_SIZE_PX = 128

        fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        /** HOME activities that are not real launchers (boot fallback, setup wizards). */
        private val IGNORED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.provision",
            "com.android.managedprovisioning",
            "com.google.android.setupwizard",
            "com.google.android.apps.restore",
            "com.sec.android.app.SecSetupWizard",
        )

        /** Common stock launchers, most specific first. */
        private val KNOWN_STOCK_LAUNCHERS = listOf(
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.android.launcher",
            "net.oneplus.launcher",
            "com.motorola.launcher3",
            "com.sonymobile.home",
            "com.lge.launcher3",
            "com.asus.launcher",
            "com.android.launcher3",
            "com.android.launcher2",
        )
    }
}

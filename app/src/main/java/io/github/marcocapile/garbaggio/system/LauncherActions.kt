package io.github.marcocapile.garbaggio.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.marcocapile.garbaggio.data.LauncherInfo

/** System screens used to manage launchers without any special privileges. */
object LauncherActions {

    /** Opens the system "Default home app" picker, falling back to more generic screens. */
    fun openHomeSettings(context: Context): Boolean =
        context.tryStart(Intent(Settings.ACTION_HOME_SETTINGS)) ||
            context.tryStart(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) ||
            context.tryStart(Intent(Settings.ACTION_SETTINGS))

    /** Opens App info, where the user can enable an app or clear its "open by default" setting. */
    fun openAppInfo(context: Context, packageName: String) {
        context.tryStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }

    /** Shows the launcher once without making it the default. */
    fun openLauncher(context: Context, launcher: LauncherInfo) {
        context.tryStart(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setComponent(launcher.component),
        )
    }

    private fun Context.tryStart(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

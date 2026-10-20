package io.github.marcocapile.garbaggio.data

import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap

/** A package that provides an activity for the HOME intent, i.e. a launcher. */
data class LauncherInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap?,
    val versionName: String?,
    /** Shipped on the system image (stock/OEM launcher). */
    val isSystem: Boolean,
    /** A system launcher that has been updated from the store. */
    val isUpdatedSystem: Boolean,
    val isEnabled: Boolean,
    /** False when the package was removed for this user (`pm uninstall --user`). */
    val isInstalledForUser: Boolean,
    val isDefault: Boolean,
) {
    val component: ComponentName get() = ComponentName(packageName, activityName)

    /** Usable as a home screen right now. */
    val isActive: Boolean get() = isEnabled && isInstalledForUser
}

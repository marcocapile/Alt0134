package io.github.marcocapile.launchermanager.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** Shows the system uninstall confirmation for one package; result is true when the user confirmed. */
class UninstallPackageContract : ActivityResultContract<String, Boolean>() {

    @Suppress("DEPRECATION")
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$input"))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}

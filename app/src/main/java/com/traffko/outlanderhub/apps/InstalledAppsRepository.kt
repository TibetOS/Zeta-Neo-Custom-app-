package com.traffko.outlanderhub.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val icon: Drawable,
)

class InstalledAppsRepository(private val context: Context) {

    /** All launchable apps except ourselves, sorted by label. */
    fun loadApps(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(app: LaunchableApp) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }
}

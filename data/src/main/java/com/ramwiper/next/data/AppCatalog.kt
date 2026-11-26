package com.ramwiper.next.data

import com.ramwiper.next.domain.*
import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
class AppCatalog(private val app: Application) : AppRepository {
    override fun read(): List<AppTarget> {
            val pm = app.packageManager
            val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
            val ignore = home + setOf(app.packageName, "com.ramwiper.app", "moe.shizuku.privileged.api")
            return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .filter { it.activityInfo.packageName !in ignore && it.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 }
                .map { AppTarget(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.distinctBy { it.pkg }.sortedBy { it.name.lowercase() }
    }
}

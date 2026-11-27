package com.ramwiper.next

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.app.NotificationManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.ramwiper.next.presentation.MainViewModel
import com.ramwiper.next.presentation.RamApp
import com.ramwiper.next.presentation.RamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this, RamViewModelFactory(application))[MainViewModel::class.java]
        val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.setBackgroundAlerts(true) else vm.notifyPermissionDenied()
        }
        val resolver = applicationContext.contentResolver
        val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) vm.finishExport(null)
            else vm.finishExport { csv ->
                requireNotNull(resolver.openOutputStream(uri, "wt")) { "Не удалось открыть документ" }.use { stream ->
                    stream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    stream.write(csv.toByteArray(Charsets.UTF_8))
                }
            }
        }
        setContent {
            RamTheme {
                RamApp(vm, onExport = {
                    if (vm.prepareExport()) {
                        try { export.launch("ram-wiper-history.csv") }
                        catch (_: Exception) { vm.finishExport(null) }
                    }
                }) { enabled ->
                    if (!enabled) vm.setBackgroundAlerts(false)
                    else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if (getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() != true) vm.notifyPermissionDenied()
                    else vm.setBackgroundAlerts(true)
                }
            }
        }
    }
}

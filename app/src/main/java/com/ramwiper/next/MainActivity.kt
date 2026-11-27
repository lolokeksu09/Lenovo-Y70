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
        setContent {
            RamTheme {
                RamApp(vm) { enabled ->
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

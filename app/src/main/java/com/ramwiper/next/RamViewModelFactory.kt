package com.ramwiper.next

import android.app.Application
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ramwiper.next.data.*
import com.ramwiper.next.domain.MemoryController
import com.ramwiper.next.domain.RunCleanupUseCase
import com.ramwiper.next.presentation.MainViewModel

/** Single composition root; the ViewModel owns the lifetime of the Shizuku adapter. */
class RamViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == MainViewModel::class.java) { "Unsupported ViewModel: ${modelClass.name}" }
        val prefs = app.getSharedPreferences("memory", 0)
        val monitor = Monitor(app)
        val access = ShizukuEngine(app)
        val runner = MemoryController(monitor, access, Process.myUid() / 100000, { NativeWiper.Session() })
        val catalog = AppCatalog(app)
        return MainViewModel(monitor, access, RunCleanupUseCase(runner, catalog), SettingsStore(prefs), HistoryStore(prefs),
            catalog, BuildConfig.VERSION_NAME, SystemClock::elapsedRealtime, AndroidBackgroundMonitor(app)) as T
    }
}

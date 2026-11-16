package app.pinduoduo.overlayru

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import app.pinduoduo.overlayru.ui.home.HomeScreen
import app.pinduoduo.overlayru.ui.design.PddNav
import app.pinduoduo.overlayru.ui.design.PddTab

/** Compose entry point. Legacy MainActivity remains as detailed settings during migration. */
class HomeActivity : ComponentActivity() {
    private lateinit var vm: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.rgb(7, 9, 12)
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.rgb(7, 9, 12)
        vm = ViewModelProvider(this)[HomeViewModel::class.java]
        setContent {
            val state by vm.state.collectAsState()
            HomeScreen(
                state = state,
                onPrimaryAction = { vm.primaryAction(this) },
                onCheckAgain = { vm.checkAgain() },
                onAppInfo = { vm.openAppInfo(this) },
                onSettings = { PddNav.open(this, PddTab.SETTINGS) },
                onLicense = { startActivity(Intent(this, LicenseActivity::class.java)) },
                onOcr = { PddNav.open(this, PddTab.PHOTO) },
                onDictionary = { PddNav.open(this, PddTab.DICTIONARY) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh(null)
        vm.start()
    }

    override fun onPause() {
        vm.stop()
        super.onPause()
    }
}

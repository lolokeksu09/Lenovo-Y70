package com.ramwiper.next

import android.os.Bundle
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
        setContent { RamTheme { RamApp(vm) } }
    }
}

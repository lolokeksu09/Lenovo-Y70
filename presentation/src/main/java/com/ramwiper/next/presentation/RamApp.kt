package com.ramwiper.next.presentation

import com.ramwiper.next.domain.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun RamApp(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(vm) { vm.setVisible(true); onStopOrDispose { vm.setVisible(false) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Память", "Приложения", "Журнал", "Настройки")
    Scaffold(bottomBar = { Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                titles.forEachIndexed { index, name ->
                    TextButton(onClick = { tab = index }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp, horizontal = 0.dp)) {
                        Text(name, color = if (index == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(titles[tab], style = MaterialTheme.typography.headlineSmall)
                Text("RAM Wiper", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.initialLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.initialError?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::loadInitial) { Text("Повторить") }
                }
            }
            if (s.settingsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (tab != 0 && s.message.isNotBlank()) Text(s.message, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
            if (s.progress != null && tab != 0) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.progress!!.stage, Modifier.weight(1f)); TextButton(onClick = vm::cancel) { Text("Отмена") }
            }
            when(tab) {
                0 -> MemoryScreen(s, vm::engine, vm::start, vm::cancel)
                1 -> ApplicationsScreen(s, vm::select, vm::protect, vm::inspect, vm::refreshApps)
                2 -> JournalScreen(s)
                3 -> SettingsScreen(s, vm::requestAccess, vm::profile, vm::refreshApps)
            }
        }
    }
    if (s.diagnostic.isNotEmpty()) AlertDialog(onDismissRequest = vm::closeDiagnostic, title = { Text("Память процесса") },
        text = { Text(s.diagnostic, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = vm::closeDiagnostic) { Text("Закрыть") } })
}

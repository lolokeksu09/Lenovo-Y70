package com.ramwiper.next

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable fun ApplicationsScreen(s: ScreenState, sh: String, vm: MainViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val visible = remember(s.targets, s.selected, s.protected, search, filter) { s.targets.filter {
        (it.name.contains(search,true) || it.pkg.contains(search,true)) &&
        (filter == 0 || filter == 1 && it.pkg in s.selected || filter == 2 && it.pkg in s.protected)
    } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("$sh · ${s.selected.size}/12", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Найти приложение") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Все", "Выбраны", "Исключены").forEachIndexed { i, label -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) }) }
            }
            if (s.loadingApps) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.appError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::refreshApps) { Text("Повторить") } }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(visible, key = { it.pkg }) { a ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(a.name, style = MaterialTheme.typography.titleSmall); Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Checkbox(checked = a.pkg in s.selected, onCheckedChange = { vm.select(a.pkg,it) }, enabled = s.progress == null && a.pkg !in s.protected)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.protect(a.pkg,a.pkg !in s.protected) }, enabled = s.progress == null) { Text(if(a.pkg in s.protected) "Вернуть в список" else "Исключить") }
                        TextButton(onClick = { vm.inspect(a.pkg) }, enabled = sh == "Shizuku подключён" && !s.diagnosticBusy && s.progress == null) { Text("RAM") }
                    }
                    if(a.pkg in s.protected) Text("Исключено из команд Shizuku", style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
            }
            if (visible.isEmpty() && !s.loadingApps) item { Text("Нет приложений по этому фильтру", Modifier.padding(20.dp)) }
        }
    }
}
@Composable internal fun ResultRow(r: Result) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(DateFormat.getDateTimeInstance().format(Date(r.time)), style = MaterialTheme.typography.labelMedium)
            Text("${r.engine} · ${r.profile}", style = MaterialTheme.typography.bodySmall)
            Text(r.outcome.label(), style = MaterialTheme.typography.labelSmall)
        }
        Text(delta(r.delta), style = MaterialTheme.typography.titleLarge)
    }
    Metric("Доступная RAM", "${gb(r.before)} → ${gb(r.after)}")
    r.settledDelta?.let { Metric("Изменение через 3 с", delta(it)) }
    r.swapDelta?.let { Metric("Изменение Swap", delta(it)) }
    r.durationMs?.let { Metric("Длительность", "${it/1000.0} с") }
    Metric("Выделено Rust", "${r.allocated/MIB} МиБ")
    Metric("Команды приняты", "${r.accepted}/${r.attempted}")
    if(r.cancelled) Text("Отменено")
    if(r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
}
@Composable fun JournalScreen(s: ScreenState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if(s.history.isEmpty()) item { Text("История пока пуста. Результаты появятся после первой операции.") }
        else item {
            SectionLabel("Последние ${s.history.size} операций")
            val done = s.history.filter { it.outcome == Outcome.COMPLETED || it.outcome == Outcome.PARTIAL }
            val measured = done.filter { it.settledDelta != null }
            Metric("Положительное изменение через 3 с", "${measured.count { (it.settledDelta ?: 0) > 0 }}/${measured.size}")
            Text("Изменение доступной RAM — не доказательство причинности и не прирост FPS.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
        }
        items(s.history) { r -> Column { ResultRow(r); HorizontalDivider() } }
    }
}
@Composable fun SettingsScreen(s: ScreenState, sh: String, vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionLabel("Доступ"); Metric("Shizuku", sh); OutlinedButton(onClick = { vm.shizuku.request() }, enabled = s.progress == null) { Text("Подключить") }; HorizontalDivider() }
        item { SectionLabel("Профиль Rust")
            Profile.entries.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.profile == p, onClick = { vm.profile(p) }, enabled = s.progress == null)
                Column { Text(profileName(p)); Text("До ${p.capMiB} МиБ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
            Text("Лимит динамически снижается с учётом резерва, PSI и температуры.", style = MaterialTheme.typography.bodySmall); HorizontalDivider()
        }
        item { SectionLabel("Диагностика"); TextButton(onClick = vm::refreshApps) { Text("Обновить список приложений") }; Metric("Версия", "2.0.0-alpha.3"); Metric("Движок", "Rust JNI"); Text("Мониторинг активен только на видимом экране.", style = MaterialTheme.typography.bodySmall) }
    }
}

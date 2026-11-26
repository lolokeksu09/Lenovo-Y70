package com.ramwiper.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}
private fun gb(bytes: Long) = String.format(Locale.getDefault(), "%.2f ГБ", bytes / (1024.0 * 1024 * 1024))
private fun delta(bytes: Long) = (if (bytes > 0) "+" else "") + String.format(Locale.getDefault(), "%.0f МиБ", bytes / (1024.0 * 1024))
private fun engineName(e: Engine) = when(e) { Engine.NATIVE -> "Native"; Engine.SHIZUKU -> "Shizuku"; Engine.HYBRID -> "Hybrid" }
private fun profileName(p: Profile) = when(p) { Profile.SOFT -> "Мягкий"; Profile.BALANCED -> "Баланс"; Profile.STRONG -> "Сильный" }
@Composable private fun App(vm: MainViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sh by vm.shizuku.status.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Память", "Приложения", "История", "Настройки")
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF81E3BE), onPrimary = Color(0xFF00382A), background = Color(0xFF080A0C), surface = Color(0xFF101416), surfaceVariant = Color(0xFF202729))) {
        Scaffold(bottomBar = { NavigationBar { tabs.forEachIndexed { i, name -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(listOf("◉", "▦", "≋", "⚙")[i]) }, label = { Text(name, maxLines = 1) }) } } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(6.dp)); Text("RAM WIPER / 2.0", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(tabs[tab], style = MaterialTheme.typography.headlineMedium)
                if (s.progress != null && tab != 0) { Text(s.progress!!.stage); OutlinedButton(onClick = vm::cancel) { Text("Отменить") } }
                when(tab) { 0 -> Home(s, sh, vm); 1 -> Apps(s, sh, vm); 2 -> History(s); 3 -> Settings(s, sh, vm) }
            }
        }
        if (s.diagnostic.isNotEmpty()) AlertDialog(onDismissRequest = vm::closeDiagnostic, title = { Text("dumpsys meminfo") }, text = { Text(s.diagnostic, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) }, confirmButton = { TextButton(onClick = vm::closeDiagnostic) { Text("Закрыть") } })
    }
}
@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}
@Composable private fun Home(s: ScreenState, sh: String, vm: MainViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Panel {
            Text("Доступная память", style = MaterialTheme.typography.titleMedium)
            val m = s.snapshot
            if (m == null) Text("Измерение…") else {
                Text(gb(m.available), style = MaterialTheme.typography.displaySmall)
                Text("Всего ${gb(m.total)} · резерв ${gb(Policy.reserve(m))}")
                LinearProgressIndicator(progress = { if(m.total > 0) (m.available.toFloat() / m.total).coerceIn(0f,1f) else 0f }, modifier = Modifier.fillMaxWidth())
                Text("Swap: " + if(m.swapTotal != null && m.swapFree != null) "${gb(m.swapTotal - m.swapFree)} / ${gb(m.swapTotal)}" else "недоступно")
                Text("PSI avg10: ${m.psiSome?.let { "$it%" } ?: "недоступно"} · Thermal: ${m.thermal ?: "н/д"}")
                if (!Policy.safe(m)) Text("Активно защитное ограничение Native", color = MaterialTheme.colorScheme.error)
            }
        } }
        item { Panel {
            Text("${engineName(s.engine)} · ${profileName(s.profile)}", style = MaterialTheme.typography.titleLarge)
            Text(sh); Text("Выбрано приложений: ${s.selected.size}")
            if (s.progress == null) Button(onClick = vm::start, enabled = !s.diagnosticBusy, modifier = Modifier.fillMaxWidth()) { Text("Запустить очистку") }
            else { val p = requireNotNull(s.progress); Text(p.stage); if(p.target > 0) LinearProgressIndicator(progress = { (p.allocated.toFloat()/p.target).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth()); OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Отменить") } }
            if (s.message.isNotEmpty()) Text(s.message)
            Text("Очистка может вызвать перезапуск фоновых приложений. Прирост RAM не гарантирован.", style = MaterialTheme.typography.bodySmall)
        } }
        s.history.firstOrNull()?.let { result -> item { ResultCard(result) } }
    }
}
@Composable private fun Apps(s: ScreenState, sh: String, vm: MainViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    Text("$sh · выбрано ${s.selected.size}/12", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Поиск приложений") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Text("Только сторонние приложения с иконкой запуска. Защита действует на команды RAM Wiper, не на LMKD.", style = MaterialTheme.typography.bodySmall)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(s.targets.filter { it.name.contains(search,true) || it.pkg.contains(search,true) }, key = { it.pkg }) { a -> Panel {
            Text(a.name, style = MaterialTheme.typography.titleMedium); Text(a.pkg, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = a.pkg in s.selected, onCheckedChange = { vm.select(a.pkg,it) }, enabled = s.progress == null && a.pkg !in s.protected); Text("Очищать") }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = a.pkg in s.protected, onCheckedChange = { vm.protect(a.pkg,it) }, enabled = s.progress == null); Spacer(Modifier.width(10.dp)); Text("Исключить") }
            TextButton(onClick = { vm.inspect(a.pkg) }, enabled = sh == "Shizuku подключён" && !s.diagnosticBusy && s.progress == null) { Text("Диагностика RAM") }
        } }
        if (s.targets.isEmpty()) item { Text("Нет доступных приложений") }
    }
}
@Composable private fun History(s: ScreenState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (s.history.isEmpty()) item { Text("История пока пуста") }
        items(s.history) { ResultCard(it) }
    }
}
@Composable private fun ResultCard(r: Result) { Panel {
    Text("${r.engine} · ${r.profile}", style = MaterialTheme.typography.titleMedium)
    Text(delta(r.delta), style = MaterialTheme.typography.headlineMedium, color = if(r.delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    Text("Доступно: ${gb(r.before)} → ${gb(r.after)}")
    Text("Выделено Native: ${r.allocated / MIB} МиБ · команды Shizuku: ${r.accepted}/${r.attempted}")
    Text("Успешная команда не подтверждает завершение процесса.", style = MaterialTheme.typography.bodySmall)
    if (r.cancelled) Text("Отменено"); if(r.note.isNotBlank()) Text(r.note)
    Text(DateFormat.getDateTimeInstance().format(Date(r.time)), style = MaterialTheme.typography.bodySmall)
} }
@Composable private fun Settings(s: ScreenState, sh: String, vm: MainViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Panel { Text("Shizuku", style = MaterialTheme.typography.titleMedium); Text(sh); OutlinedButton(onClick = { vm.shizuku.request() }, enabled = s.progress == null) { Text("Подключить") } } }
        item { Panel { Text("Движок", style = MaterialTheme.typography.titleMedium); Engine.entries.forEach { e -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = s.engine == e, onClick = { vm.engine(e) }, enabled = s.progress == null); Text(engineName(e)) } }; Text("Hybrid: выбранные приложения, затем Native при небольшом изменении RAM. Без Shizuku используется Native.", style = MaterialTheme.typography.bodySmall) } }
        item { Panel { Text("Профиль Native", style = MaterialTheme.typography.titleMedium); Profile.entries.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = s.profile == p, onClick = { vm.profile(p) }, enabled = s.progress == null); Text("${profileName(p)} · до ${p.capMiB} МиБ") } }; Text("Действительный лимит зависит от общего объёма, резерва, давления и температуры.", style = MaterialTheme.typography.bodySmall) } }
        item { Panel { Text("О приложении", style = MaterialTheme.typography.titleMedium); Text("2.0.0-alpha.1 · независимая реализация\nРучной режим, без постоянного фонового сервиса."); TextButton(onClick = vm::refreshApps) { Text("Обновить список приложений") } } }
    }
}

package com.ramwiper.next.presentation

import com.ramwiper.next.domain.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable fun ApplicationsScreen(s: ScreenState, onSelect: (String, Boolean) -> Unit, onProtect: (String, Boolean) -> Unit, onInspect: (String) -> Unit, onRefresh: () -> Unit, onScan: (List<String>) -> Unit, onCancelScan: () -> Unit, onClearSamples: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var byMemory by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(s.targets, s.selected, s.protected, search, filter) { s.targets.filter {
        (it.name.contains(search,true) || it.pkg.contains(search,true)) &&
        (filter == 0 || filter == 1 && it.pkg in s.selected || filter == 2 && it.pkg in s.protected)
    } }
    val visible = remember(filtered, byMemory, s.appSamples) {
        if (!byMemory) filtered else {
            val ranked = AppMemoryRanking.order(filtered.mapNotNull { s.appSamples[it.pkg] })
                .mapIndexed { index, sample -> sample.pkg to index }.toMap()
            filtered.sortedWith(compareBy<AppTarget> { ranked[it.pkg] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })
        }
    }
    val nextBatch = remember(filtered, s.appSamples) { filtered.filter { it.pkg !in s.appSamples }.ifEmpty { filtered }.take(AppMemorySampler.BATCH_LIMIT) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("${s.access.label()} · ${s.selected.size}/${SelectionPolicy.LIMIT}", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Найти приложение") }, singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Все", "Выбраны", "Исключены").forEachIndexed { i, label -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = byMemory, onClick = { byMemory = !byMemory }, label = { Text("По PSS") })
                if (s.scanProgress == null) {
                    TextButton(onClick = { onScan(nextBatch.map { it.pkg }) }, enabled = s.canEdit && !s.diagnosticBusy && s.access.state == AccessState.CONNECTED && nextBatch.isNotEmpty()) {
                        Text("Замерить ${nextBatch.size}")
                    }
                    TextButton(onClick = onClearSamples, enabled = !s.diagnosticBusy && s.appSamples.isNotEmpty()) { Text("Сброс") }
                } else TextButton(onClick = onCancelScan) { Text("Остановить") }
            }
            s.scanProgress?.let { Text("Замер $it", style = MaterialTheme.typography.bodySmall); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (byMemory || s.appSamples.isNotEmpty()) {
                Text("PSS: ${filtered.count { s.appSamples[it.pkg]?.pssKiB != null }}/${filtered.size} подтверждено. Замеры не одновременные.", style = MaterialTheme.typography.bodySmall)
            }
            if (s.loadingApps) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.appError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRefresh) { Text("Повторить") } }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(visible, key = { it.pkg }) { a ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(a.name, style = MaterialTheme.typography.titleSmall); Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Checkbox(checked = a.pkg in s.selected, onCheckedChange = { onSelect(a.pkg,it) }, enabled = s.canEdit && a.pkg !in s.protected)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onProtect(a.pkg,a.pkg !in s.protected) }, enabled = s.canEdit) { Text(if(a.pkg in s.protected) "Вернуть в список" else "Исключить") }
                        TextButton(onClick = { onInspect(a.pkg) }, enabled = s.access.state == AccessState.CONNECTED && !s.diagnosticBusy && s.progress == null) { Text("RAM") }
                    }
                    s.appSamples[a.pkg]?.let { sample ->
                        Text("PSS ${kib(sample.pssKiB)} · RSS ${kib(sample.rssKiB)}", style = MaterialTheme.typography.bodySmall)
                        Text("Замер ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(sample.measuredAt))}", style = MaterialTheme.typography.labelSmall)
                        sample.issue?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                    if(a.pkg in s.protected) Text("Исключено из команд Shizuku", style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
            }
            if (visible.isEmpty() && !s.loadingApps) item { Text("Нет приложений по этому фильтру", Modifier.padding(20.dp)) }
        }
    }
}
private fun kib(value: Long?): String = value?.let { String.format(Locale.getDefault(), "%.1f МиБ", it / 1024.0) } ?: "—"

@Composable internal fun ResultRow(r: CleanupResult) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(DateFormat.getDateTimeInstance().format(Date(r.time)), style = MaterialTheme.typography.labelMedium)
            Text("${r.engine} · ${r.profile}", style = MaterialTheme.typography.bodySmall)
            Text(r.outcome.label(), style = MaterialTheme.typography.labelSmall)
            if (r.trigger == CleanupTrigger.AUTOMATIC) Text("Автоматически", style = MaterialTheme.typography.labelSmall)
        }
        Text(delta(r.delta), style = MaterialTheme.typography.titleLarge)
    }
    Metric("Доступная RAM", "${gb(r.before)} → ${gb(r.after)}")
    r.settledDelta?.let { Metric("Изменение через 3 с", delta(it)) }
    r.swapDelta?.let { Metric("Изменение Swap", delta(it)) }
    r.durationMs?.let { Metric("Длительность очистки", "${it/1000.0} с") }
    Metric("Выделено Rust", "${r.allocated/MIB} МиБ")
    Metric("Команды приняты", "${r.accepted}/${r.attempted}")
    if (r.comparisons.isNotEmpty()) {
        var expanded by remember(r) { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть сравнение PSS" else "Сравнение PSS · ${r.comparisons.size}") }
        if (expanded) r.comparisons.forEach { c ->
            Text(c.pkg, style = MaterialTheme.typography.labelMedium)
            Metric("До → после", "${kib(c.before?.pssKiB)} → ${kib(c.after?.pssKiB)}")
            Metric("Изменение PSS", c.pssDeltaKiB?.let { delta(it * 1024) } ?: "Нет парного замера")
        }
    }
    if(r.cancelled) Text("Отменено")
    if(r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
}
@Composable fun JournalScreen(s: ScreenState, onExport: () -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val records = remember(s.history, filter) { s.history.filter {
        filter == 0 || filter == 1 && it.trigger == CleanupTrigger.MANUAL || filter == 2 && it.trigger == CleanupTrigger.AUTOMATIC
    } }
    val stats = remember(records) { HistoryAnalytics.summarize(records) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Все", "Ручные", "Авто").forEachIndexed { index, label ->
                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) })
                }
            }
            OutlinedButton(onClick = onExport, enabled = s.canEdit && !s.diagnosticBusy && s.history.isNotEmpty()) { Text(if (s.exportBusy) "Экспорт…" else "Экспорт всего журнала · CSV") }
            SectionLabel("Статистика · ${stats.count} операций")
            Metric("Выполнено / частично", "${stats.completed} / ${stats.partial}")
            Metric("Пропущено / отменено", "${stats.skipped} / ${stats.cancelled}")
            Metric("Ошибки", "${stats.failed}")
            Metric("Автоматические", "${stats.automatic}")
            Metric("Положительные замеры", "${stats.positive}/${stats.measured}")
            Metric("Медиана через 3 с", stats.medianDelta?.let { delta(it) } ?: "Нет данных")
            Text("Учитываются только сохранённые замеры выполненных и частичных операций. Изменение RAM не доказывает прирост FPS.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
        }
        if (records.isEmpty()) item { Text("Нет операций по этому фильтру") }
        items(records) { r -> Column { ResultRow(r); HorizontalDivider() } }
    }
}
@Composable fun SettingsScreen(s: ScreenState, onRequestAccess: () -> Unit, onProfile: (Profile) -> Unit, onRefresh: () -> Unit, onAutomation: (AutomationSettings) -> Unit, onBackground: (Boolean) -> Unit, onLimits: (NativeLimits) -> Unit, onCompare: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionLabel("Доступ"); Metric("Shizuku", s.access.label()); OutlinedButton(onClick = onRequestAccess, enabled = s.canEdit) { Text("Подключить") }; HorizontalDivider() }
        item { SectionLabel("Профиль Rust")
            Profile.entries.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.profile == p, onClick = { onProfile(p) }, enabled = s.canEdit)
                Column { Text(profileName(p)); Text("До ${p.capMiB} МиБ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
            Text("Лимит динамически снижается с учётом резерва, PSI и температуры.", style = MaterialTheme.typography.bodySmall); HorizontalDivider()
        }
        item {
            SectionLabel("Сравнительная диагностика")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PSS до и после очистки")
                    Text("До 12 выбранных приложений через Shizuku. Последовательные замеры увеличивают время операции.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = s.settings.compareSelected, enabled = s.canEdit, onCheckedChange = onCompare)
            }
            HorizontalDivider()
        }
        item {
            val limits = s.settings.nativeLimits
            SectionLabel("Ручные ограничения Rust")
            Metric("Максимум профиля с ограничением", "${limits.cap(s.profile)} МиБ")
            Metric("Удержание", "${limits.hold(s.profile)} мс")
            Text("Предел выделения", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NativeLimits.CAPS.forEach { cap ->
                    FilterChip(selected = limits.capMiB == cap, enabled = s.canEdit,
                        onClick = { onLimits(limits.copy(capMiB = cap)) },
                        label = { Text(if (cap == 0) "Профиль" else "$cap МиБ") })
                }
            }
            Text("Время удержания", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NativeLimits.HOLDS.forEach { ms ->
                    FilterChip(selected = limits.holdMs == ms, enabled = s.canEdit,
                        onClick = { onLimits(limits.copy(holdMs = ms)) },
                        label = { Text(if (ms < 0) "Профиль" else "$ms мс") })
                }
            }
            Text("Настройки только уменьшают лимит выбранного профиля; резерв, нагрев и PSI остаются обязательными. Автоочистка использует SOFT.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
        }
        item {
            val auto = s.settings.automation
            SectionLabel("Автоматизация")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Автоочистка")
                    Text("Только при открытом приложении · мягкий Rust", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = auto.foregroundEnabled, enabled = s.canEdit, onCheckedChange = { onAutomation(auto.copy(foregroundEnabled = it)) })
            }
            Text("Доступная RAM не выше", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AutomationSettings.THRESHOLDS.forEach { percent ->
                    FilterChip(selected = auto.thresholdPercent == percent, enabled = s.canEdit,
                        onClick = { onAutomation(auto.copy(thresholdPercent = percent)) }, label = { Text("$percent%") })
                }
            }
            Text("Минимальный интервал", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AutomationSettings.INTERVALS.forEach { minutes ->
                    FilterChip(selected = auto.intervalMinutes == minutes, enabled = s.canEdit,
                        onClick = { onAutomation(auto.copy(intervalMinutes = minutes)) }, label = { Text("$minutes мин") })
                }
            }
            Text("Два последовательных замера. Защита RAM, нагрева и PSI сохраняется. Shizuku автоматически не запускается.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Фоновые уведомления")
                    Text("Проверка примерно каждые 15 мин; Android может отложить запуск. Только уведомление, без очистки.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = auto.backgroundAlerts, enabled = s.canEdit, onCheckedChange = onBackground)
            }
            HorizontalDivider()
        }
        item { SectionLabel("Диагностика"); TextButton(onClick = onRefresh) { Text("Обновить список приложений") }; Metric("Версия", s.versionName); Metric("Движок", "Rust JNI"); Text("График обновляется на видимом экране. Виджет и плитку можно добавить средствами Android.", style = MaterialTheme.typography.bodySmall) }
    }
}

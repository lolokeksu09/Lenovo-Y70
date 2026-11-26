package com.ramwiper.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import java.util.Locale

internal fun gb(bytes: Long) = String.format(Locale.getDefault(), "%.2f ГиБ", bytes / (1024.0 * 1024 * 1024))
internal fun delta(bytes: Long) = (if(bytes > 0) "+" else "") + String.format(Locale.getDefault(), "%.0f МиБ", bytes / (1024.0 * 1024))
internal fun engineName(e: Engine) = when(e) { Engine.NATIVE -> "Rust"; Engine.SHIZUKU -> "Shizuku"; Engine.HYBRID -> "Hybrid" }
internal fun profileName(p: Profile) = when(p) { Profile.SOFT -> "Мягкий"; Profile.BALANCED -> "Баланс"; Profile.STRONG -> "Сильный" }
@Composable internal fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable internal fun SectionLabel(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}
@Composable fun MemoryScreen(s: ScreenState, sh: String, vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val m = s.snapshot
            Text("Доступная RAM", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (m == null) Text(s.monitorError ?: "Чтение памяти…") else {
                Row(verticalAlignment = Alignment.Bottom) { Text(gb(m.available), style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.width(10.dp)); Text("из ${gb(m.total)}", Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { if(m.total > 0) (m.available.toFloat()/m.total).coerceIn(0f,1f) else 0f }, modifier = Modifier.fillMaxWidth().height(8.dp))
                Spacer(Modifier.height(8.dp))
                if (s.samples.size > 1) {
                    Trend(s.samples, m.total)
                    Text("Последние измерения · шкала 0–${gb(m.total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Metric("Резерв Rust", gb(Policy.reserve(m)))
                Metric("Swap", if(m.swapTotal != null && m.swapFree != null) "${gb(m.swapTotal-m.swapFree)} / ${gb(m.swapTotal)}" else "Недоступно")
                Metric("PSI avg10", m.psiSome?.let { "$it%" } ?: "Недоступно")
                Metric("Тепловой статус", m.thermal?.let { when(it) { 0 -> "Норма"; 1 -> "Лёгкий"; 2 -> "Умеренный"; else -> "Высокий ($it)" } } ?: "Недоступно")
                s.monitorError?.let { Text("Последнее измерение: $it", color = MaterialTheme.colorScheme.error) }
                if (!Policy.safe(m)) Text("Rust заблокирован защитой памяти или температуры", color = MaterialTheme.colorScheme.error)
            }
        }
        item { HorizontalDivider(); SectionLabel("Очистка"); Text(sh, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Engine.entries.forEach { e -> FilterChip(selected = s.engine == e, onClick = { vm.engine(e) }, label = { Text(engineName(e)) }, enabled = s.progress == null) }
            }
            Metric("Профиль", profileName(s.profile))
            if(s.engine != Engine.NATIVE) Metric("Выбрано приложений", "${s.selected.size}/12")
            val p = s.progress
            if (p == null) Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !s.diagnosticBusy) { Text("Очистить память") }
            else {
                Text(p.stage)
                if (p.target > 0) LinearProgressIndicator(progress = { (p.allocated.toFloat()/p.target).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Остановить") }
            }
            if(s.message.isNotBlank()) Text(s.message, style = MaterialTheme.typography.bodyMedium)
            Text("Результат зависит от Android. Исключения не защищают от LMKD.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.history.firstOrNull()?.let { r -> item { HorizontalDivider(); SectionLabel("Последняя операция"); ResultRow(r) } }
    }
}
@Composable private fun Trend(samples: List<Long>, total: Long) {
    val color = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(52.dp)) {
        if (total <= 0 || samples.size < 2) return@Canvas
        for(i in 0..2) drawLine(guide, Offset(0f,size.height*i/2), Offset(size.width,size.height*i/2))
        fun point(i: Int) = Offset(size.width*i/(samples.size-1), size.height*(1f-(samples[i].toFloat()/total).coerceIn(0f,1f)))
        for (i in 1 until samples.size) drawLine(color, point(i-1), point(i), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

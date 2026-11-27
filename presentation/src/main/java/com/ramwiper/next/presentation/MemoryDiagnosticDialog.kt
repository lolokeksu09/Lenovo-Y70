package com.ramwiper.next.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable internal fun MemoryDiagnosticDialog(s: ScreenState, close: () -> Unit) {
    var raw by remember(s.diagnosticPackage) { mutableStateOf(false) }
    fun mib(kib: Long?): String = kib?.let { String.format(Locale.getDefault(), "%.1f МиБ", it / 1024.0) } ?: "Недоступно"
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Память приложения") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.diagnosticPackage.orEmpty(), style = MaterialTheme.typography.bodySmall)
                if (s.diagnosticBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Получение данных Shizuku…")
                } else {
                    val report = s.diagnosticReport
                    if (report != null && report.processes.isNotEmpty()) {
                        Metric("PSS · пропорциональная память", mib(report.totalPssKiB))
                        Metric("RSS · резидентная память", mib(report.totalRssKiB))
                        Metric("Swap PSS", mib(report.totalSwapPssKiB))
                        SectionLabel("Процессы · ${report.processes.size}")
                        report.processes.forEach { process ->
                            HorizontalDivider()
                            Text(process.name ?: "Процесс", style = MaterialTheme.typography.titleSmall)
                            Text("PID ${process.pid ?: "—"}", style = MaterialTheme.typography.labelSmall)
                            Metric("PSS", mib(process.pssKiB))
                            Metric("RSS", mib(process.rssKiB))
                            Metric("Swap PSS", mib(process.swapPssKiB))
                        }
                        Text("Сумма RSS может повторно учитывать общие страницы. Вывод meminfo может включать процессы других Android-профилей.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Числовые данные не получены. Приложение может быть не запущено или формат meminfo не поддерживается.")
                    }
                    TextButton(onClick = { raw = !raw }) { Text(if (raw) "Скрыть исходные данные" else "Исходные данные") }
                    if (raw) Text(s.diagnostic, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(if (s.diagnosticBusy) "Отмена" else "Закрыть") } }
    )
}

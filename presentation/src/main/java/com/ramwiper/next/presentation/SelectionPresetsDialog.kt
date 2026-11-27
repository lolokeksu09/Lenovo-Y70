package com.ramwiper.next.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ramwiper.next.domain.SelectionPresets

@Composable internal fun SelectionPresetsDialog(s: ScreenState, onSave: (String) -> Unit, onApply: (String) -> Unit,
                                                onDelete: (String) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val ready = s.canEdit && !s.loadingApps && s.appError == null
    val clean = name.trim()
    val duplicate = s.settings.presets.any { it.name.equals(clean,true) }
    val valid = clean.isNotEmpty() && !duplicate && clean.none { it.isISOControl() }
    AlertDialog(onDismissRequest = onClose, title = { Text("Наборы приложений") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Набор заменяет текущий выбор. Исключения и удалённые приложения учитываются заново. Очистка не запускается.", style = MaterialTheme.typography.bodySmall)
            if(s.settings.presets.isEmpty()) Text("Нет сохранённых наборов")
            s.settings.presets.forEach { preset ->
                HorizontalDivider()
                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                Text("${SelectionPresets.apply(preset,s.protected,s.targets).size} из ${preset.packages.size} доступны", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { onApply(preset.name); onClose() }, enabled = ready) { Text("Выбрать") }
                    TextButton(onClick = { deleting = preset.name }, enabled = s.canEdit) { Text("Удалить") }
                }
            }
            HorizontalDivider()
            Text("Сохранить текущий выбор · ${s.selected.size}")
            OutlinedTextField(name, { name = it.take(SelectionPresets.NAME_LIMIT) }, label = { Text("Название") }, singleLine = true,
                isError = duplicate, modifier = Modifier.fillMaxWidth(), enabled = ready)
            if(duplicate) Text("Название уже используется", color = MaterialTheme.colorScheme.error)
            Button(onClick = { onSave(clean) }, enabled = ready && valid && s.selected.isNotEmpty() && s.settings.presets.size < SelectionPresets.LIMIT) { Text("Сохранить набор") }
            if(s.settings.presets.size >= SelectionPresets.LIMIT) Text("Достигнут лимит 8 наборов")
            if(s.message.isNotBlank()) Text(s.message, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } })
    deleting?.let { target ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить набор?") },
            text = { Text("«$target». Текущий выбор приложений не изменится.") },
            confirmButton = { TextButton(onClick = { onDelete(target); deleting = null }, enabled = s.canEdit) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } })
    }
}

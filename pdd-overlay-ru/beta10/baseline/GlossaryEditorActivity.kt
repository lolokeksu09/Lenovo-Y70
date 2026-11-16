package app.pinduoduo.overlayru

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pinduoduo.overlayru.ui.design.PddPalette
import kotlinx.coroutines.launch

/** Local user translation corrections; no account, PRO, storage permission or network. */
class GlossaryEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = AndroidColor.rgb(7, 9, 12)
        @Suppress("DEPRECATION")
        window.navigationBarColor = AndroidColor.rgb(7, 9, 12)
        setContent { GlossaryEditorScreen(onBack = { finish() }) }
    }
}

private val DictionaryRed = PddPalette.Accent
private val DictionaryBg = PddPalette.Background
private val DictionarySurface = PddPalette.Surface
private val DictionaryText = PddPalette.Text
private val DictionaryMuted = PddPalette.Secondary
private val DictionaryBorder = PddPalette.Outline

@Composable
private fun GlossaryEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf("") }
    var translation by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val saved = remember(version) { CustomGlossary.entries(context) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DictionaryRed,
            onPrimary = Color.White,
            background = DictionaryBg,
            surface = DictionarySurface,
            onSurface = DictionaryText,
            onSurfaceVariant = DictionaryMuted
        )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 10.dp, bottom = 36.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text("‹  Назад") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Мой словарь",
                    color = DictionaryText, fontSize = 27.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Исправляй названия товаров, цветов и материалов. " +
                        "Словарь хранится на этом телефоне и работает без интернета.",
                    color = DictionaryMuted, fontSize = 13.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Суммы и действия оплаты нельзя заменить своим переводом.",
                    color = DictionaryMuted, fontSize = 12.sp
                )
            }
            item {
                Surface(
                    color = DictionarySurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, DictionaryBorder)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            if (editing) "Редактирование перевода" else "Новый перевод",
                            color = DictionaryText, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it },
                            label = { Text("Исходная надпись") },
                            placeholder = { Text("查看物流") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = translation,
                            onValueChange = { translation = it },
                            label = { Text("Русский вариант") },
                            placeholder = { Text("Отследить посылку") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (CustomGlossary.put(context, source, translation)) {
                                    status = "Перевод сохранён на устройстве"
                                    source = ""
                                    translation = ""
                                    editing = false
                                    version++
                                } else {
                                    status = "Не удалось сохранить. Проверь текст или лимит 200 правок."
                                }
                            },
                            enabled = CustomGlossaryPolicy.valid(source, translation),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DictionaryRed)
                        ) { Text(if (editing) "Сохранить изменения" else "Добавить перевод") }
                        if (editing) {
                            TextButton(onClick = {
                                source = ""
                                translation = ""
                                editing = false
                                status = ""
                            }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Text("Отмена редактирования")
                            }
                        }
                        if (status.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(status, color = DictionaryMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                Text(
                    "Сохранённые переводы · ${saved.size}/${CustomGlossaryPolicy.MAX_ENTRIES}",
                    color = DictionaryText, fontWeight = FontWeight.SemiBold
                )
                if (saved.isEmpty()) {
                    Text("Добавь первый перевод — он появится здесь.", color = DictionaryMuted, fontSize = 13.sp)
                }
            }
            items(saved, key = { it.first }) { item ->
                Surface(
                    color = DictionarySurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DictionaryBorder)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.first, color = DictionaryMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(item.second, color = DictionaryText, fontSize = 15.sp)
                        }
                        TextButton(onClick = {
                            source = item.first
                            translation = item.second
                            editing = true
                            status = "Измени русский вариант и сохрани"
                            scope.launch { listState.animateScrollToItem(1) }
                        }, modifier = Modifier.height(48.dp)) {
                            Text("Править", fontSize = 12.sp, color = DictionaryRed)
                        }
                        TextButton(onClick = {
                            if (CustomGlossary.delete(context, item.first)) {
                                if (source == item.first) {
                                    source = ""
                                    translation = ""
                                    editing = false
                                }
                                status = "Исправление удалено"
                                version++
                            }
                        }, modifier = Modifier.height(48.dp)) { Text("Удалить", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

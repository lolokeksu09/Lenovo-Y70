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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val DictionaryRed = Color(0xFFF13D51)
private val DictionaryBg = Color(0xFF07090C)
private val DictionarySurface = Color(0xFF14171D)
private val DictionaryText = Color(0xFFF4F5F8)
private val DictionaryMuted = Color(0xFFA7ADB8)
private val DictionaryBorder = Color(0xFF30353D)

@Composable
private fun GlossaryEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var source by rememberSaveable { mutableStateOf("") }
    var translation by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
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
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, top = 14.dp, bottom = 32.dp
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
                    "Исправляй отдельные китайские или английские подписи. " +
                        "Правки сохраняются только на телефоне и работают поверх встроенного словаря.",
                    color = DictionaryMuted, fontSize = 13.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Суммы, коды и кнопки подтверждения платежей изменять нельзя.",
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
                                    version++
                                } else {
                                    status = "Не удалось сохранить. Проверь текст или лимит 200 правок."
                                }
                            },
                            enabled = CustomGlossaryPolicy.valid(source, translation),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DictionaryRed)
                        ) { Text("Сохранить исправление") }
                        if (status.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(status, color = DictionaryMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                Text(
                    "Мои исправления · ${saved.size}/${CustomGlossaryPolicy.MAX_ENTRIES}",
                    color = DictionaryText, fontWeight = FontWeight.SemiBold
                )
                if (saved.isEmpty()) {
                    Text("Пока нет исправлений", color = DictionaryMuted, fontSize = 13.sp)
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
                            if (CustomGlossary.delete(context, item.first)) {
                                status = "Исправление удалено"
                                version++
                            }
                        }) { Text("Удалить", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

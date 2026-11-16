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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pinduoduo.overlayru.ui.design.PddFrame
import app.pinduoduo.overlayru.ui.design.PddNav
import app.pinduoduo.overlayru.ui.design.PddPalette
import app.pinduoduo.overlayru.ui.design.PddTab
import kotlinx.coroutines.launch

class GlossaryEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor=AndroidColor.rgb(6,8,13)
        @Suppress("DEPRECATION")
        window.navigationBarColor=AndroidColor.rgb(6,8,13)
        setContent {
            PddFrame(PddTab.DICTIONARY, onChoose={ PddNav.open(this,it) }) {
                DictionaryScreen(onBack={ finish() })
            }
        }
    }
}

@Composable
private fun DictionaryScreen(onBack: () -> Unit) {
    val context=LocalContext.current
    val scroll=rememberLazyListState()
    val scope=rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var translation by rememberSaveable { mutableStateOf("") }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var statusError by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val saved=remember(revision) { CustomGlossary.entries(context) }
    val filtered=remember(saved,query) {
        saved.filter {
            query.isBlank() || it.first.contains(query.trim(),ignoreCase=true) ||
                it.second.contains(query.trim(),ignoreCase=true)
        }
    }

    if (pendingDelete!=null) {
        AlertDialog(
            onDismissRequest={ pendingDelete=null },
            title={ Text("Удалить перевод?") },
            text={ Text("Исправление будет удалено только из локального словаря.") },
            confirmButton={
                TextButton(onClick={
                    val key=pendingDelete
                    if (key!=null && CustomGlossary.delete(context,key)) {
                        status="Исправление удалено"
                        statusError=false
                        if (editingKey==key || source==key) {
                            editingKey=null
                            source=""
                            translation=""
                            editing=false
                            showEditor=false
                        }
                        revision++
                    }
                    pendingDelete=null
                }) { Text("Удалить",color=PddPalette.Error) }
            },
            dismissButton={
                TextButton(onClick={ pendingDelete=null }) { Text("Отмена") }
            },
            containerColor=PddPalette.Surface,
            titleContentColor=PddPalette.Text,
            textContentColor=PddPalette.Secondary
        )
    }

    LazyColumn(
        state=scroll,
        modifier=Modifier.fillMaxSize().imePadding(),
        contentPadding=androidx.compose.foundation.layout.PaddingValues(
            start=20.dp,end=20.dp,top=15.dp,bottom=28.dp
        ),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item(key="head") {
            Text(
                "Мой словарь",
                style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold,color=PddPalette.Text
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Исправляй китайские названия и термины. Словарь работает офлайн.",
                style=MaterialTheme.typography.bodySmall,
                color=PddPalette.Secondary,lineHeight=19.sp
            )
            Spacer(Modifier.height(17.dp))
            OutlinedTextField(
                value=query,
                onValueChange={ query=it },
                label={ Text("Поиск по словарю") },
                placeholder={ Text("Китайский или русский текст") },
                singleLine=true,
                modifier=Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(13.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick={
                    editing=false
                    editingKey=null
                    status=""
                    source=""
                    translation=""
                    showEditor=!showEditor
                },
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
                shape=RoundedCornerShape(13.dp),
                colors=ButtonDefaults.buttonColors(containerColor=PddPalette.Accent)
            ) {
                Text(if (showEditor && !editing) "Скрыть форму" else "Добавить перевод")
            }
        }

        if (showEditor) {
            item(key="form") {
                Surface(
                    color=PddPalette.Surface,
                    shape=RoundedCornerShape(17.dp),
                    border=BorderStroke(1.dp,PddPalette.Outline)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(11.dp)
                    ) {
                        Text(
                            if (editing) "Изменить перевод" else "Новый перевод",
                            style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.SemiBold,color=PddPalette.Text
                        )
                        OutlinedTextField(
                            value=source,onValueChange={source=it},
                            label={Text("Оригинальная надпись")},
                            placeholder={Text("查看物流")},
                            singleLine=true,
                            modifier=Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value=translation,onValueChange={translation=it},
                            label={Text("Русский перевод")},
                            placeholder={Text("Отследить доставку")},
                            singleLine=true,
                            modifier=Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick={
                                val successful = if (editing) {
                                    editingKey?.let { CustomGlossary.replace(context,it,source,translation) } ?: false
                                } else {
                                    CustomGlossary.put(context,source,translation)
                                }
                                if (successful) {
                                    status="Перевод сохранён на телефоне"
                                    statusError=false
                                    source=""
                                    translation=""
                                    editing=false
                                    editingKey=null
                                    showEditor=false
                                    revision++
                                } else {
                                    status="Не удалось сохранить. Проверь текст, лимит и совпадающие оригиналы."
                                    statusError=true
                                }
                            },
                            enabled=CustomGlossaryPolicy.valid(source,translation),
                            shape=RoundedCornerShape(12.dp),
                            modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)
                        ) { Text(if (editing) "Сохранить изменения" else "Сохранить перевод") }
                        if (editing) {
                            TextButton(
                                onClick={
                                    showEditor=false
                                    editing=false
                                    editingKey=null
                                    source=""
                                    translation=""
                                },
                                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                            ) { Text("Отменить редактирование") }
                        }
                    }
                }
            }
        }

        item(key="count") {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier=Modifier.fillMaxWidth(),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween
            ) {
                Text("Сохранённые переводы",
                    style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.SemiBold,color=PddPalette.Text)
                Text(
                    saved.size.toString()+"/"+CustomGlossaryPolicy.MAX_ENTRIES,
                    style=MaterialTheme.typography.labelMedium,
                    color=PddPalette.Secondary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Коды, суммы и подтверждения оплаты не заменяются.",
                style=MaterialTheme.typography.bodySmall,
                color=PddPalette.Secondary
            )
            if (status.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(status,color=if (statusError) PddPalette.Error else PddPalette.Success,
                    style=MaterialTheme.typography.bodySmall)
            }
        }

        if (filtered.isEmpty()) {
            item(key="empty") {
                Surface(
                    color=PddPalette.Surface,
                    shape=RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            if (saved.isEmpty()) "Словарь пока пуст"
                            else "Совпадений не найдено",
                            color=PddPalette.Text,
                            style=MaterialTheme.typography.titleSmall,
                            fontWeight=FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (saved.isEmpty()) "Добавь первый перевод названия товара."
                            else "Попробуй изменить поисковый запрос.",
                            color=PddPalette.Secondary,
                            style=MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            items(filtered,key={ it.first }) { item ->
                Surface(
                    color=PddPalette.Surface,
                    shape=RoundedCornerShape(15.dp),
                    border=BorderStroke(1.dp,PddPalette.Outline)
                ) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Text(item.first,color=PddPalette.Secondary,
                            style=MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(item.second,color=PddPalette.Text,
                            style=MaterialTheme.typography.bodyLarge,
                            fontWeight=FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier=Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.End
                        ) {
                            TextButton(
                                onClick={
                                    source=item.first
                                    translation=item.second
                                    editing=true
                                    editingKey=item.first
                                    showEditor=true
                                    status=""
                                    scope.launch { scroll.animateScrollToItem(1) }
                                },
                                modifier=Modifier.heightIn(min=48.dp)
                            ) { Text("Править") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick={ pendingDelete=item.first },
                                modifier=Modifier.heightIn(min=48.dp)
                            ) { Text("Удалить",color=PddPalette.Secondary) }
                        }
                    }
                }
            }
        }
    }
}

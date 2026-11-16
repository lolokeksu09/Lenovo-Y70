package app.pinduoduo.overlayru

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pinduoduo.overlayru.ui.design.PddPalette

/** Settings reflect real ML Kit / Accessibility states rather than concept-board demos. */
@Composable
internal fun PremiumSettingsScreen(
    state: SettingsSnapshot,
    onEnableTranslation: (Boolean) -> Unit,
    onFloatingButton: (Boolean) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onAccessibility: () -> Unit,
    onAppInfo: () -> Unit,
    onDownloadChinese: () -> Unit,
    onDownloadEnglish: () -> Unit,
    onInspectModels: () -> Unit,
    onLicense: () -> Unit,
    onStore: () -> Unit
) {
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier=Modifier.fillMaxSize(),
        contentPadding=androidx.compose.foundation.layout.PaddingValues(
            horizontal=20.dp,vertical=18.dp
        ),
        verticalArrangement=Arrangement.spacedBy(15.dp)
    ) {
        item {
            Text("Настройки перевода",
                style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold,color=PddPalette.Text)
            Spacer(Modifier.height(5.dp))
            Text("Разрешения, языковые модели и параметры отображения.",
                style=MaterialTheme.typography.bodySmall,
                color=PddPalette.Secondary,lineHeight=19.sp)
        }

        item {
            PddSettingsCard {
                Row(
                    modifier=Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier=Modifier.weight(1f)) {
                        Text("Специальные возможности",
                            style=MaterialTheme.typography.titleSmall,
                            fontWeight=FontWeight.SemiBold,color=PddPalette.Text)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            when {
                                state.accessibilityConnected -> "Служба подключена"
                                state.accessibilityAllowed -> "Разрешена, но служба не подключена"
                                else -> "Требуется разрешение Android"
                            },
                            style=MaterialTheme.typography.bodySmall,
                            color=PddPalette.Secondary,lineHeight=17.sp
                        )
                    }
                    StatusPill(
                        if (state.accessibilityConnected) "Активна"
                        else if (state.accessibilityAllowed) "Ожидание"
                        else "Нет доступа",
                        state.accessibilityConnected
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick=onAccessibility,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) { Text("Открыть настройки службы") }
                TextButton(
                    onClick=onAppInfo,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) { Text("Сведения о приложении",color=PddPalette.Secondary) }
            }
        }

        item {
            Title("Управление переводом")
            Spacer(Modifier.height(8.dp))
            PddSettingsCard {
                ToggleItem(
                    "Включить русский перевод",
                    "Работает только в Pinduoduo",
                    state.translationEnabled,onEnableTranslation
                )
                Divider()
                ToggleItem(
                    "Плавающая кнопка «РУ»",
                    "Быстрый доступ поверх Pinduoduo",
                    state.floatingButtonEnabled,onFloatingButton
                )
            }
        }

        item {
            Title("Офлайн-модели")
            Spacer(Modifier.height(8.dp))
            PddSettingsCard {
                ModelItem(
                    "Китайский → русский",
                    state.chineseReady,state.chinesePreparing,state.chineseOverdue,
                    state.chineseError,onDownloadChinese
                )
                Divider()
                ModelItem(
                    "Английский → русский",
                    state.englishReady,state.englishPreparing,state.englishOverdue,
                    state.englishError,onDownloadEnglish
                )
                Divider()
                ToggleItem(
                    "Только по Wi-Fi",
                    "После загрузки модели работают офлайн",
                    state.wifiOnly,onWifiOnly
                )
                if (state.vpnActive && !state.chineseReady) {
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "VPN может мешать загрузке ML Kit. Временно отключи его и проверь ещё раз.",
                        color=PddPalette.Warning,
                        style=MaterialTheme.typography.bodySmall,lineHeight=19.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick=onInspectModels,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {
                    if (state.inspecting) {
                        CircularProgressIndicator(
                            modifier=Modifier.padding(end=8.dp).height(16.dp).width(16.dp),
                            strokeWidth=2.dp
                        )
                    }
                    Text("Проверить локальные модели")
                }
            }
        }

        item {
            PddSettingsCard {
                Row(
                    modifier=Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    Column(modifier=Modifier.weight(1f)) {
                        Text("Лицензия",style=MaterialTheme.typography.titleSmall,
                            fontWeight=FontWeight.SemiBold,color=PddPalette.Text)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (state.pro) "PRO подтверждён" else
                                "Free · основной перевод доступен",
                            color=PddPalette.Secondary,
                            style=MaterialTheme.typography.bodySmall
                        )
                    }
                    StatusPill(if (state.pro) "PRO" else "Free",state.pro)
                }
                Spacer(Modifier.height(5.dp))
                TextButton(
                    onClick=onLicense,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) { Text("Управление лицензией") }
            }
        }

        item {
            PddSettingsCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(modifier=Modifier.weight(1f)) {
                        Text("Диагностика и приватность",
                            style=MaterialTheme.typography.titleSmall,
                            fontWeight=FontWeight.SemiBold,color=PddPalette.Text)
                        Spacer(Modifier.height(4.dp))
                        Text("Локальный перевод без записи экрана",
                            color=PddPalette.Secondary,
                            style=MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick={ diagnosticsExpanded=!diagnosticsExpanded },
                        modifier=Modifier.heightIn(min=48.dp)
                    ) { Text(if (diagnosticsExpanded) "Скрыть" else "Открыть") }
                }
                if (diagnosticsExpanded) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (state.shizukuInstalled) "Shizuku установлен; состояние службы не проверяется"
                        else "Shizuku не установлен",
                        color=PddPalette.Secondary,
                        style=MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Неудачных переводов: " + state.translationFailures,
                        color=PddPalette.Secondary,
                        style=MaterialTheme.typography.bodySmall
                    )
                    if (state.inspection.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(state.inspection,color=PddPalette.Secondary,
                            style=MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "OCR выбранных фотографий и перевод выполняются на устройстве. " +
                        "Пароли, коды и поля ввода не переводятся. " +
                        "Для PRO может использоваться ключ и идентификатор установки.",
                        color=PddPalette.Secondary,
                        style=MaterialTheme.typography.bodySmall,lineHeight=19.sp
                    )
                }
            }
        }

        item {
            TextButton(onClick=onStore,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Открыть Pinduoduo",color=PddPalette.Secondary)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PddSettingsCard(content: @Composable () -> Unit) {
    Surface(
        color=PddPalette.Surface,
        shape=RoundedCornerShape(17.dp),
        border=BorderStroke(1.dp,PddPalette.Outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

@Composable
private fun Title(value: String) {
    Text(value,style=MaterialTheme.typography.titleMedium,
        fontWeight=FontWeight.SemiBold,color=PddPalette.Text)
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color=PddPalette.Outline,thickness=.5.dp)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ToggleItem(
    title: String,detail: String,checked: Boolean,onChange: (Boolean) -> Unit
) {
    Row(
        modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(11.dp)
    ) {
        Column(modifier=Modifier.weight(1f)) {
            Text(title,color=PddPalette.Text,style=MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(3.dp))
            Text(detail,color=PddPalette.Secondary,
                style=MaterialTheme.typography.bodySmall,lineHeight=17.sp)
        }
        Switch(
            checked=checked,onCheckedChange=onChange,
            colors=SwitchDefaults.colors(
                checkedThumbColor=Color.White,
                checkedTrackColor=PddPalette.Accent,
                uncheckedThumbColor=PddPalette.Secondary,
                uncheckedTrackColor=PddPalette.Elevated
            )
        )
    }
}

@Composable
private fun ModelItem(
    title: String,ready: Boolean,preparing: Boolean,overdue: Boolean,
    error: String,onAction: () -> Unit
) {
    Row(
        modifier=Modifier.fillMaxWidth(),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier=Modifier.weight(1f)) {
            Text(title,color=PddPalette.Text,
                style=MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(3.dp))
            Text(
                when {
                    ready -> "Доступна офлайн"
                    preparing && overdue -> "ML Kit долго не отвечает"
                    preparing -> "Выполняется загрузка"
                    error.isNotBlank() -> "Ошибка: " + error
                    else -> "Не загружена"
                },
                color=if (ready) PddPalette.Success else PddPalette.Secondary,
                style=MaterialTheme.typography.bodySmall,lineHeight=18.sp
            )
        }
        if (ready) StatusPill("Готова",true)
        else OutlinedButton(
            onClick=onAction,
            enabled=!preparing || overdue,
            modifier=Modifier.heightIn(min=48.dp)
        ) { Text(if (preparing) "Проверить" else "Скачать",
            style=MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun StatusPill(label: String,positive: Boolean) {
    Surface(
        color=if (positive) PddPalette.Success.copy(alpha=.14f) else PddPalette.Elevated,
        shape=RoundedCornerShape(9.dp)
    ) {
        Text(
            label,
            modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp),
            color=if (positive) PddPalette.Success else PddPalette.Secondary,
            style=MaterialTheme.typography.labelSmall
        )
    }
}

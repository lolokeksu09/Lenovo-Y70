package app.pinduoduo.overlayru.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * PDD Overlay RU — premium AMOLED, but task-first:
 * readiness and Start are one decision; translating a photo and customizing
 * labels are discoverable immediately rather than a 2x2 dashboard.
 */
private val Night = Color(0xFF080A0E)
private val Sheet = Color(0xFF141920)
private val SheetHighlight = Color(0xFF1B2028)
private val Hairline = Color(0xFF303741)
private val PrimaryInk = Color(0xFFF5F6F9)
private val SecondaryInk = Color(0xFFB4BCC8)
private val SubtleInk = Color(0xFF919BAA)
private val PddRed = Color(0xFFEA3A50)
private val Green = Color(0xFF63DDBB)
private val Amber = Color(0xFFFFC678)

private val PddDarkScheme = darkColorScheme(
    primary = PddRed,
    onPrimary = Color.White,
    background = Night,
    onBackground = PrimaryInk,
    surface = Sheet,
    onSurface = PrimaryInk,
    onSurfaceVariant = SecondaryInk,
    outline = Hairline
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPrimaryAction: () -> Unit,
    onCheckAgain: () -> Unit,
    onAppInfo: () -> Unit,
    onSettings: () -> Unit,
    onLicense: () -> Unit,
    onOcr: () -> Unit,
    onDictionary: () -> Unit
) {
    MaterialTheme(colorScheme = PddDarkScheme) {
        Scaffold(
            containerColor = Night,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = PddRed
                            ) {
                                Text(
                                    "P", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Text(
                                "PDD Overlay RU", color = PrimaryInk,
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = onSettings,
                            modifier = Modifier.height(48.dp)
                        ) { Text("Настройки", fontSize = 13.sp, color = SecondaryInk) }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Night, titleContentColor = PrimaryInk
                    )
                )
            }
        ) { insets ->
            Box(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 610.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 40.dp)
                ) {
                    Text(
                        "Pinduoduo",
                        color = PrimaryInk, fontWeight = FontWeight.Bold,
                        fontSize = 34.sp, lineHeight = 40.sp
                    )
                    Text(
                        "на русском",
                        color = PddRed, fontWeight = FontWeight.Bold,
                        fontSize = 34.sp, lineHeight = 40.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Магазин, изображения и собственные переводы — в одном приложении.",
                        color = SecondaryInk, fontSize = 14.sp, lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(26.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Sheet,
                        border = BorderStroke(1.dp, Hairline)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val color = if (state.ready) Green else Amber
                                Box(
                                    Modifier.size(9.dp).background(color, CircleShape)
                                )
                                Spacer(Modifier.width(11.dp))
                                Text(
                                    state.status,
                                    color = PrimaryInk,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                when (state.blocker) {
                                    HomeBlocker.CHECKING -> "Проверяем службу и наличие языковых моделей."
                                    HomeBlocker.READY -> "Служба и модели готовы — можно открывать магазин."
                                    HomeBlocker.ACCESSIBILITY_DISABLED -> "Один раз включи службу PDD в специальных возможностях Android."
                                    HomeBlocker.SERVICE_DISCONNECTED -> "Разрешение есть, но Android пока не запустил службу."
                                    HomeBlocker.MODEL_MISSING -> "Скачай офлайн-модель перевода с китайского."
                                    HomeBlocker.MODEL_LOADING -> "Загружаем модель. Повторный запрос не нужен."
                                    HomeBlocker.MODEL_OVERDUE -> "Проверим, завершена ли загрузка модели."
                                    HomeBlocker.PDD_MISSING -> "Установи оригинальное приложение Pinduoduo."
                                },
                                color = SecondaryInk, fontSize = 13.sp, lineHeight = 19.sp
                            )
                            if (state.message != null || (
                                state.blocker == HomeBlocker.MODEL_MISSING &&
                                    state.modelError.isNotBlank()
                            )) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    state.message ?: state.modelError,
                                    color = Amber, fontSize = 12.sp, lineHeight = 19.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onPrimaryAction,
                                enabled = state.actionable,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PddRed,
                                    contentColor = Color.White,
                                    disabledContainerColor = Hairline,
                                    disabledContentColor = SecondaryInk
                                )
                            ) {
                                Text(
                                    state.action,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (!state.ready && state.blocker != HomeBlocker.CHECKING) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onCheckAgain,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Hairline)
                                ) { Text("Проверить снова", color = PrimaryInk, fontSize = 13.sp) }
                            }
                            if (state.blocker == HomeBlocker.ACCESSIBILITY_DISABLED) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Если служба заблокирована, проверь «Ограниченные настройки» в сведениях о приложении.",
                                    color = SecondaryInk, fontSize = 12.sp, lineHeight = 18.sp
                                )
                                TextButton(
                                    onClick = onAppInfo, modifier = Modifier.height(48.dp)
                                ) { Text("Открыть сведения о приложении", color = PrimaryInk) }
                            }
                        }
                    }

                    Spacer(Modifier.height(30.dp))
                    Text(
                        "Инструменты", color = PrimaryInk, fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Используй отдельно от экранного перевода",
                        color = SubtleInk, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    FeatureRow(
                        code = "OCR",
                        title = "Перевод фото",
                        detail = "Скриншоты и китайские надписи",
                        onClick = onOcr
                    )
                    Spacer(Modifier.height(9.dp))
                    FeatureRow(
                        code = "Aa",
                        title = "Мой словарь",
                        detail = "Свои названия, цвета и термины",
                        onClick = onDictionary
                    )

                    Spacer(Modifier.height(28.dp))
                    Text(
                        "Приложение", color = PrimaryInk, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = Sheet,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Hairline)
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            SettingsRow(
                                title = "Кнопка «РУ»",
                                detail = if (state.floatingButtonEnabled) "Включена" else "Выключена",
                                onClick = onSettings
                            )
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    .height(1.dp).background(Hairline)
                            )
                            SettingsRow(
                                title = "Лицензия",
                                detail = if (state.isPro) "PRO активен" else "Free",
                                onClick = onLicense
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = onSettings,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Настройки и диагностика", color = SecondaryInk, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    code: String,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Sheet,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(color = SheetHighlight, shape = RoundedCornerShape(12.dp)) {
                Box(
                    Modifier.size(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(code, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PddRed)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = PrimaryInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(detail, color = SecondaryInk, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Text("›", color = SecondaryInk, fontSize = 22.sp)
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Sheet) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = PrimaryInk, fontSize = 14.sp)
            Text(detail, color = SecondaryInk, fontSize = 12.sp)
        }
    }
}

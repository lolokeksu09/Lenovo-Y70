package app.pinduoduo.overlayru.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pinduoduo.overlayru.ui.design.PddPalette
import app.pinduoduo.overlayru.ui.design.PddTheme

/**
 * Job-first Home: the primary action follows actual readiness; optional
 * tools stay accessible, and administrative details do not interrupt setup.
 * Every callback and state used by the existing HomeViewModel is preserved.
 */
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
    PddTheme {
        Scaffold(
            containerColor = PddPalette.Background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = PddPalette.Accent
                            ) {
                                Text(
                                    "RU", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "PDD Overlay", color = PddPalette.Text,
                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = onSettings,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Настройки", color = PddPalette.Secondary, fontSize = 13.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PddPalette.Background
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
                        .widthIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        "Перевод Pinduoduo",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PddPalette.Text
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Китайский интерфейс — на русском. Без изменения магазина.",
                        color = PddPalette.Secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(20.dp))

                    ReadinessPanel(
                        state = state,
                        onPrimaryAction = onPrimaryAction,
                        onCheckAgain = onCheckAgain,
                        onAppInfo = onAppInfo
                    )

                    Spacer(Modifier.height(28.dp))
                    Text(
                        "Инструменты",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PddPalette.Text
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = PddPalette.Surface,
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        Column {
                            FeatureRow(
                                title = "Перевести фотографию",
                                subtitle = "OCR товара и перевод прямо на фото",
                                isScan = true,
                                onClick = onOcr
                            )
                            HorizontalDivider(
                                color = PddPalette.Outline,
                                modifier = Modifier.padding(start = 72.dp),
                                thickness = 0.5.dp
                            )
                            FeatureRow(
                                title = "Мой словарь",
                                subtitle = "Исправить названия и термины",
                                isScan = false,
                                onClick = onDictionary
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Приложение",
                        style = MaterialTheme.typography.titleSmall,
                        color = PddPalette.Secondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(9.dp))
                    Surface(
                        color = PddPalette.Surface,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column {
                            UtilityRow(
                                title = "Плавающая кнопка «РУ»",
                                value = if (state.floatingButtonEnabled) "Включена" else "Выключена",
                                onClick = onSettings
                            )
                            HorizontalDivider(
                                color = PddPalette.Outline,
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp
                            )
                            UtilityRow(
                                title = "Лицензия",
                                value = if (state.isPro) "PRO" else "Free",
                                onClick = onLicense
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ReadinessPanel(
    state: HomeUiState,
    onPrimaryAction: () -> Unit,
    onCheckAgain: () -> Unit,
    onAppInfo: () -> Unit
) {
    Surface(
        color = PddPalette.Surface,
        shape = RoundedCornerShape(19.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val signal = when (state.blocker) {
                    HomeBlocker.READY -> PddPalette.Success
                    HomeBlocker.CHECKING, HomeBlocker.MODEL_LOADING -> PddPalette.Secondary
                    else -> PddPalette.Warning
                }
                Box(Modifier.size(9.dp).background(signal, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    state.status,
                    color = PddPalette.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                guidance(state.blocker),
                color = PddPalette.Secondary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
            val errorMessage = state.message ?: state.modelError.takeIf {
                state.blocker == HomeBlocker.MODEL_MISSING && it.isNotBlank()
            }
            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    errorMessage,
                    color = PddPalette.Warning,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(17.dp))
            Button(
                onClick = onPrimaryAction,
                enabled = state.actionable,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PddPalette.Accent,
                    contentColor = Color.White,
                    disabledContainerColor = PddPalette.Elevated,
                    disabledContentColor = PddPalette.Secondary
                )
            ) {
                Text(
                    state.action,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            if (!state.ready && state.blocker != HomeBlocker.CHECKING) {
                TextButton(
                    onClick = onCheckAgain,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Проверить состояние", color = PddPalette.Text)
                }
            }
            if (state.blocker == HomeBlocker.ACCESSIBILITY_DISABLED) {
                Text(
                    "Не удаётся включить службу? Проверь ограниченные настройки Android.",
                    color = PddPalette.Secondary,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = onAppInfo,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Сведения о приложении", color = PddPalette.Accent)
                }
            }
        }
    }
}

private fun guidance(blocker: HomeBlocker): String = when (blocker) {
    HomeBlocker.CHECKING -> "Проверяем, можно ли переводить экран."
    HomeBlocker.READY -> "Всё готово. Открой Pinduoduo — русский перевод будет поверх оригинала."
    HomeBlocker.ACCESSIBILITY_DISABLED -> "Включи службу PDD Overlay RU в специальных возможностях Android."
    HomeBlocker.SERVICE_DISCONNECTED -> "Разрешение выдано, но служба ещё не запущена."
    HomeBlocker.MODEL_MISSING -> "Скачай офлайн-модель китайского языка для перевода."
    HomeBlocker.MODEL_LOADING -> "Ожидаем завершения загрузки языковой модели."
    HomeBlocker.MODEL_OVERDUE -> "Модель долго не отвечает. Проверь её состояние."
    HomeBlocker.PDD_MISSING -> "Установи оригинальное приложение Pinduoduo."
}

@Composable
private fun FeatureRow(
    title: String,
    subtitle: String,
    isScan: Boolean,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = PddPalette.Surface) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 80.dp)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = PddPalette.Elevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    FeatureIcon(scan = isScan)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, color = PddPalette.Text,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle, color = PddPalette.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }
            Text("›", color = PddPalette.Secondary, fontSize = 21.sp)
        }
    }
}

/** Small monochrome custom icons; no unicode letters pretending to be icons. */
@Composable
private fun FeatureIcon(scan: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        if (scan) {
            val arm = w * 0.29f
            val pad = stroke / 2
            val segments = listOf(
                Offset(pad, arm), Offset(pad, pad), Offset(arm, pad),
                Offset(w - arm, pad), Offset(w - pad, pad), Offset(w - pad, arm),
                Offset(pad, h - arm), Offset(pad, h - pad), Offset(arm, h - pad),
                Offset(w - arm, h - pad), Offset(w - pad, h - pad), Offset(w - pad, h - arm)
            )
            for (i in 0 until segments.size step 3) {
                drawLine(PddPalette.Accent, segments[i], segments[i + 1], stroke)
                drawLine(PddPalette.Accent, segments[i + 1], segments[i + 2], stroke)
            }
            drawLine(
                PddPalette.Accent,
                Offset(w * 0.25f, h * 0.50f),
                Offset(w * 0.75f, h * 0.50f),
                stroke
            )
        } else {
            drawLine(
                PddPalette.Accent, Offset(w * 0.50f, h * 0.17f),
                Offset(w * 0.50f, h * 0.90f), stroke
            )
            drawLine(
                PddPalette.Accent, Offset(w * 0.50f, h * 0.23f),
                Offset(w * 0.14f, h * 0.12f), stroke
            )
            drawLine(
                PddPalette.Accent, Offset(w * 0.50f, h * 0.23f),
                Offset(w * 0.86f, h * 0.12f), stroke
            )
            drawLine(
                PddPalette.Accent, Offset(w * 0.14f, h * 0.12f),
                Offset(w * 0.14f, h * 0.79f), stroke
            )
            drawLine(
                PddPalette.Accent, Offset(w * 0.86f, h * 0.12f),
                Offset(w * 0.86f, h * 0.79f), stroke
            )
            drawLine(
                PddPalette.Accent, Offset(w * 0.14f, h * 0.79f),
                Offset(w * 0.50f, h * 0.90f), stroke
            )
            drawLine(
                PddPalette.Accent, Offset(w * 0.86f, h * 0.79f),
                Offset(w * 0.50f, h * 0.90f), stroke
            )
        }
    }
}

@Composable
private fun UtilityRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = PddPalette.Surface) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title, color = PddPalette.Text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                value, color = PddPalette.Secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

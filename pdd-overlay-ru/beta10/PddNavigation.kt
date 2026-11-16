package app.pinduoduo.overlayru.ui.design

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pinduoduo.overlayru.GlossaryEditorActivity
import app.pinduoduo.overlayru.HomeActivity
import app.pinduoduo.overlayru.ImageOcrActivity
import app.pinduoduo.overlayru.MainActivity

internal enum class PddTab(val title: String) {
    HOME("Главная"),
    PHOTO("Фото"),
    DICTIONARY("Словарь"),
    SETTINGS("Настройки")
}

/** Android tasks are retained; only top-level navigation uses REORDER_TO_FRONT. */
internal object PddNav {
    fun open(activity: Activity, tab: PddTab) {
        val destination = when (tab) {
            PddTab.HOME -> HomeActivity::class.java
            PddTab.PHOTO -> ImageOcrActivity::class.java
            PddTab.DICTIONARY -> GlossaryEditorActivity::class.java
            PddTab.SETTINGS -> MainActivity::class.java
        }
        if (activity.javaClass == destination) return
        activity.startActivity(
            Intent(activity, destination).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }
}

@Composable
internal fun PddBottomBar(
    current: PddTab,
    onChoose: (PddTab) -> Unit
) {
    NavigationBar(
        containerColor = PddPalette.Surface,
        contentColor = PddPalette.Text,
        tonalElevation = 0.dp
    ) {
        for (tab in PddTab.entries) {
            val selected = current == tab
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onChoose(tab) },
                icon = { PddTabIcon(tab, selected) },
                label = { Text(tab.title, fontSize = 11.sp) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PddPalette.Accent,
                    selectedTextColor = PddPalette.Accent,
                    indicatorColor = PddPalette.Elevated,
                    unselectedIconColor = PddPalette.Secondary,
                    unselectedTextColor = PddPalette.Secondary
                )
            )
        }
    }
}

/** Common system-inset-safe screen container for Photo/Dictionary/Settings. */
@Composable
internal fun PddFrame(
    selected: PddTab,
    onChoose: (PddTab) -> Unit,
    content: @Composable () -> Unit
) {
    PddTheme {
        Scaffold(
            containerColor = PddPalette.Background,
            bottomBar = { PddBottomBar(selected, onChoose) }
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) { content() }
        }
    }
}

/** One native stroke language shared by all tabs; no emoji placeholders. */
@Composable
internal fun PddTabIcon(tab: PddTab, selected: Boolean) {
    val ink = if (selected) PddPalette.Accent else PddPalette.Secondary
    Canvas(Modifier.size(23.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        when (tab) {
            PddTab.HOME -> {
                val roof = Path().apply {
                    moveTo(w * 0.12f,h * 0.45f)
                    lineTo(w * 0.50f,h * 0.12f)
                    lineTo(w * 0.88f,h * 0.45f)
                }
                drawPath(roof,ink, style=Stroke(sw))
                drawRoundRect(
                    color=ink,topLeft=Offset(w*0.22f,h*0.43f),
                    size=Size(w*0.56f,h*0.45f),
                    cornerRadius=CornerRadius(2.dp.toPx()),
                    style=Stroke(sw)
                )
                drawLine(ink, Offset(w*.50f,h*.61f),Offset(w*.50f,h*.88f),sw)
            }
            PddTab.PHOTO -> {
                drawRoundRect(
                    color=ink,topLeft=Offset(sw,sw),
                    size=Size(w-2*sw,h-2*sw),
                    cornerRadius=CornerRadius(4.dp.toPx()),style=Stroke(sw)
                )
                drawCircle(ink, radius=2.4.dp.toPx(), center=Offset(w*.72f,h*.31f))
                val mountains=Path().apply {
                    moveTo(w*.14f,h*.73f)
                    lineTo(w*.37f,h*.51f)
                    lineTo(w*.52f,h*.68f)
                    lineTo(w*.64f,h*.58f)
                    lineTo(w*.85f,h*.75f)
                }
                drawPath(mountains,ink, style=Stroke(sw))
            }
            PddTab.DICTIONARY -> {
                val left=Path().apply {
                    moveTo(w*.50f,h*.23f)
                    lineTo(w*.16f,h*.12f)
                    lineTo(w*.16f,h*.80f)
                    lineTo(w*.50f,h*.90f)
                    close()
                }
                val right=Path().apply {
                    moveTo(w*.50f,h*.23f)
                    lineTo(w*.84f,h*.12f)
                    lineTo(w*.84f,h*.80f)
                    lineTo(w*.50f,h*.90f)
                    close()
                }
                drawPath(left,ink,style=Stroke(sw))
                drawPath(right,ink,style=Stroke(sw))
                drawLine(ink,Offset(w*.50f,h*.23f),Offset(w*.50f,h*.90f),sw)
            }
            PddTab.SETTINGS -> {
                drawCircle(ink, radius=w*.27f,center=Offset(w*.5f,h*.5f),style=Stroke(sw))
                drawCircle(ink, radius=w*.085f,center=Offset(w*.5f,h*.5f),style=Stroke(sw))
                for (angle in 0 until 8) {
                    val theta = angle * Math.PI / 4.0
                    val dx = kotlin.math.cos(theta).toFloat()
                    val dy = kotlin.math.sin(theta).toFloat()
                    drawLine(
                        ink,
                        Offset(w*.5f+dx*w*.30f,h*.5f+dy*h*.30f),
                        Offset(w*.5f+dx*w*.42f,h*.5f+dy*h*.42f),sw
                    )
                }
            }
        }
    }
}

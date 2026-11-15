#!/usr/bin/env python3
"""One-time migration of the versioned :app ZIP, never touching licensing or server files."""
import base64
import io
import zipfile
from pathlib import Path

source = Path("pdd-overlay-ru/source_bundle.base64")
compressed = base64.b64decode(source.read_text().strip())
with zipfile.ZipFile(io.BytesIO(compressed)) as archive:
    infos = archive.infolist()
    files = {info.filename: archive.read(info) for info in infos if not info.is_dir()}

base = "pinduoduo_overlay_ru/"
def patch(path, original, replacement):
    key = base + path
    before = files[key].decode("utf-8")
    count = before.count(original)
    if count != 1:
        raise RuntimeError(f"Patch precondition for {path}: expected 1 occurrence, saw {count}")
    files[key] = before.replace(original, replacement).encode("utf-8")

gradle = "app/build.gradle"
patch(gradle, "versionCode 11\n        versionName '0.2.0-beta.1'",
              "versionCode 12\n        versionName '0.2.0-beta.2'")
activity = "app/src/main/java/app/pinduoduo/overlayru/HomeActivity.kt"
patch(activity, "                onPrimaryAction = { vm.primaryAction(this) },",
      """                onPrimaryAction = { vm.primaryAction(this) },
                onCheckAgain = { vm.checkAgain() },
                onAppInfo = { vm.openAppInfo(this) },""")
vm = "app/src/main/java/app/pinduoduo/overlayru/HomeViewModel.kt"
patch(vm, "import android.content.Intent\n", "import android.content.Intent\nimport android.net.Uri\n")
patch(vm, "    fun primaryAction(activity: Activity) {", """    fun checkAgain() {
        refresh(null)
        val snapshot = _state.value
        _state.value = snapshot.copy(
            message = when (snapshot.blocker) {
                HomeBlocker.READY -> "Готово: можно открывать Pinduoduo."
                HomeBlocker.MODEL_LOADING -> "ML Kit ещё готовит китайскую модель."
                else -> "Проверено: " + snapshot.status + "."
            }
        )
    }

    /** For sideloaded Android apps: app-info restricted settings can block Accessibility. */
    fun openAppInfo(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null))
            activity.startActivity(intent)
            refresh("Проверь меню ⋮ → «Разрешить ограниченные настройки» (если оно есть). Затем включи службу Accessibility.")
        } catch (_: ActivityNotFoundException) {
            refresh("Не удалось открыть сведения о приложении. Найди PDD Overlay RU в настройках.")
        } catch (_: SecurityException) {
            refresh("Android запретил открывать сведения о приложении.")
        }
    }

    fun primaryAction(activity: Activity) {""")
patch(vm,
  '            HomeBlocker.PDD_MISSING -> refresh("Установи оригинальный Pinduoduo, затем повтори проверку.")',
  """            HomeBlocker.PDD_MISSING -> {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.pinduoduo.com/")))
                    refresh("Открыт официальный сайт Pinduoduo. После установки нажми «Проверить снова».")
                } catch (_: ActivityNotFoundException) {
                    refresh("Не удалось открыть сайт. Установи Pinduoduo из доверенного источника.")
                } catch (_: SecurityException) {
                    refresh("Android не разрешил открыть сайт Pinduoduo.")
                }
            }""")
state = "app/src/main/java/app/pinduoduo/overlayru/ui/home/HomeUiState.kt"
patch(state, 'HomeBlocker.PDD_MISSING -> "Как установить Pinduoduo"',
      'HomeBlocker.PDD_MISSING -> "Открыть сайт Pinduoduo"')
screen = "app/src/main/java/app/pinduoduo/overlayru/ui/home/HomeScreen.kt"
patch(screen, "import androidx.compose.material3.ButtonDefaults\n",
             "import androidx.compose.material3.ButtonDefaults\nimport androidx.compose.material3.OutlinedButton\n")
patch(screen, "    onPrimaryAction: () -> Unit,\n    onSettings:",
              "    onPrimaryAction: () -> Unit,\n    onCheckAgain: () -> Unit,\n    onAppInfo: () -> Unit,\n    onSettings:")
patch(screen, "                if (state.message != null) {", """                if (!state.ready && state.blocker != HomeBlocker.CHECKING) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onCheckAgain,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Outline)
                    ) { Text("Проверить снова", color = TextPrimary) }
                }
                if (state.blocker == HomeBlocker.ACCESSIBILITY_DISABLED) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Если Android блокирует службу, открой сведения о приложении и разреши ограниченные настройки (если пункт доступен).",
                        color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    TextButton(onClick = onAppInfo, modifier = Modifier.height(48.dp)) {
                        Text("Сведения о приложении", color = TextPrimary, fontSize = 13.sp)
                    }
                }
                if (state.message != null) {""")
test = "app/src/test/java/app/pinduoduo/overlayru/HomeReadinessTest.kt"
original = files[base + test].decode("utf-8")
if not original.endswith("}\n"):
    raise RuntimeError("Unexpected test file end")
cases = """
    @Test fun missingPddHasOfficialSiteAction() {
        val ui = app.pinduoduo.overlayru.ui.home.HomeUiState(blocker = HomeBlocker.PDD_MISSING)
        assertEquals("Открыть сайт Pinduoduo", ui.action)
        assertEquals(true, ui.actionable)
    }

    @Test fun modelDownloadingDisablesPrimaryAction() {
        val ui = app.pinduoduo.overlayru.ui.home.HomeUiState(blocker = HomeBlocker.MODEL_LOADING)
        assertEquals(false, ui.actionable)
        assertEquals("Загружаем китайскую модель", ui.status)
    }
"""
files[base + test] = (original[:-2] + cases + "}\n").encode("utf-8")
patch("ONE_MODULE_ARCHITECTURE.md",
      "Version: " + "\`0.2.0-beta.1\`" + " / " + "\`versionCode 11\`",
      "Version: " + "\`0.2.0-beta.2\`" + " / " + "\`versionCode 12\`")

result = io.BytesIO()
with zipfile.ZipFile(result, "w") as dst:
    for info in infos:
        if info.is_dir():
            dst.writestr(info, b"")
        else:
            dst.writestr(info, files[info.filename])
source.write_text(base64.b64encode(result.getvalue()).decode("ascii") + "\n")
print("Repacked app v0.2.0-beta.2: one Gradle module, licensing unchanged")

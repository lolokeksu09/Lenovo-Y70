package com.ramwiper.next.presentation

import com.ramwiper.next.domain.*

data class ScreenState(
    val versionName: String,
    val settings: UserSettings = UserSettings(),
    val settingsLoaded: Boolean = false,
    val initialLoading: Boolean = true,
    val initialError: String? = null,
    val settingsBusy: Boolean = false,
    val snapshot: Snapshot? = null,
    val access: AccessStatus = AccessStatus(),
    val targets: List<AppTarget> = emptyList(),
    val selected: Set<String> = emptySet(),
    val progress: Progress? = null,
    val history: List<CleanupResult> = emptyList(),
    val message: String = "",
    val diagnostic: String = "",
    val diagnosticReport: AppMemoryReport? = null,
    val diagnosticPackage: String? = null,
    val diagnosticBusy: Boolean = false,
    val samples: List<Long> = emptyList(),
    val loadingApps: Boolean = true,
    val appError: String? = null,
    val monitorError: String? = null,
    val cooldownSeconds: Int = 0
) {
    val engine get() = settings.engine
    val profile get() = settings.profile
    val protected get() = settings.protected
    val canEdit get() = settingsLoaded && !settingsBusy && progress == null
    val canStart get() = canEdit && !diagnosticBusy && cooldownSeconds == 0 &&
        (engine != Engine.SHIZUKU || access.state == AccessState.CONNECTED && selected.isNotEmpty())
}

internal fun AccessStatus.label(): String = when (state) {
    AccessState.UNAVAILABLE -> "Shizuku недоступен"
    AccessState.NEED_PERMISSION -> "Требуется разрешение Shizuku"
    AccessState.CONNECTING -> "Подключение…"
    AccessState.CONNECTED -> "Shizuku подключён"
    AccessState.DISCONNECTED -> "Shizuku отключён"
    AccessState.DENIED -> "Доступ отклонён"
    AccessState.ERROR -> "Ошибка Shizuku" + (detail?.let { ": $it" } ?: "")
}

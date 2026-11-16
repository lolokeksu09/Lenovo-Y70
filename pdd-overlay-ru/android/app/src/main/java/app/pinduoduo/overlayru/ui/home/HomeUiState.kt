package app.pinduoduo.overlayru.ui.home

data class HomeUiState(
    val blocker: HomeBlocker = HomeBlocker.CHECKING,
    val isPro: Boolean = false,
    val translationEnabled: Boolean = false,
    val floatingButtonEnabled: Boolean = true,
    val modelError: String = "",
    val inProgress: Boolean = false,
    val message: String? = null
) {
    val status: String get() = when (blocker) {
        HomeBlocker.CHECKING -> "Проверяем готовность"
        HomeBlocker.PDD_MISSING -> "Pinduoduo не найден"
        HomeBlocker.ACCESSIBILITY_DISABLED -> "Нужно разрешение Android"
        HomeBlocker.SERVICE_DISCONNECTED -> "Служба не подключена"
        HomeBlocker.MODEL_LOADING -> "Загружаем китайскую модель"
        HomeBlocker.MODEL_OVERDUE -> "ML Kit долго не отвечает"
        HomeBlocker.MODEL_MISSING -> if (modelError.isBlank()) "Нужна китайская модель" else "Проблема с моделью"
        HomeBlocker.READY -> if (translationEnabled) "Перевод включён" else "Готов к переводу"
    }
    val action: String get() = when (blocker) {
        HomeBlocker.CHECKING -> "Проверяем…"
        HomeBlocker.PDD_MISSING -> "Открыть сайт Pinduoduo"
        HomeBlocker.ACCESSIBILITY_DISABLED -> "Разрешить перевод"
        HomeBlocker.SERVICE_DISCONNECTED -> "Проверить службу"
        HomeBlocker.MODEL_LOADING -> "Подготовка модели…"
        HomeBlocker.MODEL_OVERDUE -> "Проверить модель"
        HomeBlocker.MODEL_MISSING -> "Скачать китайскую модель"
        HomeBlocker.READY -> if (translationEnabled) "Открыть Pinduoduo" else "Переводить Pinduoduo"
    }
    val actionable: Boolean get() = !inProgress && blocker != HomeBlocker.MODEL_LOADING && blocker != HomeBlocker.CHECKING
    val ready: Boolean get() = blocker == HomeBlocker.READY
}

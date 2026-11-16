package app.pinduoduo.overlayru.ui.home

/** Deterministic precedence for the single primary action; no Android dependencies. */
enum class HomeBlocker {
    CHECKING,
    PDD_MISSING,
    ACCESSIBILITY_DISABLED,
    SERVICE_DISCONNECTED,
    MODEL_LOADING,
    MODEL_OVERDUE,
    MODEL_MISSING,
    READY
}

object HomeReadiness {
    @JvmStatic
    fun resolve(
        hasPdd: Boolean,
        accessibilityAllowed: Boolean,
        serviceConnected: Boolean,
        modelReady: Boolean,
        modelPreparing: Boolean,
        modelOverdue: Boolean
    ): HomeBlocker = when {
        !hasPdd -> HomeBlocker.PDD_MISSING
        !accessibilityAllowed -> HomeBlocker.ACCESSIBILITY_DISABLED
        !serviceConnected -> HomeBlocker.SERVICE_DISCONNECTED
        modelReady -> HomeBlocker.READY
        modelPreparing && modelOverdue -> HomeBlocker.MODEL_OVERDUE
        modelPreparing -> HomeBlocker.MODEL_LOADING
        else -> HomeBlocker.MODEL_MISSING
    }
}

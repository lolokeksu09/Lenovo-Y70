package app.pinduoduo.overlayru

import com.google.mlkit.common.model.DownloadConditions

/** Respect the user's model-download network preference for every ML Kit request. */
object ModelDownloadConditionsFactory {
    @JvmStatic
    fun create(wifiOnly: Boolean): DownloadConditions =
        DownloadConditions.Builder().apply {
            if (wifiOnly) requireWifi()
        }.build()
}

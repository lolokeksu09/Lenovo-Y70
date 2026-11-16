import app.pinduoduo.overlayru.ModelDownloadConditionsFactory;
import com.google.mlkit.common.model.DownloadConditions;

public final class ModelDownloadConditionsTest {
    public static void main(String[] args) {
        DownloadConditions mobileAllowed = ModelDownloadConditionsFactory.create(false);
        if (mobileAllowed.isWifiRequired()) throw new AssertionError("Mobile-enabled download must not require Wi-Fi");
        DownloadConditions wifiOnly = ModelDownloadConditionsFactory.create(true);
        if (!wifiOnly.isWifiRequired()) throw new AssertionError("Wi-Fi-only option must require Wi-Fi");
        if (ModelDownloadConditionsFactory.create(false).isWifiRequired()) {
            throw new AssertionError("Changing mode must not retain stale Wi-Fi requirement");
        }
        System.out.println("PASS: 3 model download network policy checks");
    }
}

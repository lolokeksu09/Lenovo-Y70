package com.google.mlkit.common.model;

/** Test-only API stub: checks the download policy without Android dependencies. */
public final class DownloadConditions {
    private final boolean wifiRequired;
    private DownloadConditions(boolean wifiRequired) { this.wifiRequired = wifiRequired; }
    public boolean isWifiRequired() { return wifiRequired; }
    public static final class Builder {
        private boolean wifiRequired;
        public Builder requireWifi() { wifiRequired = true; return this; }
        public DownloadConditions build() { return new DownloadConditions(wifiRequired); }
    }
}

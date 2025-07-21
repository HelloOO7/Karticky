package cz.nocard.scraper_bootleg;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CodeType {
    @JsonProperty("code128")
    CODE128,
    @JsonProperty("ean13")
    EAN13,
    @JsonProperty("qr")
    QR,
    @JsonProperty("interleaved2of5")
    ITF
}

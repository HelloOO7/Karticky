package cz.nocard.android;

import androidx.annotation.Keep;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.zxing.BarcodeFormat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Keep
public class NoCardConfig {

    //bug: records are broken on Android+Jackson when using R8

    private final Map<String, String> wlanMappings;
    private final LinkedHashMap<String, ProviderInfo> cardData;

    @JsonCreator
    public NoCardConfig(
            @JsonProperty("wlanMappings") Map<String, String> wlanMappings,
            @JsonProperty("cardData") LinkedHashMap<String, ProviderInfo> cardData
    ) {
        this.wlanMappings = wlanMappings;
        this.cardData = cardData;
    }

    @JsonProperty("wlanMappings")
    public Map<String, String> wlanMappings() {
        return wlanMappings;
    }

    @JsonProperty("cardData")
    public LinkedHashMap<String, ProviderInfo> cardData() {
        return cardData;
    }

    @Keep
    public static class ProviderInfo {

        private final BarcodeFormat format;
        private final List<String> codes;

        @JsonCreator
        public ProviderInfo(
                @JsonProperty("format") BarcodeFormat format,
                @JsonProperty("codes") List<String> codes
        ) {
            this.format = format;
            this.codes = codes;
        }

        @JsonProperty("format")
        public BarcodeFormat format() {
            return format;
        }

        @JsonProperty("codes")
        public List<String> codes() {
            return codes;
        }
    }
}

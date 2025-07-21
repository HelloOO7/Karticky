package cz.nocard.scraper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoCardConfig {

    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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

    public static class ProviderInfo {

        private final String providerName;
        private final String membershipName;
        private final Integer brandColor;
        private final Integer brandColorContrast;
        private final BarcodeFormat format;
        private final List<String> codes;

        @JsonCreator
        public ProviderInfo(
                @JsonProperty("providerName") String providerName,
                @JsonProperty("membershipName") String membershipName,
                @JsonProperty("brandColor") Integer brandColor,
                @JsonProperty("brandColorContrast") Integer brandColorContrast,
                @JsonProperty("format") BarcodeFormat format,
                @JsonProperty("codes") List<String> codes
        ) {
            this.providerName = providerName;
            this.membershipName = membershipName;
            this.brandColor = brandColor;
            this.brandColorContrast = brandColorContrast;
            this.format = format;
            this.codes = codes;
        }

        @JsonProperty("providerName")
        public String providerName() {
            return providerName;
        }

        @JsonProperty("membershipName")
        public String membershipName() {
            return membershipName;
        }

        @JsonProperty("brandColor")
        public Integer brandColor() {
            return brandColor;
        }

        @JsonProperty("brandColorContrast")
        public Integer brandColorContrast() {
            return brandColorContrast;
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

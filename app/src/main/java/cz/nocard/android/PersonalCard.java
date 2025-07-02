package cz.nocard.android;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.zxing.BarcodeFormat;

import java.util.function.Function;

@Keep
public class PersonalCard {

    public static final String PROVIDER_CUSTOM = "_CUSTOM";

    private final int id;
    private String name;
    private final String provider;
    private final CustomCardProperties customProperties;
    private final String cardNumber;

    @JsonCreator
    public PersonalCard(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name,
            @JsonProperty("provider") String provider,
            @Nullable @JsonProperty("customProperties") CustomCardProperties customProperties,
            @JsonProperty("cardNumber") String cardNumber
    ) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.customProperties = customProperties;
        this.cardNumber = cardNumber;
    }

    public PersonalCard(int id, String name, String provider, String cardNumber) {
        this(id, name, provider, null, cardNumber);
    }

    public PersonalCard(int id, String name, CustomCardProperties customProperties, String cardNumber) {
        this(id, name, PROVIDER_CUSTOM, customProperties, cardNumber);
    }

    public static String formatDefaultName(String providerName, String cardNumber) {
        return providerName + "\n" + cardNumber;
    }

    @JsonProperty("id")
    public int id() {
        return id;
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    public String singleLineName() {
        return name.replace("\n", " ");
    }

    @JsonProperty("provider")
    public String provider() {
        return provider;
    }

    @JsonProperty("customProperties")
    @Nullable
    public CustomCardProperties customProperties() {
        return customProperties;
    }

    public boolean isCustom() {
        return customProperties != null;
    }

    public boolean canConvertToNonCustom(Function<String, Boolean> providerKnown) {
        return !PROVIDER_CUSTOM.equals(provider) && providerKnown.apply(provider);
    }

    @JsonProperty("cardNumber")
    public String cardNumber() {
        return cardNumber;
    }

    void rename(String newName) {
        this.name = newName;
    }

    @Keep
    public static class CustomCardProperties {

        private final String providerName;
        private final BarcodeFormat format;
        private int color;

        @JsonCreator
        public CustomCardProperties(
                @JsonProperty("providerName") String providerName,
                @JsonProperty("format") BarcodeFormat format,
                @JsonProperty("color") int color
        ) {
            this.providerName = providerName;
            this.format = format;
            this.color = color;
        }

        @JsonProperty("providerName")
        public String providerName() {
            return providerName;
        }

        @JsonProperty("format")
        public BarcodeFormat format() {
            return format;
        }

        @JsonProperty("color")
        public int color() {
            return color;
        }

        void changeColor(int newColor) {
            this.color = newColor;
        }
    }
}

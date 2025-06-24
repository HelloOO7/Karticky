package cz.nocard.android;

import androidx.annotation.Keep;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Keep
public class PersonalCard {

    private final int id;
    private String name;
    private final String provider;
    private final String cardNumber;

    @JsonCreator
    public PersonalCard(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name,
            @JsonProperty("provider") String provider,
            @JsonProperty("cardNumber") String cardNumber
    ) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.cardNumber = cardNumber;
    }

    public static String formatDefaultName(String providerName, String cardNumber) {
        return providerName + "\n" + cardNumber;
    }

    @JsonProperty
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

    @JsonProperty("cardNumber")
    public String cardNumber() {
        return cardNumber;
    }

    void rename(String newName) {
        this.name = newName;
    }
}

package cz.mamstylcendy.cards.data;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.zxing.BarcodeFormat;

import java.util.function.Function;

@Keep
public class PersonalCard implements Parcelable {

    public static final String PROVIDER_CUSTOM = "_CUSTOM";

    public static final Creator<PersonalCard> CREATOR = new Creator<PersonalCard>() {
        @Override
        public PersonalCard createFromParcel(Parcel in) {
            return new PersonalCard(in);
        }

        @Override
        public PersonalCard[] newArray(int size) {
            return new PersonalCard[size];
        }
    };

    private final int id;
    @Nullable
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

    protected PersonalCard(Parcel in) {
        id = in.readInt();
        name = in.readString();
        provider = in.readString();
        customProperties = ParcelCompat.readParcelable(in, CustomCardProperties.class.getClassLoader(), CustomCardProperties.class);
        cardNumber = in.readString();
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeString(provider);
        dest.writeParcelable(customProperties, flags);
        dest.writeString(cardNumber);
    }

    @Keep
    public static class CustomCardProperties implements Parcelable {

        public static final Creator<CustomCardProperties> CREATOR = new Creator<CustomCardProperties>() {
            @Override
            public CustomCardProperties createFromParcel(Parcel in) {
                return new CustomCardProperties(in);
            }

            @Override
            public CustomCardProperties[] newArray(int size) {
                return new CustomCardProperties[size];
            }
        };

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

        protected CustomCardProperties(Parcel in) {
            providerName = in.readString();
            format = ParcelCompat.readSerializable(in, BarcodeFormat.class.getClassLoader(), BarcodeFormat.class);
            color = in.readInt();
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

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(providerName);
            dest.writeSerializable(format);
            dest.writeInt(color);
        }
    }
}

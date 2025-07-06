package cz.nocard.android;

import android.net.Uri;
import android.util.Base64;

import java.util.HashMap;
import java.util.Map;

public class LinkCardTransfer {

    private static final String PARAM_TYPE = "t";
    private static final String PARAM_DATA = "d";

    private static final int BASE64_FLAGS = Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING;

    public static LinkType getLinkType(Uri link) {
        String type = link.getQueryParameter(PARAM_TYPE);
        if (type == null) {
            return null;
        }
        return LinkType.QUERY_TO_LINK_TYPE.get(type);
    }

    public static byte[] getData(Uri link) {
        String dataString = link.getQueryParameter(PARAM_DATA);
        if (dataString == null) {
            return null;
        }
        return Base64.decode(dataString, BASE64_FLAGS);
    }

    public static Uri newDeepLink(LinkType type, byte[] packet) {
        return Uri.parse(
                BuildConfig.DEEP_LINK_BASE_URL
                + "?t=" + type.queryTypeName
                + "&d=" + Base64.encodeToString(packet, BASE64_FLAGS)
        );
    }

    public static CardTransfer newCardTransfer() {
        return new CardTransfer();
    }

    public static enum LinkType {
        APP_CARD_PACKET("acp");

        private static final Map<String, LinkType> QUERY_TO_LINK_TYPE = new HashMap<>();

        static {
            for (LinkType lt : LinkType.values()) {
                QUERY_TO_LINK_TYPE.put(lt.queryTypeName, lt);
            }
        }

        private final String queryTypeName;

        private LinkType(String queryTypeName) {
            this.queryTypeName = queryTypeName;
        }
    }
}

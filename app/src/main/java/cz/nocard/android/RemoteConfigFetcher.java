package cz.nocard.android;

import android.util.Log;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.zxing.BarcodeFormat;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteConfigFetcher {

    private static final String TAG = RemoteConfigFetcher.class.getSimpleName();

    private static final Pattern CARD_DATA_REGEX = Pattern.compile(
            "const cardData = (\\{.*\\});",
            Pattern.DOTALL
    );

    private static final ObjectMapper JS_JSON_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    private static Connection.Response sendConfigRequest(Connection.Method method) throws IOException {
        return Jsoup.connect(BuildConfig.REMOTE_CONFIG_PAGE)
                .method(method)
                .timeout(10000)
                .execute();
    }

    public static Result fetchRemoteConfig(NoCardConfig current, String currentETag) throws IOException {
        Connection.Response response = sendConfigRequest(Connection.Method.HEAD);

        String etag = null;

        if (response.hasHeader("ETag")) {
            etag = response.header("ETag");
            if (currentETag != null && Objects.equals(etag, currentETag)) {
                return new Result(Status.NO_CHANGE, current, etag);
            }
        }

        Document page = sendConfigRequest(Connection.Method.GET).parse();

        LinkedHashMap<String, NoCardConfig.ProviderInfo> providerInfos = new LinkedHashMap<>();

        Elements providers = page.select("div[data-key]");
        for (Element providerDiv : providers) {
            providerInfos.put(
                    providerDiv.attr("data-key"),
                    new NoCardConfig.ProviderInfo(parseBarcodeFormat(providerDiv.attr("data-type")), new ArrayList<>())
            );
        }

        String cardDataScript = findCardDataScript(page);
        if (cardDataScript == null) {
            Log.e(TAG, "No card data script found in the remote config page.");
            return new Result(Status.INCOMPATIBLE, null, null);
        }

        Matcher matcher = CARD_DATA_REGEX.matcher(cardDataScript);
        if (!matcher.find()) {
            Log.e(TAG, "No card data found in the script (should not happen - bad regex?).");
            return new Result(Status.INCOMPATIBLE, null, null);
        }

        String cardJson = matcher.group(1);

        CardData cardData = JS_JSON_MAPPER.readValue(cardJson, CardData.class);
        for (var entry : cardData.entrySet()) {
            NoCardConfig.ProviderInfo pi = providerInfos.get(entry.getKey());
            if (pi == null) {
                Log.w(TAG, "Card data found, but no provider info for " + entry.getKey());
                continue;
            }
            pi.codes().addAll(entry.getValue());
        }

        return new Result(Status.SUCCESS, new NoCardConfig(current.wlanMappings(), providerInfos), etag);
    }

    private static String findCardDataScript(Document page) {
        for (Element script : page.getElementsByTag("script")) {
            String scriptText = getScriptText(script);
            if (scriptText.contains("const cardData")) {
                return scriptText;
            }
        }
        return null;
    }

    private static String getScriptText(Element scriptElement) {
        if (scriptElement.childNodeSize() > 0) {
            Node content = scriptElement.childNode(0);
            if (content instanceof DataNode text) {
                return text.getWholeData();
            }
        }
        return "";
    }

    private static BarcodeFormat parseBarcodeFormat(String attr) {
        return switch (attr) {
            case "qr" -> BarcodeFormat.QR_CODE;
            case "ean13" -> BarcodeFormat.EAN_13;
            case "ean8" -> BarcodeFormat.EAN_8;
            case "code39" -> BarcodeFormat.CODE_39;
            case "code93" -> BarcodeFormat.CODE_93;
            case "code128" -> BarcodeFormat.CODE_128;
            case "itf" -> BarcodeFormat.ITF;
            default -> throw new IllegalArgumentException("Unknown barcode format: " + attr);
        };
    }

    private static class CardData extends LinkedHashMap<String, List<String>> {

    }

    public static enum Status {
        SUCCESS,
        NO_CHANGE,
        INCOMPATIBLE
    }

    public static record Result(Status status, NoCardConfig config, String eTag) {

    }
}

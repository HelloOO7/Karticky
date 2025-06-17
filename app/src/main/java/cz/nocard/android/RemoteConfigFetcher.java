package cz.nocard.android;

import android.graphics.Color;
import android.util.Log;

import androidx.annotation.Keep;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteConfigFetcher {

    private static final String TAG = RemoteConfigFetcher.class.getSimpleName();

    private static final Pattern CARD_DATA_REGEX = Pattern.compile(
            "const cardData = (\\{.*\\});",
            Pattern.DOTALL
    );

    private static final Pattern COLOR_CLASS_REGEX = Pattern.compile(
            "\\.card\\.([^ :]+)([^#]+)(#[A-Fa-f0-9]+);"
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

        Map<BrandColorKey, Integer> brandColors = extractBrandColors(page);

        Elements providers = page.select("div[data-key]");
        for (Element providerDiv : providers) {
            providerInfos.put(
                    providerDiv.attr("data-key"),
                    new NoCardConfig.ProviderInfo(
                            providerDiv.text(),
                            providerDiv.attr("data-name"),
                            getBrandColor(brandColors, providerDiv, false),
                            getBrandColor(brandColors, providerDiv, true),
                            parseBarcodeFormat(providerDiv.attr("data-type")),
                            new ArrayList<>()
                    )
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

    private static Integer getBrandColor(Map<BrandColorKey, Integer> brandColors, Element providerElement, boolean isContrast) {
        for (String clazz : providerElement.classNames()) {
            BrandColorKey key = new BrandColorKey(clazz, isContrast);
            Integer color = brandColors.get(key);
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    private static Map<BrandColorKey, Integer> extractBrandColors(Document document) {
        Map<BrandColorKey, Integer> brandColors = new HashMap<>();
        for (Element style : document.getElementsByTag("style")) {
            String styleText = getScriptText(style);
            Matcher matcher = COLOR_CLASS_REGEX.matcher(styleText);
            while (matcher.find()) {
                String className = matcher.group(1);
                String spec = matcher.group(2);
                String colorHex = matcher.group(3);

                if (className != null && colorHex != null) {
                    boolean isContrast = spec != null && spec.contains("h2");
                    try {
                        brandColors.put(new BrandColorKey(className, isContrast), Color.parseColor(expandCssColor(colorHex)));
                    } catch (IllegalArgumentException ex) {
                        Log.e(TAG, "Invalid Android color format: " + colorHex, ex);
                    }
                }
            }
        }
        return brandColors;
    }

    private static String expandCssColor(String color) {
        if (color.length() == 4) {
            char c1 = color.charAt(1);
            char c2 = color.charAt(2);
            char c3 = color.charAt(3);
            color = "#" + c1 + c1 + c2 + c2 + c3 + c3;
        }
        return color;
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

    @Keep
    private static class CardData extends LinkedHashMap<String, List<String>> {

    }

    public static enum Status {
        SUCCESS,
        NO_CHANGE,
        INCOMPATIBLE
    }

    public static record Result(Status status, NoCardConfig config, String eTag) {

    }

    private static record BrandColorKey(String cssClass, boolean isContrast) {

    }
}

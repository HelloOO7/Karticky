package cz.mamstylcendy.cards.scraper;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.zxing.BarcodeFormat;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteConfigFetcher {

    private static final Pattern COLOR_CLASS_REGEX = Pattern.compile(
            "\\.card\\.([^ :]+)([^#]+)(#[A-Fa-f0-9]+);"
    );
    private static final Pattern CARD_DATA_REGEX = Pattern.compile(
            "window.cardData = (\\{.*?\\});"
    );

    private static final ObjectMapper CARD_DATA_OBJECT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .build();

    private static Connection.Response sendConfigRequest(String url, Connection.Method method) throws IOException {
        return Jsoup.connect(url)
                .method(method)
                .userAgent("NoCard-Android Remote config fetcher")
                .timeout(10000)
                .execute();
    }

    public static NoCardConfig fetchRemoteConfig(String url) throws IOException {
        Document page = sendConfigRequest(url, Connection.Method.GET).parse();

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
                            new ArrayList<>(List.of(providerDiv.attr("data-code")))
                    )
            );
        }

        return new NoCardConfig(Map.of(), providerInfos);
    }

    public static NoCardConfig fetchRemoteConfigUsingCardData(String url) throws IOException {
        Document page = sendConfigRequest(url, Connection.Method.GET).parse();

        LinkedHashMap<String, NoCardConfig.ProviderInfo> providerInfos = new LinkedHashMap<>();

        Map<BrandColorKey, Integer> brandColors = extractBrandColors(page);

        String cardDataScript = getCardDataScript(page);

        WebCardData.Map cardData = CARD_DATA_OBJECT_MAPPER.readValue(cardDataScript, WebCardData.Map.class);

        Elements providers = page.select("div[data-key]");
        for (Element providerDiv : providers) {
            String key = providerDiv.attr("data-key");
            WebCardData jsonData = cardData.get(key);
            if (jsonData == null) {
                System.err.println("Warning: No card data found for key: " + key);
                continue;
            }
            providerInfos.put(
                    key,
                    new NoCardConfig.ProviderInfo(
                            providerDiv.text(),
                            providerDiv.attr("data-name"),
                            getBrandColor(brandColors, providerDiv, false),
                            getBrandColor(brandColors, providerDiv, true),
                            parseBarcodeFormat(jsonData.type()),
                            jsonData.codes()
                    )
            );
        }

        return new NoCardConfig(Map.of(), providerInfos);
    }

    private static String getCardDataScript(Document page) {
        for (Element scriptElement : page.getElementsByTag("script")) {
            String scriptText = getScriptText(scriptElement);

            Matcher matcher = CARD_DATA_REGEX.matcher(scriptText);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
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
                    brandColors.put(new BrandColorKey(className, isContrast), parseColor(expandCssColor(colorHex)));
                }
            }
        }
        return brandColors;
    }

    private static int parseColor(String color) {
        String hex = color.substring(1);
        return Integer.parseInt(hex, 16) | 0xFF000000;
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

    private static record BrandColorKey(String cssClass, boolean isContrast) {

    }
}

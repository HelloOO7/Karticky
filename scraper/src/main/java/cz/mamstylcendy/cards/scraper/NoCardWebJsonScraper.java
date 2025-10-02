package cz.mamstylcendy.cards.scraper;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class NoCardWebJsonScraper {

    private static final String WEB_URL = "https://nocard.cz/";

    public static void main(String[] args) throws Exception {
        NoCardConfig mergedConfig = NoCardConfig.JSON_MAPPER.readValue(new File("data/base-config.json"), NoCardConfig.class);

        NoCardConfig config = RemoteConfigFetcher.fetchRemoteConfigUsingCardData(WEB_URL);
        config.cardData().forEach((s, providerInfo) -> {
            if (!mergedConfig.cardData().containsKey(s)) {
                mergedConfig.cardData().put(s, providerInfo);
            } else {
                List<String> dest = mergedConfig.cardData().get(s).codes();
                for (String code : providerInfo.codes()) {
                    if (!dest.contains(code)) {
                        dest.add(code);
                    }
                }
            }
        });

        for (NoCardConfig.ProviderInfo pi : mergedConfig.cardData().values()) {
            Collections.sort(pi.codes());
        }

        NoCardConfig.JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File("data/config-nocardcz-carddata.json"), mergedConfig);
    }
}
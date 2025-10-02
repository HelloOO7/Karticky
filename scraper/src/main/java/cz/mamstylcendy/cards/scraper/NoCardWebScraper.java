package cz.mamstylcendy.cards.scraper;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class NoCardWebScraper {

    private static final String WEB_URL = "https://nocard.cz/";

    private static final int RETRIES = 60;
    private static final int RETRY_DELAY_MIN = 1000; // 1 second
    private static final int RETRY_DELAY_MAX = 2000;

    public static void main(String[] args) throws Exception {
        NoCardConfig mergedConfig = NoCardConfig.JSON_MAPPER.readValue(new File("data/base-config.json"), NoCardConfig.class);

        RandomWaiting waiting = new RandomWaiting(RETRY_DELAY_MIN, RETRY_DELAY_MAX);
        int failures = 0;
        for (int i = 0; i < RETRIES; i++) {
            if (i != 0) {
                waiting.sleep();
            }
            System.out.println("Fetching remote config, pass " + (i + 1) + " of " + RETRIES);

            try {
                NoCardConfig config = RemoteConfigFetcher.fetchRemoteConfig(WEB_URL);
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
                failures = 0;
            } catch (IOException ex) {
                ++failures;
                if (failures > 2) {
                    throw ex;
                }
            }
        }

        for (NoCardConfig.ProviderInfo pi : mergedConfig.cardData().values()) {
            Collections.sort(pi.codes());
        }

        NoCardConfig.JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File("data/config-nocardcz.json"), mergedConfig);
    }
}
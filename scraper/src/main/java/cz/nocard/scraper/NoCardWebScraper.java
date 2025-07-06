package cz.nocard.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class NoCardWebScraper {

    private static final String WEB_URL = "https://nocard.cz/";

    private static final int RETRIES = 60;
    private static final int RETRY_DELAY_MIN = 1000; // 1 second
    private static final int RETRY_DELAY_MAX = 2000;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        NoCardConfig mergedConfig = JSON_MAPPER.readValue(new File("data/base-config.json"), NoCardConfig.class);

        Random rng = new Random();
        int failures = 0;
        for (int i = 0; i < RETRIES; i++) {
            if (i != 0) {
                Thread.sleep(RETRY_DELAY_MIN + rng.nextInt(RETRY_DELAY_MAX - RETRY_DELAY_MIN));
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

        JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File("data/config.json"), mergedConfig);
    }
}
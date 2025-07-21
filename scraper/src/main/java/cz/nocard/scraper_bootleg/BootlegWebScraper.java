package cz.nocard.scraper_bootleg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.zxing.BarcodeFormat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import cz.nocard.scraper.NoCardConfig;
import cz.nocard.scraper.RandomWaiting;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class BootlegWebScraper {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("Accept-Language", "cs,sk;q=0.8,en-US;q=0.5,en;q=0.3")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                    .header("Cache-Control", "no-cache")
                    .build()
            ))
            /*.addInterceptor(new HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.BASIC))*/
            .build();

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            scrape(args);
        } finally {
            client.connectionPool().evictAll();
        }
    }

    public static void scrape(String[] args) throws IOException, InterruptedException {
        NoCardConfig mergedConfig = NoCardConfig.JSON_MAPPER.readValue(new File("data/base-config.json"), NoCardConfig.class);

        BootlegNoCardAPI api = new Retrofit.Builder()
                .baseUrl("https://nocard.store/api/v1/")
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create(JSON_MAPPER))
                .build()
                .create(BootlegNoCardAPI.class);

        StoresResponse stores = api.getStores().execute().body();

        Map<StoreInfo, Set<String>> cardIDs = new HashMap<>();

        List<StoreInfo> randomRequests = new ArrayList<>();

        Random random = new Random();

        for (StoreInfo store : stores.data()) {
            for (int i = 0; i < 5 + random.nextInt(5); i++) {
                randomRequests.add(store);
            }
        }

        Collections.shuffle(randomRequests);

        RandomWaiting waiting = new RandomWaiting(1000, 2000);

        System.out.println("Scraping with " + randomRequests.size() + " requests...");

        int sent = 0;

        for (StoreInfo store : randomRequests) {
            CardResponse card = api.getCard(store.id).execute().body();
            cardIDs.computeIfAbsent(store, k -> new HashSet<>()).add(card.card().number);
            waiting.sleep();
            ++sent;
            System.out.println("Sent " + sent + " requests, store: " + store.name + ", code: " + card.card().number);
        }

        System.out.println("Finishing up");

        for (StoreInfo store : stores.data()) {
            NoCardConfig.ProviderInfo pi = new NoCardConfig.ProviderInfo(
                    store.name,
                    store.name,
                    null,
                    null,
                    web2zxing(store.codeType),
                    cardIDs.getOrDefault(store, Set.of()).stream().sorted().toList()
            );
            mergedConfig.cardData().put(store.id + "_" + store.name, pi);
        }

        NoCardConfig.JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File("data/config-bootleg.json"), mergedConfig);
    }

    private static BarcodeFormat web2zxing(CodeType codeType) {
        return switch (codeType) {
            case QR -> BarcodeFormat.QR_CODE;
            case CODE128 -> BarcodeFormat.CODE_128;
            case EAN13 -> BarcodeFormat.EAN_13;
            case ITF -> BarcodeFormat.ITF;
        };
    }
}

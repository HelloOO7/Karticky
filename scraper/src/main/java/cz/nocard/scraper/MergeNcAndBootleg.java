package cz.nocard.scraper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MergeNcAndBootleg {

    private static final Map<String, String> BOOTLEG_RENAME = Map.of(
            "Penny Market", "Penny"
    );

    public static void main(String[] args) throws IOException {
        ConfigMerger merger = new ConfigMerger();

        NoCardConfig normalConfig = readConfig("data/config-nocardcz.json");
        merger.merge(normalConfig);

        NoCardConfig bootlegConfig = readConfig("data/config-bootleg.json");
        remapBootlegConfig(bootlegConfig, normalConfig);
        merger.merge(bootlegConfig);

        NoCardConfig.JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File("data/config.json"), merger.getResult());
    }

    private static void remapBootlegConfig(NoCardConfig config, NoCardConfig normalConfig) {
        Map<String, String> remapQueue = new HashMap<>();

        for (var provider : config.cardData().entrySet()) {
            String newName = BOOTLEG_RENAME.get(provider.getValue().providerName());
            if (newName != null) {
                provider.setValue(new NoCardConfig.ProviderInfo(
                        newName,
                        provider.getValue().membershipName(),
                        provider.getValue().brandColor(),
                        provider.getValue().brandColorContrast(),
                        provider.getValue().format(),
                        provider.getValue().codes()
                ));
            }

            String existId = findProviderIDByName(normalConfig, provider.getValue().providerName());
            if (existId != null) {
                // If the provider already exists, remap the ID
                remapQueue.put(provider.getKey(), existId);
            }
        }

        remapQueue.forEach((from, to) -> remapProviderID(config, from, to));
    }

    private static String findProviderIDByName(NoCardConfig config, String name) {
        name = normalizeName(name);
        for (var entry : config.cardData().entrySet()) {
            String pn = entry.getValue().providerName();
            if (pn != null && normalizeName(pn).equals(name)) {
                return entry.getKey();
            }
            String mn = entry.getValue().membershipName();
            if (mn != null && normalizeName(mn).equals(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String normalizeName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static void remapProviderID(NoCardConfig config, String from, String to) {
        var data = config.cardData().remove(from);
        if (data != null) {
            config.cardData().put(to, data);
        }
    }

    private static NoCardConfig readConfig(String path) throws IOException {
        return NoCardConfig.JSON_MAPPER.readValue(new File(path), NoCardConfig.class);
    }
}

package cz.mamstylcendy.cards.scraper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class ConfigMerger {

    private final Map<String, String> wlanMappings = new LinkedHashMap<>();
    private final Map<String, NoCardConfig.ProviderInfo> providers = new LinkedHashMap<>();

    public void merge(NoCardConfig config) {
        wlanMappings.putAll(config.wlanMappings());

        for (var provider : config.cardData().entrySet()) {
            if (!providers.containsKey(provider.getKey())) {
                providers.put(provider.getKey(), provider.getValue());
            } else {
                providers.put(provider.getKey(), mergeProviderInfo(providers.get(provider.getKey()), provider.getValue()));
            }
        }
    }

    public NoCardConfig getResult() {
        return new NoCardConfig(
                wlanMappings,
                new LinkedHashMap<>(providers)
        );
    }

    private NoCardConfig.ProviderInfo mergeProviderInfo(NoCardConfig.ProviderInfo a, NoCardConfig.ProviderInfo b) {
        return new NoCardConfig.ProviderInfo(
                mergeField(a, b, NoCardConfig.ProviderInfo::providerName),
                mergeField(a, b, NoCardConfig.ProviderInfo::membershipName),
                mergeField(a, b, NoCardConfig.ProviderInfo::brandColor),
                mergeField(a, b, NoCardConfig.ProviderInfo::brandColorContrast),
                mergeField(a, b, NoCardConfig.ProviderInfo::format),
                Stream.concat(a.codes().stream(), b.codes().stream()).distinct().sorted().toList()
        );
    }

    private <O, T> T mergeField(O a, O b, Function<O, T> getter) {
        T val = getter.apply(a);
        if (val != null) {
            return val;
        } else {
            return getter.apply(b);
        }
    }
}

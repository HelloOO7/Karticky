package cz.nocard.scraper;

import java.util.LinkedHashMap;
import java.util.List;

public record WebCardData(String name, String type, List<String> codes) {

    public static class Map extends LinkedHashMap<String, WebCardData> {

    }
}

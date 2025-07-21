package cz.nocard.scraper_bootleg;

import java.util.List;

public record StoresResponse(String status, List<StoreInfo> data) {

}

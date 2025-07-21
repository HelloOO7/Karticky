package cz.nocard.scraper_bootleg;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface BootlegNoCardAPI {

    @GET("stores")
    public Call<StoresResponse> getStores();
    @GET("card/{storeId}")
    public Call<CardResponse> getCard(@Path("storeId") int storeId);
}

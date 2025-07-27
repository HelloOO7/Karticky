package cz.nocard.android.data;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.Objects;

import cz.nocard.android.BuildConfig;

public class RemoteConfigFetcher {

    private static final ObjectMapper SAFE_OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static Connection.Response sendConfigRequest(Connection.Method method) throws IOException {
        return Jsoup.connect(BuildConfig.REMOTE_CONFIG_LOCATION)
                .method(method)
                .ignoreContentType(true)
                .timeout(10000)
                .execute();
    }

    public static Result fetchRemoteConfig(NoCardConfig current, String currentETag) throws IOException {
        Connection.Response response = sendConfigRequest(Connection.Method.HEAD);

        String etag = null;

        if (response.hasHeader("ETag")) {
            etag = response.header("ETag");
            if (currentETag != null && Objects.equals(etag, currentETag)) {
                return new Result(Status.NO_CHANGE, current, etag);
            }
        }

        response = sendConfigRequest(Connection.Method.GET);

        NoCardConfig config = SAFE_OBJECT_MAPPER.readValue(response.body(), NoCardConfig.class);

        return new Result(Status.SUCCESS, config, etag);
    }

    public static enum Status {
        SUCCESS,
        NO_CHANGE,
        INCOMPATIBLE
    }

    public static record Result(Status status, NoCardConfig config, String eTag) {

    }
}

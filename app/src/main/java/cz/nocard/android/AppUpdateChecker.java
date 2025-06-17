package cz.nocard.android;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppUpdateChecker {

    private static final Pattern VERSION_CODE_REGEX = Pattern.compile("versionCode\\s*(\\d+)");

    public static int checkForUpdate() throws IOException {
        Connection.Response response = Jsoup.connect(BuildConfig.REMOTE_BUILD_GRADLE).execute();
        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            String body = response.body();
            Matcher matcher = VERSION_CODE_REGEX.matcher(body);
            if (matcher.find()) {
                String versionCodeStr = matcher.group(1);
                try {
                    return Integer.parseInt(versionCodeStr);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid version code format: " + versionCodeStr, e);
                }
            } else {
                throw new IOException("Version code not found in the response.");
            }
        }
        else {
            throw new IOException("Failed to fetch remote build.gradle, status code: " + response.statusCode());
        }
    }
}

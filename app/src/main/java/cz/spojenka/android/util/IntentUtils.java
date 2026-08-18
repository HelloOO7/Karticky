package cz.spojenka.android.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.StringRes;
import androidx.core.app.ShareCompat;

public class IntentUtils {

    /**
     * Create an intent to open an app store page for the given package name.
     * On devices with Google Play installed, the intent will try to open the app in Google Play.
     * Otherwise, a default app store will be invoked using the "market://" URI.
     *
     * @param context     Context
     * @param packageName Package name of the target app
     * @return Intent to open the app store page. It may not always be possible to safely launch the intent,
     * so callers should be prepared to catch {@link android.content.ActivityNotFoundException} when calling the resulting intent.
     */
    public static Intent createMarketIntent(Context context, String packageName) {
        Intent storeIntent;
        if (GoogleUtils.isGooglePlayInstalled(context)) {
            String playStoreUri = "https://play.google.com/store/apps/details?id=" + packageName;
            storeIntent = createDeepLinkIntent(GoogleUtils.getGooglePlayPackageName(), playStoreUri);
            if (canSafelyLaunchIntent(context, storeIntent)) {
                return storeIntent;
            }
        }
        return createDeepLinkIntent("market://details?id=" + packageName);
    }

    /**
     * Create an intent to open a deep link URL in an app without checking if the app is installed or can be queried.
     * Callers must catch {@link android.content.ActivityNotFoundException} when calling the resulting intent.
     *
     * @param appPackageName Package name of the target app, or null to allow any app to handle the intent
     * @param url            Deep link URL
     * @return Intent to open the deep link URL
     */
    public static Intent createDeepLinkIntent(String appPackageName, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setPackage(appPackageName);
        return intent;
    }

    public static Intent createDeepLinkIntent(String url) {
        return createDeepLinkIntent(null, url);
    }

    public static Intent createWebBrowserIntent(Uri url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, url);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        return intent;
    }

    public static Intent createWebBrowserIntent(String url) {
        return createWebBrowserIntent(Uri.parse(url));
    }

    public static boolean canSafelyLaunchIntent(Context context, Intent intent) {
        return intent != null && intent.resolveActivity(context.getPackageManager()) != null;
    }

    public static String getIntentPackage(Intent intent) {
        if (intent.getPackage() != null) {
            return intent.getPackage();
        } else if (intent.getComponent() != null) {
            return intent.getComponent().getPackageName();
        } else {
            return null;
        }
    }

    /**
     * Create an {@link Intent} for launching the main entry point of an app. On newer
     * Android versions, the calling context must be able to query the app's launch intent
     * for it to be considered installed.
     *
     * @param context        Context
     * @param appPackageName Package name of the app to be launched
     * @return Intent for launching the app, or null if the app is not installed or can not be queried in the calling context
     */
    public static Intent createLaunchIntent(Context context, String appPackageName) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(appPackageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        } else {
            return null;
        }
    }

    public static Intent createApplicationDetailsIntent(String packageName) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null));
    }

    /**
     * Build a deep link URL from the given components. It is assumed that they are all
     * properly encoded.
     *
     * @param scheme     Scheme part (before the "://")
     * @param host       Host part (between the "://" and the first "/")
     * @param pathPrefix Path prefix (after the host)
     * @return Combined URL
     */
    public static String formatDeepLink(String scheme, String host, String pathPrefix) {
        return scheme + "://" + host + pathPrefix;
    }

    /**
     * Build a deep link for use within the app. The scheme will be the package name of the app.
     *
     * @param context    Context
     * @param host       Host part
     * @param pathPrefix Path prefix
     * @return Combined URL
     * @see #formatDeepLink(String, String, String)
     */
    public static String formatInternalDeepLink(Context context, String host, String pathPrefix) {
        return formatDeepLink(context.getPackageName(), host, pathPrefix);
    }

    /**
     * Build a deep link for use within the app. The scheme will be the package name of the app.
     *
     * @param context    Context
     * @param host       Host part resource ID
     * @param pathPrefix Path prefix resource ID
     * @return Combined URL
     * @see #formatDeepLink(String, String, String)
     */
    public static String formatInternalDeepLink(Context context, @StringRes int host, @StringRes int pathPrefix) {
        return formatInternalDeepLink(context, context.getString(host), context.getString(pathPrefix));
    }

    public static Intent createLinkSharingIntent(Context context, String url) {
        return new ShareCompat.IntentBuilder(context)
                .setType("text/url")
                .setText(url)
                .createChooserIntent();
    }
}

package cz.spojenka.android.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.StringRes;

import java.util.Optional;

import cz.spojenka.android.system.AppIntent;

public class IntentUtils {

    /**
     * Create an intent to open an app store page for the given package name.
     * On devices with Google Play installed, the intent will try to open the app in Google Play.
     * Otherwise, a default app store will be invoked using the "market://" URI.
     *
     * @param context Context
     * @param packageName Package name of the target app
     * @return Intent to open the app store page
     */
    public static Intent createMarketIntent(Context context, String packageName) {
        if (GoogleUtils.isGooglePlayInstalled(context)) {
            String playStoreUri = "https://play.google.com/store/apps/details?id=" + packageName;
            return createDeepLinkIntent(context, "com.android.vending", playStoreUri)
                    .orElseGet(() -> new Intent(Intent.ACTION_VIEW, Uri.parse(playStoreUri)));
        } else {
            return new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
        }
    }

    /**
     * Create an {@link AppIntent} indicating that an app is not installed and providing an intent to open the app store.
     *
     * @param context Context
     * @param packageName Package name of the target app
     * @return AppIntent with status {@link AppIntent.Status#APP_NOT_INSTALLED} and an intent to open the app store
     */
    public static AppIntent createMarketAppIntent(Context context, String packageName) {
        return new AppIntent(packageName, AppIntent.Status.APP_NOT_INSTALLED, createMarketIntent(context, packageName));
    }

    /**
     * Create an intent to open a deep link URL in an app. This method does not restrict
     * the app that should handle the intent.
     *
     * @param context Context
     * @param url Deep link URL
     * @return Intent to open the deep link URL, or an empty optional if no app can handle the intent
     */
    public static Optional<Intent> createDeepLinkIntent(Context context, String url) {
        return createDeepLinkIntent(context, null, url);
    }

    /**
     * Create an intent to open a deep link URL in an app. The intent will be restricted to the
     * app with the given package name.
     *
     * @param context Context
     * @param appPackageName Package name of the target app
     * @param url Deep link URL
     * @return Intent to open the deep link URL, or an empty optional if the app is not installed
     * or can not be queried in the calling context
     */
    public static Optional<Intent> createDeepLinkIntent(Context context, String appPackageName, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setPackage(appPackageName);
        if (!context.getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
            return Optional.of(intent);
        }
        return Optional.empty();
    }

    /**
     * Create an {@link AppIntent} for a deep link URL. If the app is not installed or can not
     * be queried, the status of the {@link AppIntent} will be {@link AppIntent.Status#APP_NOT_INSTALLED}
     * and the intent will be null. Otherwise, the status will be {@link AppIntent.Status#READY}.
     * <p>
     * If you need to open an app market in case the app is not installed, react to the status
     * with {@link #createMarketAppIntent(Context, String)} or use {@link #createLaunchOrMarketIntent(Context, String)}.
     *
     * @param context Context
     * @param appPackageName Package name of the target app
     * @param deepLinkUri Deep link URL
     * @return AppIntent for the deep link URL
     */
    public static AppIntent createDefaultAppDeepLinkIntent(Context context, String appPackageName, String deepLinkUri) {
        var intent = createDeepLinkIntent(context, appPackageName, deepLinkUri);
        return intent
                .map(value -> new AppIntent(appPackageName, AppIntent.Status.READY, value))
                .orElseGet(() -> new AppIntent(appPackageName, AppIntent.Status.APP_NOT_INSTALLED, null));
    }

    /**
     * Create an {@link AppIntent} for launching the main entry point of an app. On newer
     * Android versions, the calling context must be able to query the app's launch intent
     * for it to be considered installed.
     *
     * @param context Context
     * @param appPackageName Package name of the app to be launched
     * @return AppIntent for launching the app with status {@link AppIntent.Status#READY} and the intent if the app is installed,
     * or {@link AppIntent.Status#APP_NOT_INSTALLED} and no intent if the app is not installed.
     */
    public static AppIntent createLaunchIntent(Context context, String appPackageName) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(appPackageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return new AppIntent(appPackageName, AppIntent.Status.READY, intent);
        } else {
            return new AppIntent(appPackageName, AppIntent.Status.APP_NOT_INSTALLED, null);
        }
    }

    /**
     * Create an intent to launch an app or open its app store page if the app is not installed.
     *
     * @param context Context
     * @param appPackageName Package name of the target app
     * @return Intent to launch the app or open its app store page
     */
    public static Intent createLaunchOrMarketIntent(Context context, String appPackageName) {
        AppIntent appIntent = createLaunchIntent(context, appPackageName);
        if (appIntent.status() == AppIntent.Status.APP_NOT_INSTALLED) {
            return createMarketIntent(context, appPackageName);
        }
        return appIntent.intent();
    }

    public static Intent createApplicationDetailsIntent(String packageName) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null));
    }

    /**
     * Build a deep link URL from the given components. It is assumed that they are all
     * properly encoded.
     *
     * @param scheme Scheme part (before the "://")
     * @param host Host part (between the "://" and the first "/")
     * @param pathPrefix Path prefix (after the host)
     * @return Combined URL
     */
    public static String formatDeepLink(String scheme, String host, String pathPrefix) {
        return scheme + "://" + host + pathPrefix;
    }

    /**
     * Build a deep link for use within the app. The scheme will be the package name of the app.
     *
     * @see #formatDeepLink(String, String, String)
     *
     * @param context Context
     * @param host Host part
     * @param pathPrefix Path prefix
     * @return Combined URL
     */
    public static String formatInternalDeepLink(Context context, String host, String pathPrefix) {
        return formatDeepLink(context.getPackageName(), host, pathPrefix);
    }

    /**
     * Build a deep link for use within the app. The scheme will be the package name of the app.
     *
     * @see #formatDeepLink(String, String, String)
     *
     * @param context Context
     * @param host Host part resource ID
     * @param pathPrefix Path prefix resource ID
     * @return Combined URL
     */
    public static String formatInternalDeepLink(Context context, @StringRes int host, @StringRes int pathPrefix) {
        return formatInternalDeepLink(context, context.getString(host), context.getString(pathPrefix));
    }
}

package cz.spojenka.android.util;

import android.content.Context;
import android.content.pm.PackageManager;

import cz.spojenka.android.polyfills.PackageManagerCompat;

/**
 * Utility class for Google Play and Google Mobile Services related operations.
 */
public class GoogleUtils {

    private static final String GOOGLE_PLAY_PACKAGE_NAME = "com.android.vending";

    private static Boolean isGooglePlayInstalled;

    /**
     * Check if Google Play is installed on the device.
     * The result is cached for the lifetime of the application.
     *
     * @param context Context
     * @return true if Google Play is installed, false otherwise
     */
    public static boolean isGooglePlayInstalled(Context context) {
        if (isGooglePlayInstalled == null) {
            try {
                isGooglePlayInstalled = context.getPackageManager().getPackageInfo(GOOGLE_PLAY_PACKAGE_NAME, 0) != null;
            } catch (PackageManager.NameNotFoundException e) {
                isGooglePlayInstalled = false;
            }
        }
        return isGooglePlayInstalled;
    }

    /**
     * Check if an app originates from the Google Play Store.
     * <p>
     * This should not be used as a pseudo-security measure or as a means of discriminating against
     * users who have installed the app from other sources. Rather, it may be useful to toggle
     * certain features or behaviors (such as auto-updates) based on the app's origin.
     *
     * @param context Context
     * @param packageName Package name of the app
     * @return true if the app originates from the Google Play Store, false otherwise or if an exception occurred while checking
     */
    public static boolean isAppFromGooglePlay(Context context, String packageName) {
        try {
            String installer = PackageManagerCompat.getInstallerPackageName(context.getPackageManager(), packageName);

            return GOOGLE_PLAY_PACKAGE_NAME.equals(installer);
        } catch (Exception e) {
            return false;
        }
    }
}

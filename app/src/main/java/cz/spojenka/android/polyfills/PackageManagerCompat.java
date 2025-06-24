package cz.spojenka.android.polyfills;

import android.content.pm.PackageManager;
import android.os.Build;

public class PackageManagerCompat {

    @SuppressWarnings("deprecation")
    public static String getInstallerPackageName(PackageManager packageManager, String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return packageManager.getInstallSourceInfo(packageName).getInstallingPackageName();
            } catch (PackageManager.NameNotFoundException ex) {
                throw new IllegalArgumentException(ex);
            }
        } else {
            return packageManager.getInstallerPackageName(packageName);
        }
    }
}

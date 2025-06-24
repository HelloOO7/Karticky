package cz.spojenka.android.system;

import android.content.Intent;

/**
 * Represents an intent to be sent to a specific app.
 *
 * @param packageName Package name of the target app
 * @param status      Status of the app (installed or not)
 * @param intent      The intent to be sent, which may be null if the app is not installed
 */
public record AppIntent(
        String packageName,
        Status status,
        Intent intent
) {

    public enum Status {
        READY,
        APP_NOT_INSTALLED,
    }
}

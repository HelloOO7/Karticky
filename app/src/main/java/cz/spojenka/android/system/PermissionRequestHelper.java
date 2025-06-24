package cz.spojenka.android.system;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.util.Map;
import java.util.Optional;

import cz.spojenka.android.util.ViewUtils;

public class PermissionRequestHelper {

    public static int COARSE_LOCATION = 1;
    public static int FINE_LOCATION = 2;

    private static boolean getGrantRequestBool(Map<String, Boolean> result, String permission) {
        return Optional.ofNullable(result.get(permission)).orElse(false);
    }

    /**
     * Check if the app has a specific permission.
     *
     * @param context Context
     * @param permission The permission
     * @return true/false
     */
    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if the app has access to device location, either fine or coarse.
     *
     * @param context Context
     * @return true/false
     */
    public static boolean hasLocationPermission(Context context) {
        return hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /**
     * Check if the app has access to fine device location ({@link Manifest.permission#ACCESS_FINE_LOCATION}).
     *
     * @param context Context
     * @return true/false
     */
    public static boolean hasFineLocationPermission(Context context) {
        return hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /**
     * Get the mask of location permissions granted to the app.
     *
     * @param context Context
     * @return Mask of granted permissions, which is a combination of {@link #FINE_LOCATION} and {@link #COARSE_LOCATION}, or zero if none are granted.
     */
    public static int getGrantedLocationPrecision(Context context) {
        if (hasFineLocationPermission(context)) {
            return FINE_LOCATION | COARSE_LOCATION;
        } else if (hasLocationPermission(context)) {
            return COARSE_LOCATION;
        } else {
            return 0;
        }
    }

    /**
     * Sets up a runnable that may be called to request the specified location permissions.
     * This must be called at a time when {@link ActivityResultCaller#registerForActivityResult(ActivityResultContract, ActivityResultCallback)}
     * can be called.
     *
     * @param caller The caller that will be used to register activity result callbacks.
     * @param context Context
     * @param precisionMask Mask of the location permissions to request. This is a combination of {@link #FINE_LOCATION} and {@link #COARSE_LOCATION}.
     * @return A {@link Requester} that can be called to request the permissions.
     */
    public static Requester<LocationPermissionHandler> setupRequestLocationPermission(ActivityResultCaller caller, Context context, int precisionMask) {
        return new Requester<LocationPermissionHandler>() {

            private final ActivityResultLauncher<String[]> launcher;
            private LocationPermissionHandler currentHandler;

            {
                launcher = caller.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    boolean fine = getGrantRequestBool(result, Manifest.permission.ACCESS_FINE_LOCATION);
                    boolean coarse = getGrantRequestBool(result, Manifest.permission.ACCESS_COARSE_LOCATION);

                    currentHandler.onPermissionDecided((fine ? FINE_LOCATION : 0) | (coarse ? COARSE_LOCATION : 0));
                });
            }

            @Override
            public void request(LocationPermissionHandler handler) {
                if (hasLocationPermission(context)) {
                    handler.onPermissionDecided(getGrantedLocationPrecision(context));
                    return;
                }

                boolean wantsFine = (precisionMask & FINE_LOCATION) != 0;
                boolean wantsCoarse = (precisionMask & COARSE_LOCATION) != 0;

                Activity activity = ViewUtils.getActivityContext(context);
                if (activity.shouldShowRequestPermissionRationale(wantsFine ? Manifest.permission.ACCESS_FINE_LOCATION : Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    handler.onPermissionDecided(0);
                    return;
                }

                currentHandler = handler;
                if (wantsFine) {
                    launcher.launch(new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
                } else if (wantsCoarse) {
                    launcher.launch(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION});
                } else {
                    throw new IllegalArgumentException("Invalid precision mask: " + precisionMask);
                }
            }
        };
    }

    /**
     * Check if the app has access to camera ({@link Manifest.permission#CAMERA}).
     *
     * @param context Context
     * @return true/false
     */
    public static boolean hasCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public static Requester<CameraPermissionHandler> setupRequestCameraPermission(ActivityResultCaller caller, Context context) {
        return new Requester<CameraPermissionHandler>() {

            private final ActivityResultLauncher<String> launcher;
            private CameraPermissionHandler currentHandler;
            private final Activity activity = ViewUtils.getActivityContext(context);

            {
                launcher = caller.registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
                    if (result) {
                        currentHandler.onPermissionGranted();
                    } else {
                        currentHandler.onPermissionDenied(isPermissionNeverAskAgainAfterDenial(activity, Manifest.permission.CAMERA));
                    }
                });
            }

            @Override
            public void request(CameraPermissionHandler handler) {
                if (hasCameraPermission(context)) {
                    handler.onPermissionGranted();
                    return;
                }

                currentHandler = handler;
                launcher.launch(Manifest.permission.CAMERA);
            }
        };
    }

    private static boolean isPermissionNeverAskAgainAfterDenial(Activity activity, String permission) {
        return !activity.shouldShowRequestPermissionRationale(permission);
    }

    public interface Requester<H> {

        /**
         * Call the permission request UI.
         *
         * @param h A request-specific handler that will be called when the user has made a decision.
         */
        void request(H h);
    }

    public interface LocationPermissionHandler {

        /**
         * Called by a {@link Requester} returned by {@link #setupRequestLocationPermission(ActivityResultCaller, Context, int)}
         * when the user has made a decision about the permission request.
         *
         * @param grantedMask Mask of granted permissions, same way as in {@link #getGrantedLocationPrecision(Context)}.
         */
        void onPermissionDecided(int grantedMask);
    }

    public interface SimplePermissionHandler {

        void onPermissionGranted();
        void onPermissionDenied(boolean neverAskAgain);
    }

    public interface CameraPermissionHandler extends SimplePermissionHandler {

    }
}

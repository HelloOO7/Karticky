package cz.nocard.android;

import android.Manifest;
import android.animation.LayoutTransition;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import cz.nocard.android.databinding.ActivityMainBinding;
import cz.nocard.android.databinding.ProviderCardBinding;

public class MainActivity extends AppCompatActivity implements WlanFencingManager.OnNearbyProviderCallback {

    private static final String LOG_TAG = "NoCard";

    public static final String ACTION_SHOW_CARD = MainActivity.class.getName() + ".ACTION_SHOW_CARD";
    public static final String EXTRA_PROVIDER = MainActivity.class.getName() + ".EXTRA_PROVIDER";

    private static final String STATE_SHOWING_PROVIDER = "showing_provider";
    private static final String STATE_LOCAL_AUTO_DETECT_ENABLED = "local_auto_detect_enabled";
    private static final String STATE_PENDING_PERMISSIONS = "pending_permissions";

    private static final String SETTINGS_SHEET_FRAGMENT_TAG = "settings_sheet";

    private PermissionRequestHelper.Requester<PermissionRequestHelper.LocationPermissionHandler> locationPermissionRequester;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Inject
    NoCardPreferences prefs;

    @Inject
    ConfigManager configManager;
    @Inject
    WlanFencingManager wlanFencingManager;
    @Inject
    CardNotificationManager cardNotificationManager;

    private ActivityMainBinding ui;

    private String showingProvider = null;

    private boolean localAutoDetectEnabled;

    private final Queue<String> pendingPermissionRequests = new LinkedList<>();

    private Handler handler;
    private Runnable wlanScanUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ui = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(ui.getRoot());

        NoCardApplication.getInstance().getApplicationComponent().inject(this);

        handler = new Handler(getMainLooper());

        locationPermissionRequester = PermissionRequestHelper.setupRequestLocationPermission(this, this, PermissionRequestHelper.FINE_LOCATION);
        notificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {

        });

        ui.clRoot.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);

        ui.ivCard.setImageDrawable(new EmptyDrawable());

        if (savedInstanceState != null) {
            showingProvider = savedInstanceState.getString(STATE_SHOWING_PROVIDER, null);
            localAutoDetectEnabled = savedInstanceState.getBoolean(STATE_LOCAL_AUTO_DETECT_ENABLED, false);
            String[] pendingPermissions = savedInstanceState.getStringArray(STATE_PENDING_PERMISSIONS);
            if (pendingPermissions != null) {
                pendingPermissionRequests.addAll(Arrays.asList(pendingPermissions));
            }
        } else {
            showingProvider = null;
            localAutoDetectEnabled = prefs.getWlanAutoDetect();
        }

        handleIntent(getIntent(), true);

        if (!canUseAutoDetect()) {
            persistAutoDetectSetting(false);
        }

        initUI();

        if (showingProvider != null) {
            showCardForProvider(showingProvider);
        }

        tryBindToWlanFencingManager();

        if (wlanFencingManager.isExplicitScanNeeded()) {
            prefs.putNotificationNagDisabled(true);
        }

        if (!prefs.isNotificationNagDisabled()) {
            promptEnableNotification();
        }

        addOnNewIntentListener(intent -> handleIntent(intent, false));

        updateRemoteConfig();

        if (wlanFencingManager.isExplicitScanNeeded()) {
            // https://developer.android.com/develop/connectivity/wifi/wifi-scan
            // period of 45 seconds is a limit imposed by Android 9
            wlanScanUpdater = () -> {
                wlanFencingManager.performExplicitScan();
                handler.postDelayed(wlanScanUpdater, 45000);
            };
        }
    }

    private void popupSettingsSheet() {
        if (getSupportFragmentManager().findFragmentByTag(SETTINGS_SHEET_FRAGMENT_TAG) != null) {
            return; //already shown
        }
        new SettingsSheetFragment().show(getSupportFragmentManager(), SETTINGS_SHEET_FRAGMENT_TAG);
    }

    private void showGdprDialog() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.gdpr_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void initUI() {
        ui.swAutoDetect.setChecked(localAutoDetectEnabled);

        ui.swAutoDetect.setOnClickListener(v -> {
            //do not use setOnCheckedChangeListener, as it reacts to programmatic changes
            updateAutoDetectSetting(ui.swAutoDetect.isChecked());
        });

        for (String provider : configManager.getAllProviders()) {
            addCardMenuItem(provider);
        }

        ui.btnSettings.setOnClickListener(v -> popupSettingsSheet());

        ui.tvGdprLink.setOnClickListener(v -> showGdprDialog());

        displayDefaultRemoteConfigState();
    }

    private void addCardMenuItem(String provider) {
        ProviderCardBinding binding = ProviderCardBinding.inflate(getLayoutInflater(), ui.llAvailableCards, false);
        binding.tvProviderName.setText(provider);
        ui.llAvailableCards.addView(binding.getRoot());

        binding.getRoot().setOnClickListener(v -> {
            localAutoDetectEnabled = false; //without persisting
            if (!provider.equals(showingProvider)) {
                ui.swAutoDetect.setChecked(false);
            }
            showCardForProvider(provider);
            ui.getRoot().smoothScrollTo(0, 0);
        });
    }

    private void handleIntent(Intent intent, boolean silent) {
        if (intent == null) {
            return;
        }
        String action = getIntent().getAction();
        if (ACTION_SHOW_CARD.equals(action)) {
            showingProvider = getIntent().getStringExtra(EXTRA_PROVIDER);
            localAutoDetectEnabled = wlanFencingManager.isCurrent(showingProvider) && canUseAutoDetect();

            if (!silent) {
                showCardForProvider(showingProvider);
            }
        }
    }

    private boolean canUseAutoDetect() {
        return PermissionRequestHelper.hasFineLocationPermission(this) && prefs.getWlanAutoDetect();
    }

    private void updateAutoDetectSetting(boolean enabled) {
        if (enabled) {
            if (!PermissionRequestHelper.hasFineLocationPermission(this)) {
                locationPermissionRequester.request(grantedMask -> {
                    if ((grantedMask & PermissionRequestHelper.FINE_LOCATION) != 0) {
                        persistAutoDetectSetting(true);
                        bindToWlanFencingManager();
                        wlanFencingManager.update();
                    } else {
                        persistAutoDetectSetting(false);
                        ui.swAutoDetect.setChecked(false);
                        showLocationRationale();
                    }
                });
            } else {
                persistAutoDetectSetting(true);
                bindToWlanFencingManager();
            }
        } else {
            persistAutoDetectSetting(false);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SHOWING_PROVIDER, showingProvider);
        outState.putBoolean(STATE_LOCAL_AUTO_DETECT_ENABLED, localAutoDetectEnabled);
        outState.putStringArray(STATE_PENDING_PERMISSIONS, pendingPermissionRequests.toArray(new String[0]));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!wlanFencingManager.isExplicitScanNeeded()) {
            wlanFencingManager.update();
        }
        tryScheduleWorker();
        showBackgroundLocationPromptIfNeeded();
        cardNotificationManager.clearNotification();
        processPermissionRequests();

        if (wlanFencingManager.isExplicitScanNeeded() && wlanScanUpdater != null) {
            handler.post(wlanScanUpdater);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(wlanScanUpdater);
    }

    public boolean showBackgroundLocationPromptIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (
                    prefs.isBGNotificationEnabled()
                            && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                //user has probably enabled fine location, but not background location
                pushPermissionToRequest(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                return true;
            }
        }
        return false;
    }

    private void tryScheduleWorker() {
        if (prefs.isBGNotificationEnabled() && BackgroundWlanCheckWorker.isUseable(this)) {
            BackgroundWlanCheckWorker.scheduleWork(this, prefs);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wlanFencingManager.unregisterOnNearbyProviderCallback(this);
    }

    private void tryBindToWlanFencingManager() {
        if (PermissionRequestHelper.hasFineLocationPermission(this)) {
            bindToWlanFencingManager();
        }
    }

    private void bindToWlanFencingManager() {
        wlanFencingManager.registerOnNearbyProviderCallback(this, true);
    }

    private void callApplicationDetails() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", getPackageName(), null))
        );
    }

    private void showLocationRationale() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.location_permission_required_title)
                .setMessage(R.string.location_permission_required_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> callApplicationDetails())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void persistAutoDetectSetting(boolean enabled) {
        localAutoDetectEnabled = enabled;
        prefs.putWlanAutoDetect(enabled);
    }

    private void promptEnableNotification() {
        if (!prefs.isBGNotificationEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.notification_enable_title)
                    .setMessage(R.string.notification_enable_message)
                    .setPositiveButton(R.string.enable, (dialog, which) -> {
                        prefs.putNotificationNagDisabled(true);
                        prefs.putBGNotificationEnabled(true);
                        tryScheduleWorker();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pushPermissionToRequest(Manifest.permission.POST_NOTIFICATIONS);
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            pushPermissionToRequest(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                        } else {
                            pushPermissionToRequest(Manifest.permission.ACCESS_FINE_LOCATION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.do_not_show_again, (dialog, which) -> {
                        prefs.putNotificationNagDisabled(true);
                        prefs.putBGNotificationEnabled(false);
                    })
                    .show();
        }
    }

    private void pushPermissionToRequest(String permission) {
        if (pendingPermissionRequests.contains(permission)) {
            return;
        }
        pendingPermissionRequests.add(permission);
        processPermissionRequests();
    }

    private boolean permRequestProcessing = false;

    private void processPermissionRequests() {
        if (pendingPermissionRequests.isEmpty()) {
            return; //nothing to do
        }
        if (permRequestProcessing) {
            return;
        }
        permRequestProcessing = true;
        String permission = pendingPermissionRequests.peek();
        runOnUiThread(() -> {
            permRequestProcessing = false;
            pendingPermissionRequests.remove();
        }); //run on next loop
        if (Manifest.permission.POST_NOTIFICATIONS.equals(permission)) {
            requestRuntimeNotificationPermission();
        } else if (Manifest.permission.ACCESS_BACKGROUND_LOCATION.equals(permission)) {
            requestBackgroundLocationPermissions();
        } else if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)) {
            locationPermissionRequester.request(grantedMask -> {
                processPermissionRequests();
            });
        } else {
            Log.w(LOG_TAG, "Unknown permission requested: " + permission);
        }
    }

    private void requestRuntimeNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return; //no runtime permission needed
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void requestBackgroundLocationPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return; //no background location permission needed
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String settingLabel = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            settingLabel = String.valueOf(getPackageManager().getBackgroundPermissionOptionLabel());
        }
        if (TextUtils.isEmpty(settingLabel)) {
            settingLabel = getString(R.string.background_location_permission_option_default);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.background_location_permission_title)
                .setMessage(getString(
                        !wasBackgroundLocationAlreadyRequested()
                                ? R.string.background_location_permission_message
                                : R.string.background_location_permission_message_again,
                        settingLabel
                )).setPositiveButton(R.string.open_settings, (dialog, which) -> callApplicationDetails())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    prefs.putBGNotificationEnabled(false);
                    processPermissionRequests();
                })
                .show();
    }

    private boolean wasBackgroundLocationAlreadyRequested() {
        return prefs.isBGNotificationEnabled();
    }

    private CompletableFuture<ZxingCodeDrawable> currentAsyncLoadFuture = null;

    private void showCardForProvider(String provider) {
        boolean providerChanged = !provider.equals(showingProvider);
        showingProvider = provider;
        ui.tvProvider.setVisibility(View.VISIBLE);
        ui.tvProvider.setText(provider);
        if (providerChanged) {
            ui.ivCard.setVisibility(View.GONE);
            ui.pbCardImageLoading.setVisibility(View.VISIBLE);
        }

        if (currentAsyncLoadFuture != null) {
            currentAsyncLoadFuture.cancel(true);
        }

        (currentAsyncLoadFuture = AsyncUtils.supplyAsync(() -> {
            NoCardConfig.ProviderInfo pi = configManager.getProviderInfo(provider);

            return new ZxingCodeDrawable(
                    getResources(),
                    new ZxingCodeDrawable.Options()
                            .setPadding(0.05f)
                            .setData(configManager.getRandomCode(pi))
                            .setFormat(pi.format())
            );
        })).handleAsync((zxingCodeDrawable, throwable) -> {
            if (throwable instanceof CancellationException) {
                Log.d(LOG_TAG, "QR code generation cancelled for provider: " + provider);
                return null; //cancelled
            }
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to generate QR code", throwable);
                ui.ivCard.setImageDrawable(new EmptyDrawable());
                ui.tvProvider.setText(R.string.code_generation_error);
            } else {
                ui.ivCard.setImageDrawable(zxingCodeDrawable);
            }
            ui.pbCardImageLoading.setVisibility(View.GONE);
            ui.ivCard.setVisibility(View.VISIBLE);
            return null;
        }, AsyncUtils.getLifecycleExecutor(this));
    }

    private void updateRemoteConfig() {
        ui.tvRemoteConfigState.setText(R.string.remote_config_updating);
        AsyncUtils.supplyAsync(
                () -> RemoteConfigFetcher.fetchRemoteConfig(configManager.getCurrentConfig(), prefs.getLastRemoteEtag())
        ).handleAsync((result, throwable) -> {
            if (throwable != null) {
                displayRemoteConfigError(throwable);
            } else {
                if (result.status() == RemoteConfigFetcher.Status.SUCCESS) {
                    try {
                        configManager.updateConfig(result.config());
                        prefs.putLastRemoteUpdate(Instant.now());
                        prefs.putLastRemoteEtag(result.eTag());
                        displayDefaultRemoteConfigState();
                        if (showingProvider != null && configManager.getProviderInfo(showingProvider) != null) {
                            //show new card to make sure invalid ones are discarded
                            showCardForProvider(showingProvider);
                        }
                    } catch (IOException e) {
                        displayRemoteConfigError(e);
                    }
                } else if (result.status() == RemoteConfigFetcher.Status.NO_CHANGE) {
                    displayDefaultRemoteConfigState();
                } else if (result.status() == RemoteConfigFetcher.Status.INCOMPATIBLE) {
                    ui.tvRemoteConfigState.setText(R.string.remote_config_incompatible);
                }
            }
            return null;
        }, AsyncUtils.getLifecycleExecutor(this));
    }

    private boolean isErrorNetDown(Throwable error) {
        return error instanceof SocketTimeoutException || error instanceof SocketException || error instanceof UnknownHostException;
    }

    private void displayRemoteConfigError(Throwable error) {
        if (isErrorNetDown(error)) {
            displayDefaultRemoteConfigState();
        } else {
            Log.e(LOG_TAG, "Failed to update config", error);
            ui.tvRemoteConfigState.setText(getString(R.string.remote_config_error, error.getClass().getSimpleName()));
        }
    }

    private void displayDefaultRemoteConfigState() {
        Instant lastUpdateTime = prefs.getLastRemoteUpdate();
        if (lastUpdateTime == null) {
            lastUpdateTime = BuildConfig.BUILD_TIME;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
        ui.tvRemoteConfigState.setText(getString(R.string.remote_config_last_update, formatter.format(lastUpdateTime.atZone(ZoneId.systemDefault()))));
    }

    @Override
    public void providerNearby(String provider) {
        if (!localAutoDetectEnabled) {
            return;
        }
        showCardForProvider(provider);
    }

    @Override
    public void providerLost(String provider) {

    }
}
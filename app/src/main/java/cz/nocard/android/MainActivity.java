package cz.nocard.android;

import android.Manifest;
import android.animation.LayoutTransition;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

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
    private static final String STATE_PROCESS_PERMISSIONS_UPON_RETURN = "process_permissions_upon_return";
    private static final String STATE_UPDATE_CHECK_DONE = "update_check_done";

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
    private NoCardConfig.ProviderInfo showingProviderInfo = null;
    private String showingCardCode = null;
    private List<String> favouriteProviders = new ArrayList<>();
    private List<String> allProviders = new ArrayList<>();

    private boolean localAutoDetectEnabled;

    private final Queue<String> pendingPermissionRequests = new LinkedList<>();
    private boolean processPermissionsUponReturn = false;

    private Handler handler;
    private Runnable wlanScanUpdater;

    private boolean updateCheckDone;

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
            if (!granted) {
                prefs.putBGNotificationEnabled(false);
                prefs.putNotificationNagDisabled(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    //if the user cancelled the notification prompt, it is pointless to ask for the background location permission
                    //on older Android versions, the fine location permission demand will not be discarded, as it is useful
                    //for non-notification-related features as well. however, in practice, the notification prompt will not be
                    //shown on these versions anyway, as they do not have runtime notification permissions nor support for BG wifi scanning
                    pendingPermissionRequests.remove(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                }
            }
            processPermissionRequests();
        });

        ui.ivCard.setImageDrawable(new EmptyDrawable());

        if (savedInstanceState != null) {
            showingProvider = savedInstanceState.getString(STATE_SHOWING_PROVIDER, null);
            localAutoDetectEnabled = savedInstanceState.getBoolean(STATE_LOCAL_AUTO_DETECT_ENABLED, false);
            String[] pendingPermissions = savedInstanceState.getStringArray(STATE_PENDING_PERMISSIONS);
            if (pendingPermissions != null) {
                pendingPermissionRequests.addAll(Arrays.asList(pendingPermissions));
            }
            processPermissionsUponReturn = savedInstanceState.getBoolean(STATE_PROCESS_PERMISSIONS_UPON_RETURN, false);
            updateCheckDone = savedInstanceState.getBoolean(STATE_UPDATE_CHECK_DONE, false);
        } else {
            showingProvider = null;
            localAutoDetectEnabled = prefs.getWlanAutoDetect();
        }

        favouriteProviders = new ArrayList<>(prefs.getFavouriteProviders());
        allProviders = configManager.getAllProviders();

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
        ui.btnBlacklist.setOnClickListener(v -> showBlacklistDialog());

        ui.swAutoDetect.setChecked(localAutoDetectEnabled);

        ui.swAutoDetect.setOnClickListener(v -> {
            //do not use setOnCheckedChangeListener, as it reacts to programmatic changes
            updateAutoDetectSetting(ui.swAutoDetect.isChecked());
        });

        for (String provider : getSortedProviders()) {
            addCardMenuItem(provider);
        }

        ui.btnSettings.setOnClickListener(v -> popupSettingsSheet());

        ui.tvGdprLink.setOnClickListener(v -> showGdprDialog());

        displayDefaultRemoteConfigState();

        ui.llAvailableCards.setLayoutTransition(new LayoutTransition());

        ui.tvAppVersion.setText(getString(R.string.app_version_format, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE));
    }

    private void showBlacklistDialog() {
        if (showingProvider == null || showingCardCode == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.blacklist_title)
                .setMessage(R.string.blacklist_message)
                .setPositiveButton(R.string.blacklist_add, (dialog, which) -> {
                    prefs.addCardToBlacklist(showingProvider, showingCardCode);
                    showCardForProvider(showingProvider); //refresh card
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private List<String> getSortedProviders() {
        List<String> providers = new ArrayList<>(allProviders);
        List<String> existingFavourites = new ArrayList<>(favouriteProviders);
        existingFavourites.retainAll(providers);
        providers.removeAll(existingFavourites);
        providers.addAll(0, existingFavourites);
        return providers;
    }

    private void updateRowOrder() {
        List<String> newOrder = getSortedProviders();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < ui.llAvailableCards.getChildCount(); ++i) {
                View current = ui.llAvailableCards.getChildAt(i);
                int index = newOrder.indexOf((String) current.getTag());
                if (index != -1 && index != i) {
                    ui.llAvailableCards.removeViewAt(i);
                    if (index > i) {
                        --index;
                    }
                    ui.llAvailableCards.addView(current, index);
                    changed = true;
                }
            }
        }
    }

    private void addCardMenuItem(String provider) {
        NoCardConfig.ProviderInfo providerInfo = configManager.getProviderInfo(provider);

        ProviderCardBinding binding = ProviderCardBinding.inflate(getLayoutInflater(), ui.llAvailableCards, false);
        binding.tvProviderName.setText(providerInfo.providerName() != null ? providerInfo.providerName() : provider);
        if (providerInfo.brandColor() != null) {
            binding.ivBrandColor.setImageTintList(ColorStateList.valueOf(providerInfo.brandColor()));
        } else {
            binding.ivBrandColor.setVisibility(View.GONE);
        }
        binding.getRoot().setTag(provider);
        binding.btnToggleFavourite.setChecked(favouriteProviders.contains(provider));
        binding.btnToggleFavourite.setOnClickListener(v -> {
            boolean favourited = binding.btnToggleFavourite.isChecked();
            if (favourited) {
                favouriteProviders.add(provider);
            } else {
                favouriteProviders.remove(provider);
            }
            prefs.putFavouriteProviders(favouriteProviders);
            updateRowOrder();
        });

        ui.llAvailableCards.addView(binding.getRoot());

        binding.getRoot().setOnClickListener(v -> {
            localAutoDetectEnabled = false; //without persisting
            if (!provider.equals(showingProvider)) {
                ui.swAutoDetect.setChecked(false);
            }
            fakeShowProviderInfo(provider);
            scrollToYAndThen(0, ViewUtils.dpToPx(this, 100), () -> showCardForProvider(provider));
        });
    }

    private void fakeShowProviderInfo(String provider) {
        ui.tvProvider.setText(configManager.getProviderInfo(provider).membershipName());
    }

    private void scrollToYAndThen(int y, int threshold, Runnable action) {
        ScrollView sv = ui.svScroller;
        if (sv.getScrollY() == y) {
            action.run();
        } else {
            sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override
                public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    if (Math.abs(scrollY - y) <= threshold) {
                        action.run();
                        sv.setOnScrollChangeListener(null);
                    }
                }
            });
            sv.smoothScrollTo(0, y);
        }
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
        outState.putBoolean(STATE_PROCESS_PERMISSIONS_UPON_RETURN, processPermissionsUponReturn);
        outState.putBoolean(STATE_UPDATE_CHECK_DONE, updateCheckDone);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!wlanFencingManager.isExplicitScanNeeded()) {
            wlanFencingManager.update();
        }
        tryScheduleWorker();
        showNotificationPermissionPromptIfNeeded();
        showBackgroundLocationPromptIfNeeded();
        cardNotificationManager.clearNotification();

        if (processPermissionsUponReturn) {
            processPermissionsUponReturn = false;
            processPermissionRequests();
        }

        if (wlanFencingManager.isExplicitScanNeeded() && wlanScanUpdater != null) {
            handler.post(wlanScanUpdater);
        }
        if (!permRequestProcessing && !updateCheckDone) {
            updateCheckDone = true;
            checkForAppUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(wlanScanUpdater);
    }

    private void showNotificationPermissionPromptIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (prefs.isBGNotificationEnabled() && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pushPermissionToRequest(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
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
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        );
    }

    private void showLocationRationale() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.location_permission_required_title)
                .setMessage(R.string.location_permission_required_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    callApplicationDetails();
                    processPermissionsUponReturn = true;
                })
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
        runLater(() -> {
            permRequestProcessing = false;
            pendingPermissionRequests.remove();
        }); //run on next loop
        if (Manifest.permission.POST_NOTIFICATIONS.equals(permission)) {
            requestRuntimeNotificationPermission();
        } else if (Manifest.permission.ACCESS_BACKGROUND_LOCATION.equals(permission)) {
            requestBackgroundLocationPermissions();
        } else if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)) {
            locationPermissionRequester.request(grantedMask -> processPermissionRequests());
        } else {
            Log.w(LOG_TAG, "Unknown permission requested: " + permission);
        }
    }

    private void runLater(Runnable runnable) {
        handler.post(runnable);
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
                        !shouldUseBackgroundLocationUpgradeText()
                                ? R.string.background_location_permission_message
                                : R.string.background_location_permission_message_again,
                        settingLabel
                )).setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    callApplicationDetails();
                    processPermissionsUponReturn = true;
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    prefs.putBGNotificationEnabled(false);
                    processPermissionRequests();
                })
                .show();
    }

    private boolean shouldUseBackgroundLocationUpgradeText() {
        return prefs.isBGNotificationEnabled() && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private CompletableFuture<Pair<String, ZxingCodeDrawable>> currentAsyncLoadFuture = null;

    private void showCardForProvider(String provider) {
        boolean providerChanged = !provider.equals(showingProvider);
        showingProvider = provider;
        showingProviderInfo = configManager.getProviderInfo(provider);
        ui.tvProvider.setVisibility(View.VISIBLE);
        ui.tvProvider.setText(showingProviderInfo.membershipName());
        if (providerChanged) {
            ui.ivCard.setVisibility(View.GONE);
            ui.pbCardImageLoading.setVisibility(View.VISIBLE);
        }

        if (currentAsyncLoadFuture != null) {
            currentAsyncLoadFuture.cancel(true);
        }

        (currentAsyncLoadFuture = AsyncUtils.supplyAsync(() -> {
            NoCardConfig.ProviderInfo pi = configManager.getProviderInfo(provider);
            Set<String> blacklist = prefs.getCardBlacklist(provider);

            String code = configManager.getRandomCode(pi, Predicate.not(blacklist::contains));

            if (code == null) {
                throw new NoSuchElementException();
            }

            return new Pair<>(code, new ZxingCodeDrawable(
                    getResources(),
                    new ZxingCodeDrawable.Options()
                            .setPadding(0.05f)
                            .setData(code)
                            .setFormat(pi.format())
            ));
        })).handleAsync((codeAndDrawable, throwable) -> {
            if (throwable instanceof CancellationException) {
                Log.d(LOG_TAG, "QR code generation cancelled for provider: " + provider);
                return null; //cancelled
            }
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to generate QR code", throwable);
                ui.ivCard.setImageDrawable(new EmptyDrawable());
                if (throwable instanceof NoSuchElementException) {
                    //no code available
                    ui.tvErrorText.setText(R.string.code_not_available);
                } else {
                    ui.tvErrorText.setText(R.string.code_generation_error);
                }
                ui.tvErrorText.setVisibility(View.VISIBLE);
                ui.btnBlacklist.setVisibility(View.GONE);
            } else {
                ui.tvErrorText.setVisibility(View.GONE);
                ui.btnBlacklist.setVisibility(View.VISIBLE);
                showingCardCode = codeAndDrawable.first;
                ui.ivCard.setImageDrawable(codeAndDrawable.second);
            }
            ui.ivCard.setVisibility(View.VISIBLE);
            runOnTransitionDone(ui.ivCard, LayoutTransition.APPEARING, () -> ui.pbCardImageLoading.setVisibility(View.GONE));
            return null;
        }, AsyncUtils.getLifecycleExecutor(this));
    }

    private void runOnTransitionDone(View view, int type, Runnable callback) {
        LayoutTransition layoutTransition = ui.clRoot.getLayoutTransition();
        layoutTransition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition transition, ViewGroup container, View view2, int transitionType) {
                if (view2 == view) {
                    layoutTransition.removeTransitionListener(this);
                }
            }

            @Override
            public void endTransition(LayoutTransition transition, ViewGroup container, View view2, int transitionType) {
                if (view2 == view && transitionType == type) {
                    callback.run();
                    layoutTransition.removeTransitionListener(this);
                }
            }
        });
    }

    private void updateRemoteConfig() {
        ui.tvRemoteConfigState.setText(R.string.remote_config_updating);
        String etag = prefs.getLastRemoteEtag();
        if (prefs.getLastRemoteUpdate() == null || BuildConfig.BUILD_TIME.isAfter(prefs.getLastRemoteUpdate())) {
            etag = null; //force update
        }
        final String _etag = etag;
        AsyncUtils.supplyAsync(
                () -> RemoteConfigFetcher.fetchRemoteConfig(configManager.getCurrentConfig(), _etag)
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
                        Log.e(LOG_TAG, "Failed to update remote config", e);
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

    private void checkForAppUpdates() {
        AsyncUtils
                .supplyAsync(AppUpdateChecker::checkForUpdate)
                .handleAsync((newVersionCode, throwable) -> {
                    if (throwable != null) {
                        Log.e(LOG_TAG, "Failed to check for app updates", throwable);
                    }
                    if (newVersionCode != null) {
                        if (newVersionCode > BuildConfig.VERSION_CODE) {
                            showUpdateAvailableSnackbar();
                        } else {
                            Log.d(LOG_TAG,
                                    "No updates available, current version (" + BuildConfig.VERSION_CODE + ")"
                                            + " is up to date or newer than remote (" + newVersionCode + ")"
                            );
                        }
                    }
                    return null;
                });
    }

    private void showUpdateAvailableSnackbar() {
        Snackbar snackbar = Snackbar.make(ui.clCoordinator, R.string.update_available, Snackbar.LENGTH_INDEFINITE);
        ViewUtils.registerCompatInsetsFixups(snackbar.getView());
        snackbar.setDuration(10000)
                .setAction(R.string.update_action, v -> {
                    snackbar.dismiss();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_HOMEPAGE)));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failed to open update URL", e);
                        Snackbar.make(ui.clCoordinator, R.string.url_open_error, Snackbar.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    @Override
    public void providerNearby(WlanFencingManager.ProviderAPInfo provider) {
        cardNotificationManager.ackAPForFutureNotification(provider);
        if (!localAutoDetectEnabled) {
            return;
        }
        if (!provider.provider().equals(showingProvider)) {
            showCardForProvider(provider.provider());
        }
    }

    @Override
    public void providerLost(WlanFencingManager.ProviderAPInfo provider) {

    }
}
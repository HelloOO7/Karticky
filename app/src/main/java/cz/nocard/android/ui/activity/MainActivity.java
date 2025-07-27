package cz.nocard.android.ui.activity;

import android.Manifest;
import android.animation.LayoutTransition;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

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
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.inject.Inject;

import cz.nocard.android.AppUpdateChecker;
import cz.nocard.android.beacon.BackgroundWlanCheckWorker;
import cz.nocard.android.BuildConfig;
import cz.nocard.android.beacon.CardNotificationManager;
import cz.nocard.android.ui.dialogs.CommonDialogs;
import cz.nocard.android.data.ConfigManager;
import cz.nocard.android.NoCardApplication;
import cz.nocard.android.ui.dialogs.ProgressDialog;
import cz.nocard.android.ui.view.PersonalCardListAdapter;
import cz.nocard.android.ui.view.ProviderCardView;
import cz.nocard.android.ui.view.ProviderCardViewHolder;
import cz.nocard.android.R;
import cz.nocard.android.ui.dialogs.SettingsSheetFragment;
import cz.nocard.android.beacon.WlanFencingManager;
import cz.nocard.android.ui.view.UniversalCardListAdapter;
import cz.nocard.android.util.ZxingCodeDrawable;
import cz.nocard.android.sharing.CardTransfer;
import cz.nocard.android.sharing.LinkCardTransfer;
import cz.nocard.android.data.NoCardConfig;
import cz.nocard.android.data.NoCardPreferences;
import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.data.PersonalCardStore;
import cz.nocard.android.data.RemoteConfigFetcher;
import cz.nocard.android.databinding.ActivityMainBinding;
import cz.spojenka.android.system.PermissionRequestHelper;
import cz.spojenka.android.ui.drawable.EmptyDrawable;
import cz.spojenka.android.ui.helpers.ArrayListAdapter;
import cz.spojenka.android.ui.helpers.EdgeToEdgeSupport;
import cz.spojenka.android.ui.helpers.SingleViewAdapter;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.android.util.ViewUtils;

public class MainActivity extends AppCompatActivity implements WlanFencingManager.OnNearbyProviderCallback, PersonalCardStore.Listener {

    private static final String LOG_TAG = "NoCard";

    public static final String ACTION_SHOW_CARD = MainActivity.class.getName() + ".ACTION_SHOW_CARD";
    public static final String EXTRA_PROVIDER = MainActivity.class.getName() + ".EXTRA_PROVIDER";

    private static final String STATE_SHOWING_PROVIDER = "showing_provider";
    private static final String STATE_SHOWING_AUTO_PROVIDER = "showing_auto_provider";
    private static final String STATE_CURRENT_AUTO_PROVIDER = "current_auto_provider";
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
    PersonalCardStore personalCardStore;
    @Inject
    WlanFencingManager wlanFencingManager;
    @Inject
    CardNotificationManager cardNotificationManager;

    private ActivityMainBinding ui;
    private ProviderCardView autoDetectCard;
    private RecyclerView cardRecycler;
    private ArrayListAdapter<PersonalCard, ProviderCardViewHolder<ProviderCardView.WithoutAction>> personalCardAdapter;
    private ArrayListAdapter<String, ProviderCardViewHolder<ProviderCardView.WithFavouriteAction>> universalCardAdapter;
    private SingleViewAdapter listEmptyPlaceholderAdapter;
    private TextView tvRemoteConfigState;

    private boolean showingAutoProvider;
    private String currentAutoProvider;
    private boolean autodetectCallbackBound = false;

    private String showingProvider = null;
    private NoCardConfig.ProviderInfo showingProviderInfo = null;
    private String showingCardCode = null;
    private Integer showingPersonalCardId = null;

    private List<String> favouriteProviders = new ArrayList<>();
    private List<String> allProviders = new ArrayList<>();

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

        personalCardStore.addListener(this, AsyncUtils.getLifecycleExecutor(this));

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
            showingAutoProvider = savedInstanceState.getBoolean(STATE_SHOWING_AUTO_PROVIDER, false);
            currentAutoProvider = savedInstanceState.getString(STATE_CURRENT_AUTO_PROVIDER, null);
            String[] pendingPermissions = savedInstanceState.getStringArray(STATE_PENDING_PERMISSIONS);
            if (pendingPermissions != null) {
                pendingPermissionRequests.addAll(Arrays.asList(pendingPermissions));
            }
            processPermissionsUponReturn = savedInstanceState.getBoolean(STATE_PROCESS_PERMISSIONS_UPON_RETURN, false);
            updateCheckDone = savedInstanceState.getBoolean(STATE_UPDATE_CHECK_DONE, false);
        } else {
            showingProvider = null;
            showingAutoProvider = prefs.getWlanAutoDetect();
        }

        favouriteProviders = new ArrayList<>(prefs.getFavouriteProviders());
        allProviders = configManager.getAllProviders();

        boolean isIntentCardRequested = handleIntent(getIntent(), true);

        if (!canUseAutoDetect()) {
            persistAutoDetectSetting(false);
        }

        initUI();

        if (showingPersonalCardId != null) {
            PersonalCard card = personalCardStore.getCardById(showingPersonalCardId);
            if (card != null) {
                showPersonalCard(card);
            }
        } else if (showingProvider != null) {
            showCardForProvider(showingProvider, isIntentCardRequested);
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

    private void initUI() {
        ui.btnBlacklist.setOnClickListener(v -> showBlacklistDialog());

        //intellij is stupid and displays a type cast exception when using the viewbinding
        //with a nested class. therefore, use findViewById.
        autoDetectCard = ui.getRoot().findViewById(R.id.pcvAutoCard);
        autoDetectCard.setEnabled(false);

        if (canUseAutoDetect()) {
            setAutoDetectDiscoveringUI();
        } else {
            setAutoDetectInitialUI();
        }

        cardRecycler = ui.rvCards;
        EdgeToEdgeSupport.useExplicitFitsSystemWindows(
                ui.clCoordinator,
                EdgeToEdgeSupport.SIDE_HORIZONTAL | EdgeToEdgeSupport.SIDE_TOP,
                EdgeToEdgeSupport.FLAG_APPLY_AS_PADDING
        );
        EdgeToEdgeSupport.useExplicitFitsSystemWindows(
                cardRecycler,
                EdgeToEdgeSupport.SIDE_HORIZONTAL | EdgeToEdgeSupport.SIDE_BOTTOM,
                EdgeToEdgeSupport.FLAG_APPLY_AS_PADDING
        );

        tvRemoteConfigState = new TextView(this);
        ViewUtils.setPaddingDp(tvRemoteConfigState, 0, 8);

        universalCardAdapter = new UniversalCardListAdapter(configManager) {

            private static final int VIEW_TYPE_CARD = 0;
            private static final int VIEW_TYPE_FOOTER = 1;

            @Override
            public int getItemViewType(int position) {
                if (position == size()) {
                    return VIEW_TYPE_FOOTER;
                }
                return VIEW_TYPE_CARD;
            }

            @Override
            public int getItemCount() {
                return size() + 1;
            }

            @Override
            protected List<String> getFavouriteProviders() {
                return favouriteProviders;
            }

            @Override
            protected void onProviderFavouriteChange(String provider, boolean favourited) {
                if (favourited) {
                    favouriteProviders.add(provider);
                } else {
                    favouriteProviders.remove(provider);
                }
                prefs.putFavouriteProviders(favouriteProviders);
                updateRowOrder();
            }

            @Override
            protected void onProviderClicked(String provider) {
                showingAutoProvider = false;
                requestShowCard(new MainActivity.UniversalCardRequest(provider, false));
            }

            @NonNull
            @Override
            public ProviderCardViewHolder<ProviderCardView.WithFavouriteAction> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == VIEW_TYPE_FOOTER) {
                    return ProviderCardViewHolder.createExtraViewHolder(tvRemoteConfigState);
                }
                return super.onCreateViewHolder(parent, viewType);
            }

            @Override
            public void onBindViewHolder(@NonNull ProviderCardViewHolder<ProviderCardView.WithFavouriteAction> holder, int position) {
                if (position >= size()) {
                    return;
                }
                super.onBindViewHolder(holder, position);
            }
        };

        universalCardAdapter.addAll(getSortedProviders());

        personalCardAdapter = new PersonalCardListAdapter(configManager, personalCardStore) {

            @Override
            public void onPersonalCardClicked(PersonalCard personalCard) {
                showingAutoProvider = false;
                requestShowCard(new MainActivity.PersonalCardRequest(personalCard));
            }
        };

        personalCardAdapter.addAll(personalCardStore.getPersonalCards());

        listEmptyPlaceholderAdapter = new SingleViewAdapter(() -> {
            TextView tv = new TextView(this);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.text_title_m));
            tv.setText(R.string.card_list_empty);
            tv.setGravity(Gravity.CENTER);
            return tv;
        });

        ui.btnSettings.setOnClickListener(v -> popupSettingsSheet());

        displayDefaultRemoteConfigState();

        ui.tlCardListTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateSelectedCardList();
                prefs.putLastCardListTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                updateSelectedCardList();
            }
        });
        TabLayout.Tab tab = ui.tlCardListTabs.getTabAt(prefs.getLastCardListTab());
        if (tab != null) {
            tab.select();
        } else {
            ui.tlCardListTabs.selectTab(ui.tlCardListTabs.getTabAt(0));
        }
        selectNonEmptyTabIfNeeded();
    }

    private void selectNonEmptyTabIfNeeded() {
        ArrayListAdapter<?, ? extends ProviderCardViewHolder<?>> list = getSelectedCardListView();
        if (list.isEmpty()) {
            for (int i = 0; i < ui.tlCardListTabs.getTabCount(); i++) {
                if (!getCardListAdapterByTabIndex(i).isEmpty()) {
                    ui.tlCardListTabs.getTabAt(i).select();
                    return; //found a non-empty tab
                }
            }
        }
    }

    private ArrayListAdapter<?, ? extends ProviderCardViewHolder<?>> getCardListAdapterByTabIndex(int tabIndex) {
        return tabIndex == 0 ? personalCardAdapter : universalCardAdapter;
    }

    private ArrayListAdapter<?, ? extends ProviderCardViewHolder<?>> getSelectedCardListView() {
        return getCardListAdapterByTabIndex(ui.tlCardListTabs.getSelectedTabPosition());
    }

    private void updateSelectedCardList() {
        ArrayListAdapter<?, ? extends ProviderCardViewHolder<?>> actualCardList = getSelectedCardListView();
        if (actualCardList.isEmpty()) {
            cardRecycler.setAdapter(listEmptyPlaceholderAdapter);
        } else {
            cardRecycler.setAdapter(actualCardList);
        }
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
                    refreshCurrentUniversalCard(); //refresh card
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshCurrentUniversalCard() {
        if (!Objects.equals(showingProvider, PersonalCard.PROVIDER_CUSTOM)) {
            showCardForProvider(showingProvider, false);
        }
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
            for (int i = 0; i < universalCardAdapter.size(); ++i) {
                String current = universalCardAdapter.get(i);
                int index = newOrder.indexOf(current);
                if (index != -1 && index != i) {
                    universalCardAdapter.remove(i);
                    if (index > i) {
                        --index;
                    }
                    universalCardAdapter.add(index, current);
                    changed = true;
                }
            }
        }
    }

    private void removeCardMenuItem(PersonalCard personalCard) {
        personalCardAdapter.remove(personalCard);
    }

    private void requestShowCard(CardRequest request) {
        fakeShowProviderInfo(request.getProviderInfo());
        expandHeaderAndRun(() -> showCard(request));
    }

    private void fakeShowProviderInfo(NoCardConfig.ProviderInfo providerInfo) {
        ui.tvProvider.setText(providerInfo.membershipName());
    }

    private void expandHeaderAndRun(Runnable action) {
        ui.ablAppBar.setExpanded(true, true);
        action.run();
    }

    private boolean handleIntent(Intent intent, boolean silent) {
        if (intent == null) {
            return false;
        }
        String action = intent.getAction();
        if (ACTION_SHOW_CARD.equals(action)) {
            showingProvider = intent.getStringExtra(EXTRA_PROVIDER);
            showingAutoProvider = wlanFencingManager.isCurrent(showingProvider) && canUseAutoDetect();

            if (!silent) {
                showCardForProvider(showingProvider, true);
            }
            return true;
        } else if (Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                LinkCardTransfer.LinkType type = LinkCardTransfer.getLinkType(uri);
                if (type == LinkCardTransfer.LinkType.APP_CARD_PACKET) {
                    ProgressDialog.doInBackground(this, R.string.transferring_cards, () -> {
                        byte[] packet = LinkCardTransfer.getData(uri);
                        if (packet != null) {
                            CardTransfer transfer = LinkCardTransfer.newCardTransfer();
                            List<PersonalCard> cards = transfer.receivePersonalCardPacket(packet);
                            personalCardStore.merge(cards);
                            return cards;
                        }
                        return null;
                    }).handleAsync((mergedCards, throwable) -> {
                        if (throwable instanceof IllegalArgumentException) {
                            Log.e(LOG_TAG, "Invalid link data", throwable);
                            CommonDialogs.newInfoDialog(this, R.string.error, R.string.invalid_deep_link).show();
                        } else if (throwable instanceof IOException) {
                            Log.e(LOG_TAG, "Incompatible link data", throwable);
                            CommonDialogs.newInfoDialog(this, R.string.error, R.string.incompatible_deep_link).show();
                        } else if (mergedCards != null) {
                            CommonDialogs
                                    .newInfoDialog(
                                            this,
                                            getString(R.string.success),
                                            getResources().getQuantityString(R.plurals.link_cards_received, mergedCards.size(), mergedCards.size())
                                    )
                                    .setNeutralButton(R.string.open_my_cards, (dialog, which) -> startActivity(new Intent(this, PersonalCardsActivity.class)))
                                    .show();
                        }
                        return null;
                    }, AsyncUtils.getLifecycleExecutor(this));
                }
            }
        }
        return false;
    }

    private boolean canUseAutoDetect() {
        return PermissionRequestHelper.hasFineLocationPermission(this);
    }

    private void checkPermsAndShowAutoProvider() {
        if (!canUseAutoDetect()) {
            unbindFromWlanFencingManager();
            locationPermissionRequester.request(grantedMask -> {
                if ((grantedMask & PermissionRequestHelper.FINE_LOCATION) != 0) {
                    showAutoProviderIfExists();
                    wlanFencingManager.update();
                } else {
                    persistAutoDetectSetting(false);
                    showLocationRationale();
                }
            });
        } else {
            showAutoProviderIfExists();
        }
    }

    private void showAutoProviderIfExists() {
        persistAutoDetectSetting(true);
        if (autodetectCallbackBound) {
            if (currentAutoProvider != null) {
                showCardForProvider(currentAutoProvider, true);
            }
        } else {
            bindToWlanFencingManager();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SHOWING_PROVIDER, showingProvider);
        outState.putBoolean(STATE_SHOWING_AUTO_PROVIDER, showingAutoProvider);
        outState.putString(STATE_CURRENT_AUTO_PROVIDER, currentAutoProvider);
        outState.putStringArray(STATE_PENDING_PERMISSIONS, pendingPermissionRequests.toArray(new String[0]));
        outState.putBoolean(STATE_PROCESS_PERMISSIONS_UPON_RETURN, processPermissionsUponReturn);
        outState.putBoolean(STATE_UPDATE_CHECK_DONE, updateCheckDone);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (canUseAutoDetect() && !wlanFencingManager.isExplicitScanNeeded()) {
            wlanFencingManager.update();
        }
        tryScheduleWorker();
        showNotificationPermissionPromptIfNeeded();
        showBackgroundLocationPromptIfNeeded();
        cardNotificationManager.clearNotification();

        if (!canUseAutoDetect()) {
            unbindFromWlanFencingManager();
            setAutoDetectInitialUI();
        }

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
        unbindFromWlanFencingManager();
        personalCardStore.removeListener(this);
    }

    private void unbindFromWlanFencingManager() {
        if (!autodetectCallbackBound) {
            return;
        }
        autodetectCallbackBound = false;
        wlanFencingManager.unregisterOnNearbyProviderCallback(this);
    }

    private void tryBindToWlanFencingManager() {
        if (canUseAutoDetect()) {
            bindToWlanFencingManager();
        }
    }

    private void bindToWlanFencingManager() {
        if (autodetectCallbackBound) {
            return;
        }
        autodetectCallbackBound = true;
        currentAutoProvider = null;
        setAutoDetectDiscoveringUI();
        //if an auto provider exists, callIfCurrent will make sure it is set
        wlanFencingManager.registerOnNearbyProviderCallback(this, AsyncUtils.getLifecycleExecutor(this), true);
    }

    private void setAutoDetectText(String text) {
        CharSequence styledText;
        int openBrace = text.indexOf('(');
        int closeBrace = text.indexOf(')');
        if (openBrace != -1 && closeBrace != -1) {
            styledText = Html.fromHtml(
                    text.substring(0, openBrace)
                            + "<small>" + text.substring(openBrace, closeBrace + 1) + "</small>"
                            + text.substring(closeBrace + 1),
                    Html.FROM_HTML_MODE_LEGACY
            );
        } else {
            styledText = text;
        }
        autoDetectCard.overridePrimaryText(styledText);
    }

    private void disableAutoDetectCard() {
        autoDetectCard.setEnabled(false);
        autoDetectCard.setCustomChipGradient(getResources().getIntArray(R.array.black_white_gradient));
        autoDetectCard.setOnClickListener(null);
    }

    private void enableAutoDetectCard() {
        autoDetectCard.setCustomChipGradient(getResources().getIntArray(R.array.rainbow_gradient));
        autoDetectCard.setEnabled(true);
        autoDetectCard.setOnClickListener(v -> checkPermsAndShowAutoProvider());
    }

    private void setAutoDetectDiscoveringUI() {
        disableAutoDetectCard();
        setAutoDetectText(getString(R.string.autodetect_provider_format, getString(R.string.autodetect_discovering)));
    }

    private void setAutoDetectInitialUI() {
        enableAutoDetectCard();
        setAutoDetectText(getString(R.string.autodetect_provider_initial));
    }

    private void setAutoDetectNoProviderUI() {
        disableAutoDetectCard();
        setAutoDetectText(getString(R.string.autodetect_provider_format, getString(R.string.autodetect_no_provider)));
    }

    private void setAutoDetectProviderUI(String provider) {
        enableAutoDetectCard();
        setAutoDetectText(getString(R.string.autodetect_provider_format, configManager.getProviderNameOrDefault(provider)));
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
        showingAutoProvider = enabled;
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

    private boolean isShowingPersonalCard() {
        return showingPersonalCardId != null;
    }

    private CompletableFuture<CardLoadingResult> currentAsyncLoadFuture = null;

    private void showCardForProvider(String provider, boolean preferPersonal) {
        if (PersonalCard.PROVIDER_CUSTOM.equals(provider)) {
            return;
        }
        showCard(new UniversalCardRequest(provider, preferPersonal));
    }

    private void showPersonalCard(PersonalCard card) {
        showCard(new PersonalCardRequest(card));
    }

    private boolean isNewProviderDifferent(CardRequest cardRequest) {
        if (!Objects.equals(cardRequest.getProviderId(), showingProvider)) {
            return true; //provider changed
        }
        if (showingPersonalCardId != null) {
            PersonalCard personalCard = cardRequest.getPersonalCard();
            if (personalCard != null) {
                PersonalCard.CustomCardProperties newCustomProps = personalCard.customProperties();
                if (newCustomProps != null) {
                    PersonalCard currentPersonalCard = personalCardStore.getCardById(showingPersonalCardId);
                    if (currentPersonalCard != null) {
                        PersonalCard.CustomCardProperties currentCustomProps = currentPersonalCard.customProperties();
                        if (currentCustomProps != null) {
                            return !Objects.equals(currentCustomProps.providerName(), newCustomProps.providerName());
                        }
                    }
                }
            }
        }
        return false;
    }

    private void showCard(CardRequest card) {
        boolean providerChanged = isNewProviderDifferent(card);
        NoCardConfig.ProviderInfo pi = card.getProviderInfo();
        showingProvider = card.getProviderId();
        showingProviderInfo = pi;
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
            String code = card.getCardCode();
            PersonalCard personal = card.getPersonalCard();

            return new CardLoadingResult(code, personal, new ZxingCodeDrawable(
                    getResources(),
                    new ZxingCodeDrawable.Options()
                            .setPadding(0.05f)
                            .setData(code)
                            .setFormat(pi.format())
            ));
        })).handleAsync((result, throwable) -> {
            if (throwable instanceof CancellationException) {
                Log.d(LOG_TAG, "QR code generation cancelled for request: " + card);
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
                ui.tvPersonalCardNotice.setVisibility(View.GONE);
                showingCardCode = null;
                showingPersonalCardId = null;
            } else {
                ui.tvErrorText.setVisibility(View.GONE);
                showingCardCode = result.code();
                showingPersonalCardId = result.personalCard() != null ? result.personalCard.id() : null;
                ui.ivCard.setImageDrawable(result.codeDrawable());
                if (isShowingPersonalCard()) {
                    ui.btnBlacklist.setVisibility(View.INVISIBLE);
                    ui.tvPersonalCardNotice.setVisibility(View.VISIBLE);
                    ui.tvPersonalCardNotice.setText(getString(R.string.personal_card_desc_format, personalCardStore.getCardSingleLineName(result.personalCard(), configManager)));
                } else {
                    ui.btnBlacklist.setVisibility(View.VISIBLE);
                    ui.tvPersonalCardNotice.setVisibility(View.INVISIBLE);
                }
            }
            ui.ivCard.setVisibility(View.VISIBLE);
            runOnTransitionDone(ui.ivCard, () -> {
                ui.pbCardImageLoading.setVisibility(View.GONE);
            }, LayoutTransition.APPEARING, LayoutTransition.CHANGE_APPEARING);
            return null;
        }, AsyncUtils.getLifecycleExecutor(this));
    }

    private void runOnTransitionDone(View view, Runnable callback, int... types) {
        Set<Integer> acceptedTypes = Arrays.stream(types).boxed().collect(Collectors.toSet());

        LayoutTransition layoutTransition = ui.clCardDisplay.getLayoutTransition();
        layoutTransition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition transition, ViewGroup container, View view2, int transitionType) {
                if (view2 == view && !acceptedTypes.contains(transitionType)) {
                    layoutTransition.removeTransitionListener(this);
                }
            }

            @Override
            public void endTransition(LayoutTransition transition, ViewGroup container, View view2, int transitionType) {
                if (view2 == view && acceptedTypes.contains(transitionType)) {
                    callback.run();
                    layoutTransition.removeTransitionListener(this);
                }
            }
        });
    }

    private void updateRemoteConfig() {
        tvRemoteConfigState.setText(R.string.remote_config_updating);
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
                            refreshCurrentUniversalCard();
                        }
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Failed to update remote config", e);
                        displayRemoteConfigError(e);
                    }
                } else if (result.status() == RemoteConfigFetcher.Status.NO_CHANGE) {
                    displayDefaultRemoteConfigState();
                } else if (result.status() == RemoteConfigFetcher.Status.INCOMPATIBLE) {
                    tvRemoteConfigState.setText(R.string.remote_config_incompatible);
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
            tvRemoteConfigState.setText(getString(R.string.remote_config_error, error.getClass().getSimpleName()));
        }
    }

    private void displayDefaultRemoteConfigState() {
        Instant lastUpdateTime = prefs.getLastRemoteUpdate();
        if (lastUpdateTime == null) {
            lastUpdateTime = BuildConfig.BUILD_TIME;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
        tvRemoteConfigState.setText(getString(R.string.remote_config_last_update, formatter.format(lastUpdateTime.atZone(ZoneId.systemDefault()))));
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
        EdgeToEdgeSupport.registerCompatInsetsFixups(snackbar.getView());
        snackbar.setDuration(10000)
                .setAction(R.string.update_action, v -> {
                    snackbar.dismiss();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.APP_HOMEPAGE)));
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
        currentAutoProvider = provider.provider();
        autoDetectCard.setEnabled(true);
        setAutoDetectProviderUI(provider.provider());
        if (!showingAutoProvider) {
            return;
        }
        if (!provider.provider().equals(showingProvider)) {
            showCardForProvider(provider.provider(), true);
        }
    }

    @Override
    public void providerLost(WlanFencingManager.ProviderAPInfo provider) {

    }

    @Override
    public void noProvider() {
        if (!wlanFencingManager.isDoneInitialScan()) {
            return; //wait till initial scan arrives
        }
        cardNotificationManager.ackAPForFutureNotification(null);
        currentAutoProvider = null;
        setAutoDetectNoProviderUI();
    }

    @Override
    public void onCardAdded(PersonalCard card) {
        personalCardAdapter.add(card);
        updateSelectedCardList();
        if (showingProvider != null && showingPersonalCardId == null && Objects.equals(showingProvider, card.provider())) {
            refreshCurrentUniversalCard();
        }
    }

    @Override
    public void onCardRemoved(PersonalCard card) {
        removeCardMenuItem(card);
        updateSelectedCardList();
        onCardChanged(card);
    }

    @Override
    public void onCardChanged(PersonalCard card) {
        int adapterIndex = personalCardAdapter.indexOf(card);
        if (adapterIndex != -1) {
            //notify items changed
            personalCardAdapter.set(adapterIndex, card);
        }
        if (showingPersonalCardId != null && card.id() == showingPersonalCardId) {
            refreshCurrentUniversalCard();
        }
    }

    private static record CardLoadingResult(String code, PersonalCard personalCard,
                                            ZxingCodeDrawable codeDrawable) {

    }

    private static interface CardRequest {

        public String getProviderId();

        public NoCardConfig.ProviderInfo getProviderInfo();

        public String getCardCode();

        public PersonalCard getPersonalCard();
    }

    private class UniversalCardRequest implements CardRequest {

        private final String providerId;
        private final boolean preferPersonal;

        public UniversalCardRequest(String providerId, boolean preferPersonal) {
            this.providerId = providerId;
            this.preferPersonal = preferPersonal;
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public NoCardConfig.ProviderInfo getProviderInfo() {
            return configManager.getProviderInfo(providerId);
        }

        @Override
        public String getCardCode() {
            Set<String> blacklist = prefs.getCardBlacklist(providerId);

            String code = null;

            if (preferPersonal) {
                PersonalCard personal = personalCardStore.getCardForProvider(providerId);

                if (personal != null) {
                    code = personal.cardNumber();
                }
            }

            if (code == null) {
                code = configManager.getRandomCode(getProviderInfo(), Predicate.not(blacklist::contains));
            }

            if (code == null) {
                throw new NoSuchElementException();
            }

            return code;
        }

        @Override
        public PersonalCard getPersonalCard() {
            if (!preferPersonal) {
                return null;
            }
            return personalCardStore.getCardForProvider(providerId);
        }
    }

    private class PersonalCardRequest implements CardRequest {

        private final PersonalCard card;

        public PersonalCardRequest(PersonalCard card) {
            this.card = card;
        }

        @Override
        public String getProviderId() {
            return card.provider();
        }

        @Override
        public NoCardConfig.ProviderInfo getProviderInfo() {
            return personalCardStore.getCardProviderInfo(card, configManager);
        }

        @Override
        public String getCardCode() {
            return card.cardNumber();
        }

        @Override
        public PersonalCard getPersonalCard() {
            return card;
        }
    }
}
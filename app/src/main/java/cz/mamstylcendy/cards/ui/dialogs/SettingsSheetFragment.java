package cz.mamstylcendy.cards.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ExistingWorkPolicy;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.Consumer;

import javax.inject.Inject;

import cz.mamstylcendy.cards.CardsApplication;
import cz.mamstylcendy.cards.beacon.BackgroundWlanCheckWorker;
import cz.mamstylcendy.cards.BuildConfig;
import cz.mamstylcendy.cards.R;
import cz.mamstylcendy.cards.beacon.WlanFencingManager;
import cz.mamstylcendy.cards.data.CardsPreferences;
import cz.mamstylcendy.cards.databinding.SettingsSheetBinding;
import cz.mamstylcendy.cards.ui.activity.MainActivity;
import cz.mamstylcendy.cards.ui.activity.ManageBlacklistActivity;
import cz.mamstylcendy.cards.ui.activity.PersonalCardsActivity;
import cz.spojenka.android.ui.helpers.SimpleTextWatcher;

public class SettingsSheetFragment extends BottomSheetDialogFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    CardsPreferences prefs;
    @Inject
    WlanFencingManager wlanFencingManager;

    private SettingsSheetBinding binding;
    private boolean freezeChanges = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        CardsApplication.getInstance().getApplicationComponent().inject(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Dialog dialog = requireDialog();
        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheetView = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            BottomSheetBehavior.from(bottomSheetView).setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        binding = SettingsSheetBinding.inflate(inflater, container, false);
        prefs.getPrefs().registerOnSharedPreferenceChangeListener(this);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        prefs.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateSettingsUI();
        binding.btnClose.setOnClickListener(v -> dismiss());

        binding.swUseNotifications.setOnClickListener(v -> {
            if (binding.swUseNotifications.isChecked()) {
                if (wlanFencingManager.isExplicitScanNeeded()) {
                    binding.swUseNotifications.setChecked(false);
                    showBackgroundScanUnsupportedError();
                } else {
                    prefs.putBGNotificationEnabled(true);
                    if (!getMainActivity().showBackgroundLocationPromptIfNeeded()) {
                        BackgroundWlanCheckWorker.scheduleWork(requireContext(), prefs);
                    }
                }
            } else {
                prefs.putBGNotificationEnabled(false);
            }
        });

        registerEditTextListener(binding.etWlanCheckInterval, val -> {
            try {
                int interval = Integer.parseInt(val);
                if (interval < 1) {
                    interval = 1;
                }
                int oldInterval = prefs.getBackgroundCheckInterval();
                if (interval != oldInterval) {
                    final int _interval = interval;
                    performChange(() -> {
                        prefs.putBackgroundCheckInterval(_interval);
                        BackgroundWlanCheckWorker.scheduleWork(requireContext(), prefs, ExistingWorkPolicy.REPLACE);
                    });
                }
            } catch (NumberFormatException ignored) {
            }
        });

        registerEditTextListener(binding.etMinWifiSignal, val -> {
            try {
                int minSignal = Integer.parseInt(val);
                performChange(() -> prefs.putMinWlanDbm(minSignal));
            } catch (NumberFormatException ignored) {
            }
        });

        binding.btnBlacklistManagement.setOnClickListener(v -> startActivity(new Intent(requireContext(), ManageBlacklistActivity.class)));
        binding.btnPersonalCards.setOnClickListener(v -> startActivity(new Intent(requireContext(), PersonalCardsActivity.class)));

        binding.tvGdprLink.setOnClickListener(v -> showGdprDialog());
        binding.tvAppVersion.setText(getString(R.string.app_version_format, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE));
    }

    private void showGdprDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.gdpr_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showBackgroundScanUnsupportedError() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.background_scan_unsupported_title)
                .setMessage(R.string.background_scan_unsupported_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void registerEditTextListener(EditText et, Consumer<String> func) {
        et.addTextChangedListener((SimpleTextWatcher) s -> func.accept(s.toString()));
    }

    private void performChange(Runnable change) {
        freezeChanges = true;
        change.run();
        freezeChanges = false;
    }

    private MainActivity getMainActivity() {
        return (MainActivity) requireActivity();
    }

    private void updateSettingsUI() {
        binding.swUseNotifications.setChecked(prefs.isBGNotificationEnabled());
        binding.etWlanCheckInterval.setText(String.valueOf(prefs.getBackgroundCheckInterval()));
        binding.etMinWifiSignal.setText(String.valueOf(prefs.getMinWlanDbm()));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (!freezeChanges) {
            updateSettingsUI();
        }
    }
}

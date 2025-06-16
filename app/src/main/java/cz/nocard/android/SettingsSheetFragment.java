package cz.nocard.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ExistingWorkPolicy;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.Consumer;

import javax.inject.Inject;

import cz.nocard.android.databinding.SettingsSheetBinding;

public class SettingsSheetFragment extends BottomSheetDialogFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    NoCardPreferences prefs;
    @Inject
    WlanFencingManager wlanFencingManager;

    private SettingsSheetBinding binding;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
                prefs.putBackgroundCheckInterval(interval);
                if (interval != oldInterval) {
                    BackgroundWlanCheckWorker.scheduleWork(requireContext(), prefs, ExistingWorkPolicy.REPLACE);
                }
            } catch (NumberFormatException ignored) {
            }
        });

        registerEditTextListener(binding.etMinWifiSignal, val -> {
            try {
                int minSignal = Integer.parseInt(val);
                prefs.putMinWlanDbm(minSignal);
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private void showBackgroundScanUnsupportedError() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.background_scan_unsupported_title)
                .setMessage(R.string.background_scan_unsupported_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void registerEditTextListener(EditText et, Consumer<String> func) {
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                func.accept(s.toString());
            }
        });
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
        updateSettingsUI();
    }
}

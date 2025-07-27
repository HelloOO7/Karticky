package cz.nocard.android.ui.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public abstract class NfcActivityBase extends AppCompatActivity {

    protected NfcAdapter adapter;
    private BroadcastReceiver adapterStateChangedReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onCreateUI();

        adapter = NfcAdapter.getDefaultAdapter(this);

        if (adapter != null) {
            adapterStateChangedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int adapterState = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF);
                    switch (adapterState) {
                        case NfcAdapter.STATE_ON, NfcAdapter.STATE_TURNING_ON ->
                                onNfcAdapterEnabled();
                        default -> onNfcAdapterDisabled();
                    }
                }
            };
            registerReceiver(adapterStateChangedReceiver, new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));

            onNfcAdapterInitialized();
        } else {
            onNfcNotSupported();
        }
    }

    @CallSuper
    protected void onCreateUI() {

    }

    protected abstract void onNfcNotSupported();

    protected abstract void onNfcAdapterInitialized();
    protected abstract void onNfcAdapterEnabled();
    protected abstract void onNfcAdapterDisabled();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapterStateChangedReceiver != null) {
            unregisterReceiver(adapterStateChangedReceiver);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            onResumeNfc();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adapter != null) {
            onPauseNfc();
        }
    }

    @CallSuper
    protected void onResumeNfc() {

    }

    @CallSuper
    protected void onPauseNfc() {

    }

    protected void callNfcSettings() {
        startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
    }
}

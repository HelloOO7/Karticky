package cz.nocard.android;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.List;

import javax.inject.Inject;

import cz.nocard.android.databinding.ActivityNfcImportBinding;
import cz.spojenka.android.util.AsyncUtils;

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
        unregisterReceiver(adapterStateChangedReceiver);
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

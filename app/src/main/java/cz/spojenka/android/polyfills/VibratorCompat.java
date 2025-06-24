package cz.spojenka.android.polyfills;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.RequiresApi;

public class VibratorCompat {

    @SuppressWarnings("deprecation")
    public static void vibrate(Vibrator vibrator, long milliseconds) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect effect = VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                        effect,
                        createDefaultVibrationAttributes()
                );
            } else {
                vibrator.vibrate(
                        effect,
                        createDefaultAudioAttributes()
                );
            }
        } else {
            vibrator.vibrate(milliseconds);
        }
    }

    public static void vibrate(Vibrator vibrator, long[] timings) {
        vibrate(vibrator, timings, -1);
    }

    @SuppressWarnings("deprecation")
    public static void vibrate(Vibrator vibrator, long[] timings, int repeat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                    VibrationEffect.createWaveform(timings, repeat),
                    createDefaultVibrationAttributes()
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                    VibrationEffect.createWaveform(timings, repeat),
                    createDefaultAudioAttributes()
            );
        } else {
            vibrator.vibrate(timings, repeat);
        }
    }

    @SuppressWarnings("deprecation")
    public static Vibrator getDefaultVibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = context.getSystemService(VibratorManager.class);
            if (vibratorManager != null) {
                return vibratorManager.getDefaultVibrator();
            }
            return null;
        } else {
            return context.getSystemService(Vibrator.class);
        }
    }

    private static AudioAttributes createDefaultAudioAttributes() {
        return new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private static VibrationAttributes createDefaultVibrationAttributes() {
        return new VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                .build();
    }
}


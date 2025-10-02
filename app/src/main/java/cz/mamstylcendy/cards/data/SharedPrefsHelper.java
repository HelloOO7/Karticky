package cz.mamstylcendy.cards.data;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class SharedPrefsHelper {

    private static final String TAG = SharedPrefsHelper.class.getSimpleName();

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)
            .build();

    public static boolean saveObject(SharedPreferences prefs, String key, Object object) {
        return saveObject(prefs, key, object, false);
    }

    public static boolean saveObject(SharedPreferences prefs, String key, Object object, boolean async) {
        try {
            var json = JSON_MAPPER.writeValueAsString(object);
            var editor = prefs.edit().putString(key, json);
            if (async) {
                editor.apply();
                return true;
            } else {
                return editor.commit();
            }
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Failed to save object to shared preferences", e);
            return false;
        }
    }

    public static <T> T loadObject(SharedPreferences prefs, String key, Class<T> clazz) {
        var json = prefs.getString(key, null);
        if (json == null) {
            return null;
        }
        try {
            return JSON_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Failed to load object from shared preferences", e);
            return null;
        }
    }

    public static <E extends Enum<E>> E loadEnum(SharedPreferences prefs, String key, Class<E> clazz, E defaultValue) {
        var stringValue = prefs.getString(key, null);
        if (stringValue == null) {
            return defaultValue;
        }
        if (stringValue.isEmpty()) {
            return null; //empty string is reserved for null
        }
        try {
            return Enum.valueOf(clazz, stringValue);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to load enum from shared preferences", e);
            return defaultValue;
        }
    }

    public static <E extends Enum<E>> @NonNull E loadEnumNullSafe(SharedPreferences prefs, String key, Class<E> clazz, E defaultValue) {
        E val = loadEnum(prefs, key, clazz, defaultValue);
        return val != null ? val : defaultValue;
    }

    public static <E extends Enum<E>> boolean saveEnum(SharedPreferences prefs, String key, E value) {
        return prefs.edit().putString(key, value != null ? value.name() : "").commit();
    }
}

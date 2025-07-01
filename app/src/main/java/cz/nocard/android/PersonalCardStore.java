package cz.nocard.android;

import android.content.SharedPreferences;

import androidx.annotation.Keep;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PersonalCardStore {

    private static final String PREF_KEY = "personal_cards";
    private static final String ID_AUTOINCREMENT_PREF_KEY = "personal_card_id_autoincrement";

    private final SharedPreferences sharedPreferences;
    private List<PersonalCard> personalCards;
    private List<Listener> listeners = new ArrayList<>();

    public PersonalCardStore(NoCardPreferences preferences) {
        sharedPreferences = preferences.getPrefs();
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public int newCardId() {
        int id = sharedPreferences.getInt(ID_AUTOINCREMENT_PREF_KEY, 1);
        sharedPreferences.edit().putInt(ID_AUTOINCREMENT_PREF_KEY, id + 1).apply();
        return id;
    }

    public PersonalCard getCardById(int id) {
        return getPersonalCards().stream()
                .filter(card -> card.id() == id)
                .findFirst()
                .orElse(null);
    }

    public PersonalCard getCardForProvider(String providerId) {
        return getPersonalCards().stream()
                .filter(card -> card.provider().equals(providerId))
                .findFirst()
                .orElse(null);
    }

    public List<PersonalCard> getPersonalCards() {
        if (personalCards == null) {
            personalCards = SharedPrefsHelper.loadObject(sharedPreferences, PREF_KEY, SettingValueType.class);
        }
        if (personalCards == null) {
            personalCards = new SettingValueType();
        }
        return personalCards;
    }

    private void invokeListeners(Consumer<Listener> action) {
        for (Listener listener : listeners) {
            action.accept(listener);
        }
    }

    public void addCard(PersonalCard card) {
        getPersonalCards().add(card);
        persist();
        invokeListeners(listener -> listener.onCardAdded(card));
    }

    public void removeCard(PersonalCard card) {
        getPersonalCards().remove(card);
        persist();
        invokeListeners(listener -> listener.onCardRemoved(card));
    }

    private void invokeCardChanged(PersonalCard card) {
        invokeListeners(listener -> listener.onCardChanged(card));
    }

    public void renameCard(PersonalCard card, String newName) {
        card.rename(newName);
        persist();
        invokeCardChanged(card);
    }

    public void persist() {
        SharedPrefsHelper.saveObject(sharedPreferences, PREF_KEY, personalCards, true);
    }

    @Keep
    private static class SettingValueType extends ArrayList<PersonalCard> {

    }

    public static interface Listener {

        public void onCardAdded(PersonalCard card);
        public void onCardRemoved(PersonalCard card);
        public void onCardChanged(PersonalCard card);
    }
}
